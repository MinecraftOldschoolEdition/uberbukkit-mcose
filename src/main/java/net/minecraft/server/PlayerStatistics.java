package net.minecraft.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks per-player statistics and achievements server-side.
 * This mirrors the single-player statistics system.
 */
public class PlayerStatistics {
    
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
    
    // Statistics: statistic ID -> value
    private final Map<Integer, Integer> statistics = new HashMap<Integer, Integer>();
    
    // Unlocked achievements: achievement ID
    private final Set<Integer> unlockedAchievements = new HashSet<Integer>();
    
    // Pending achievements to announce (cleared after sending)
    private final Set<Achievement> pendingAchievements = new HashSet<Achievement>();
    
    public PlayerStatistics(EntityPlayer player) {
        this.player = player;
    }
    
    /**
     * Increment a statistic by the given amount.
     */
    public void addStatistic(Statistic statistic, int amount) {
        if (statistic == null || amount <= 0) return;
        
        int currentValue = statistics.containsKey(statistic.e) ? statistics.get(statistic.e) : 0;
        int newValue = currentValue + amount;
        statistics.put(statistic.e, newValue);
        
        // Report to server-wide statistics
        ServerStatistics.getInstance().recordStatistic(player.name, statistic, amount);
        
        // Check if this is an achievement
        if (statistic instanceof Achievement) {
            unlockAchievement((Achievement) statistic);
        }
        
        // Send statistic update packet to client
        if (player.netServerHandler != null && player.protocol.canReceivePacket(200)) {
            player.netServerHandler.sendPacket(new Packet200Statistic(statistic.e, amount));
        }
    }
    
    /**
     * Get the current value of a statistic.
     */
    public int getStatistic(Statistic statistic) {
        if (statistic == null) return 0;
        return statistics.containsKey(statistic.e) ? statistics.get(statistic.e) : 0;
    }
    
    /**
     * Check if an achievement is unlocked.
     */
    public boolean hasAchievement(Achievement achievement) {
        if (achievement == null) return false;
        return unlockedAchievements.contains(achievement.e);
    }
    
    /**
     * Unlock an achievement.
     */
    public void unlockAchievement(Achievement achievement) {
        if (achievement == null) return;
        
        // Check if already unlocked
        if (unlockedAchievements.contains(achievement.e)) {
            return;
        }
        
        // Check if prerequisite is met
        if (achievement.c != null && !hasAchievement(achievement.c)) {
            return;
        }
        
        // Unlock the achievement
        unlockedAchievements.add(achievement.e);
        pendingAchievements.add(achievement);
        
        // Report to server-wide statistics
        ServerStatistics.getInstance().recordAchievement(player.name, achievement);
        
        // Announce to player and server
        announceAchievement(achievement);
    }
    
    /**
     * Announce an achievement unlock.
     */
    private void announceAchievement(Achievement achievement) {
        // Get achievement display name with fallback chain
        String achievementName = null;
        
        // Try the translated name from the Statistic.f field
        if (achievement.f != null && !achievement.f.startsWith("achievement.")) {
            achievementName = achievement.f;
        }
        
        // Fallback to our hardcoded map
        if (achievementName == null || achievementName.startsWith("achievement.")) {
            String fallback = ACHIEVEMENT_NAMES.get(achievement.e);
            if (fallback != null) {
                achievementName = fallback;
            }
        }
        
        // Ultimate fallback
        if (achievementName == null) {
            achievementName = "Unknown Achievement";
        }
        
        // Send to player via chat
        String message = "\u00A7e" + player.name + " has just earned the achievement \u00A7a[" + achievementName + "]";
        
        // Try to get the MinecraftServer from multiple sources
        MinecraftServer server = player.b;
        if (server == null && player.world instanceof WorldServer) {
            server = ((WorldServer) player.world).server;
        }
        
        // Broadcast to all players on the server (if advertiseAchievements gamerule is enabled)
        if (server != null && server.serverConfigurationManager != null) {
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
            
            if (shouldBroadcast) {
                server.serverConfigurationManager.sendAll(new Packet3Chat(message));
                // Also log to console
                MinecraftServer.log.info(player.name + " has just earned the achievement [" + achievementName + "]");
            } else {
                // Only send to the player who earned it
                if (player.netServerHandler != null) {
                    player.netServerHandler.sendPacket(new Packet3Chat(message));
                }
            }
        } else {
            // Fallback: just send to the player if server not available
            if (player.netServerHandler != null) {
                player.netServerHandler.sendPacket(new Packet3Chat(message));
            }
        }
        
        // Send achievement packet to client (if supported)
        if (player.netServerHandler != null && player.protocol.canReceivePacket(200)) {
            player.netServerHandler.sendPacket(new Packet200Statistic(achievement.e, 1));
        }
    }
    
    /**
     * Save statistics to NBT.
     */
    public void saveToNBT(NBTTagCompound nbt) {
        // Save statistics
        NBTTagCompound statsNbt = new NBTTagCompound();
        for (Map.Entry<Integer, Integer> entry : statistics.entrySet()) {
            statsNbt.a("stat_" + entry.getKey(), entry.getValue());
        }
        nbt.a("Statistics", statsNbt);
        
        // Save achievements as NBTTagList of ints
        NBTTagList achievementList = new NBTTagList();
        for (Integer id : unlockedAchievements) {
            NBTTagCompound achievementTag = new NBTTagCompound();
            achievementTag.a("id", id);
            achievementList.a(achievementTag);
        }
        nbt.a("Achievements", achievementList);
    }
    
    /**
     * Load statistics from NBT.
     */
    public void loadFromNBT(NBTTagCompound nbt) {
        // Load statistics
        if (nbt.hasKey("Statistics")) {
            NBTTagCompound statsNbt = nbt.k("Statistics");
            for (String key : statsNbt.getKeys()) {
                if (key.startsWith("stat_")) {
                    try {
                        int statId = Integer.parseInt(key.substring(5));
                        int value = statsNbt.e(key);
                        statistics.put(statId, value);
                    } catch (NumberFormatException e) {
                        // Skip invalid entries
                    }
                }
            }
        }
        
        // Load achievements from NBTTagList
        if (nbt.hasKey("Achievements")) {
            NBTTagList achievementList = nbt.l("Achievements");
            for (int i = 0; i < achievementList.c(); i++) {
                NBTTagCompound achievementTag = (NBTTagCompound) achievementList.a(i);
                unlockedAchievements.add(achievementTag.e("id"));
            }
        }
    }
    
    /**
     * Get all statistics as a map.
     */
    public Map<Integer, Integer> getAllStatistics() {
        return new HashMap<Integer, Integer>(statistics);
    }
    
    /**
     * Get all unlocked achievement IDs.
     */
    public Set<Integer> getUnlockedAchievementIds() {
        return new HashSet<Integer>(unlockedAchievements);
    }
    
    /**
     * Clear pending achievements (called after client sync).
     */
    public void clearPendingAchievements() {
        pendingAchievements.clear();
    }
    
    /**
     * Get pending achievements for client sync.
     */
    public Set<Achievement> getPendingAchievements() {
        return new HashSet<Achievement>(pendingAchievements);
    }
}

