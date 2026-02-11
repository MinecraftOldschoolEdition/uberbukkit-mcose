package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

/**
 * Public API facade for block registration and lookups.
 */
public final class BlockRegistryApi {
    private BlockRegistryApi() {}

    public static void register(ResourceLocation key, Block value) {
        if (key == null || value == null) {
            return;
        }
        BlockRegistry.register(key, value, BlockRegistry.getLegacyId(value));
    }

    public static void register(ResourceLocation key, Block value, int legacyId) {
        BlockRegistry.register(key, value, legacyId);
    }

    public static void registerAlias(ResourceLocation alias, Block value) {
        BlockRegistry.registerAlias(alias, value);
    }

    public static Block get(ResourceLocation key) {
        return BlockRegistry.get(key);
    }

    public static Block getByIdentifier(String any) {
        return BlockRegistry.getByIdentifier(any);
    }

    public static ResourceLocation getKey(Block value) {
        return BlockRegistry.getKey(value);
    }

    public static Set<ResourceLocation> keys() {
        return BlockRegistry.keys();
    }

    public static Collection<Block> values() {
        return BlockRegistry.values();
    }

    public static int getLegacyId(Block value) {
        return BlockRegistry.getLegacyId(value);
    }

    public static Integer getLegacyId(String identifier) {
        Block value = getByIdentifier(identifier);
        return value == null ? null : Integer.valueOf(BlockRegistry.getLegacyId(value));
    }

    public static String getIdentifier(int legacyId) {
        Block value = BlockRegistry.getByLegacyId(legacyId);
        if (value == null) {
            return null;
        }
        ResourceLocation key = BlockRegistry.getKey(value);
        return key == null ? null : key.toString();
    }

    public static String normalizeInputIdentifier(String any) {
        return BlockRegistry.normalizeInputIdentifier(any);
    }

    public static String canonicalizeIdentifier(String any) {
        return BlockRegistry.canonicalizeIdentifier(any);
    }
}
