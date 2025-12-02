package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles server-side friend verification when both players are on the same
 * online-mode=true server. This saves API costs by verifying friendships locally
 * when possible.
 * 
 * Protocol:
 * - MCOSE|FQUERY: Client asks if a friend UUID is online on this server
 * - MCOSE|FONLINE: Server responds with online status and player info
 * - MCOSE|FVERIFY: Client requests mutual verification with another player
 * - MCOSE|FCONFIRM: Server confirms both players have each other as friends
 */
public class FriendsVerificationHandler {
    
    private static final Logger log = Logger.getLogger("Minecraft");
    
    // Channel names for friend verification packets
    public static final String CHANNEL_QUERY = "MCOSE|FQUERY";    // Query if friend is online
    public static final String CHANNEL_ONLINE = "MCOSE|FONLINE";  // Response: friend online status
    public static final String CHANNEL_VERIFY = "MCOSE|FVERIFY";  // Request mutual verification
    public static final String CHANNEL_CONFIRM = "MCOSE|FCONFIRM"; // Confirmation of mutual friendship
    
    // Track pending verification requests: requesterUUID -> targetUUID
    private static final Map<String, String> pendingVerifications = new ConcurrentHashMap<String, String>();
    
    // Track which players have added which friends (for mutual verification)
    // playerUUID -> Map<friendUUID, timestamp>
    private static final Map<String, Map<String, Long>> friendClaims = new ConcurrentHashMap<String, Map<String, Long>>();
    
    private final MinecraftServer server;
    
    public FriendsVerificationHandler(MinecraftServer server) {
        this.server = server;
    }
    
    /**
     * Handle incoming custom payload packet for friends verification.
     * Returns true if the packet was handled, false otherwise.
     */
    public boolean handlePacket(EntityPlayer player, Packet250CustomPayload packet) {
        if (packet.channel == null || packet.data == null) {
            return false;
        }
        
        // Only handle friends verification on online-mode servers
        if (!server.onlineMode) {
            return false;
        }
        
        try {
            switch (packet.channel) {
                case CHANNEL_QUERY:
                    return handleFriendQuery(player, packet.data);
                case CHANNEL_VERIFY:
                    return handleVerifyRequest(player, packet.data);
                default:
                    return false;
            }
        } catch (Exception e) {
            log.warning("[FriendsVerify] Error handling packet: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Handle FQUERY: Client asking if a friend UUID is online on this server
     */
    private boolean handleFriendQuery(EntityPlayer requester, byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        String friendUuid = in.readUTF();
        
        // Normalize UUID (remove dashes, lowercase)
        friendUuid = normalizeUuid(friendUuid);
        String requesterUuid = normalizeUuid(getPlayerUuid(requester));
        
        // Find the friend on this server
        EntityPlayer friend = findPlayerByUuid(friendUuid);
        
        // Send response
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF(friendUuid);
        out.writeBoolean(friend != null);
        
        if (friend != null) {
            out.writeUTF(friend.name); // Their current username
        }
        
        Packet250CustomPayload response = new Packet250CustomPayload(CHANNEL_ONLINE, baos.toByteArray());
        requester.netServerHandler.sendPacket(response);
        
        return true;
    }
    
    /**
     * Handle FVERIFY: Client requesting mutual verification with another player
     * The client sends their friend claim, and we check if the other player also has them
     */
    private boolean handleVerifyRequest(EntityPlayer requester, byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        String friendUuid = in.readUTF();
        
        // Normalize UUIDs
        friendUuid = normalizeUuid(friendUuid);
        String requesterUuid = normalizeUuid(getPlayerUuid(requester));
        
        if (requesterUuid == null) {
            return false;
        }
        
        // Record that this player claims this friend
        Map<String, Long> claims = friendClaims.get(requesterUuid);
        if (claims == null) {
            claims = new ConcurrentHashMap<String, Long>();
            friendClaims.put(requesterUuid, claims);
        }
        claims.put(friendUuid, System.currentTimeMillis());
        
        // Check if the friend is online and has also claimed the requester
        EntityPlayer friend = findPlayerByUuid(friendUuid);
        boolean mutuallyVerified = false;
        
        if (friend != null) {
            Map<String, Long> friendsClaims = friendClaims.get(friendUuid);
            if (friendsClaims != null && friendsClaims.containsKey(requesterUuid)) {
                // Both players have each other as friends!
                mutuallyVerified = true;
                
                // Notify both players of the mutual verification
                sendVerificationConfirm(requester, friendUuid, friend.name, true);
                sendVerificationConfirm(friend, requesterUuid, requester.name, true);
                
                log.info("[FriendsVerify] Mutual verification: " + requester.name + " <-> " + friend.name);
            } else {
                // Friend is online but hasn't added requester yet
                // Store pending verification
                pendingVerifications.put(requesterUuid, friendUuid);
                
                // Tell requester: friend is online but not verified yet
                sendVerificationConfirm(requester, friendUuid, friend.name, false);
            }
        } else {
            // Friend not online - tell requester
            sendVerificationConfirm(requester, friendUuid, null, false);
        }
        
        return true;
    }
    
    /**
     * Send verification confirmation to a player
     */
    private void sendVerificationConfirm(EntityPlayer player, String friendUuid, String friendName, boolean verified) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF(friendUuid);
            out.writeBoolean(verified);
            out.writeBoolean(friendName != null); // Is friend online?
            if (friendName != null) {
                out.writeUTF(friendName);
            }
            
            Packet250CustomPayload packet = new Packet250CustomPayload(CHANNEL_CONFIRM, baos.toByteArray());
            player.netServerHandler.sendPacket(packet);
        } catch (IOException e) {
            log.warning("[FriendsVerify] Failed to send confirm: " + e.getMessage());
        }
    }
    
    /**
     * Called when a player joins - check for pending verifications
     */
    public void onPlayerJoin(EntityPlayer player) {
        if (!server.onlineMode) return;
        
        String playerUuid = normalizeUuid(getPlayerUuid(player));
        if (playerUuid == null) return;
        
        // Check if anyone was waiting to verify with this player
        for (Map.Entry<String, String> pending : pendingVerifications.entrySet()) {
            if (pending.getValue().equals(playerUuid)) {
                String requesterUuid = pending.getKey();
                EntityPlayer requester = findPlayerByUuid(requesterUuid);
                
                if (requester != null) {
                    // Notify requester that their friend is now online
                    sendVerificationConfirm(requester, playerUuid, player.name, false);
                }
            }
        }
    }
    
    /**
     * Called when a player leaves - clean up their data
     */
    public void onPlayerLeave(EntityPlayer player) {
        String playerUuid = normalizeUuid(getPlayerUuid(player));
        if (playerUuid == null) return;
        
        // Remove pending verifications
        pendingVerifications.remove(playerUuid);
        
        // Don't remove friend claims immediately - they might rejoin
        // Claims will be cleaned up periodically or on server restart
    }
    
    /**
     * Find a player by their UUID
     */
    private EntityPlayer findPlayerByUuid(String uuid) {
        uuid = normalizeUuid(uuid);
        for (Object obj : server.serverConfigurationManager.players) {
            if (obj instanceof EntityPlayer) {
                EntityPlayer p = (EntityPlayer) obj;
                String pUuid = normalizeUuid(getPlayerUuid(p));
                if (uuid.equals(pUuid)) {
                    return p;
                }
            }
        }
        return null;
    }
    
    /**
     * Get a player's UUID from their session
     */
    private String getPlayerUuid(EntityPlayer player) {
        // Try to get UUID from the player's entity UUID first
        if (player.uniqueId != null) {
            return player.uniqueId.toString();
        }
        
        // Fallback: try to get from CraftPlayer
        try {
            if (player.getBukkitEntity() instanceof org.bukkit.craftbukkit.entity.CraftPlayer) {
                org.bukkit.craftbukkit.entity.CraftPlayer cp = 
                    (org.bukkit.craftbukkit.entity.CraftPlayer) player.getBukkitEntity();
                if (cp.getUniqueId() != null) {
                    return cp.getUniqueId().toString();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return null;
    }
    
    /**
     * Normalize UUID to lowercase without dashes
     */
    private String normalizeUuid(String uuid) {
        if (uuid == null) return null;
        return uuid.replace("-", "").toLowerCase();
    }
    
    /**
     * Check if this server supports friends verification (online-mode only)
     */
    public boolean isSupported() {
        return server.onlineMode;
    }
    
    /**
     * Clean up old friend claims (older than 24 hours)
     * Should be called periodically
     */
    public void cleanupOldClaims() {
        long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24 hours
        
        for (Map<String, Long> claims : friendClaims.values()) {
            claims.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }
        
        // Remove empty claim maps
        friendClaims.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}

