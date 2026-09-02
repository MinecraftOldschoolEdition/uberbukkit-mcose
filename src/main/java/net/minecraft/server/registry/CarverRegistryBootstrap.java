package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

public final class CarverRegistryBootstrap {
    private static boolean initialized = false;

    private CarverRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        CarverRegistryApi.register(
                new ResourceLocation("minecraft", "cave"),
                ConfiguredCarverCodec.caveType());
        CarverRegistryApi.register(
                new ResourceLocation("minecraft", "nether_cave"),
                ConfiguredCarverCodec.netherCaveType());
    }
}

