package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * UDP server for low-latency voice chat.
 * Uses the modern client VoicePacket wire format only.
 *
 * Architecture intentionally mirrors Simple Voice Chat:
 * socket read thread -> packet queue -> processing thread.
 */
public class VoiceChatUDPServer {

    private static final Logger log = Logger.getLogger("Minecraft");

    private static final byte PACKET_AUTH = 0x01;
    private static final byte PACKET_AUTH_ACK = 0x02;
    private static final byte PACKET_MIC = 0x03;
    private static final byte PACKET_PLAYER_SOUND = 0x04;
    private static final byte PACKET_KEEP_ALIVE = 0x05;
    private static final byte PACKET_STATE = 0x06;
    private static final byte PACKET_CONNECTION_CHECK = 0x07;

    private static final long CLIENT_TIMEOUT_MS = 30_000L;
    private static final long UNCHECKED_CLIENT_TIMEOUT_MS = 12_000L;
    private static final long MIC_PACKET_TTL_MS = 500L;
    private static final long PROCESS_POLL_TIMEOUT_MS = 10L;
    private static final long PROCESS_MAINTENANCE_INTERVAL_MS = 1000L;
    private static final int MAX_PACKET_QUEUE_SIZE = 1024;

    private static final double WHISPER_DISTANCE_MULTIPLIER = 0.25D;
    private static final long MIC_ATTEMPT_LOG_INTERVAL_MS = 1000L;
    private static final long MIC_DROP_LOG_INTERVAL_MS = 1500L;
    private static final long UNKNOWN_MIC_DROP_LOG_INTERVAL_MS = 3000L;
    private static final long QUEUE_DROP_LOG_INTERVAL_MS = 2000L;

    private final MinecraftServer server;
    private final int port;
    private DatagramSocket socket;
    private Thread receiveThread;
    private Thread processThread;
    private volatile boolean running;
    private volatile long lastUnknownMicDropLogAt = 0L;
    private volatile long lastQueueDropLogAt = 0L;

    private final BlockingQueue<ReceivedDatagram> packetQueue = new LinkedBlockingQueue<ReceivedDatagram>();

    // Connected voice clients: secret UUID -> VoiceClient
    private final Map<UUID, VoiceClient> clients = new ConcurrentHashMap<UUID, VoiceClient>();
    // Player UUID -> connected VoiceClient
    private final Map<UUID, VoiceClient> playerToClient = new ConcurrentHashMap<UUID, VoiceClient>();
    // Player UUID -> authenticated but not yet connection-checked VoiceClient
    private final Map<UUID, VoiceClient> uncheckedClients = new ConcurrentHashMap<UUID, VoiceClient>();
    // Address key ([ip]:port) -> last authenticated/connected VoiceClient
    private final Map<String, VoiceClient> addressToClient = new ConcurrentHashMap<String, VoiceClient>();

    // Secret generation for authentication
    private final Map<UUID, UUID> playerSecrets = new ConcurrentHashMap<UUID, UUID>();

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
        socket.setSoTimeout(100);
        running = true;

        receiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                receiveLoop();
            }
        }, "VoiceChat-UDP-Receive");
        receiveThread.setDaemon(true);
        receiveThread.start();

        processThread = new Thread(new Runnable() {
            @Override
            public void run() {
                processLoop();
            }
        }, "VoiceChat-UDP-Process");
        processThread.setDaemon(true);
        processThread.start();
    }

    public void stop() {
        running = false;

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (receiveThread != null) {
            receiveThread.interrupt();
            try {
                receiveThread.join(1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        if (processThread != null) {
            processThread.interrupt();
            try {
                processThread.join(1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        packetQueue.clear();
        clients.clear();
        playerToClient.clear();
        uncheckedClients.clear();
        addressToClient.clear();
        playerSecrets.clear();
    }

    private void receiveLoop() {
        byte[] buffer = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (running) {
            try {
                socket.receive(packet);

                byte[] data = new byte[packet.getLength()];
                System.arraycopy(buffer, 0, data, 0, packet.getLength());

                enqueuePacket(data, packet.getAddress(), packet.getPort(), System.currentTimeMillis());
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                if (running) {
                    log.warning("[VoiceChat] UDP receive error: " + e.getMessage());
                }
            }
        }
    }

    private void enqueuePacket(byte[] data, InetAddress address, int port, long receivedAt) {
        if (data == null || data.length < 1) {
            return;
        }
        if (packetQueue.size() >= MAX_PACKET_QUEUE_SIZE) {
            logQueueDrop(receivedAt, packetQueue.size());
            return;
        }
        packetQueue.offer(new ReceivedDatagram(data, address, port, receivedAt));
    }

    private void processLoop() {
        long lastMaintenanceAt = 0L;

        while (running) {
            try {
                ReceivedDatagram packet = packetQueue.poll(PROCESS_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                long now = System.currentTimeMillis();

                if (packet != null) {
                    handlePacket(packet.data, packet.address, packet.port, packet.receivedAt);
                }

                if (now - lastMaintenanceAt >= PROCESS_MAINTENANCE_INTERVAL_MS) {
                    cleanupStaleClients(now);
                    sendServerKeepAlives(now);
                    lastMaintenanceAt = now;
                }
            } catch (InterruptedException ignored) {
                if (!running) {
                    return;
                }
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                if (running) {
                    log.warning("[VoiceChat] UDP process error: " + t.getMessage());
                }
            }
        }
    }

    private void handlePacket(byte[] data, InetAddress address, int port, long receivedAt) {
        if (data == null || data.length < 1) {
            return;
        }

        switch (data[0]) {
            case PACKET_AUTH:
                handleAuth(data, address, port, receivedAt);
                break;
            case PACKET_MIC:
                handleMicPacket(data, address, port, receivedAt);
                break;
            case PACKET_KEEP_ALIVE:
                handleKeepAlive(data, address, port);
                break;
            case PACKET_STATE:
                handleStateUpdate(data, address, port);
                break;
            case PACKET_CONNECTION_CHECK:
                handleConnectionCheck(data, address, port);
                break;
            default:
                break;
        }
    }

    private void handleAuth(byte[] data, InetAddress address, int port, long receivedAt) {
        if (data.length < 33) {
            return;
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 1, data.length - 1));
            UUID playerId = readUUID(in);
            UUID secret = readUUID(in);

            UUID expectedSecret = playerSecrets.get(playerId);
            if (expectedSecret == null || !expectedSecret.equals(secret)) {
                sendAuthAck(address, port, false, "Invalid secret");
                return;
            }

            EntityPlayer player = findPlayerByUUID(playerId);
            if (player == null) {
                sendAuthAck(address, port, false, "Player not found");
                return;
            }

            VoiceClient existingConnected = playerToClient.get(playerId);
            if (existingConnected != null) {
                removeClient(existingConnected, false);
            }
            VoiceClient existingUnchecked = uncheckedClients.get(playerId);
            if (existingUnchecked != null) {
                removeClient(existingUnchecked, false);
            }

            VoiceClient sameAddress = findAnyClientByAddress(address, port);
            if (sameAddress != null && !playerId.equals(sameAddress.playerId)) {
                removeClient(sameAddress, false);
            }

            VoiceClient candidate = new VoiceClient(playerId, player.name, address, port, secret);
            candidate.lastActivity = receivedAt;
            uncheckedClients.put(playerId, candidate);
            addressToClient.put(addressKey(address, port), candidate);

            log.info("[VoiceChat] Player " + player.name + " authenticated for voice chat from " + address + ", waiting for connection check");
            sendAuthAck(address, port, true, "OK");
        } catch (IOException e) {
            sendAuthAck(address, port, false, "Malformed auth packet");
        }
    }

    private void handleMicPacket(byte[] data, InetAddress address, int port, long receivedAt) {
        long now = System.currentTimeMillis();

        VoiceClient sender = findConnectedClientByAddress(address, port);
        if (sender == null) {
            VoiceClient pending = findAnyClientByAddress(address, port);
            if (pending != null && !pending.validated) {
                pending.lastActivity = now;
                logMicDrop(pending, "not-validated", now);
                return;
            }
            logUnknownMicDrop(address, port, "unknown-client", now);
            return;
        }

        if (now - receivedAt > MIC_PACKET_TTL_MS) {
            logMicDrop(sender, "stale-ttl queueDelayMs=" + (now - receivedAt), now);
            return;
        }

        sender.lastActivity = now;

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 1, data.length - 1));
            long sequence = in.readLong();
            boolean whispering = in.readBoolean();
            int payloadLength = in.readUnsignedShort();
            if (payloadLength > Packet64Voice.MAX_PAYLOAD_SIZE) {
                logMicDrop(sender, "payload-too-large:" + payloadLength, now);
                return;
            }
            if (payloadLength > in.available()) {
                logMicDrop(sender, "truncated-payload expected=" + payloadLength + " available=" + in.available(), now);
                return;
            }
            byte[] audioData = new byte[payloadLength];
            if (payloadLength > 0) {
                in.readFully(audioData);
            }

            if (sender.lastSequence >= 0L && sequence <= sender.lastSequence) {
                logMicDrop(sender, "stale-sequence current=" + sequence + " last=" + sender.lastSequence, now);
                return;
            }
            sender.lastSequence = sequence;

            EntityPlayer senderPlayer = findPlayerByUUID(sender.playerId);
            if (senderPlayer == null) {
                logMicDrop(sender, "player-not-found", now);
                return;
            }
            if (uk.betacraft.uberbukkit.AdminRegistry.isMuted(senderPlayer.name)) {
                logMicDrop(sender, "player-muted", now);
                return;
            }

            boolean routeRoom = server.chatRoomManager.shouldRouteVoiceToRoom(senderPlayer);
            int recipients;
            if (routeRoom) {
                recipients = broadcastToRoom(senderPlayer, sequence, audioData, whispering);
            } else {
                recipients = broadcastByProximity(senderPlayer, sequence, audioData, whispering);
            }
            logMicAttempt(sender, sequence, payloadLength, whispering, routeRoom ? "room" : "proximity", recipients, now);
        } catch (IOException e) {
            logMicDrop(sender, "malformed-mic-packet:" + e.getMessage(), now);
        }
    }

    private int broadcastToRoom(EntityPlayer senderPlayer, long sequence, byte[] audioData, boolean whispering) {
        List<EntityPlayer> recipients = server.chatRoomManager.getVoiceRoomRecipients(senderPlayer);
        if (recipients.isEmpty()) {
            return 0;
        }

        double whisperDistance = server.getVoiceChatBroadcastRadius() * WHISPER_DISTANCE_MULTIPLIER;
        double whisperDistanceSq = whisperDistance * whisperDistance;
        int delivered = 0;

        for (EntityPlayer recipientPlayer : recipients) {
            VoiceClient recipient = getClientForPlayer(recipientPlayer);
            if (recipient == null) {
                continue;
            }

            float packetDistance = -1.0F;
            if (whispering) {
                if (recipientPlayer.dimension != senderPlayer.dimension) {
                    continue;
                }
                double distanceSq = senderPlayer.g(recipientPlayer);
                if (distanceSq > whisperDistanceSq) {
                    continue;
                }
                packetDistance = (float) Math.sqrt(distanceSq);
            }

            if (sendPlayerSound(recipient, senderPlayer.getMojangUUID(), sequence, audioData, whispering, packetDistance)) {
                delivered++;
            }
        }
        return delivered;
    }

    private int broadcastByProximity(EntityPlayer senderPlayer, long sequence, byte[] audioData, boolean whispering) {
        double maxDistance = server.getVoiceChatBroadcastRadius();
        if (whispering) {
            maxDistance *= WHISPER_DISTANCE_MULTIPLIER;
        }
        double maxDistanceSq = maxDistance * maxDistance;
        int delivered = 0;

        for (VoiceClient recipient : clients.values()) {
            if (recipient.playerId.equals(senderPlayer.getMojangUUID())) {
                continue;
            }

            EntityPlayer recipientPlayer = findPlayerByUUID(recipient.playerId);
            if (recipientPlayer == null || recipientPlayer.dimension != senderPlayer.dimension) {
                continue;
            }

            double distanceSq = senderPlayer.g(recipientPlayer);
            if (distanceSq > maxDistanceSq) {
                continue;
            }

            if (sendPlayerSound(
                recipient,
                senderPlayer.getMojangUUID(),
                sequence,
                audioData,
                whispering,
                (float) Math.sqrt(distanceSq)
            )) {
                delivered++;
            }
        }
        return delivered;
    }

    private VoiceClient getClientForPlayer(EntityPlayer player) {
        if (player == null || player.getMojangUUID() == null) {
            return null;
        }
        return playerToClient.get(player.getMojangUUID());
    }

    private void handleKeepAlive(byte[] data, InetAddress address, int port) {
        VoiceClient client = findAnyClientByAddress(address, port);
        if (client == null) {
            return;
        }
        client.lastActivity = System.currentTimeMillis();
    }

    private void handleStateUpdate(byte[] data, InetAddress address, int port) {
        VoiceClient client = findAnyClientByAddress(address, port);
        if (client == null) {
            return;
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 1, data.length - 1));
            UUID packetPlayerId = readUUID(in);
            if (!client.playerId.equals(packetPlayerId)) {
                return;
            }
            client.talking = in.readBoolean();
            client.muted = in.readBoolean();
            client.deafened = in.readBoolean();
            client.lastActivity = System.currentTimeMillis();
        } catch (IOException ignored) {
        }
    }

    private void handleConnectionCheck(byte[] data, InetAddress address, int port) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 1, data.length - 1));
            long id = in.readLong();
            boolean response = in.readBoolean();

            VoiceClient client = findAnyClientByAddress(address, port);
            if (client == null) {
                return;
            }

            client.lastActivity = System.currentTimeMillis();
            if (!response) {
                if (!client.validated) {
                    promoteValidatedClient(client);
                }
                sendConnectionCheck(address, port, id, true);
            }
        } catch (IOException ignored) {
        }
    }

    private void promoteValidatedClient(VoiceClient client) {
        if (client == null || client.validated) {
            return;
        }

        client.validated = true;
        uncheckedClients.remove(client.playerId, client);

        VoiceClient replaced = playerToClient.put(client.playerId, client);
        if (replaced != null && replaced != client) {
            removeClient(replaced, false);
        }

        clients.put(client.secret, client);
        addressToClient.put(addressKey(client.address, client.port), client);
        log.info("[VoiceChat] Player " + client.playerName + " connected to voice chat from " + client.address);
    }

    private VoiceClient findConnectedClientByAddress(InetAddress address, int port) {
        VoiceClient client = findAnyClientByAddress(address, port);
        if (client == null || !client.validated) {
            return null;
        }
        return client;
    }

    private VoiceClient findAnyClientByAddress(InetAddress address, int port) {
        if (address == null) {
            return null;
        }
        return addressToClient.get(addressKey(address, port));
    }

    private String addressKey(InetAddress address, int port) {
        if (address == null) {
            return "[unknown]:" + port;
        }
        return "[" + address.getHostAddress() + "]:" + port;
    }

    private void sendAuthAck(InetAddress address, int port, boolean success, String message) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeByte(PACKET_AUTH_ACK);
            dos.writeBoolean(success);
            dos.writeUTF(message != null ? message : "");

            byte[] payload = bos.toByteArray();
            DatagramPacket packet = new DatagramPacket(payload, payload.length, address, port);
            socket.send(packet);
        } catch (IOException e) {
            log.warning("[VoiceChat] Failed to send auth ack: " + e.getMessage());
        }
    }

    private void sendServerKeepAlives(long now) {
        for (VoiceClient client : playerToClient.values()) {
            if (client == null || !client.validated) {
                continue;
            }
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos);
                dos.writeByte(PACKET_KEEP_ALIVE);
                dos.writeLong(now);
                byte[] payload = bos.toByteArray();
                DatagramPacket packet = new DatagramPacket(payload, payload.length, client.address, client.port);
                socket.send(packet);
            } catch (IOException ignored) {
            }
        }
    }

    private boolean sendPlayerSound(VoiceClient recipient, UUID senderId, long sequence, byte[] audioData, boolean whispering, float distance) {
        if (recipient == null || senderId == null || audioData == null) {
            return false;
        }
        if (audioData.length > Packet64Voice.MAX_PAYLOAD_SIZE) {
            return false;
        }

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeByte(PACKET_PLAYER_SOUND);
            writeUUID(dos, senderId);
            dos.writeLong(sequence);
            dos.writeBoolean(whispering);
            dos.writeFloat(distance);
            dos.writeShort(audioData.length);
            if (audioData.length > 0) {
                dos.write(audioData);
            }

            byte[] payload = bos.toByteArray();
            DatagramPacket packet = new DatagramPacket(payload, payload.length, recipient.address, recipient.port);
            socket.send(packet);
            return true;
        } catch (IOException e) {
            log.warning("[VoiceChat][ServerTx] Failed forwarding voice packet to " + recipient.playerName + " seq=" + sequence + ": " + e.getMessage());
            return false;
        }
    }

    private void logMicAttempt(VoiceClient sender, long sequence, int payloadLength, boolean whispering, String route, int recipients, long now) {
        if (now - sender.lastTransmitLogAt < MIC_ATTEMPT_LOG_INTERVAL_MS) {
            return;
        }
        sender.lastTransmitLogAt = now;
        log.info(
            "[VoiceChat][ServerRx] Voice transmit attempt player=" + sender.playerName +
            ", seq=" + sequence +
            ", bytes=" + payloadLength +
            ", whisper=" + whispering +
            ", route=" + route +
            ", recipients=" + recipients
        );
    }

    private void logMicDrop(VoiceClient sender, String reason, long now) {
        if (sender == null) {
            return;
        }
        if (now - sender.lastTransmitDropLogAt < MIC_DROP_LOG_INTERVAL_MS) {
            return;
        }
        sender.lastTransmitDropLogAt = now;
        log.info("[VoiceChat][ServerRx] Dropped voice transmit from " + sender.playerName + ", reason=" + reason);
    }

    private void logUnknownMicDrop(InetAddress address, int port, String reason, long now) {
        if (now - this.lastUnknownMicDropLogAt < UNKNOWN_MIC_DROP_LOG_INTERVAL_MS) {
            return;
        }
        this.lastUnknownMicDropLogAt = now;
        log.info("[VoiceChat][ServerRx] Dropped voice transmit from " + address.getHostAddress() + ":" + port + ", reason=" + reason);
    }

    private void logQueueDrop(long now, int queueSize) {
        if (now - this.lastQueueDropLogAt < QUEUE_DROP_LOG_INTERVAL_MS) {
            return;
        }
        this.lastQueueDropLogAt = now;
        log.warning("[VoiceChat][ServerRx] Dropping incoming UDP packet: queue-overflow size=" + queueSize);
    }

    private void sendConnectionCheck(InetAddress address, int port, long id, boolean response) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeByte(PACKET_CONNECTION_CHECK);
            dos.writeLong(id);
            dos.writeBoolean(response);

            byte[] payload = bos.toByteArray();
            DatagramPacket packet = new DatagramPacket(payload, payload.length, address, port);
            socket.send(packet);
        } catch (IOException ignored) {
        }
    }

    private void cleanupStaleClients(long now) {
        Iterator<Map.Entry<UUID, VoiceClient>> connectedIterator = playerToClient.entrySet().iterator();
        while (connectedIterator.hasNext()) {
            Map.Entry<UUID, VoiceClient> entry = connectedIterator.next();
            VoiceClient client = entry.getValue();
            if (client == null) {
                connectedIterator.remove();
                continue;
            }
            if (now - client.lastActivity > CLIENT_TIMEOUT_MS) {
                removeClient(client, false);
                log.info("[VoiceChat] Player " + client.playerName + " timed out from voice chat");
            }
        }

        Iterator<Map.Entry<UUID, VoiceClient>> uncheckedIterator = uncheckedClients.entrySet().iterator();
        while (uncheckedIterator.hasNext()) {
            Map.Entry<UUID, VoiceClient> entry = uncheckedIterator.next();
            VoiceClient client = entry.getValue();
            if (client == null) {
                uncheckedIterator.remove();
                continue;
            }
            if (now - client.lastActivity > UNCHECKED_CLIENT_TIMEOUT_MS) {
                removeClient(client, false);
            }
        }
    }

    private void removeClient(VoiceClient client, boolean removeSecret) {
        if (client == null) {
            return;
        }
        addressToClient.remove(addressKey(client.address, client.port), client);
        uncheckedClients.remove(client.playerId, client);
        playerToClient.remove(client.playerId, client);
        if (client.secret != null) {
            clients.remove(client.secret, client);
        }
        if (removeSecret) {
            playerSecrets.remove(client.playerId);
        }
    }

    /**
     * Called when a player disconnects from the game.
     */
    public void onPlayerDisconnect(UUID playerId) {
        VoiceClient connected = playerToClient.get(playerId);
        if (connected != null) {
            removeClient(connected, false);
        }

        VoiceClient pending = uncheckedClients.get(playerId);
        if (pending != null) {
            removeClient(pending, false);
        }

        playerSecrets.remove(playerId);
    }

    private EntityPlayer findPlayerByUUID(UUID playerId) {
        for (Object obj : server.serverConfigurationManager.players) {
            if (!(obj instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) obj;
            if (player.getMojangUUID() != null && player.getMojangUUID().equals(playerId)) {
                return player;
            }
        }
        return null;
    }

    private static UUID readUUID(DataInputStream in) throws IOException {
        long msb = in.readLong();
        long lsb = in.readLong();
        return new UUID(msb, lsb);
    }

    private static void writeUUID(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static class ReceivedDatagram {
        final byte[] data;
        final InetAddress address;
        final int port;
        final long receivedAt;

        ReceivedDatagram(byte[] data, InetAddress address, int port, long receivedAt) {
            this.data = data;
            this.address = address;
            this.port = port;
            this.receivedAt = receivedAt;
        }
    }

    /**
     * Represents a connected/authenticated voice client.
     */
    private static class VoiceClient {
        final UUID playerId;
        final String playerName;
        final InetAddress address;
        final int port;
        final UUID secret;

        boolean validated = false;
        boolean talking = false;
        boolean muted = false;
        boolean deafened = false;

        long lastActivity = System.currentTimeMillis();
        long lastSequence = -1L;
        long lastTransmitLogAt = 0L;
        long lastTransmitDropLogAt = 0L;

        VoiceClient(UUID playerId, String playerName, InetAddress address, int port, UUID secret) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.address = address;
            this.port = port;
            this.secret = secret;
        }
    }
}
