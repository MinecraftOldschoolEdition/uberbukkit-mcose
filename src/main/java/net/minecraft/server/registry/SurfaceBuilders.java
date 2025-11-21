package net.minecraft.server.registry;

import net.minecraft.server.*;
import net.minecraft.server.util.ResourceLocation;

public final class SurfaceBuilders {
    private SurfaceBuilders() {}

    public static void applyOverworld(ChunkProviderGenerate provider, int x, int z, byte[] blocks, BiomeBase[] biomes) {
        try { Registries.SURFACE_BUILDER.get(new ResourceLocation("minecraft","overworld_default")); } catch (Throwable ignored) {}
        provider.a(x, z, blocks, biomes);
    }

    public static void applySky(ChunkProviderSky provider, int x, int z, byte[] blocks, BiomeBase[] biomes) {
        try { Registries.SURFACE_BUILDER.get(new ResourceLocation("minecraft","sky_default")); } catch (Throwable ignored) {}
        provider.a(x, z, blocks, biomes);
    }

    public static void applyNether(ChunkProviderHell provider, int x, int z, byte[] blocks) {
        try { Registries.SURFACE_BUILDER.get(new ResourceLocation("minecraft","nether_default")); } catch (Throwable ignored) {}
        provider.b(x, z, blocks);
    }
}



