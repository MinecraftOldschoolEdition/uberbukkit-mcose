package net.minecraft.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * UDP server for low-latency voice chat.
 * Handles client authentication, audio forwarding, and proximity-based broadcasting.
 */
public class VoiceChatUDPServer {
    
    private static final Logger log = Logger.getLogger("Minecraft");
    
    // Packet types
    private static final byte PACKET_AUTH = 0x01;
    private static final byte PACKET_AUTH_ACK = 0x02;
    private static final byte PACKET_MIC = 0x03;
    private static final byte PACKET_PLAYER_SOUND = 0x04;
    private static final byte PACKET_KEEP_ALIVE = 0x05;
    private static final byte PACKET_DISCONNECT = 0x06;
    private static final byte PACKET_STATE = 0x07;
    
    private final MinecraftServer server;
    private final int port;
    private DatagramSocket socket;
    private Thread receiveThread;
    private volatile boolean running;
    
    // Connected voice clients: secret UUID -> VoiceClient
    private final Map<UUID, VoiceClient> clients = new ConcurrentHashMap<>();
    // Player UUID -> VoiceClient mapping for quick lookup
    private final Map<UUID, VoiceClient> playerToClient = new ConcurrentHashMap<>();
    
    // Secret generation for authentication
    private final Map<UUID, UUID> playerSecrets = new ConcurrentHashMap<>();
    
    public VoiceChatUDPServer(MinecraftServer server, int port) {
        this.server = server;
        this.port = port;
    }
    
    /**
     * Generate a secret for a player to authenticate their voice connection.
     */
    public UUID generateSecret(UUID playerId) {
        UUID secret = UUID.randomUUID();
        playerSecrets.put(playerId, secret);
        return secret;
    }
    
    /**
     * Get the secret for a player (for sending to client).
     */
    public UUID getSecret(UUID playerId) {
        return playerSecrets.get(playerId);
    }
    
    public void start() throws SocketException {
        if (running) {
            return;
        }
        
        socket = new DatagramSocket(port);
        socket.setSoTimeout(100); // 100ms timeout for clean shutdown
        running = true;
        
        receiveThread = new Thread(this::receiveLoop, "VoiceChat-UDP");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }
    
    public void stop() {
        running = false;
        
        // Disconnect all clients
        for (VoiceClient client : clients.values()) {
            try {
                sendDisconnect(client, "Server shutting down");
            } catch (Exception ignored) {}
        }
        clients.clear();
        playerToClient.clear();
        playerSecrets.clear();
        
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        if (receiveThread != null) {
            try {
                receiveThread.join(1000);
            } catch (InterruptedException ignored) {}
        }
    }
    
    private void receiveLoop() {
        byte[] buffer = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        
        while (running) {
            try {
                socket.receive(packet);
                
                byte[] data = new byte[packet.getLength()];
                System.arraycopy(buffer, 0, data, 0, packet.getLength());
                
                handlePacket(data, packet.getAddress(), packet.getPort());
                
            } catch (SocketTimeoutException e) {
                // Normal timeout, continue
                cleanupStaleClients();
            } catch (IOException e) {
                if (running) {
                    log.warning("[VoiceChat] UDP receive error: " + e.getMessage());
                }
            }
        }
    }
    
    private void handlePacket(byte[] data, InetAddress address, int port) {
        if (data.length < 1) {
            return;
        }
        
        byte packetType = data[0];
        
        switch (packetType) {
            case PACKET_AUTH:
                handleAuth(data, address, port);
                break;
            case PACKET_MIC:
                handleMicPacket(data, address, port);
                break;
            case PACKET_KEEP_ALIVE:
                handleKeepAlive(data, address, port);
                break;
            case PACKET_STATE:
                handleStateUpdate(data, address, port);
                break;
        }
    }
    
    private void handleAuth(byte[] data, InetAddress address, int port) {
        if (data.length < 33) { // 1 byte type + 16 bytes player UUID + 16 bytes secret
            return;
        }
        
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.get(); // skip packet type
        
        long playerMsb = buf.getLong();
        long playerLsb = buf.getLong();
        UUID playerId = new UUID(playerMsb, playerLsb);
        
        long secretMsb = buf.getLong();
        long secretLsb = buf.getLong();
        UUID secret = new UUID(secretMsb, secretLsb);
        
        // Validate secret
        UUID expectedSecret = playerSecrets.get(playerId);
        if (expectedSecret == null || !expectedSecret.equals(secret)) {
            sendAuthAck(address, port, false, "Invalid secret");
            return;
        }
        
        // Find the player
        EntityPlayer player = findPlayerByUUID(playerId);
        if (player == null) {
            sendAuthAck(address, port, false, "Player not found");
            return;
        }
        
        // Register client
        VoiceClient client = new VoiceClient(playerId, player.name, address, port);
        clients.put(secret, client);
        playerToClient.put(playerId, client);
        
        log.info("[VoiceChat] Player " + player.name + " connected to voice chat from " + address);
        sendAuthAck(address, port, true, "OK");
    }
    
    private EntityPlayer findPlayerByUUID(UUID playerId) {
        for (Object obj : server.serverConfigurationManager.players) {
            if (obj instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) obj;
                // Try to match by Mojang UUID if available
                if (player.getMojangUUID() != null && player.getMojangUUID().equals(playerId)) {
                    return player;
                }
            }
        }
        return null;
    }
    
    private void handleMicPacket(byte[] data, InetAddress address, int port) {
        VoiceClient sender = findClientByAddress(address, port);
        if (sender == null || !sender.authenticated) {
            return;
        }
        
        sender.lastActivity = System.currentTimeMillis();
        
        if (data.length < 10) {
            return;
        }
        
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.get(); // skip packet type
        long sequence = buf.getLong();
        boolean whispering = buf.get() != 0;
        
        byte[] audioData = new byte[data.length - 10];
        buf.get(audioData);
        
        // Find the sending player
        EntityPlayer senderPlayer = findPlayerByUUID(sender.playerId);
        if (senderPlayer == null) {
            return;
        }
        
        // Broadcast to nearby players
        double maxDistance = server.getVoiceChatBroadcastRadius();
        
        for (VoiceClient recipient : clients.values()) {
            if (recipient.playerId.equals(sender.playerId)) {
                continue; // Don't send to self
            }
            
            EntityPlayer recipientPlayer = findPlayerByUUID(recipient.playerId);
            if (recipientPlayer == null || recipientPlayer.dimension != senderPlayer.dimension) {
                continue;
            }
            
            double distance = senderPlayer.g(recipientPlayer); // distance squared
            if (distance > maxDistance * maxDistance) {
                continue;
            }
            
            // Send audio to recipient
            sendPlayerSound(recipient, sender.playerId, sender.playerName, audioData, 
                           senderPlayer.locX, senderPlayer.locY, senderPlayer.locZ, 
                           (float) Math.sqrt(distance), (float) maxDistance, whispering);
        }
    }
    
    private void handleKeepAlive(byte[] data, InetAddress address, int port) {
        VoiceClient client = findClientByAddress(address, port);
        if (client != null) {
            client.lastActivity = System.currentTimeMillis();
            // Echo back
            try {
                DatagramPacket response = new DatagramPacket(data, data.length, address, port);
                socket.send(response);
            } catch (IOException ignored) {}
        }
    }
    
    private void handleStateUpdate(byte[] data, InetAddress address, int port) {
        VoiceClient client = findClientByAddress(address, port);
        if (client != null && data.length >= 4) {
            client.lastActivity = System.currentTimeMillis();
            ByteBuffer buf = ByteBuffer.wrap(data);
            buf.get(); // skip type
            client.talking = buf.get() != 0;
            client.muted = buf.get() != 0;
            client.deafened = buf.get() != 0;
        }
    }
    
    private VoiceClient findClientByAddress(InetAddress address, int port) {
        for (VoiceClient client : clients.values()) {
            if (client.address.equals(address) && client.port == port) {
                return client;
            }
        }
        return null;
    }
    
    private void sendAuthAck(InetAddress address, int port, boolean success, String message) {
        try {
            byte[] msgBytes = message.getBytes("UTF-8");
            ByteBuffer buf = ByteBuffer.allocate(2 + msgBytes.length);
            buf.put(PACKET_AUTH_ACK);
            buf.put((byte) (success ? 1 : 0));
            buf.put(msgBytes);
            
            DatagramPacket packet = new DatagramPacket(buf.array(), buf.position(), address, port);
            socket.send(packet);
        } catch (IOException e) {
            log.warning("[VoiceChat] Failed to send auth ack: " + e.getMessage());
        }
    }
    
    private void sendPlayerSound(VoiceClient recipient, UUID senderId, String senderName,
                                  byte[] audioData, double x, double y, double z,
                                  float distance, float maxDistance, boolean whispering) {
        try {
            byte[] nameBytes = senderName.getBytes("UTF-8");
            ByteBuffer buf = ByteBuffer.allocate(1 + 16 + 1 + nameBytes.length + 8 + 8 + 8 + 4 + 4 + 1 + audioData.length);
            
            buf.put(PACKET_PLAYER_SOUND);
            buf.putLong(senderId.getMostSignificantBits());
            buf.putLong(senderId.getLeastSignificantBits());
            buf.put((byte) nameBytes.length);
            buf.put(nameBytes);
            buf.putDouble(x);
            buf.putDouble(y);
            buf.putDouble(z);
            buf.putFloat(distance);
            buf.putFloat(maxDistance);
            buf.put((byte) (whispering ? 1 : 0));
            buf.put(audioData);
            
            DatagramPacket packet = new DatagramPacket(buf.array(), buf.position(), recipient.address, recipient.port);
            socket.send(packet);
        } catch (IOException e) {
            // Recipient may have disconnected
        }
    }
    
    private void sendDisconnect(VoiceClient client, String reason) {
        try {
            byte[] msgBytes = reason.getBytes("UTF-8");
            ByteBuffer buf = ByteBuffer.allocate(1 + msgBytes.length);
            buf.put(PACKET_DISCONNECT);
            buf.put(msgBytes);
            
            DatagramPacket packet = new DatagramPacket(buf.array(), buf.position(), client.address, client.port);
            socket.send(packet);
        } catch (IOException ignored) {}
    }
    
    private void cleanupStaleClients() {
        long now = System.currentTimeMillis();
        long timeout = 30000; // 30 seconds
        
        clients.entrySet().removeIf(entry -> {
            VoiceClient client = entry.getValue();
            if (now - client.lastActivity > timeout) {
                playerToClient.remove(client.playerId);
                log.info("[VoiceChat] Player " + client.playerName + " timed out from voice chat");
                return true;
            }
            return false;
        });
    }
    
    /**
     * Called when a player disconnects from the game.
     */
    public void onPlayerDisconnect(UUID playerId) {
        VoiceClient client = playerToClient.remove(playerId);
        if (client != null) {
            clients.values().remove(client);
            playerSecrets.remove(playerId);
        }
    }
    
    /**
     * Represents a connected voice client.
     */
    private static class VoiceClient {
        final UUID playerId;
        final String playerName;
        final InetAddress address;
        final int port;
        boolean authenticated = true;
        boolean talking = false;
        boolean muted = false;
        boolean deafened = false;
        long lastActivity = System.currentTimeMillis();
        
        VoiceClient(UUID playerId, String playerName, InetAddress address, int port) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.address = address;
            this.port = port;
        }
    }
}
