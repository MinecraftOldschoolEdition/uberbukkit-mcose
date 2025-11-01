package net.minecraft.server.registry;

public final class EntityTypeRegistryBootstrap {
    private static boolean initialized = false;

    private EntityTypeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        EntityTypeRegistry.bootstrapFromEntityTypes();
    }
}


