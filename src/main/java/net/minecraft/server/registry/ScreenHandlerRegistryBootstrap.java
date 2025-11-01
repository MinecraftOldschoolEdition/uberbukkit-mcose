package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

/**
 * Registers server-side screen handlers (container classes) under stable keys.
 * Keys must match the client GUI side so both agree during synchronization.
 */
public final class ScreenHandlerRegistryBootstrap {
    private ScreenHandlerRegistryBootstrap() {}

    public static synchronized void initialize() {
        // Core containers
        reg("chest", safe("net.minecraft.server.ContainerChest"));
        reg("furnace", safe("net.minecraft.server.ContainerFurnace"));
        reg("dispenser", safe("net.minecraft.server.ContainerDispenser"));
        reg("workbench", safe("net.minecraft.server.ContainerWorkbench"));
        // Enchanting/Brewing are not part of this version – intentionally omitted
    }

    private static void reg(String path, Class<?> container) {
        if (container == null) return;
        Registries.SCREEN_HANDLER.registerIfAbsent(new ResourceLocation("minecraft", path), container);
    }

    private static Class<?> safe(String name) {
        try { return Class.forName(name); } catch (Throwable ignored) { return null; }
    }
}


