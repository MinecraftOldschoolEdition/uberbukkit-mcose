package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

public final class FoliagePlacerTypeRegistryBootstrap {
    private static boolean initialized = false;

    private FoliagePlacerTypeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        reg("blob_foliage_placer", new FoliagePlacerType("Classic blob canopy"));
        reg("spruce_foliage_placer", new FoliagePlacerType("Conical spruce canopy"));
        reg("pine_foliage_placer", new FoliagePlacerType("Tall pine canopy"));
        reg("fancy_foliage_placer", new FoliagePlacerType("Large branching canopy"));
        reg("jungle_foliage_placer", new FoliagePlacerType("Jungle canopy"));
        reg("acacia_foliage_placer", new FoliagePlacerType("Flat acacia canopy"));
        reg("bush_foliage_placer", new FoliagePlacerType("Shrub/bush canopy"));
        reg("dark_oak_foliage_placer", new FoliagePlacerType("Dense dark oak canopy"));
        reg("mega_pine_foliage_placer", new FoliagePlacerType("Mega pine canopy"));
        reg("mega_jungle_foliage_placer", new FoliagePlacerType("Mega jungle canopy"));
        reg("random_spread_foliage_placer", new FoliagePlacerType("Random spread foliage"));
    }

    private static void reg(String path, FoliagePlacerType t) {
        try { Registries.FOLIAGE_PLACER_TYPE.registerIfAbsent(new ResourceLocation("minecraft", path), t); } catch (Throwable ignored) {}
    }
}



