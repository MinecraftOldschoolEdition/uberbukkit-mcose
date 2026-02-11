package net.minecraft.server;

import net.minecraft.server.registry.StatisticRegistryApi;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks per-player non-achievement statistics server-side.
 * Achievement ownership is centralized in {@link AchievementManager}.
 */
public class PlayerStatistics {

    private static final int ACHIEVEMENT_STAT_BASE = 5242880;
    private static final int MAX_PACKET200_DELTA = Byte.MAX_VALUE;
    private static final String SNAPSHOT_CHANNEL = "MCOSE|StatsSync";
    private static final int SNAPSHOT_VERSION = 1;
    private static final int SNAPSHOT_ENTRIES_PER_CHUNK = 3000;

    private final EntityPlayer player;

    // statistic ID -> value
    private final Map<Integer, Integer> statistics = new HashMap<Integer, Integer>();

    public PlayerStatistics(EntityPlayer player) {
        this.player = player;
    }

    /**
     * Compatibility entry point.
     */
    public synchronized void addStatistic(Statistic statistic, int amount) {
        recordIncrement(statistic, amount);
    }

    /**
     * Authoritative stat increment path for non-achievement player statistics.
     */
    public synchronized void recordIncrement(Statistic statistic, int amount) {
        if (statistic == null || amount <= 0) {
            return;
        }

        // Route all achievement updates through AchievementManager so persistence and sync stay consistent.
        if (statistic instanceof Achievement) {
            if (player.achievementManager != null) {
                player.achievementManager.unlock((Achievement) statistic);
            }
            return;
        }

        Integer key = Integer.valueOf(statistic.e);
        int currentValue = statistics.containsKey(key) ? statistics.get(key).intValue() : 0;
        long next = (long) currentValue + (long) amount;
        int newValue = next > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) next;
        int appliedDelta = newValue - currentValue;
        if (appliedDelta <= 0) {
            return;
        }

        statistics.put(key, Integer.valueOf(newValue));
        ServerStatistics.getInstance().recordStatistic(player.name, statistic, appliedDelta);
        sendLiveDelta(statistic.e, appliedDelta);
    }

    private void sendLiveDelta(int statId, int amount) {
        if (amount <= 0) {
            return;
        }
        if (player.netServerHandler == null || player.protocol == null || !player.protocol.canReceivePacket(200)) {
            return;
        }

        int remaining = amount;
        while (remaining > 0) {
            int chunk = Math.min(MAX_PACKET200_DELTA, remaining);
            player.netServerHandler.sendPacket(new Packet200Statistic(statId, chunk));
            remaining -= chunk;
        }
    }

    /**
     * Send a full absolute stats snapshot to the client (join baseline sync).
     */
    public synchronized void sendFullSnapshotToClient() {
        if (player.netServerHandler == null || player.protocol == null || !player.protocol.canReceivePacket(250)) {
            return;
        }

        Map<Integer, Integer> snapshot = new HashMap<Integer, Integer>(statistics);
        List<Integer> sortedStatIds = new ArrayList<Integer>();
        for (Map.Entry<Integer, Integer> entry : snapshot.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().intValue() < 0) {
                continue;
            }
            sortedStatIds.add(entry.getKey());
        }
        Collections.sort(sortedStatIds);

        int totalChunks = Math.max(1, (sortedStatIds.size() + SNAPSHOT_ENTRIES_PER_CHUNK - 1) / SNAPSHOT_ENTRIES_PER_CHUNK);
        for (int chunkIndex = 0; chunkIndex < totalChunks; ++chunkIndex) {
            int start = chunkIndex * SNAPSHOT_ENTRIES_PER_CHUNK;
            int end = Math.min(sortedStatIds.size(), start + SNAPSHOT_ENTRIES_PER_CHUNK);
            int entryCount = end - start;

            ByteArrayOutputStream baos = new ByteArrayOutputStream(16 + Math.max(0, entryCount) * 8);
            DataOutputStream out = new DataOutputStream(baos);
            try {
                out.writeInt(SNAPSHOT_VERSION);
                out.writeInt(chunkIndex);
                out.writeInt(totalChunks);
                out.writeInt(entryCount);

                for (int i = start; i < end; ++i) {
                    Integer statId = sortedStatIds.get(i);
                    Integer value = snapshot.get(statId);
                    out.writeInt(statId.intValue());
                    out.writeInt(value.intValue());
                }

                player.netServerHandler.sendPacket(new Packet250CustomPayload(SNAPSHOT_CHANNEL, baos.toByteArray()));
            } catch (IOException ex) {
                System.err.println("[PlayerStatistics] Failed to send stat snapshot chunk to " + player.name + ": " + ex.getMessage());
                break;
            } finally {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Get the current value of a statistic.
     */
    public synchronized int getStatistic(Statistic statistic) {
        if (statistic == null) {
            return 0;
        }

        Integer value = statistics.get(Integer.valueOf(statistic.e));
        return value == null ? 0 : value.intValue();
    }

    /**
     * Compatibility helper, delegated to AchievementManager.
     */
    public boolean hasAchievement(Achievement achievement) {
        return player.achievementManager != null && player.achievementManager.hasAchievement(achievement);
    }

    /**
     * Compatibility helper, delegated to AchievementManager.
     */
    public void unlockAchievement(Achievement achievement) {
        if (player.achievementManager != null) {
            player.achievementManager.unlock(achievement);
        }
    }

    /**
     * Save statistics to NBT.
     */
    public synchronized void saveToNBT(NBTTagCompound nbt) {
        NBTTagCompound statsNbt = new NBTTagCompound();
        for (Map.Entry<Integer, Integer> entry : statistics.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            int statId = entry.getKey().intValue();
            int value = entry.getValue().intValue();
            if (statId < 0 || value < 0) {
                continue;
            }

            statsNbt.a("stat_" + statId, value);
        }
        nbt.a("Statistics", statsNbt);
    }

    /**
     * Load statistics from NBT.
     */
    public synchronized void loadFromNBT(NBTTagCompound nbt) {
        statistics.clear();

        if (nbt == null || !nbt.hasKey("Statistics")) {
            return;
        }

        NBTTagCompound statsNbt = nbt.k("Statistics");
        for (String key : statsNbt.getKeys()) {
            if (!key.startsWith("stat_")) {
                continue;
            }

            try {
                int statId = Integer.parseInt(key.substring(5));
                int value = statsNbt.e(key);

                if (statId < 0 || value < 0) {
                    System.err.println("[PlayerStatistics] Ignoring malformed stat entry key=" + key + " value=" + value);
                    continue;
                }

                if (statId >= ACHIEVEMENT_STAT_BASE) {
                    // Achievements are tracked by AchievementManager.
                    continue;
                }

                if (StatisticRegistryApi.getByStatId(statId) == null) {
                    System.err.println("[PlayerStatistics] Unknown stat id in save for " + player.name + ": " + statId + " (keeping value for compatibility)");
                }

                statistics.put(Integer.valueOf(statId), Integer.valueOf(value));
            } catch (NumberFormatException ignored) {
                System.err.println("[PlayerStatistics] Invalid stat key in save for " + player.name + ": " + key);
            }
        }
    }

    /**
     * Get all statistics as a map.
     */
    public synchronized Map<Integer, Integer> getAllStatistics() {
        return new HashMap<Integer, Integer>(statistics);
    }

    /**
     * Compatibility helper, delegated to AchievementManager.
     */
    public Set<Integer> getUnlockedAchievementIds() {
        if (player.achievementManager == null) {
            return new HashSet<Integer>();
        }

        return player.achievementManager.getUnlockedAchievementIds();
    }

    /**
     * Compatibility no-op; pending achievement queue is no longer tracked here.
     */
    public void clearPendingAchievements() {
    }

    /**
     * Compatibility helper; pending achievement queue is no longer tracked here.
     */
    public Set<Achievement> getPendingAchievements() {
        return new HashSet<Achievement>();
    }
}
