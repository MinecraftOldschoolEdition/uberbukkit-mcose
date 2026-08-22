package net.minecraft.server.registry;

import net.minecraft.server.EnumArt;
import net.minecraft.server.PaintingVariant;
import net.minecraft.server.util.ResourceLocation;

public final class PaintingMotiveRegistryBootstrap {
    private static boolean initialized = false;

    private PaintingMotiveRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        PaintingVariantRegistryBootstrap.initialize();

        PaintingVariant[] variants = PaintingVariantRegistryApi.values().toArray(
                new PaintingVariant[PaintingVariantRegistryApi.size()]);
        for (PaintingVariant variant : variants) {
            EnumArt art = variant.getLegacyArt();
            ResourceLocation key = PaintingVariantRegistryApi.getKey(variant);
            PaintingMotiveRegistryApi.register(key, art);
            // Alias: enum constant name (lowercase)
            String lc = art.name().toLowerCase();
            if (!lc.equals(key.getPath())) {
                PaintingMotiveRegistryApi.register(new ResourceLocation("minecraft", lc), art);
            }
        }
        initialized = true;
    }
}
