package net.minecraft.server.registry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.Achievement;
import net.minecraft.server.util.ResourceLocation;

/**
 * Registry-backed API for achievement lookups and key normalization.
 */
public final class AchievementRegistryApi {
    private static final Map<Integer, Achievement> BY_STAT_ID = new HashMap<Integer, Achievement>();

    private AchievementRegistryApi() {
    }

    public static synchronized void register(ResourceLocation key, Achievement value) {
        if (key == null || value == null) {
            return;
        }

        Registries.ACHIEVEMENT.register(key, value);
        BY_STAT_ID.put(Integer.valueOf(value.e), value);
    }

    public static Achievement get(ResourceLocation key) {
        if (key == null) {
            return null;
        }

        return Registries.ACHIEVEMENT.get(key);
    }

    public static synchronized Achievement getByStatId(int statId) {
        Achievement achievement = BY_STAT_ID.get(Integer.valueOf(statId));
        if (achievement != null) {
            return achievement;
        }

        rebuildByStatId();
        return BY_STAT_ID.get(Integer.valueOf(statId));
    }

    public static ResourceLocation getKey(Achievement value) {
        if (value == null) {
            return null;
        }

        return Registries.ACHIEVEMENT.getKey(value);
    }

    public static Set<ResourceLocation> keys() {
        return Registries.ACHIEVEMENT.keys();
    }

    public static Collection<Achievement> values() {
        return Registries.ACHIEVEMENT.values();
    }

    public static int size() {
        return keys().size();
    }

    public static String canonicalizeIdentifier(String any) {
        if (any == null) {
            return null;
        }

        ResourceLocation normalized = normalizeIdentifier(any);
        if (normalized == null) {
            return null;
        }

        Achievement achievement = get(normalized);
        if (achievement == null) {
            return null;
        }

        ResourceLocation canonical = getKey(achievement);
        return canonical == null ? null : canonical.toString();
    }

    public static String normalizeInputIdentifier(String any) {
        ResourceLocation normalized = normalizeIdentifier(any);
        return normalized == null ? null : normalized.toString();
    }

    private static ResourceLocation normalizeIdentifier(String any) {
        if (any == null) {
            return null;
        }

        try {
            ResourceLocation direct = new ResourceLocation(any);
            if (get(direct) != null) {
                return direct;
            }

            if (any.indexOf(':') < 0) {
                ResourceLocation namespaced = new ResourceLocation("minecraft", any);
                if (get(namespaced) != null) {
                    return namespaced;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static void rebuildByStatId() {
        BY_STAT_ID.clear();
        for (Achievement achievement : Registries.ACHIEVEMENT.values()) {
            if (achievement != null) {
                BY_STAT_ID.put(Integer.valueOf(achievement.e), achievement);
            }
        }
    }
}
