package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

public final class CarverRegistryBootstrap {
    private static boolean initialized = false;

    private CarverRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        reg("cave", new CarverType("Overworld caves"));
        reg("nether_cave", new CarverType("Nether caves"));
        reg("sky_cave", new CarverType("Sky dimension caves"));
    }

    private static void reg(String path, CarverType t) {
        try { Registries.CARVER.registerIfAbsent(new ResourceLocation("minecraft", path), t); } catch (Throwable ignored) {}
    }
}



