package net.minecraft.server.registry;

import java.util.Locale;
import java.util.Collection;
import java.util.Set;
import net.minecraft.server.WorldGenerator;
import net.minecraft.server.WorldGenTrees;
import net.minecraft.server.WorldGenBigTree;
import net.minecraft.server.WorldGenTaiga1;
import net.minecraft.server.WorldGenTaiga2;
import net.minecraft.server.util.ResourceLocation;

/**
 * Server-side factory for resolving foliage placer keys to world generators.
 */
public final class FoliagePlacers {
    private FoliagePlacers() {}

    public static boolean register(ResourceLocation key, FoliagePlacerType value) {
        return FoliagePlacerTypeRegistryApi.register(key, value);
    }

    public static FoliagePlacerType get(ResourceLocation key) {
        return FoliagePlacerTypeRegistryApi.get(key);
    }

    public static FoliagePlacerType getByIdentifier(String any) {
        return FoliagePlacerTypeRegistryApi.getByIdentifier(any);
    }

    public static ResourceLocation getKey(FoliagePlacerType value) {
        return FoliagePlacerTypeRegistryApi.getKey(value);
    }

    public static Set<ResourceLocation> keys() {
        return FoliagePlacerTypeRegistryApi.keys();
    }

    public static Collection<FoliagePlacerType> values() {
        return FoliagePlacerTypeRegistryApi.values();
    }

    public static int size() {
        return FoliagePlacerTypeRegistryApi.size();
    }

    public static String normalizeInputIdentifier(String any) {
        return FoliagePlacerTypeRegistryApi.normalizeInputIdentifier(any);
    }

    public static String canonicalizeIdentifier(String any) {
        return FoliagePlacerTypeRegistryApi.canonicalizeIdentifier(any);
    }

    public static WorldGenerator create(ResourceLocation key) {
        if (key == null) return new WorldGenTrees();
        String path = key.getPath();
        if (path == null) path = "blob_foliage_placer";
        String p = path.toLowerCase(Locale.ROOT);
        if ("fancy_foliage_placer".equals(p)) return new WorldGenBigTree();
        if ("spruce_foliage_placer".equals(p)) return new WorldGenTaiga2();
        if ("pine_foliage_placer".equals(p)) return new WorldGenTaiga1();
        return new WorldGenTrees();
    }

    public static WorldGenerator create(String namespaced) {
        try {
            ResourceLocation key = namespaced.indexOf(':') >= 0 ? new ResourceLocation(namespaced) : new ResourceLocation("minecraft", namespaced);
            FoliagePlacerType t = FoliagePlacerTypeRegistryApi.get(key);
            if (t == null) return create(new ResourceLocation("minecraft","blob_foliage_placer"));
            return create(key);
        } catch (Throwable ignored) {
            return new WorldGenTrees();
        }
    }
}


