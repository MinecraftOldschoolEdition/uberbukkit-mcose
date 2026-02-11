package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

public final class SensorTypeRegistryBootstrap {
    private static boolean initialized = false;

    private SensorTypeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        reg("nearest_player", new SensorType("Find nearest player within a radius"));
        reg("line_of_sight", new SensorType("Has line of sight to target"));
        reg("daylight", new SensorType("World is in daytime"));
        reg("low_light", new SensorType("World is in low light (night)"));
        reg("in_water", new SensorType("Entity is in water"));
        reg("in_lava", new SensorType("Entity is in lava"));
        reg("on_fire", new SensorType("Entity is burning"));
        reg("on_ground", new SensorType("Entity is on ground"));
        reg("can_climb", new SensorType("Entity can climb/clinging to wall"));
    }

    private static void reg(String path, SensorType type) {
        try {
            SensorTypeRegistryApi.register(new ResourceLocation("minecraft", path), type);
        } catch (Throwable ignored) {}
    }
}

