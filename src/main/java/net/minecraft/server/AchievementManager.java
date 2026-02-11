package net.minecraft.server;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import net.minecraft.server.registry.AchievementRegistryApi;
import net.minecraft.server.util.ResourceLocation;

/**
 * Centralized Achievement Manager for the server.
 * Handles achievement tracking, unlocking, persistence, and broadcasting.
 */
public final class AchievementManager {

    private static final Logger log = Logger.getLogger("Minecraft");

    private static final int ACHIEVEMENT_STAT_BASE = 5242880;
    private static final int NBT_VERSION_LEGACY = 1;
    private static final int NBT_VERSION_KEYED = 2;
    private static final String NBT_VERSION = "Version";
    private static final String NBT_ACHIEVEMENTS = "Achievements";
    private static final String NBT_ID = "id";
    private static final String NBT_KEY = "key";

    private final EntityPlayer player;
    private final Set<Integer> unlockedAchievementIds;

    public AchievementManager(EntityPlayer player) {
        this.player = player;
        this.unlockedAchievementIds = new HashSet<Integer>();
    }

    public boolean hasAchievement(Achievement achievement) {
        if (achievement == null) {
            return false;
        }

        return unlockedAchievementIds.contains(Integer.valueOf(achievement.e));
    }

    public boolean hasAchievementById(int statId) {
        return unlockedAchievementIds.contains(Integer.valueOf(statId));
    }

    public boolean hasAchievement(String namespacedKey) {
        Achievement achievement = findAchievementByKey(namespacedKey);
        return achievement != null && hasAchievement(achievement);
    }

    public boolean canUnlock(Achievement achievement) {
        if (achievement == null) {
            return false;
        }

        return achievement.c == null || hasAchievement(achievement.c);
    }

    public boolean unlock(Achievement achievement) {
        if (achievement == null) {
            return false;
        }

        if (hasAchievement(achievement)) {
            return false;
        }

        if (!canUnlock(achievement)) {
            return false;
        }

        unlockedAchievementIds.add(Integer.valueOf(achievement.e));
        ServerStatistics.getInstance().recordAchievement(player.name, achievement);
        sendAchievementPacket(achievement);
        broadcastAchievement(achievement);

        log.info("[Achievement] " + player.name + " earned: " + getAchievementName(achievement));
        return true;
    }

    public boolean unlock(String namespacedKey) {
        return unlock(findAchievementByKey(namespacedKey));
    }

    public boolean forceUnlock(Achievement achievement) {
        if (achievement == null) {
            return false;
        }

        if (hasAchievement(achievement)) {
            return false;
        }

        unlockedAchievementIds.add(Integer.valueOf(achievement.e));
        ServerStatistics.getInstance().recordAchievement(player.name, achievement);
        sendAchievementPacket(achievement);
        broadcastAchievement(achievement);

        log.info("[Achievement] " + player.name + " earned (forced): " + getAchievementName(achievement));
        return true;
    }

    public boolean forceUnlock(String namespacedKey) {
        return forceUnlock(findAchievementByKey(namespacedKey));
    }

    public Achievement getAchievement(String namespacedKey) {
        return findAchievementByKey(namespacedKey);
    }

    private void sendAchievementPacket(Achievement achievement) {
        if (player.netServerHandler == null) {
            return;
        }

        if (!player.protocol.canReceivePacket(200)) {
            return;
        }

        player.netServerHandler.sendPacket(new Packet200Statistic(achievement.e, 1));
    }

    private void broadcastAchievement(Achievement achievement) {
        MinecraftServer server = player.b;
        if (server == null && player.world instanceof WorldServer) {
            server = ((WorldServer) player.world).server;
        }

        if (server == null || server.serverConfigurationManager == null) {
            sendChatToPlayer("\u00A7eYou have just earned the achievement \u00A7a[" + getAchievementName(achievement) + "]");
            return;
        }

        boolean shouldBroadcast = true;
        try {
            WorldServer overworld = server.getWorldServer(0);
            if (overworld != null && overworld.worldData != null) {
                shouldBroadcast = overworld.worldData.getAdvertiseAchievements();
            }
        } catch (Exception ignored) {
            shouldBroadcast = true;
        }

        String achievementName = getAchievementName(achievement);
        String message = "\u00A7e" + player.name + " has just earned the achievement \u00A7a[" + achievementName + "]";

        if (shouldBroadcast) {
            server.serverConfigurationManager.sendAll(new Packet3Chat(message));
        } else {
            sendChatToPlayer(message);
        }
    }

    private void sendChatToPlayer(String message) {
        if (player.netServerHandler != null) {
            player.netServerHandler.sendPacket(new Packet3Chat(message));
        }
    }

    public static String getAchievementName(Achievement achievement) {
        if (achievement == null) {
            return "Unknown Achievement";
        }

        if (achievement.f != null && !achievement.f.startsWith("achievement.")) {
            return achievement.f;
        }

        ResourceLocation key = AchievementRegistryApi.getKey(achievement);
        if (key != null) {
            return formatAchievementName(key.getPath());
        }

        return "Unknown Achievement";
    }

    private static String formatAchievementName(String keyPath) {
        if (keyPath == null || keyPath.length() == 0) {
            return "Unknown Achievement";
        }

        StringBuilder out = new StringBuilder();
        boolean nextUpper = true;
        for (int i = 0; i < keyPath.length(); i++) {
            char c = keyPath.charAt(i);
            if (c == '_') {
                out.append(' ');
                nextUpper = true;
                continue;
            }

            if (nextUpper && c >= 'a' && c <= 'z') {
                out.append((char) (c - 32));
            } else {
                out.append(c);
            }
            nextUpper = false;
        }

        return out.toString();
    }

    public void saveToNBT(NBTTagCompound nbt) {
        nbt.a(NBT_VERSION, NBT_VERSION_KEYED);

        NBTTagList achievementList = new NBTTagList();
        for (Integer id : unlockedAchievementIds) {
            int statId = id.intValue();
            NBTTagCompound achievementTag = new NBTTagCompound();

            Achievement achievement = findAchievementByStatId(statId);
            if (achievement != null) {
                ResourceLocation key = AchievementRegistryApi.getKey(achievement);
                if (key != null) {
                    achievementTag.setString(NBT_KEY, key.toString());
                }
                statId = achievement.e;
            }

            achievementTag.a(NBT_ID, statId);
            achievementList.a(achievementTag);
        }

        nbt.a(NBT_ACHIEVEMENTS, achievementList);
    }

    public void loadFromNBT(NBTTagCompound nbt) {
        unlockedAchievementIds.clear();

        if (nbt == null || !nbt.hasKey(NBT_ACHIEVEMENTS)) {
            log.fine("[AchievementManager] Loaded 0 achievements for " + player.name);
            return;
        }

        int version = nbt.hasKey(NBT_VERSION) ? nbt.e(NBT_VERSION) : NBT_VERSION_LEGACY;

        NBTTagList achievementList = nbt.l(NBT_ACHIEVEMENTS);
        int resolved = 0;

        for (int i = 0; i < achievementList.c(); i++) {
            NBTBase entry = achievementList.a(i);
            if (!(entry instanceof NBTTagCompound)) {
                continue;
            }

            NBTTagCompound achievementTag = (NBTTagCompound) entry;
            Achievement achievement = resolveAchievement(achievementTag, version);
            if (achievement != null) {
                unlockedAchievementIds.add(Integer.valueOf(achievement.e));
                resolved++;
                continue;
            }

            if (achievementTag.hasKey(NBT_ID)) {
                int statId = achievementTag.e(NBT_ID);
                if (statId >= ACHIEVEMENT_STAT_BASE) {
                    unlockedAchievementIds.add(Integer.valueOf(statId));
                }
            }
        }

        log.fine("[AchievementManager] Loaded " + unlockedAchievementIds.size() + " achievements for " + player.name + " (" + resolved + " registry-resolved)");
    }

    private Achievement resolveAchievement(NBTTagCompound achievementTag, int version) {
        if (achievementTag == null) {
            return null;
        }

        if (achievementTag.hasKey(NBT_KEY)) {
            String keyString = achievementTag.getString(NBT_KEY);
            Achievement byKey = findAchievementByKey(keyString);
            if (byKey != null) {
                return byKey;
            }
        }

        if (achievementTag.hasKey(NBT_ID)) {
            Achievement byId = findAchievementByStatId(achievementTag.e(NBT_ID));
            if (byId != null) {
                return byId;
            }
        }

        if (version <= NBT_VERSION_LEGACY && achievementTag.hasKey(NBT_KEY)) {
            Achievement byLegacyKey = findAchievementByKey(achievementTag.getString(NBT_KEY));
            if (byLegacyKey != null) {
                return byLegacyKey;
            }
        }

        return null;
    }

    public void syncAllToClient() {
        if (player.netServerHandler == null) {
            return;
        }

        if (!player.protocol.canReceivePacket(200)) {
            return;
        }

        for (Integer statId : new TreeSet<Integer>(unlockedAchievementIds)) {
            player.netServerHandler.sendPacket(new Packet200Statistic(statId.intValue(), 1));
        }

        log.fine("[AchievementManager] Synced " + unlockedAchievementIds.size() + " achievements to " + player.name);
    }

    public Set<Integer> getUnlockedAchievementIds() {
        return new HashSet<Integer>(unlockedAchievementIds);
    }

    public Set<String> getUnlockedAchievementKeys() {
        Set<String> keys = new TreeSet<String>();
        for (Integer statId : unlockedAchievementIds) {
            Achievement achievement = findAchievementByStatId(statId.intValue());
            if (achievement == null) {
                continue;
            }

            ResourceLocation key = AchievementRegistryApi.getKey(achievement);
            if (key != null) {
                keys.add(key.toString());
            }
        }

        return keys;
    }

    public int getUnlockedCount() {
        return unlockedAchievementIds.size();
    }

    public static Achievement findAchievementByStatId(int statId) {
        if (statId < ACHIEVEMENT_STAT_BASE) {
            return null;
        }

        return AchievementRegistryApi.getByStatId(statId);
    }

    public static Achievement findAchievementByKey(ResourceLocation key) {
        return AchievementRegistryApi.get(key);
    }

    public static Achievement findAchievementByKey(String keyString) {
        ResourceLocation key = parseKey(keyString);
        return key == null ? null : findAchievementByKey(key);
    }

    private static ResourceLocation parseKey(String keyString) {
        if (keyString == null || keyString.length() == 0) {
            return null;
        }

        try {
            return new ResourceLocation(keyString);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean trigger(EntityPlayer player, Achievement achievement) {
        if (player != null && player.achievementManager != null && achievement != null) {
            return player.achievementManager.unlock(achievement);
        }

        return false;
    }

    public static boolean trigger(EntityPlayer player, String namespacedKey) {
        if (player != null && player.achievementManager != null) {
            return player.achievementManager.unlock(namespacedKey);
        }

        return false;
    }

    public static boolean hasUnlocked(EntityPlayer player, Achievement achievement) {
        if (player != null && player.achievementManager != null && achievement != null) {
            return player.achievementManager.hasAchievement(achievement);
        }

        return false;
    }

    public static boolean hasUnlocked(EntityPlayer player, String namespacedKey) {
        if (player != null && player.achievementManager != null) {
            return player.achievementManager.hasAchievement(namespacedKey);
        }

        return false;
    }
}
