package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Explicit legacy numeric ID <-> namespaced key bridge.
 */
public final class LegacyIdBridge {
    private static final Map<Integer, ResourceLocation> itemIdToKey = new HashMap<Integer, ResourceLocation>();
    private static final Map<Integer, ResourceLocation> blockIdToKey = new HashMap<Integer, ResourceLocation>();
    private static final Map<String, Integer> itemKeyToId = new HashMap<String, Integer>();
    private static final Map<String, Integer> blockKeyToId = new HashMap<String, Integer>();
    private static boolean initialized = false;

    private LegacyIdBridge() {}

    public static synchronized void refresh() {
        itemIdToKey.clear();
        blockIdToKey.clear();
        itemKeyToId.clear();
        blockKeyToId.clear();

        ItemRegistry.keys();
        BlockRegistry.keys();

        for (ResourceLocation key : ItemRegistry.keys()) {
            Item item = ItemRegistry.get(key);
            if (item == null) continue;
            int legacyId = ItemRegistry.getLegacyId(item);
            if (legacyId < 0) continue;

            mapKeyToId(itemKeyToId, key.toString(), legacyId);
            ResourceLocation primary = ItemRegistry.getKey(item);
            if (primary != null) {
                itemIdToKey.put(Integer.valueOf(legacyId), primary);
            } else if (!itemIdToKey.containsKey(Integer.valueOf(legacyId))) {
                itemIdToKey.put(Integer.valueOf(legacyId), key);
            }
        }

        for (ResourceLocation key : BlockRegistry.keys()) {
            Block block = BlockRegistry.get(key);
            if (block == null) continue;
            int legacyId = BlockRegistry.getLegacyId(block);
            if (legacyId < 0) continue;

            mapKeyToId(blockKeyToId, key.toString(), legacyId);
            ResourceLocation primary = BlockRegistry.getKey(block);
            if (primary != null) {
                blockIdToKey.put(Integer.valueOf(legacyId), primary);
            } else if (!blockIdToKey.containsKey(Integer.valueOf(legacyId))) {
                blockIdToKey.put(Integer.valueOf(legacyId), key);
            }
        }

        initialized = true;
    }

    public static synchronized String itemKeyFromId(int legacyId) {
        ensureInitialized();
        ResourceLocation key = itemIdToKey.get(Integer.valueOf(legacyId));
        return key == null ? null : key.toString();
    }

    public static synchronized String blockKeyFromId(int legacyId) {
        ensureInitialized();
        ResourceLocation key = blockIdToKey.get(Integer.valueOf(legacyId));
        return key == null ? null : key.toString();
    }

    public static synchronized Integer itemIdFromKey(String key) {
        ensureInitialized();
        return lookupKey(itemKeyToId, key);
    }

    public static synchronized Integer blockIdFromKey(String key) {
        ensureInitialized();
        return lookupKey(blockKeyToId, key);
    }

    private static void ensureInitialized() {
        if (!initialized) {
            refresh();
        }
    }

    private static void mapKeyToId(Map<String, Integer> map, String key, int id) {
        if (key == null) return;
        String normalized = RegistryKeyPolicy.normalizeIdentifier(key);
        if (normalized == null) return;
        if (!map.containsKey(normalized)) {
            map.put(normalized, Integer.valueOf(id));
        }
    }

    private static Integer lookupKey(Map<String, Integer> map, String key) {
        if (key == null) return null;
        String normalized = RegistryKeyPolicy.normalizeIdentifier(key);
        if (normalized == null) return null;
        return map.get(normalized);
    }
}
