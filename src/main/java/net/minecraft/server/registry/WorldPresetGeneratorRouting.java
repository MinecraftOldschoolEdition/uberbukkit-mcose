package net.minecraft.server.registry;

import net.minecraft.server.World;
import net.minecraft.server.WorldProvider;
import net.minecraft.server.WorldProviderHell;
import net.minecraft.server.WorldProviderSky;
import net.minecraft.server.util.ResourceLocation;

/** Symbolic routing shared by every legacy provider/generator selection path. */
public final class WorldPresetGeneratorRouting {
    public static final ResourceLocation OVERWORLD = key("overworld");
    public static final ResourceLocation THE_NETHER = key("the_nether");
    public static final ResourceLocation SKY = key("sky");

    public static final ResourceLocation DEFAULT = key("default");
    public static final ResourceLocation NETHER = key("nether");
    public static final ResourceLocation NETHER_SKY = key("nether_sky");
    public static final ResourceLocation FLAT = key("flat");
    public static final ResourceLocation SKY_GENERATOR = key("sky");
    public static final ResourceLocation CLASSIC = key("classic");
    public static final ResourceLocation CLASSIC_NETHER = key("classic_nether");
    public static final ResourceLocation ALPHA = key("alpha");
    public static final ResourceLocation INFDEV = key("infdev");

    private WorldPresetGeneratorRouting() {}

    public static ResourceLocation dimensionKey(WorldProvider provider) {
        if (provider instanceof WorldProviderHell) return THE_NETHER;
        if (provider instanceof WorldProviderSky) return SKY;
        return OVERWORLD;
    }

    public static int terrainType(World world) {
        if (world != null && world.worldProvider instanceof WorldProviderHell) {
            return ((WorldProviderHell)world.worldProvider).getNetherVariantTerrainType();
        }
        return world != null && world.worldData != null
                ? world.worldData.getTerrainType() : 0;
    }

    public static ResourceLocation generatorKey(World world) {
        if (world == null || world.worldProvider == null) {
            throw new IllegalArgumentException("World and provider cannot be null");
        }
        return generatorKey(terrainType(world), dimensionKey(world.worldProvider));
    }

    public static ResourceLocation generatorKey(
            int terrainType, ResourceLocation dimensionKey) {
        return WorldPresetDataBootstrap.generatorKeyFor(terrainType, dimensionKey);
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
