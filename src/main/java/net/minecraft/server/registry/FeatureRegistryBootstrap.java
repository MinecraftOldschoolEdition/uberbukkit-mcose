package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

public final class FeatureRegistryBootstrap {
    private static boolean initialized = false;

    private FeatureRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        reg("dungeon", new FeatureType("Mob dungeon structure"));
        reg("lake_water", new FeatureType("Water lake"));
        reg("lake_lava", new FeatureType("Lava lake"));
    }

    private static void reg(String path, FeatureType t) {
        try { Registries.FEATURE.registerIfAbsent(new ResourceLocation("minecraft", path), t); } catch (Throwable ignored) {}
    }
}



