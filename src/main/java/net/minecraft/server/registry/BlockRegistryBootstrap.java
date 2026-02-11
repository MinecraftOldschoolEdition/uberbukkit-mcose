package net.minecraft.server.registry;

public final class BlockRegistryBootstrap {
    private static boolean initialized = false;

    private BlockRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        BlockRegistry.bootstrapFromBlocksList();
        LegacyIdBridge.refresh();
    }
}

