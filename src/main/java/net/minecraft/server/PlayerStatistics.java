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
        // Send to player via chat
        String message = "\u00A7e" + player.name + " has just earned the achievement \u00A7a[" + achievement.f + "]";
        
        // Broadcast to all players on the server
        if (player.b != null && player.b.serverConfigurationManager != null) {
            player.b.serverConfigurationManager.sendAll(new Packet3Chat(message));
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

