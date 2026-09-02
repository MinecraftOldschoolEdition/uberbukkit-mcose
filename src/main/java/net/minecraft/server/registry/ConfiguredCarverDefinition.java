package net.minecraft.server.registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.server.Block;
import net.minecraft.server.MapGenBase;
import net.minecraft.server.util.ResourceLocation;

/** Immutable configured-carver projection that preserves the legacy RNG model. */
public final class ConfiguredCarverDefinition {
    public enum HeightDistribution {
        LEGACY_NESTED_RANDOM,
        UNIFORM
    }

    private final ResourceLocation id;
    private final ResourceLocation type;
    private final int range;
    private final int countBound;
    private final int startChanceBound;
    private final HeightDistribution heightDistribution;
    private final int heightBound;
    private final int heightOffset;
    private final float tunnelThicknessMultiplier;
    private final float horizontalRadiusMultiplier;
    private final double roomVerticalRadiusMultiplier;
    private final double verticalRadiusMultiplier;
    private final double floorLevel;
    private final int maximumCarveY;
    private final int lavaLevel;
    private final List<ResourceLocation> replaceableBlocks;
    private final List<ResourceLocation> avoidedFluids;
    private final boolean[] replaceableLegacyIds;
    private final boolean[] avoidedFluidLegacyIds;

    public ConfiguredCarverDefinition(
            ResourceLocation id,
            ResourceLocation type,
            int range,
            int countBound,
            int startChanceBound,
            HeightDistribution heightDistribution,
            int heightBound,
            int heightOffset,
            float tunnelThicknessMultiplier,
            float horizontalRadiusMultiplier,
            double roomVerticalRadiusMultiplier,
            double verticalRadiusMultiplier,
            double floorLevel,
            int maximumCarveY,
            int lavaLevel,
            List<ResourceLocation> replaceableBlocks,
            List<ResourceLocation> avoidedFluids) {
        if (id == null || type == null || heightDistribution == null) {
            throw new IllegalArgumentException(
                    "Configured carver identifiers and height distribution are required");
        }
        if (range < 2 || range > 32) {
            throw new IllegalArgumentException("Carver range must be between 2 and 32");
        }
        if (countBound <= 0 || startChanceBound <= 0
                || heightBound <= 0 || heightBound > 128) {
            throw new IllegalArgumentException(
                    "Carver count and start-chance bounds must be positive, "
                            + "and the height bound must be between 1 and 128");
        }
        if (heightOffset < 0 || heightOffset >= 128) {
            throw new IllegalArgumentException(
                    "Carver height offset must be between 0 and 127");
        }
        if (heightDistribution == HeightDistribution.LEGACY_NESTED_RANDOM
                && heightOffset == 0) {
            throw new IllegalArgumentException(
                    "Nested-random carver height offset must be positive");
        }
        if (heightDistribution == HeightDistribution.UNIFORM
                && heightOffset != 0) {
            throw new IllegalArgumentException(
                    "Uniform carver height cannot define an offset");
        }
        requirePositiveFinite(tunnelThicknessMultiplier,
                "tunnel thickness multiplier");
        requirePositiveFinite(horizontalRadiusMultiplier,
                "horizontal radius multiplier");
        requirePositiveFinite(roomVerticalRadiusMultiplier,
                "room vertical radius multiplier");
        requirePositiveFinite(verticalRadiusMultiplier,
                "vertical radius multiplier");
        if (Double.isNaN(floorLevel) || Double.isInfinite(floorLevel)
                || floorLevel < -1.0D || floorLevel > 1.0D) {
            throw new IllegalArgumentException(
                    "Carver floor level must be finite and between -1 and 1");
        }
        if (maximumCarveY <= 0 || maximumCarveY >= 128) {
            throw new IllegalArgumentException(
                    "Maximum carve Y must be between 1 and 127");
        }
        if (lavaLevel < -1 || lavaLevel >= 128) {
            throw new IllegalArgumentException(
                    "Carver lava level must be absent or between 0 and 127");
        }
        if (replaceableBlocks == null || replaceableBlocks.isEmpty()
                || avoidedFluids == null || avoidedFluids.isEmpty()) {
            throw new IllegalArgumentException(
                    "Configured carver block-policy sets cannot be empty");
        }
        this.id = id;
        this.type = type;
        this.range = range;
        this.countBound = countBound;
        this.startChanceBound = startChanceBound;
        this.heightDistribution = heightDistribution;
        this.heightBound = heightBound;
        this.heightOffset = heightOffset;
        this.tunnelThicknessMultiplier = tunnelThicknessMultiplier;
        this.horizontalRadiusMultiplier = horizontalRadiusMultiplier;
        this.roomVerticalRadiusMultiplier = roomVerticalRadiusMultiplier;
        this.verticalRadiusMultiplier = verticalRadiusMultiplier;
        this.floorLevel = floorLevel;
        this.maximumCarveY = maximumCarveY;
        this.lavaLevel = lavaLevel;
        this.replaceableBlocks = Collections.unmodifiableList(
                new ArrayList<ResourceLocation>(replaceableBlocks));
        this.avoidedFluids = Collections.unmodifiableList(
                new ArrayList<ResourceLocation>(avoidedFluids));
        this.replaceableLegacyIds = legacyIdMask(this.replaceableBlocks);
        this.avoidedFluidLegacyIds = legacyIdMask(this.avoidedFluids);
    }

    public ResourceLocation getId() {
        return this.id;
    }

    public ResourceLocation getType() {
        return this.type;
    }

    public int getRange() {
        return this.range;
    }

    public int getCountBound() {
        return this.countBound;
    }

    public int getStartChanceBound() {
        return this.startChanceBound;
    }

    public HeightDistribution getHeightDistribution() {
        return this.heightDistribution;
    }

    public int getHeightBound() {
        return this.heightBound;
    }

    public int getHeightOffset() {
        return this.heightOffset;
    }

    public float getTunnelThicknessMultiplier() {
        return this.tunnelThicknessMultiplier;
    }

    public float getHorizontalRadiusMultiplier() {
        return this.horizontalRadiusMultiplier;
    }

    public double getRoomVerticalRadiusMultiplier() {
        return this.roomVerticalRadiusMultiplier;
    }

    public double getVerticalRadiusMultiplier() {
        return this.verticalRadiusMultiplier;
    }

    public double getFloorLevel() {
        return this.floorLevel;
    }

    public int getMaximumCarveY() {
        return this.maximumCarveY;
    }

    public boolean hasLavaLevel() {
        return this.lavaLevel >= 0;
    }

    public int getLavaLevel() {
        return this.lavaLevel;
    }

    public List<ResourceLocation> getReplaceableBlocks() {
        return this.replaceableBlocks;
    }

    public List<ResourceLocation> getAvoidedFluids() {
        return this.avoidedFluids;
    }

    public boolean isReplaceable(byte legacyBlockId) {
        return this.replaceableLegacyIds[legacyBlockId & 255];
    }

    public boolean isAvoidedFluid(byte legacyBlockId) {
        return this.avoidedFluidLegacyIds[legacyBlockId & 255];
    }

    /** Retains Beta's nested nextInt expression and therefore its exact draw count. */
    public int sampleCaveCount(Random random) {
        return random.nextInt(random.nextInt(random.nextInt(this.countBound) + 1) + 1);
    }

    /** Retains Beta's independent rarity draw even when the sampled count is zero. */
    public boolean passesStartChance(Random random) {
        return random.nextInt(this.startChanceBound) == 0;
    }

    public int sampleY(Random random) {
        if (this.heightDistribution == HeightDistribution.LEGACY_NESTED_RANDOM) {
            return random.nextInt(random.nextInt(this.heightBound) + this.heightOffset);
        }
        return random.nextInt(this.heightBound);
    }

    public MapGenBase create() {
        CarverType carverType = CarverRegistryApi.get(this.type);
        if (carverType == null) {
            throw new IllegalStateException(
                    "Configured carver " + this.id
                            + " references missing carver type " + this.type);
        }
        return carverType.create(this);
    }

    public static ConfiguredCarverDefinition legacyCaveDefaults() {
        return new ConfiguredCarverDefinition(
                key("cave"), key("cave"),
                8, 40, 15, HeightDistribution.LEGACY_NESTED_RANDOM,
                120, 8, 1.0F, 1.0F, 0.5D, 1.0D, -0.7D, 120, 10,
                Arrays.asList(key("stone"), key("dirt"), key("grass_block")),
                Arrays.asList(key("water"), key("water_still")));
    }

    public static ConfiguredCarverDefinition legacyNetherCaveDefaults() {
        return new ConfiguredCarverDefinition(
                key("nether_cave"), key("nether_cave"),
                8, 10, 5, HeightDistribution.UNIFORM,
                128, 0, 2.0F, 1.0F, 0.5D, 0.5D, -0.7D, 120, -1,
                Arrays.asList(key("netherrack"), key("dirt"), key("grass_block")),
                Arrays.asList(key("lava"), key("lava_still")));
    }

    private static boolean[] legacyIdMask(List<ResourceLocation> keys) {
        boolean[] mask = new boolean[256];
        for (int i = 0; i < keys.size(); i++) {
            Block block = BlockRegistry.get(keys.get(i));
            if (block == null || block.id < 0 || block.id >= mask.length) {
                throw new IllegalArgumentException(
                        "Configured carver references unavailable legacy block "
                                + keys.get(i));
            }
            mask[block.id] = true;
        }
        return mask;
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }

    private static void requirePositiveFinite(double value, String description) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException(
                    "Carver " + description + " must be positive and finite");
        }
    }
}
