package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

public final class SurfaceBuilderRegistryBootstrap {
    private static boolean initialized = false;

    private SurfaceBuilderRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        reg("overworld_default", new SurfaceBuilderType("Default overworld surface"));
        reg("nether_default", new SurfaceBuilderType("Default nether surface"));
        reg("sky_default", new SurfaceBuilderType("Default sky surface"));
        reg("alpha_overworld", new SurfaceBuilderType("Alpha overworld surface"));
        reg("classic_overworld", new SurfaceBuilderType("Classic overworld surface"));
    }

    private static void reg(String path, SurfaceBuilderType t) {
        try { SurfaceBuilderRegistryApi.register(new ResourceLocation("minecraft", path), t); } catch (Throwable ignored) {}
    }
}


