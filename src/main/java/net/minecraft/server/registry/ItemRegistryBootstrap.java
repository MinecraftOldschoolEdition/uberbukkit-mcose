package net.minecraft.server.registry;

public final class ItemRegistryBootstrap {
    private static boolean initialized = false;

    private ItemRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        ItemRegistry.bootstrapFromItemsList();
        LegacyIdBridge.refresh();
    }
}
