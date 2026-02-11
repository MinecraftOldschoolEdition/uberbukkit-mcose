package net.minecraft.server.registry;

/**
 * Bootstrap for item capability defaults.
 */
public final class ItemCapabilityRegistryBootstrap {
    private static boolean initialized = false;

    private ItemCapabilityRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        int count = ItemCapabilityRegistryApi.bootstrapDefaults();
        ItemCapabilityRegistryApi.registerFuel("minecraft:coal_block", 1600 * 9);

        System.out.println("[ItemCapabilityRegistryBootstrap] Registered " + count + " item capabilities");
    }
}
