package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.util.ResourceLocation;

public final class PointOfInterestRegistryBootstrap {
    private static boolean initialized = false;

    private PointOfInterestRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        // Nether portal blocks
        reg("nether_portal", new PointOfInterestType("Nether portal blocks", Block.PORTAL.id));

        // Grazing blocks for sheep (tall grass + grass block)
        reg("grazing", new PointOfInterestType("Grazing blocks (tall grass & grass)", Block.LONG_GRASS.id, Block.GRASS.id));

        // Water and lava sources (flowing + stationary)
        reg("water", new PointOfInterestType("Water sources", Block.WATER.id, Block.STATIONARY_WATER.id));
        reg("lava", new PointOfInterestType("Lava sources", Block.LAVA.id, Block.STATIONARY_LAVA.id));
    }

    private static void reg(String path, PointOfInterestType type) {
        try { Registries.POINT_OF_INTEREST_TYPE.registerIfAbsent(new ResourceLocation("minecraft", path), type); } catch (Throwable ignored) {}
    }
}


