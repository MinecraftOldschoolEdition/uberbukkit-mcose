package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.util.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ensures naturally-growing blocks remain random-ticked through registry lookups.
 */
public final class NaturalGrowthRegistryBootstrap {
    private static boolean initialized = false;
    private static final Set<Integer> trackedBlockIds = new LinkedHashSet<Integer>();

    private NaturalGrowthRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        trackedBlockIds.clear();

        ensureRandomTicking("minecraft:crops", Block.CROPS);
        ensureRandomTicking("minecraft:wheat", Block.CROPS);

        ensureRandomTicking("minecraft:sapling", Block.SAPLING);
        ensureRandomTicking("minecraft:oak_sapling", Block.SAPLING);
        ensureRandomTicking("minecraft:spruce_sapling", Block.SAPLING);
        ensureRandomTicking("minecraft:birch_sapling", Block.SAPLING);

        ensureRandomTicking("minecraft:pumpkin_stem", Block.PUMPKIN_STEM);
        ensureRandomTicking("minecraft:melon_stem", Block.MELON_STEM);

        ensureRandomTicking("minecraft:grass", Block.GRASS);
        ensureRandomTicking("minecraft:grass_block", Block.GRASS);

        System.out.println("[NaturalGrowthRegistryBootstrap] Ensured random ticking for " + trackedBlockIds.size() + " natural growth blocks");
    }

    public static synchronized int getTrackedBlockCount() {
        return trackedBlockIds.size();
    }

    private static void ensureRandomTicking(String identifier, Block fallback) {
        Block block = resolve(identifier, fallback);
        if (block == null) {
            return;
        }

        int id = block.id;
        if (id < 0 || id >= Block.n.length) {
            return;
        }

        Block.n[id] = true;
        trackedBlockIds.add(Integer.valueOf(id));
    }

    private static Block resolve(String identifier, Block fallback) {
        if (identifier != null) {
            try {
                Block byKey = BlockRegistryApi.get(new ResourceLocation(identifier));
                if (byKey != null) {
                    return byKey;
                }
            } catch (Throwable ignored) {}
        }
        return fallback;
    }
}
