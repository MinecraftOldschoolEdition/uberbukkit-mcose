package net.minecraft.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Centralized Achievement Manager for the server.
 * Handles all achievement tracking, unlocking, persistence, and broadcasting.
 * 
 * This is the ONLY class that should be used to unlock achievements server-side
 * to ensure consistent behavior across all code paths.
 */
public final class AchievementManager {
    
    private static final Logger log = Logger.getLogger("Minecraft");
    
    // Fallback achievement display names (in case translation file fails)
    private static final Map<Integer, String> ACHIEVEMENT_NAMES = new HashMap<Integer, String>();
    static {
        ACHIEVEMENT_NAMES.put(5242880, "Taking Inventory");
        ACHIEVEMENT_NAMES.put(5242881, "Getting Wood");
        ACHIEVEMENT_NAMES.put(5242882, "Benchmarking");
        ACHIEVEMENT_NAMES.put(5242883, "Time to Mine!");
        ACHIEVEMENT_NAMES.put(5242884, "Hot Topic");
        ACHIEVEMENT_NAMES.put(5242885, "Acquire Hardware");
        ACHIEVEMENT_NAMES.put(5242886, "Time to Farm!");
        ACHIEVEMENT_NAMES.put(5242887, "Bake Bread");
        ACHIEVEMENT_NAMES.put(5242888, "The Lie");
        ACHIEVEMENT_NAMES.put(5242889, "Getting an Upgrade");
        ACHIEVEMENT_NAMES.put(5242890, "Delicious Fish");
        ACHIEVEMENT_NAMES.put(5242891, "On A Rail");
        ACHIEVEMENT_NAMES.put(5242892, "Time to Strike!");
        ACHIEVEMENT_NAMES.put(5242893, "Monster Hunter");
        ACHIEVEMENT_NAMES.put(5242894, "Cow Tipper");
        ACHIEVEMENT_NAMES.put(5242895, "When Pigs Fly");
        ACHIEVEMENT_NAMES.put(5242896, "DIAMONDS!");
        ACHIEVEMENT_NAMES.put(5242897, "We Need to Go Deeper");
        ACHIEVEMENT_NAMES.put(5242898, "Librarian");
        ACHIEVEMENT_NAMES.put(5242899, "KABOOM!");
        ACHIEVEMENT_NAMES.put(5242900, "Hot Stuff");
        ACHIEVEMENT_NAMES.put(5242901, "Pork Chop");
        ACHIEVEMENT_NAMES.put(5242902, "Sweet Dreams");
        ACHIEVEMENT_NAMES.put(5242903, "Pathfinder");
        ACHIEVEMENT_NAMES.put(5242904, "DJ");
        ACHIEVEMENT_NAMES.put(5242905, "Sniper Duel");
        ACHIEVEMENT_NAMES.put(5242906, "Pushin' Around");
        ACHIEVEMENT_NAMES.put(5242907, "Tick Tock");
        ACHIEVEMENT_NAMES.put(5242908, "Overkill");
        ACHIEVEMENT_NAMES.put(5242909, "Have a Shearful Day");
        ACHIEVEMENT_NAMES.put(5242910, "Egg Hunt");
        ACHIEVEMENT_NAMES.put(5242911, "Rainbow Collection");
    }
    
    private final EntityPlayer player;
    private final Set<Integer> unlockedAchievementIds;
    
    /**
     * Create an achievement manager for a player.
     * @param player The player this manager belongs to
     */
    public AchievementManager(EntityPlayer player) {
        this.player = player;
        this.unlockedAchievementIds = new HashSet<Integer>();
    }
    
    /**
     * Check if the player has an achievement unlocked.
     * @param achievement The achievement to check
     * @return true if unlocked
     */
    public boolean hasAchievement(Achievement achievement) {
        if (achievement == null) return false;
        return unlockedAchievementIds.contains(achievement.e);
    }
    
    /**
     * Check if the player has an achievement unlocked by stat ID.
     * @param statId The stat ID
     * @return true if unlocked
     */
    public boolean hasAchievementById(int statId) {
        return unlockedAchievementIds.contains(statId);
    }
    
    /**
     * Check if the prerequisite for an achievement is met.
     * @param achievement The achievement to check
     * @return true if the prerequisite is met or there is no prerequisite
     */
    public boolean canUnlock(Achievement achievement) {
        if (achievement == null) return false;
        if (achievement.c == null) return true; // c is parent achievement
        return hasAchievement(achievement.c);
    }
    
    /**
     * Attempt to unlock an achievement. This is the main entry point.
     * Handles prerequisite checking, persistence, client sync, and broadcasting.
     * 
     * @param achievement The achievement to unlock
     * @return true if the achievement was newly unlocked
     */
    public boolean unlock(Achievement achievement) {
        if (achievement == null) return false;
        
        // Already unlocked?
        if (hasAchievement(achievement)) {
            return false;
        }
        
        // Check prerequisite
        if (!canUnlock(achievement)) {
            return false;
        }
        
        // Unlock it
        unlockedAchievementIds.add(achievement.e);
        
        // Report to server-wide statistics
        ServerStatistics.getInstance().recordAchievement(player.name, achievement);
        
        // Sync to client via packet
        sendAchievementPacket(achievement);
        
        // Broadcast to server if enabled
        broadcastAchievement(achievement);
        
        log.info("[Achievement] " + player.name + " earned: " + getAchievementName(achievement));
        
        return true;
    }
    
    /**
     * Force unlock an achievement without prerequisite checking.
     * Use this for admin commands or special cases.
     * 
     * @param achievement The achievement to unlock
     * @return true if the achievement was newly unlocked
     */
    public boolean forceUnlock(Achievement achievement) {
        if (achievement == null) return false;
        
        // Already unlocked?
        if (hasAchievement(achievement)) {
            return false;
        }
        
        // Unlock it
        unlockedAchievementIds.add(achievement.e);
        
        // Report to server-wide statistics
        ServerStatistics.getInstance().recordAchievement(player.name, achievement);
        
        // Sync to client via packet
        sendAchievementPacket(achievement);
        
        // Broadcast to server if enabled
        broadcastAchievement(achievement);
        
        log.info("[Achievement] " + player.name + " earned (forced): " + getAchievementName(achievement));
        
        return true;
    }
    
    /**
     * Send the achievement unlock packet to the client.
     */
    private void sendAchievementPacket(Achievement achievement) {
        if (player.netServerHandler == null) return;
        if (!player.protocol.canReceivePacket(200)) return;
        
        // Send Packet200Statistic with the achievement ID and amount of 1
        player.netServerHandler.sendPacket(new Packet200Statistic(achievement.e, 1));
    }
    
    /**
     * Broadcast the achievement to all players on the server.
     */
    private void broadcastAchievement(Achievement achievement) {
        // Get the server
        MinecraftServer server = player.b;
        if (server == null && player.world instanceof WorldServer) {
            server = ((WorldServer) player.world).server;
        }
        
        if (server == null || server.serverConfigurationManager == null) {
            // Fallback: just send to the player
            sendChatToPlayer("§eYou have just earned the achievement §a[" + getAchievementName(achievement) + "]");
            return;
        }
        
        // Check gamerule - default to true if we can't check
        boolean shouldBroadcast = true;
        try {
            WorldServer overworld = server.getWorldServer(0);
            if (overworld != null && overworld.worldData != null) {
                shouldBroadcast = overworld.worldData.getAdvertiseAchievements();
            }
        } catch (Exception e) {
            // Default to broadcasting if we can't check
            shouldBroadcast = true;
        }
        
        String achievementName = getAchievementName(achievement);
        String message = "§e" + player.name + " has just earned the achievement §a[" + achievementName + "]";
        
        if (shouldBroadcast) {
            server.serverConfigurationManager.sendAll(new Packet3Chat(message));
        } else {
            // Only send to the player who earned it
            sendChatToPlayer(message);
        }
    }
    
    /**
     * Send a chat message to the player.
     */
    private void sendChatToPlayer(String message) {
        if (player.netServerHandler != null) {
            player.netServerHandler.sendPacket(new Packet3Chat(message));
        }
    }
    
    /**
     * Get the display name for an achievement.
     */
    public static String getAchievementName(Achievement achievement) {
        if (achievement == null) return "Unknown Achievement";
        
        // Try the translated name from the Statistic.f field
        if (achievement.f != null && !achievement.f.startsWith("achievement.")) {
            return achievement.f;
        }
        
        // Fallback to our hardcoded map
        String fallback = ACHIEVEMENT_NAMES.get(achievement.e);
        if (fallback != null) {
            return fallback;
        }
        
        // Ultimate fallback
        return "Unknown Achievement";
    }
    
    /**
     * Save achievements to NBT.
     */
    public void saveToNBT(NBTTagCompound nbt) {
        NBTTagList achievementList = new NBTTagList();
        for (Integer id : unlockedAchievementIds) {
            NBTTagCompound achievementTag = new NBTTagCompound();
            achievementTag.a("id", id);
            achievementList.a(achievementTag);
        }
        nbt.a("Achievements", achievementList);
    }
    
    /**
     * Load achievements from NBT.
     */
    public void loadFromNBT(NBTTagCompound nbt) {
        unlockedAchievementIds.clear();
        
        if (nbt.hasKey("Achievements")) {
            NBTTagList achievementList = nbt.l("Achievements");
            for (int i = 0; i < achievementList.c(); i++) {
                NBTTagCompound achievementTag = (NBTTagCompound) achievementList.a(i);
                unlockedAchievementIds.add(achievementTag.e("id"));
            }
        }
        
        log.fine("[AchievementManager] Loaded " + unlockedAchievementIds.size() + " achievements for " + player.name);
    }
    
    /**
     * Sync all unlocked achievements to the client.
     * Call this when a player joins to ensure their client is up-to-date.
     */
    public void syncAllToClient() {
        if (player.netServerHandler == null) return;
        if (!player.protocol.canReceivePacket(200)) return;
        
        for (Integer statId : unlockedAchievementIds) {
            player.netServerHandler.sendPacket(new Packet200Statistic(statId, 1));
        }
        
        log.fine("[AchievementManager] Synced " + unlockedAchievementIds.size() + " achievements to " + player.name);
    }
    
    /**
     * Get all unlocked achievement IDs.
     * @return A copy of the unlocked achievement IDs set
     */
    public Set<Integer> getUnlockedAchievementIds() {
        return new HashSet<Integer>(unlockedAchievementIds);
    }
    
    /**
     * Get the count of unlocked achievements.
     * @return Number of unlocked achievements
     */
    public int getUnlockedCount() {
        return unlockedAchievementIds.size();
    }
    
    /**
     * Find an achievement by its stat ID.
     * @param statId The stat ID to search for
     * @return The achievement, or null if not found
     */
    public static Achievement findAchievementByStatId(int statId) {
        // Check if this is in the achievement ID range (5242880+)
        if (statId < 5242880) {
            return null;
        }
        
        // Search the achievement list
        for (Object obj : AchievementList.e) {
            Achievement achievement = (Achievement) obj;
            if (achievement.e == statId) {
                return achievement;
            }
        }
        
        return null;
    }
    
    /**
     * Convenience method to trigger an achievement for a player.
     * Use this as a one-liner from game code.
     * 
     * @param player The player who earned the achievement
     * @param achievement The achievement to unlock
     * @return true if the achievement was newly unlocked
     */
    public static boolean trigger(EntityPlayer player, Achievement achievement) {
        if (player != null && player.achievementManager != null && achievement != null) {
            return player.achievementManager.unlock(achievement);
        }
        return false;
    }
    
    /**
     * Convenience method to check if a player has an achievement.
     * 
     * @param player The player to check
     * @param achievement The achievement to check
     * @return true if the player has the achievement
     */
    public static boolean hasUnlocked(EntityPlayer player, Achievement achievement) {
        if (player != null && player.achievementManager != null && achievement != null) {
            return player.achievementManager.hasAchievement(achievement);
        }
        return false;
    }
}
