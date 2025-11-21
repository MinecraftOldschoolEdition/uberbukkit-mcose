package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

public final class TreeDecoratorTypeRegistryBootstrap {
    private static boolean initialized = false;

    private TreeDecoratorTypeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        reg("trunk_vine", new TreeDecoratorType("Attach vines to trunk"));
        reg("leaves_vine", new TreeDecoratorType("Vines hanging under leaves"));
        reg("cocoa", new TreeDecoratorType("Cocoa pods on jungle trunks"));
        reg("beehive", new TreeDecoratorType("Beehive on trunk"));
        reg("alter_ground", new TreeDecoratorType("Alter ground around trunk"));
    }

    private static void reg(String path, TreeDecoratorType t) {
        try { Registries.TREE_DECORATOR_TYPE.registerIfAbsent(new ResourceLocation("minecraft", path), t); } catch (Throwable ignored) {}
    }
}



