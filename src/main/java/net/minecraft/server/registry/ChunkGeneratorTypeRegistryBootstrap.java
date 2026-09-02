package net.minecraft.server.registry;

import java.util.LinkedHashMap;
import net.minecraft.server.Alpha.AlphaChunkProvider;
import net.minecraft.server.ChunkProviderFlat;
import net.minecraft.server.ChunkProviderGenerate;
import net.minecraft.server.ChunkProviderHell;
import net.minecraft.server.ChunkProviderNetherSky;
import net.minecraft.server.ChunkProviderSky;
import net.minecraft.server.Classic.ChunkProviderClassic;
import net.minecraft.server.Classic.ChunkProviderHellClassic;
import net.minecraft.server.Infdev.InfdevChunkProvider;
import net.minecraft.server.util.ResourceLocation;

/** Explicit symbolic generator registrations; values are never reflectively constructed. */
public final class ChunkGeneratorTypeRegistryBootstrap {
    private static boolean initialized;

    private ChunkGeneratorTypeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        LinkedHashMap<ResourceLocation, Class<?>> generators =
                new LinkedHashMap<ResourceLocation, Class<?>>();
        generators.put(WorldPresetGeneratorRouting.DEFAULT, ChunkProviderGenerate.class);
        generators.put(WorldPresetGeneratorRouting.NETHER, ChunkProviderHell.class);
        generators.put(WorldPresetGeneratorRouting.NETHER_SKY, ChunkProviderNetherSky.class);
        generators.put(WorldPresetGeneratorRouting.FLAT, ChunkProviderFlat.class);
        generators.put(WorldPresetGeneratorRouting.SKY_GENERATOR, ChunkProviderSky.class);
        generators.put(WorldPresetGeneratorRouting.CLASSIC, ChunkProviderClassic.class);
        generators.put(
                WorldPresetGeneratorRouting.CLASSIC_NETHER,
                ChunkProviderHellClassic.class);
        generators.put(WorldPresetGeneratorRouting.ALPHA, AlphaChunkProvider.class);
        generators.put(WorldPresetGeneratorRouting.INFDEV, InfdevChunkProvider.class);
        if (!Registries.CHUNK_GENERATOR_TYPE.registerAllAtomic(generators)) {
            throw new IllegalStateException(
                    "Could not atomically publish chunk-generator types");
        }
        initialized = true;
    }
}
