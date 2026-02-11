package org.bukkit.util;

import net.minecraft.server.registry.LegacyIdBridge;

/**
 * Bukkit-facing helper for explicit legacy-ID to namespaced-key translation.
 */
public final class LegacyRegistryTranslator {
    private LegacyRegistryTranslator() {}

    public static String itemKeyFromId(int legacyId) {
        return LegacyIdBridge.itemKeyFromId(legacyId);
    }

    public static String blockKeyFromId(int legacyId) {
        return LegacyIdBridge.blockKeyFromId(legacyId);
    }

    public static Integer itemIdFromKey(String key) {
        return LegacyIdBridge.itemIdFromKey(key);
    }

    public static Integer blockIdFromKey(String key) {
        return LegacyIdBridge.blockIdFromKey(key);
    }
}
