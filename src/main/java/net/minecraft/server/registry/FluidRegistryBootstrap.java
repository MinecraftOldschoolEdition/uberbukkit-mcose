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
            Registries.FLUID.registerIfAbsent(new ResourceLocation("minecraft","water"), Block.WATER);
            Registries.FLUID.registerIfAbsent(new ResourceLocation("minecraft","water_stationary"), Block.STATIONARY_WATER);
            Registries.FLUID.registerIfAbsent(new ResourceLocation("minecraft","lava"), Block.LAVA);
            Registries.FLUID.registerIfAbsent(new ResourceLocation("minecraft","lava_stationary"), Block.STATIONARY_LAVA);
        } catch (Throwable ignored) {}
    }
}


