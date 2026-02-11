package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.util.ResourceLocation;

public final class FluidRegistryBootstrap {
    private static boolean initialized = false;

    private FluidRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        try {
            // Canonical keys map to the flowing variant; provide aliases for stationary
            FluidRegistryApi.register(new ResourceLocation("minecraft","water"), Block.WATER);
            FluidRegistryApi.register(new ResourceLocation("minecraft","water_stationary"), Block.STATIONARY_WATER);
            FluidRegistryApi.register(new ResourceLocation("minecraft","lava"), Block.LAVA);
            FluidRegistryApi.register(new ResourceLocation("minecraft","lava_stationary"), Block.STATIONARY_LAVA);
        } catch (Throwable ignored) {}
    }
}

