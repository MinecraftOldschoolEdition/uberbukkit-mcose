package net.minecraft.server;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Tracks server-wide combined statistics from all players.
 * Useful for yearly recaps and server milestones.
 * 
 * This aggregates:
 * - Total statistics across all players
 * - Per-statistic leaderboards
 * - Server lifetime totals
 * - Historical snapshots
 */
public class ServerStatistics {
    
    private static final Logger log = Logger.getLogger("Minecraft");
    private static ServerStatistics instance;
    
    private final File statsFile;
    private final File historyDir;
    
    // Combined totals: statistic ID -> total value across all players
    private final Map<Integer, Long> combinedStats = new ConcurrentHashMap<Integer, Long>();
    
    // Per-player contributions: statistic ID -> (player name -> value)
    private final Map<Integer, Map<String, Long>> playerContributions = new ConcurrentHashMap<Integer, Map<String, Long>>();
    
    // Achievement counts: achievement ID -> number of players who have unlocked it
    private final Map<Integer, Set<String>> achievementUnlocks = new ConcurrentHashMap<Integer, Set<String>>();
    
    // Server milestones
    private long serverStartTime;
    private long totalPlayersEverJoined;
    private long totalUniquePlayersJoined;
    private Set<String> uniquePlayers = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    
    // Periodic save
    private long lastSaveTime = 0;
    private static final long SAVE_INTERVAL = 300000; // 5 minutes
    
    private ServerStatistics() {
        this.statsFile = new File("server-statistics.dat");
        this.historyDir = new File("statistics-history");
        this.historyDir.mkdirs();
        this.serverStartTime = System.currentTimeMillis();
        load();
    }
    
    public static synchronized ServerStatistics getInstance() {
        if (instance == null) {
            instance = new ServerStatistics();
        }
        return instance;
    }
    
    /**
     * Record a statistic increment from a player.
     */
    public void recordStatistic(String playerName, Statistic statistic, int amount) {
        if (statistic == null || amount <= 0 || playerName == null) return;
        
        int statId = statistic.e;
        
        // Update combined total
        long currentTotal = combinedStats.containsKey(statId) ? combinedStats.get(statId) : 0L;
        combinedStats.put(statId, currentTotal + amount);
        
        // Update player contribution
        Map<String, Long> contributions = playerContributions.get(statId);
        if (contributions == null) {
            contributions = new ConcurrentHashMap<String, Long>();
            playerContributions.put(statId, contributions);
        }
        long playerCurrent = contributions.containsKey(playerName) ? contributions.get(playerName) : 0L;
        contributions.put(playerName, playerCurrent + amount);
        
        // Track unique players
        if (!uniquePlayers.contains(playerName)) {
            uniquePlayers.add(playerName);
            totalUniquePlayersJoined = uniquePlayers.size();
        }
        
        // Periodic auto-save
        checkAutoSave();
    }
    
    /**
     * Record an achievement unlock.
     */
    public void recordAchievement(String playerName, Achievement achievement) {
        if (achievement == null || playerName == null) return;
        
        int achievementId = achievement.e;
        
        Set<String> players = achievementUnlocks.get(achievementId);
        if (players == null) {
            players = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
            achievementUnlocks.put(achievementId, players);
        }
        players.add(playerName);
        
        checkAutoSave();
    }
    
    /**
     * Record a player joining.
     */
    public void recordPlayerJoin(String playerName) {
        totalPlayersEverJoined++;
        if (!uniquePlayers.contains(playerName)) {
            uniquePlayers.add(playerName);
            totalUniquePlayersJoined = uniquePlayers.size();
        }
    }
    
    /**
     * Get combined total for a statistic.
     */
    public long getCombinedTotal(Statistic statistic) {
        if (statistic == null) return 0;
        return combinedStats.containsKey(statistic.e) ? combinedStats.get(statistic.e) : 0L;
    }
    
    /**
     * Get combined total by statistic ID.
     */
    public long getCombinedTotal(int statId) {
        return combinedStats.containsKey(statId) ? combinedStats.get(statId) : 0L;
    }
    
    /**
     * Get top players for a statistic.
     */
    public List<Map.Entry<String, Long>> getLeaderboard(Statistic statistic, int limit) {
        if (statistic == null) return new ArrayList<Map.Entry<String, Long>>();
        
        Map<String, Long> contributions = playerContributions.get(statistic.e);
        if (contributions == null) return new ArrayList<Map.Entry<String, Long>>();
        
        List<Map.Entry<String, Long>> sorted = new ArrayList<Map.Entry<String, Long>>(contributions.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return Long.compare(b.getValue(), a.getValue()); // Descending
            }
        });
        
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }
    
    /**
     * Get player count for an achievement.
     */
    public int getAchievementUnlockCount(Achievement achievement) {
        if (achievement == null) return 0;
        Set<String> players = achievementUnlocks.get(achievement.e);
        return players != null ? players.size() : 0;
    }
    
    /**
     * Get all players who have unlocked an achievement.
     */
    public Set<String> getAchievementHolders(Achievement achievement) {
        if (achievement == null) return new HashSet<String>();
        Set<String> players = achievementUnlocks.get(achievement.e);
        return players != null ? new HashSet<String>(players) : new HashSet<String>();
    }
    
    /**
     * Get total unique players.
     */
    public long getTotalUniquePlayers() {
        return totalUniquePlayersJoined;
    }
    
    /**
     * Get total player joins (including reconnects).
     */
    public long getTotalPlayerJoins() {
        return totalPlayersEverJoined;
    }
    
    /**
     * Get all combined statistics as a map.
     */
    public Map<Integer, Long> getAllCombinedStats() {
        return new HashMap<Integer, Long>(combinedStats);
    }
    
    /**
     * Get a summary of server statistics for display/export.
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        
        summary.put("generatedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        summary.put("serverStartTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(serverStartTime)));
        summary.put("totalUniquePlayers", totalUniquePlayersJoined);
        summary.put("totalPlayerJoins", totalPlayersEverJoined);
        
        // Add key statistics
        Map<String, Long> keyStats = new LinkedHashMap<String, Long>();
        
        // Distance stats
        keyStats.put("totalDistanceWalked", getCombinedTotal(StatisticList.l));
        keyStats.put("totalDistanceSwum", getCombinedTotal(StatisticList.m));
        keyStats.put("totalDistanceFallen", getCombinedTotal(StatisticList.n));
        keyStats.put("totalDistanceClimbed", getCombinedTotal(StatisticList.o));
        keyStats.put("totalDistanceFlown", getCombinedTotal(StatisticList.p));
        keyStats.put("totalDistanceByMinecart", getCombinedTotal(StatisticList.r));
        keyStats.put("totalDistanceByBoat", getCombinedTotal(StatisticList.s));
        keyStats.put("totalDistanceByPig", getCombinedTotal(StatisticList.t));
        
        // Combat stats
        keyStats.put("totalDamageDealt", getCombinedTotal(StatisticList.w));
        keyStats.put("totalDamageTaken", getCombinedTotal(StatisticList.x));
        keyStats.put("totalDeaths", getCombinedTotal(StatisticList.y));
        keyStats.put("totalMobKills", getCombinedTotal(StatisticList.z));
        keyStats.put("totalPlayerKills", getCombinedTotal(StatisticList.A));
        
        // Other stats
        keyStats.put("totalJumps", getCombinedTotal(StatisticList.u));
        keyStats.put("totalItemsDropped", getCombinedTotal(StatisticList.v));
        keyStats.put("totalFishCaught", getCombinedTotal(StatisticList.B));
        keyStats.put("totalPlayTimeMinutes", getCombinedTotal(StatisticList.k));
        
        summary.put("statistics", keyStats);
        
        // Achievement unlock counts
        Map<String, Integer> achievementCounts = new LinkedHashMap<String, Integer>();
        for (Object obj : AchievementList.e) {
            if (obj instanceof Achievement) {
                Achievement achievement = (Achievement) obj;
                achievementCounts.put(achievement.f, getAchievementUnlockCount(achievement));
            }
        }
        summary.put("achievementUnlocks", achievementCounts);
        
        return summary;
    }
    
    private void checkAutoSave() {
        long now = System.currentTimeMillis();
        if (now - lastSaveTime > SAVE_INTERVAL) {
            save();
            lastSaveTime = now;
        }
    }
    
    /**
     * Safely get a numeric value from NBT, handling both Long and Float types.
     * This is needed for backwards compatibility with older data formats.
     */
    private long getNumericValue(NBTTagCompound nbt, String key) {
        try {
            NBTBase tag = nbt.b(key);
            if (tag instanceof NBTTagLong) {
                return ((NBTTagLong) tag).a;
            } else if (tag instanceof NBTTagFloat) {
                return (long) ((NBTTagFloat) tag).a;
            } else if (tag instanceof NBTTagInt) {
                return ((NBTTagInt) tag).a;
            } else if (tag instanceof NBTTagDouble) {
                return (long) ((NBTTagDouble) tag).a;
            } else if (tag instanceof NBTTagShort) {
                return ((NBTTagShort) tag).a;
            } else if (tag instanceof NBTTagByte) {
                return ((NBTTagByte) tag).a;
            }
        } catch (Exception e) {
            // Fall through to default
        }
        return 0L;
    }
    
    /**
     * Save statistics to file.
     */
    public synchronized void save() {
        try {
            NBTTagCompound root = new NBTTagCompound();
            
            // Save metadata
            root.a("ServerStartTime", serverStartTime);
            root.a("TotalPlayersEverJoined", totalPlayersEverJoined);
            root.a("TotalUniquePlayersJoined", totalUniquePlayersJoined);
            root.a("LastSaveTime", System.currentTimeMillis());
            
            // Save unique players
            NBTTagList playerList = new NBTTagList();
            for (String player : uniquePlayers) {
                NBTTagCompound playerTag = new NBTTagCompound();
                playerTag.setString("Name", player);
                playerList.a(playerTag);
            }
            root.a("UniquePlayers", playerList);
            
            // Save combined statistics
            NBTTagCompound statsNbt = new NBTTagCompound();
            for (Map.Entry<Integer, Long> entry : combinedStats.entrySet()) {
                statsNbt.a("stat_" + entry.getKey(), entry.getValue());
            }
            root.a("CombinedStats", statsNbt);
            
            // Save player contributions (for leaderboards)
            NBTTagCompound contribNbt = new NBTTagCompound();
            for (Map.Entry<Integer, Map<String, Long>> entry : playerContributions.entrySet()) {
                NBTTagCompound statContrib = new NBTTagCompound();
                for (Map.Entry<String, Long> playerEntry : entry.getValue().entrySet()) {
                    statContrib.a(playerEntry.getKey(), playerEntry.getValue());
                }
                contribNbt.a("stat_" + entry.getKey(), statContrib);
            }
            root.a("PlayerContributions", contribNbt);
            
            // Save achievement unlocks
            NBTTagCompound achievementsNbt = new NBTTagCompound();
            for (Map.Entry<Integer, Set<String>> entry : achievementUnlocks.entrySet()) {
                NBTTagList holders = new NBTTagList();
                for (String player : entry.getValue()) {
                    NBTTagCompound holderTag = new NBTTagCompound();
                    holderTag.setString("Name", player);
                    holders.a(holderTag);
                }
                achievementsNbt.a("achievement_" + entry.getKey(), holders);
            }
            root.a("AchievementUnlocks", achievementsNbt);
            
            // Write to file
            File tempFile = new File(statsFile.getPath() + ".tmp");
            FileOutputStream fos = new FileOutputStream(tempFile);
            CompressedStreamTools.a(root, fos);
            fos.close();
            
            // Atomic rename
            if (statsFile.exists()) {
                statsFile.delete();
            }
            tempFile.renameTo(statsFile);
            
        } catch (IOException e) {
            log.warning("[ServerStats] Failed to save server statistics: " + e.getMessage());
        }
    }
    
    /**
     * Load statistics from file.
     */
    private void load() {
        if (!statsFile.exists()) {
            log.info("[ServerStats] No existing server statistics file, starting fresh.");
            return;
        }
        
        try {
            FileInputStream fis = new FileInputStream(statsFile);
            NBTTagCompound root = CompressedStreamTools.a(fis);
            fis.close();
            
            // Load metadata (use getNumericValue for backwards compatibility with older Float data)
            if (root.hasKey("ServerStartTime")) {
                serverStartTime = getNumericValue(root, "ServerStartTime");
            }
            if (root.hasKey("TotalPlayersEverJoined")) {
                totalPlayersEverJoined = getNumericValue(root, "TotalPlayersEverJoined");
            }
            if (root.hasKey("TotalUniquePlayersJoined")) {
                totalUniquePlayersJoined = getNumericValue(root, "TotalUniquePlayersJoined");
            }
            
            // Load unique players
            if (root.hasKey("UniquePlayers")) {
                NBTTagList playerList = root.l("UniquePlayers");
                for (int i = 0; i < playerList.c(); i++) {
                    NBTTagCompound playerTag = (NBTTagCompound) playerList.a(i);
                    uniquePlayers.add(playerTag.getString("Name"));
                }
            }
            
            // Load combined statistics
            if (root.hasKey("CombinedStats")) {
                NBTTagCompound statsNbt = root.k("CombinedStats");
                for (String key : statsNbt.getKeys()) {
                    if (key.startsWith("stat_")) {
                        try {
                            int statId = Integer.parseInt(key.substring(5));
                            long value = getNumericValue(statsNbt, key);
                            combinedStats.put(statId, value);
                        } catch (Exception e) {
                            // Skip invalid entries
                        }
                    }
                }
            }
            
            // Load player contributions
            if (root.hasKey("PlayerContributions")) {
                NBTTagCompound contribNbt = root.k("PlayerContributions");
                for (String key : contribNbt.getKeys()) {
                    if (key.startsWith("stat_")) {
                        try {
                            int statId = Integer.parseInt(key.substring(5));
                            NBTTagCompound statContrib = contribNbt.k(key);
                            Map<String, Long> contributions = new ConcurrentHashMap<String, Long>();
                            for (String playerName : statContrib.getKeys()) {
                                contributions.put(playerName, getNumericValue(statContrib, playerName));
                            }
                            playerContributions.put(statId, contributions);
                        } catch (Exception e) {
                            // Skip invalid entries
                        }
                    }
                }
            }
            
            // Load achievement unlocks
            if (root.hasKey("AchievementUnlocks")) {
                NBTTagCompound achievementsNbt = root.k("AchievementUnlocks");
                for (String key : achievementsNbt.getKeys()) {
                    if (key.startsWith("achievement_")) {
                        try {
                            int achievementId = Integer.parseInt(key.substring(12));
                            NBTTagList holders = achievementsNbt.l(key);
                            Set<String> players = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
                            for (int i = 0; i < holders.c(); i++) {
                                NBTTagCompound holderTag = (NBTTagCompound) holders.a(i);
                                players.add(holderTag.getString("Name"));
                            }
                            achievementUnlocks.put(achievementId, players);
                        } catch (NumberFormatException e) {
                            // Skip invalid entries
                        }
                    }
                }
            }
            
            log.info("[ServerStats] Loaded server statistics: " + combinedStats.size() + " stats, " + 
                     uniquePlayers.size() + " unique players, " + achievementUnlocks.size() + " achievement types tracked.");
            
        } catch (Exception e) {
            log.warning("[ServerStats] Failed to load server statistics: " + e.getMessage());
        }
    }
    
    /**
     * Create a yearly snapshot for recap purposes.
     */
    public void createYearlySnapshot(int year) {
        try {
            File snapshotFile = new File(historyDir, "yearly-" + year + ".json");
            Map<String, Object> summary = getSummary();
            summary.put("snapshotYear", year);
            
            // Write as simple text format (JSON-like)
            PrintWriter writer = new PrintWriter(new FileWriter(snapshotFile));
            writer.println("# Server Statistics Snapshot - Year " + year);
            writer.println("# Generated: " + summary.get("generatedAt"));
            writer.println();
            writeMapToFile(writer, summary, 0);
            writer.close();
            
            log.info("[ServerStats] Created yearly snapshot for " + year);
        } catch (IOException e) {
            log.warning("[ServerStats] Failed to create yearly snapshot: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void writeMapToFile(PrintWriter writer, Map<String, Object> map, int indent) {
        String prefix = "";
        for (int i = 0; i < indent; i++) prefix += "  ";
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Map) {
                writer.println(prefix + entry.getKey() + ":");
                writeMapToFile(writer, (Map<String, Object>) entry.getValue(), indent + 1);
            } else {
                writer.println(prefix + entry.getKey() + ": " + entry.getValue());
            }
        }
    }
    
    /**
     * Shutdown - save and cleanup.
     */
    public void shutdown() {
        save();
        log.info("[ServerStats] Server statistics saved on shutdown.");
    }
}

