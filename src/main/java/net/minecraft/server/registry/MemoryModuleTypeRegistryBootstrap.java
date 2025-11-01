package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

public final class MemoryModuleTypeRegistryBootstrap {
    private static boolean initialized = false;

    private MemoryModuleTypeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        reg("nearest_player", new MemoryModuleType("Nearest non-creative player entity"));
        reg("visible_target", new MemoryModuleType("Current visible target entity"));
        reg("attack_target", new MemoryModuleType("Entity being actively attacked"));
        reg("hurt_by", new MemoryModuleType("Last entity that hurt this entity"));
        reg("last_seen_pos", new MemoryModuleType("Last known position of target"));
        reg("is_daylight", new MemoryModuleType("Whether world is currently in daylight"));
        reg("in_water", new MemoryModuleType("Whether entity is in water"));
        reg("on_fire", new MemoryModuleType("Whether entity is burning"));
    }

    private static void reg(String path, MemoryModuleType type) {
        try { Registries.MEMORY_MODULE_TYPE.registerIfAbsent(new ResourceLocation("minecraft", path), type); } catch (Throwable ignored) {}
    }
}


