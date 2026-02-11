package net.minecraft.server.registry;

import net.minecraft.server.WorldProvider;
import net.minecraft.server.WorldProviderHell;
import net.minecraft.server.WorldProviderSky;
import net.minecraft.server.util.ResourceLocation;

public final class DimensionTypeRegistryBootstrap {
    private static boolean initialized = false;

    private DimensionTypeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        try {
            register("overworld", WorldProvider.class);
            register("nether", WorldProviderHell.class);
            // Sky dimension provider is present in this codebase
            register("sky", WorldProviderSky.class);
        } catch (Throwable ignored) {}
    }

    private static void register(String keyPath, Class<?> provider) {
        if (provider == null) return;
        DimensionTypeRegistryApi.register(new ResourceLocation("minecraft", keyPath), provider);
    }
}

