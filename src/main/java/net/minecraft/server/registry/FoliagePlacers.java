package net.minecraft.server.registry;

import java.util.Locale;
import net.minecraft.server.WorldGenerator;
import net.minecraft.server.WorldGenTrees;
import net.minecraft.server.WorldGenBigTree;
import net.minecraft.server.WorldGenTaiga1;
import net.minecraft.server.WorldGenTaiga2;
import net.minecraft.server.util.ResourceLocation;

/**
 * Server-side factory for resolving foliage placer keys to world generators.
 */
public final class FoliagePlacers {
    private FoliagePlacers() {}

    public static WorldGenerator create(ResourceLocation key) {
        if (key == null) return new WorldGenTrees();
        String path = key.getPath();
        if (path == null) path = "blob_foliage_placer";
        String p = path.toLowerCase(Locale.ROOT);
        if ("fancy_foliage_placer".equals(p)) return new WorldGenBigTree();
        if ("spruce_foliage_placer".equals(p)) return new WorldGenTaiga2();
        if ("pine_foliage_placer".equals(p)) return new WorldGenTaiga1();
        return new WorldGenTrees();
    }

    public static WorldGenerator create(String namespaced) {
        try {
            ResourceLocation key = namespaced.indexOf(':') >= 0 ? new ResourceLocation(namespaced) : new ResourceLocation("minecraft", namespaced);
            FoliagePlacerType t = Registries.FOLIAGE_PLACER_TYPE.get(key);
            if (t == null) return create(new ResourceLocation("minecraft","blob_foliage_placer"));
            return create(key);
        } catch (Throwable ignored) {
            return new WorldGenTrees();
        }
    }
}



