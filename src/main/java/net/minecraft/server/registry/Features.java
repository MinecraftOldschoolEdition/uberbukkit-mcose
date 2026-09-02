package net.minecraft.server.registry;

import net.minecraft.server.*;
import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class Features {
    private Features() {}

    public static boolean register(ResourceLocation key, FeatureType value) {
        return FeatureRegistryApi.register(key, value);
    }

    public static FeatureType get(ResourceLocation key) {
        return FeatureRegistryApi.get(key);
    }

    public static FeatureType getByIdentifier(String any) {
        return FeatureRegistryApi.getByIdentifier(any);
    }

    public static ResourceLocation getKey(FeatureType value) {
        return FeatureRegistryApi.getKey(value);
    }

    public static Set<ResourceLocation> keys() {
        return FeatureRegistryApi.keys();
    }

    public static Collection<FeatureType> values() {
        return FeatureRegistryApi.values();
    }

    public static int size() {
        return FeatureRegistryApi.size();
    }

    public static String normalizeInputIdentifier(String any) {
        return FeatureRegistryApi.normalizeInputIdentifier(any);
    }

    public static String canonicalizeIdentifier(String any) {
        return FeatureRegistryApi.canonicalizeIdentifier(any);
    }

    public static WorldGenerator create(ResourceLocation key) {
        if (key == null) return null;
        String path = key.getPath();
        if ("dungeon".equals(path) || "monster_room".equals(path)) return new WorldGenDungeons();
        if ("lake_water".equals(path)) return new WorldGenLakes("minecraft:water");
        if ("lake_lava".equals(path)) return new WorldGenLakes("minecraft:lava");
        return null;
    }

    public static WorldGenerator create(String namespaced) {
        ResourceLocation key = namespaced.indexOf(':') >= 0 ? new ResourceLocation(namespaced) : new ResourceLocation("minecraft", namespaced);
        FeatureType t = FeatureRegistryApi.get(key);
        if (t == null) return create(key);
        return create(key);
    }
}

