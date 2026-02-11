package net.minecraft.server.registry;

/**
 * Bootstrap for the server-side player statistics registry.
 */
public final class StatRegistryBootstrap {
    private static boolean initialized = false;

    private StatRegistryBootstrap() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        StatisticRegistryApi.bootstrapBuiltins();
        System.out.println("[StatRegistryBootstrap] Registered " + StatisticRegistryApi.size() + " stat keys");
    }
}
