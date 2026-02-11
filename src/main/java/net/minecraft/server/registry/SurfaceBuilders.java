package net.minecraft.server.registry;

import net.minecraft.server.*;
import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class SurfaceBuilders {
    private SurfaceBuilders() {}

    public static boolean register(ResourceLocation key, SurfaceBuilderType value) {
        return SurfaceBuilderRegistryApi.register(key, value);
    }

    public static SurfaceBuilderType get(ResourceLocation key) {
        return SurfaceBuilderRegistryApi.get(key);
    }

    public static SurfaceBuilderType getByIdentifier(String any) {
        return SurfaceBuilderRegistryApi.getByIdentifier(any);
    }

    public static ResourceLocation getKey(SurfaceBuilderType value) {
        return SurfaceBuilderRegistryApi.getKey(value);
    }

    public static Set<ResourceLocation> keys() {
        return SurfaceBuilderRegistryApi.keys();
    }

    public static Collection<SurfaceBuilderType> values() {
        return SurfaceBuilderRegistryApi.values();
    }

    public static int size() {
        return SurfaceBuilderRegistryApi.size();
    }

    public static String normalizeInputIdentifier(String any) {
        return SurfaceBuilderRegistryApi.normalizeInputIdentifier(any);
    }

    public static String canonicalizeIdentifier(String any) {
        return SurfaceBuilderRegistryApi.canonicalizeIdentifier(any);
    }

    public static void applyOverworld(ChunkProviderGenerate provider, int x, int z, byte[] blocks, BiomeBase[] biomes) {
        try { SurfaceBuilderRegistryApi.getByIdentifier("minecraft:overworld_default"); } catch (Throwable ignored) {}
        provider.a(x, z, blocks, biomes);
    }

    public static void applySky(ChunkProviderSky provider, int x, int z, byte[] blocks, BiomeBase[] biomes) {
        try { SurfaceBuilderRegistryApi.getByIdentifier("minecraft:sky_default"); } catch (Throwable ignored) {}
        provider.a(x, z, blocks, biomes);
    }

    public static void applyNether(ChunkProviderHell provider, int x, int z, byte[] blocks) {
        try { SurfaceBuilderRegistryApi.getByIdentifier("minecraft:nether_default"); } catch (Throwable ignored) {}
        provider.b(x, z, blocks);
    }
}


