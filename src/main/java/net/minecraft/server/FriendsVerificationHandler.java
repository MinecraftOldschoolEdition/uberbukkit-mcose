package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;

/**
 * Handles server-side friend verification for online-mode servers.
 * 
 * This system allows verification WITHOUT using the web API when possible:
 * 1. Both players on same server → Instant mutual verification
 * 2. One player on server, other in menu → Menu player can ping this server directly
 * 3. Neither on a server → Fall back to web API (client-side)
 * 
 * Friend claims are persisted so verification can happen even if players
 * aren't online at the same time.
 * 
 * Protocol:
 * - MCOSE|FQUERY: Client asks if a friend UUID is online on this server
 * - MCOSE|FONLINE: Server responds with online status and player info
 * - MCOSE|FVERIFY: Client requests mutual verification with another player
 * - MCOSE|FCONFIRM: Server confirms verification result
 * - MCOSE|FCLAIM: Client registers their friend claim with the server
 * - MCOSE|FCHECK: Client checks if their friend has claimed them (for external clients)
 */
public class FriendsVerificationHandler {
    
    private static final Logger log = Logger.getLogger("Minecraft");
    
    // Channel names for friend verification packets
    public static final String CHANNEL_QUERY = "MCOSE|FQUERY";    // Query if friend is online
    public static final String CHANNEL_ONLINE = "MCOSE|FONLINE";  // Response: friend online status
    public static final String CHANNEL_VERIFY = "MCOSE|FVERIFY";  // Request mutual verification
    public static final String CHANNEL_CONFIRM = "MCOSE|FCONFIRM"; // Confirmation of mutual friendship
    public static final String CHANNEL_CLAIM = "MCOSE|FCLAIM";    // Register a friend claim
    public static final String CHANNEL_CHECK = "MCOSE|FCHECK";    // Check if friend has claimed us

    static final int MAX_CLAIMS_PER_PLAYER = 256;
    static final long CLAIM_TTL_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final long CLAIM_CLEANUP_INTERVAL_MS = 60000L;
    private static final long MAX_FUTURE_TIMESTAMP_SKEW_MS = 5L * 60L * 1000L;
    
    // Track which players have added which friends (for mutual verification)
    // playerUUID -> Map<friendUUID, claimData>
    private final Map<String, Map<String, FriendClaim>> friendClaims = new ConcurrentHashMap<String, Map<String, FriendClaim>>();
    
    private final MinecraftServer server;
    private final File claimsFile;
    private long lastSaveTime = 0;
    private static final long SAVE_INTERVAL = 60000; // Save every 60 seconds if dirty
    private boolean dirty = false;
    private long lastCleanupTime = 0L;
    
    public FriendsVerificationHandler(MinecraftServer server) {
        this.server = server;
        this.claimsFile = new File("friends_claims.json");
        loadClaims();
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

        cleanupExpiredClaimsIfNeeded();
        
        try {
            switch (packet.channel) {
                case CHANNEL_QUERY:
                    return handleFriendQuery(player, packet.data);
                case CHANNEL_VERIFY:
                    return handleVerifyRequest(player, packet.data);
                case CHANNEL_CLAIM:
                    return handleClaimRequest(player, packet.data);
                case CHANNEL_CHECK:
                    return handleCheckRequest(player, packet.data);
                default:
                    return false;
            }
        } catch (Exception e) {
            log.warning("[FriendsVerify] Error handling packet: " + e.getMessage());
            return false;
        }
    }

    static boolean isClientRequestChannel(String channel) {
        return CHANNEL_QUERY.equals(channel)
                || CHANNEL_VERIFY.equals(channel)
                || CHANNEL_CLAIM.equals(channel)
                || CHANNEL_CHECK.equals(channel);
    }
    
    /**
     * Handle unauthenticated verification request (from players not fully logged in).
     * This allows players in the menu to verify against this server.
     */
    public boolean handleUnauthenticatedPacket(NetworkManager networkManager, Packet250CustomPayload packet) {
        if (packet.channel == null || packet.data == null) {
            return false;
        }
        
        if (!server.onlineMode) {
            return false;
        }
        
        try {
            switch (packet.channel) {
                case CHANNEL_CHECK:
                    return handleUnauthenticatedCheck(networkManager, packet.data);
                default:
                    return false;
            }
        } catch (Exception e) {
            log.warning("[FriendsVerify] Error handling unauthenticated packet: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Handle FQUERY: Client asking if a friend UUID is online on this server
     */
    private boolean handleFriendQuery(EntityPlayer requester, byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        String friendUuid = PacketLimits.readUtf(in, PacketLimits.MAX_UUID_CHARS, "friend UUID");
        
        friendUuid = normalizeUuid(friendUuid);
        if (friendUuid == null || in.available() != 0) {
            return false;
        }
        
        // Find the friend on this server
        EntityPlayer friend = findPlayerByUuid(friendUuid);
        
        // Send response
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF(friendUuid);
        out.writeBoolean(friend != null);
        
        if (friend != null) {
            out.writeUTF(friend.name);
        }
        
        Packet250CustomPayload response = new Packet250CustomPayload(CHANNEL_ONLINE, baos.toByteArray());
        requester.netServerHandler.sendPacket(response);
        
        return true;
    }
    
    /**
     * Handle FCLAIM: Client registering their friend claim with this server
     */
    private boolean handleClaimRequest(EntityPlayer requester, byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        String friendUuid = PacketLimits.readUtf(in, PacketLimits.MAX_UUID_CHARS, "friend UUID");
        String signature = PacketLimits.readUtf(in, PacketLimits.MAX_SIGNATURE_CHARS, "friend signature");
        long addedAt = in.readLong();
        String publicKey = PacketLimits.readUtf(in, PacketLimits.MAX_PUBLIC_KEY_CHARS, "friend public key");
        
        String requesterUuid = normalizeUuid(getPlayerUuid(requester));
        friendUuid = normalizeUuid(friendUuid);
        
        if (requesterUuid == null || friendUuid == null || requesterUuid.equals(friendUuid)
                || in.available() != 0 || !isValidClaimProof(signature, publicKey)) {
            return false;
        }
        
        // Store the claim
        FriendClaim claim = new FriendClaim(requesterUuid, friendUuid, requester.name, signature, addedAt, publicKey);
        
        if (!storeClaim(requesterUuid, friendUuid, claim)) {
            return false;
        }
        dirty = true;
        
        log.fine("[FriendsVerify] Stored claim: " + requester.name + " -> " + friendUuid);
        
        // Check if we can verify immediately (friend has also claimed us)
        checkAndNotifyMutual(requesterUuid, friendUuid, requester);
        
        return true;
    }
    
    /**
     * Handle FVERIFY: Client requesting mutual verification
     */
    private boolean handleVerifyRequest(EntityPlayer requester, byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        String friendUuid = PacketLimits.readUtf(in, PacketLimits.MAX_UUID_CHARS, "friend UUID");
        
        // Older clients sent only the UUID. A present proof must be complete
        // and canonical; partial data is rejected rather than downgraded.
        String signature = null;
        long addedAt = 0L;
        String publicKey = null;
        boolean hasClaimData = in.available() > 0;
        if (hasClaimData) {
            signature = PacketLimits.readUtf(in, PacketLimits.MAX_SIGNATURE_CHARS, "friend signature");
            addedAt = in.readLong();
            publicKey = PacketLimits.readUtf(in, PacketLimits.MAX_PUBLIC_KEY_CHARS, "friend public key");
        }
        
        friendUuid = normalizeUuid(friendUuid);
        String requesterUuid = normalizeUuid(getPlayerUuid(requester));
        
        if (requesterUuid == null || friendUuid == null || requesterUuid.equals(friendUuid)
                || in.available() != 0 || hasClaimData && !isValidClaimProof(signature, publicKey)) {
            return false;
        }
        
        // Store/update the claim if we got claim data
        if (hasClaimData) {
            FriendClaim claim = new FriendClaim(requesterUuid, friendUuid, requester.name, signature, addedAt, publicKey);
            if (!storeClaim(requesterUuid, friendUuid, claim)) {
                return false;
            }
            dirty = true;
        }
        
        // Check for mutual verification
        checkAndNotifyMutual(requesterUuid, friendUuid, requester);
        
        return true;
    }
    
    /**
     * Handle FCHECK: Client checking if a friend has claimed them
     */
    private boolean handleCheckRequest(EntityPlayer requester, byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        String friendUuid = PacketLimits.readUtf(in, PacketLimits.MAX_UUID_CHARS, "friend UUID");
        
        friendUuid = normalizeUuid(friendUuid);
        String requesterUuid = normalizeUuid(getPlayerUuid(requester));
        
        if (requesterUuid == null || friendUuid == null || in.available() != 0) {
            return false;
        }
        
        // Check if friend has claimed requester
        FriendClaim friendsClaim = getClaimFromTo(friendUuid, requesterUuid);
        
        sendCheckResponse(requester, friendUuid, friendsClaim);
        
        return true;
    }
    
    /**
     * Handle unauthenticated check - for players verifying without fully joining
     */
    private boolean handleUnauthenticatedCheck(NetworkManager networkManager, byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        String requesterUuid = PacketLimits.readUtf(in, PacketLimits.MAX_UUID_CHARS, "requester UUID");
        String friendUuid = PacketLimits.readUtf(in, PacketLimits.MAX_UUID_CHARS, "friend UUID");
        
        requesterUuid = normalizeUuid(requesterUuid);
        friendUuid = normalizeUuid(friendUuid);
        if (requesterUuid == null || friendUuid == null || requesterUuid.equals(friendUuid)) {
            return false;
        }

        // Legacy menu verification may append its own claim proof. Validate it
        // for shape but never store it because this socket is unauthenticated.
        if (in.available() > 0) {
            String signature = PacketLimits.readUtf(in, PacketLimits.MAX_SIGNATURE_CHARS, "friend signature");
            in.readLong();
            String publicKey = PacketLimits.readUtf(in, PacketLimits.MAX_PUBLIC_KEY_CHARS, "friend public key");
            if (!isValidClaimProof(signature, publicKey)) {
                return false;
            }
        }
        if (in.available() != 0) {
            return false;
        }
        
        // Check if friend has claimed requester
        FriendClaim friendsClaim = getClaimFromTo(friendUuid, requesterUuid);
        
        // Check if requester has claimed friend (they might have sent claim data too)
        FriendClaim requestersClaim = getClaimFromTo(requesterUuid, friendUuid);
        
        boolean mutual = friendsClaim != null && requestersClaim != null;
        
        // Send response directly through network manager
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF(friendUuid);
        out.writeBoolean(mutual);
        out.writeBoolean(friendsClaim != null); // Friend has claimed us
        
        if (friendsClaim != null) {
            out.writeUTF(friendsClaim.claimerName);
            out.writeUTF(friendsClaim.signature);
            out.writeLong(friendsClaim.addedAt);
            out.writeUTF(friendsClaim.publicKey);
        }
        
        Packet250CustomPayload response = new Packet250CustomPayload(CHANNEL_CONFIRM, baos.toByteArray());
        networkManager.queue(response);
        
        if (mutual) {
            log.fine("[FriendsVerify] Unauthenticated mutual verification: " + requesterUuid + " <-> " + friendUuid);
        }
        
        return true;
    }
    
    /**
     * Check if two players have mutual claims and notify them
     */
    private void checkAndNotifyMutual(String playerAUuid, String playerBUuid, EntityPlayer playerA) {
        FriendClaim aClaimsB = getClaimFromTo(playerAUuid, playerBUuid);
        FriendClaim bClaimsA = getClaimFromTo(playerBUuid, playerAUuid);
        
        boolean mutual = aClaimsB != null && bClaimsA != null;
        
        // Find if friend is online
        EntityPlayer playerB = findPlayerByUuid(playerBUuid);
        
        // Notify player A
        sendVerificationConfirm(playerA, playerBUuid, 
            playerB != null ? playerB.name : (bClaimsA != null ? bClaimsA.claimerName : null),
            mutual, bClaimsA);
        
        // Notify player B if online
        if (playerB != null && mutual) {
            sendVerificationConfirm(playerB, playerAUuid, playerA.name, true, aClaimsB);
            log.fine("[FriendsVerify] Mutual verification: " + playerA.name + " <-> " + playerB.name);
        }
    }
    
    /**
     * Get a claim from one player to another
     */
    private FriendClaim getClaimFromTo(String fromUuid, String toUuid) {
        String normalizedFrom = normalizeUuid(fromUuid);
        String normalizedTo = normalizeUuid(toUuid);
        if (normalizedFrom == null || normalizedTo == null) {
            return null;
        }
        Map<String, FriendClaim> claims = friendClaims.get(normalizedFrom);
        if (claims == null) return null;
        return claims.get(normalizedTo);
    }

    private boolean storeClaim(String requesterUuid, String friendUuid, FriendClaim claim) {
        Map<String, FriendClaim> claims = friendClaims.get(requesterUuid);
        if (claims == null) {
            claims = new ConcurrentHashMap<String, FriendClaim>();
            friendClaims.put(requesterUuid, claims);
        }
        if (!canStoreClaim(claims, friendUuid)) {
            return false;
        }
        claims.put(friendUuid, claim);
        return true;
    }

    static boolean canStoreClaim(Map<String, ?> claims, String friendUuid) {
        return claims == null
                || claims.containsKey(friendUuid)
                || claims.size() < MAX_CLAIMS_PER_PLAYER;
    }
    
    /**
     * Send verification confirmation to a player
     */
    private void sendVerificationConfirm(EntityPlayer player, String friendUuid, String friendName, 
            boolean verified, FriendClaim friendsClaim) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF(friendUuid);
            out.writeBoolean(verified);
            out.writeBoolean(friendName != null); // Is friend known?
            
            if (friendName != null) {
                out.writeUTF(friendName);
            }
            
            // Include friend's claim data if available
            out.writeBoolean(friendsClaim != null);
            if (friendsClaim != null) {
                out.writeUTF(friendsClaim.signature);
                out.writeLong(friendsClaim.addedAt);
                out.writeUTF(friendsClaim.publicKey);
            }
            
            Packet250CustomPayload packet = new Packet250CustomPayload(CHANNEL_CONFIRM, baos.toByteArray());
            player.netServerHandler.sendPacket(packet);
        } catch (IOException e) {
            log.warning("[FriendsVerify] Failed to send confirm: " + e.getMessage());
        }
    }
    
    /**
     * Send check response to a player
     */
    private void sendCheckResponse(EntityPlayer player, String friendUuid, FriendClaim friendsClaim) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF(friendUuid);
            out.writeBoolean(friendsClaim != null); // Friend has claimed us
            
            if (friendsClaim != null) {
                out.writeUTF(friendsClaim.claimerName);
                out.writeUTF(friendsClaim.signature);
                out.writeLong(friendsClaim.addedAt);
                out.writeUTF(friendsClaim.publicKey);
            }
            
            Packet250CustomPayload response = new Packet250CustomPayload(CHANNEL_CONFIRM, baos.toByteArray());
            player.netServerHandler.sendPacket(response);
        } catch (IOException e) {
            log.warning("[FriendsVerify] Failed to send check response: " + e.getMessage());
        }
    }
    
    /**
     * Called when a player joins - check for pending verifications
     */
    public void onPlayerJoin(EntityPlayer player) {
        if (!server.onlineMode) return;
        cleanupExpiredClaimsIfNeeded();
        
        String playerUuid = normalizeUuid(getPlayerUuid(player));
        if (playerUuid == null) return;
        
        // Check if any stored claims can now be verified
        Map<String, FriendClaim> playersClaims = friendClaims.get(playerUuid);
        if (playersClaims != null) {
            for (String friendUuid : playersClaims.keySet()) {
                EntityPlayer friend = findPlayerByUuid(friendUuid);
                if (friend != null) {
                    // Friend is online, check for mutual
                    checkAndNotifyMutual(playerUuid, friendUuid, player);
                }
            }
        }
        
        // Also check if anyone has claimed this player
        for (Map.Entry<String, Map<String, FriendClaim>> entry : friendClaims.entrySet()) {
            String claimerUuid = entry.getKey();
            if (entry.getValue().containsKey(playerUuid)) {
                EntityPlayer claimer = findPlayerByUuid(claimerUuid);
                if (claimer != null) {
                    checkAndNotifyMutual(claimerUuid, playerUuid, claimer);
                }
            }
        }
    }
    
    /**
     * Called when a player leaves
     */
    public void onPlayerLeave(EntityPlayer player) {
        // Claims persist - no cleanup needed
        saveClaimsIfNeeded();
    }
    
    /**
     * Find a player by their UUID
     */
    private EntityPlayer findPlayerByUuid(String uuid) {
        uuid = normalizeUuid(uuid);
        if (uuid == null) {
            return null;
        }
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
     * Get a player's UUID
     */
    private String getPlayerUuid(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        if (player.uniqueId != null) {
            return player.uniqueId.toString();
        }
        
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
    
    static String normalizeUuid(String uuid) {
        if (uuid == null || uuid.length() != 32 && uuid.length() != 36) {
            return null;
        }

        StringBuilder normalized = new StringBuilder(32);
        for (int i = 0; i < uuid.length(); ++i) {
            char c = uuid.charAt(i);
            if (uuid.length() == 36 && (i == 8 || i == 13 || i == 18 || i == 23)) {
                if (c != '-') {
                    return null;
                }
                continue;
            }
            if (c >= '0' && c <= '9') {
                normalized.append(c);
            } else if (c >= 'a' && c <= 'f') {
                normalized.append(c);
            } else if (c >= 'A' && c <= 'F') {
                normalized.append(Character.toLowerCase(c));
            } else {
                return null;
            }
        }
        return normalized.length() == 32 ? normalized.toString() : null;
    }

    static boolean isValidClaimProof(String signature, String publicKey) {
        if (signature == null || signature.length() != PacketLimits.MAX_SIGNATURE_CHARS
                || publicKey == null || publicKey.length() != PacketLimits.MAX_PUBLIC_KEY_CHARS) {
            return false;
        }
        try {
            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKey);
            return signatureBytes.length == 64
                    && publicKeyBytes.length == 32
                    && signature.equals(Base64.getEncoder().encodeToString(signatureBytes))
                    && publicKey.equals(Base64.getEncoder().encodeToString(publicKeyBytes));
        } catch (IllegalArgumentException invalidBase64) {
            return false;
        }
    }
    
    public boolean isSupported() {
        return server.onlineMode;
    }
    
    /**
     * Save claims periodically
     */
    public void saveClaimsIfNeeded() {
        cleanupExpiredClaimsIfNeeded();
        if (!dirty) return;
        
        long now = System.currentTimeMillis();
        if (now - lastSaveTime < SAVE_INTERVAL) return;
        
        saveClaims();
    }
    
    /**
     * Save claims to disk
     */
    @SuppressWarnings("unchecked")
    public void saveClaims() {
        try {
            JSONObject root = new JSONObject();
            JSONArray claimsArray = new JSONArray();
            
            for (Map.Entry<String, Map<String, FriendClaim>> playerClaims : friendClaims.entrySet()) {
                for (FriendClaim claim : playerClaims.getValue().values()) {
                    JSONObject claimJson = new JSONObject();
                    claimJson.put("claimer", claim.claimerUuid);
                    claimJson.put("friend", claim.friendUuid);
                    claimJson.put("name", claim.claimerName);
                    claimJson.put("signature", claim.signature);
                    claimJson.put("addedAt", claim.addedAt);
                    claimJson.put("publicKey", claim.publicKey);
                    claimJson.put("timestamp", claim.timestamp);
                    claimsArray.add(claimJson);
                }
            }
            
            root.put("claims", claimsArray);
            root.put("savedAt", System.currentTimeMillis());
            
            try (FileWriter writer = new FileWriter(claimsFile)) {
                writer.write(root.toJSONString());
            }
            
            dirty = false;
            lastSaveTime = System.currentTimeMillis();
            log.fine("[FriendsVerify] Saved " + claimsArray.size() + " friend claims");
        } catch (Exception e) {
            log.warning("[FriendsVerify] Failed to save claims: " + e.getMessage());
        }
    }
    
    /**
     * Load claims from disk
     */
    private void loadClaims() {
        if (!claimsFile.exists()) {
            return;
        }
        
        try {
            JSONParser parser = new JSONParser();
            JSONObject root = (JSONObject) parser.parse(new FileReader(claimsFile));
            JSONArray claimsArray = (JSONArray) root.get("claims");
            
            if (claimsArray == null) return;
            
            int loaded = 0;
            long now = System.currentTimeMillis();
            
            for (Object obj : claimsArray) {
                JSONObject claimJson = (JSONObject) obj;
                
                long timestamp = ((Number) claimJson.getOrDefault("timestamp", 0L)).longValue();
                if (isClaimExpired(timestamp, now)) continue;
                
                String claimer = normalizeUuid((String) claimJson.get("claimer"));
                String friend = normalizeUuid((String) claimJson.get("friend"));
                String name = (String) claimJson.get("name");
                String signature = (String) claimJson.get("signature");
                long addedAt = ((Number) claimJson.get("addedAt")).longValue();
                String publicKey = (String) claimJson.get("publicKey");
                if (claimer == null || friend == null || claimer.equals(friend)
                        || !isValidClaimProof(signature, publicKey)) {
                    continue;
                }
                
                FriendClaim claim = new FriendClaim(claimer, friend, name, signature, addedAt, publicKey);
                claim.timestamp = timestamp;
                if (storeClaim(claimer, friend, claim)) {
                    loaded++;
                }
            }
            
            log.fine("[FriendsVerify] Loaded " + loaded + " friend claims");
        } catch (Exception e) {
            log.warning("[FriendsVerify] Failed to load claims: " + e.getMessage());
        }
    }
    
    /**
     * Clean up old claims (older than 7 days)
     */
    public void cleanupOldClaims() {
        long now = System.currentTimeMillis();
        this.lastCleanupTime = now;
        cleanupExpiredClaims(now);
    }

    private void cleanupExpiredClaimsIfNeeded() {
        long now = System.currentTimeMillis();
        long elapsed = now - this.lastCleanupTime;
        if (this.lastCleanupTime != 0L && elapsed >= 0L && elapsed < CLAIM_CLEANUP_INTERVAL_MS) {
            return;
        }
        this.lastCleanupTime = now;
        cleanupExpiredClaims(now);
    }

    private void cleanupExpiredClaims(long now) {
        boolean removed = false;
        for (Map<String, FriendClaim> claims : friendClaims.values()) {
            int previousSize = claims.size();
            claims.entrySet().removeIf(entry -> isClaimExpired(entry.getValue().timestamp, now));
            removed |= claims.size() != previousSize;
        }
        int previousPlayers = friendClaims.size();
        friendClaims.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        removed |= friendClaims.size() != previousPlayers;
        if (removed) {
            dirty = true;
        }
    }

    static boolean isClaimExpired(long timestamp, long now) {
        if (timestamp <= 0L || timestamp > now && timestamp - now > MAX_FUTURE_TIMESTAMP_SKEW_MS) {
            return true;
        }
        return timestamp <= now && now - timestamp > CLAIM_TTL_MS;
    }
    
    /**
     * Inner class to hold friend claim data
     */
    private static class FriendClaim {
        final String claimerUuid;
        final String friendUuid;
        final String claimerName;
        final String signature;
        final long addedAt;
        final String publicKey;
        long timestamp;
        
        FriendClaim(String claimerUuid, String friendUuid, String claimerName, 
                String signature, long addedAt, String publicKey) {
            this.claimerUuid = claimerUuid;
            this.friendUuid = friendUuid;
            this.claimerName = claimerName;
            this.signature = signature;
            this.addedAt = addedAt;
            this.publicKey = publicKey;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
