package net.minecraft.server.registry;

import net.minecraft.server.WorldProvider;
import net.minecraft.server.WorldProviderHell;
import net.minecraft.server.WorldProviderSky;
import net.minecraft.server.util.ResourceLocation;

import java.util.LinkedHashMap;

public final class DimensionTypeRegistryBootstrap {
    private static boolean initialized = false;

    private DimensionTypeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        DimensionTypeDataBootstrap.initialize();

        LinkedHashMap<ResourceLocation, Class<?>> providers =
                new LinkedHashMap<ResourceLocation, Class<?>>();
        // Preserve the pre-data bridge's public value exactly. Runtime
        // WorldProvider.byDimension(0) continues to construct WorldProviderNormal.
        providers.put(DimensionTypeDataBootstrap.OVERWORLD, WorldProvider.class);
        providers.put(DimensionTypeDataBootstrap.THE_NETHER, WorldProviderHell.class);
        providers.put(DimensionTypeDataBootstrap.SKY, WorldProviderSky.class);

        LinkedHashMap<Integer, ResourceLocation> ids =
                new LinkedHashMap<Integer, ResourceLocation>();
        ids.put(Integer.valueOf(0), DimensionTypeDataBootstrap.OVERWORLD);
        ids.put(Integer.valueOf(-1), DimensionTypeDataBootstrap.THE_NETHER);
        ids.put(Integer.valueOf(1), DimensionTypeDataBootstrap.SKY);
        if (!DimensionTypeRegistryApi.publishProviderMappingsAtomic(providers, ids)) {
            throw new IllegalStateException(
                    "Could not atomically publish legacy dimension provider mappings");
        }
        initialized = true;
    }
}
