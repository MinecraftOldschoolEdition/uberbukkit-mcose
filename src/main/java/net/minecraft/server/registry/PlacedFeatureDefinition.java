package net.minecraft.server.registry;

import java.util.Random;
import net.minecraft.server.util.ResourceLocation;

/** Immutable supported projection of a 26.3 placed worldgen feature. */
public final class PlacedFeatureDefinition {
    public static final ResourceLocation UNIFORM =
            new ResourceLocation("minecraft", "uniform");
    public static final ResourceLocation TRAPEZOID =
            new ResourceLocation("minecraft", "trapezoid");
    public static final ResourceLocation BIASED_TO_BOTTOM =
            new ResourceLocation("minecraft", "biased_to_bottom");
    public static final ResourceLocation VERY_BIASED_TO_BOTTOM =
            new ResourceLocation("minecraft", "very_biased_to_bottom");
    public static final ResourceLocation BLOCK_PREDICATE_FILTER =
            new ResourceLocation("minecraft", "block_predicate_filter");
    public static final ResourceLocation MATCHING_FLUIDS =
            new ResourceLocation("minecraft", "matching_fluids");
    public static final ResourceLocation RARITY_FILTER =
            new ResourceLocation("minecraft", "rarity_filter");
    public static final ResourceLocation LEGACY_LAVA_LAKE_FILTER =
            new ResourceLocation("minecraft", "legacy_lava_lake_filter");
    public static final ResourceLocation BIOME =
            new ResourceLocation("minecraft", "biome");

    public static final class HeightProvider {
        private final ResourceLocation type;
        private final int minimumInclusive;
        private final int maximumInclusive;
        private final int plateau;
        private final int inner;

        public HeightProvider(
                ResourceLocation type,
                int minimumInclusive,
                int maximumInclusive,
                int plateau) {
            this(type, minimumInclusive, maximumInclusive, plateau, 0);
        }

        public HeightProvider(
                ResourceLocation type,
                int minimumInclusive,
                int maximumInclusive,
                int plateau,
                int inner) {
            if (!UNIFORM.equals(type) && !TRAPEZOID.equals(type)
                    && !BIASED_TO_BOTTOM.equals(type)
                    && !VERY_BIASED_TO_BOTTOM.equals(type)) {
                throw new IllegalArgumentException(
                        "Unsupported height provider type: " + type);
            }
            if (minimumInclusive > maximumInclusive || plateau < 0
                    || inner < 0) {
                throw new IllegalArgumentException("Invalid height-provider bounds");
            }
            if (TRAPEZOID.equals(type)
                    && plateau > maximumInclusive - minimumInclusive) {
                throw new IllegalArgumentException(
                        "Trapezoid plateau exceeds its height range");
            }
            boolean biased = BIASED_TO_BOTTOM.equals(type)
                    || VERY_BIASED_TO_BOTTOM.equals(type);
            if (biased && (inner < 1
                    || maximumInclusive - minimumInclusive - inner + 1 <= 0)) {
                throw new IllegalArgumentException(
                        "Biased height provider has an empty inner range");
            }
            if (!biased && inner != 0) {
                throw new IllegalArgumentException(
                        "Only biased height providers accept inner");
            }
            this.type = type;
            this.minimumInclusive = minimumInclusive;
            this.maximumInclusive = maximumInclusive;
            this.plateau = plateau;
            this.inner = inner;
        }

        public ResourceLocation getType() { return this.type; }
        public int getMinimumInclusive() { return this.minimumInclusive; }
        public int getMaximumInclusive() { return this.maximumInclusive; }
        public int getPlateau() { return this.plateau; }
        public int getInner() { return this.inner; }

        /** Samples with the exact Random calls used by the corresponding legacy code. */
        public int sampleLegacy(Random random) {
            if (random == null) {
                throw new IllegalArgumentException("Random source cannot be null");
            }
            int range = this.maximumInclusive - this.minimumInclusive;
            if (BIASED_TO_BOTTOM.equals(this.type)) {
                int limit = random.nextInt(range - this.inner + 1);
                return this.minimumInclusive
                        + random.nextInt(limit + this.inner);
            }
            if (VERY_BIASED_TO_BOTTOM.equals(this.type)) {
                int upperInclusive = this.minimumInclusive + this.inner
                        + random.nextInt(range - this.inner + 1);
                int biasedUpperInclusive = this.minimumInclusive
                        + random.nextInt(upperInclusive - this.minimumInclusive);
                return this.minimumInclusive + random.nextInt(
                        biasedUpperInclusive - this.minimumInclusive
                                + this.inner);
            }
            if (UNIFORM.equals(this.type) || this.plateau >= range) {
                return this.minimumInclusive + random.nextInt(range + 1);
            }
            int lowerSlope = (range - this.plateau) / 2;
            int upperSlope = range - lowerSlope;
            return this.minimumInclusive
                    + random.nextInt(upperSlope + 1)
                    + random.nextInt(lowerSlope + 1);
        }
    }

    private final ResourceLocation id;
    private final ResourceLocation feature;
    private final int count;
    private final HeightProvider height;
    private final ResourceLocation requiredOriginFluid;
    private final int rarityChance;
    private final boolean legacyLavaLakeFilter;
    private final boolean biomeOnly;

    /** Creates the modern biome-filter-only shape used by freeze_top_layer. */
    public PlacedFeatureDefinition(
            ResourceLocation id,
            ResourceLocation feature) {
        if (id == null || feature == null) {
            throw new IllegalArgumentException(
                    "Placed feature identifiers are required");
        }
        this.id = id;
        this.feature = feature;
        this.count = 0;
        this.height = null;
        this.requiredOriginFluid = null;
        this.rarityChance = 0;
        this.legacyLavaLakeFilter = false;
        this.biomeOnly = true;
    }

    public PlacedFeatureDefinition(
            ResourceLocation id,
            ResourceLocation feature,
            int count,
            HeightProvider height) {
        this(id, feature, count, height, null, 0, false);
    }

    public PlacedFeatureDefinition(
            ResourceLocation id,
            ResourceLocation feature,
            int count,
            HeightProvider height,
            ResourceLocation requiredOriginFluid) {
        this(id, feature, count, height, requiredOriginFluid, 0, false);
    }

    public PlacedFeatureDefinition(
            ResourceLocation id,
            ResourceLocation feature,
            int count,
            HeightProvider height,
            ResourceLocation requiredOriginFluid,
            int rarityChance,
            boolean legacyLavaLakeFilter) {
        if (id == null || feature == null || height == null) {
            throw new IllegalArgumentException(
                    "Placed feature identifiers and height are required");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Placed feature count must be positive");
        }
        if (rarityChance < 0) {
            throw new IllegalArgumentException(
                    "Placed feature rarity chance cannot be negative");
        }
        if (legacyLavaLakeFilter && rarityChance == 0) {
            throw new IllegalArgumentException(
                    "Legacy lava lake filter requires a rarity filter");
        }
        this.id = id;
        this.feature = feature;
        this.count = count;
        this.height = height;
        this.requiredOriginFluid = requiredOriginFluid;
        this.rarityChance = rarityChance;
        this.legacyLavaLakeFilter = legacyLavaLakeFilter;
        this.biomeOnly = false;
    }

    public ResourceLocation getId() { return this.id; }
    public ResourceLocation getFeature() { return this.feature; }
    public int getCount() { return this.count; }
    public HeightProvider getHeight() { return this.height; }
    public ResourceLocation getRequiredOriginFluid() {
        return this.requiredOriginFluid;
    }
    public boolean hasBlockPredicateFilter() {
        return this.requiredOriginFluid != null;
    }
    public boolean hasRarityFilter() { return this.rarityChance > 0; }
    public int getRarityChance() { return this.rarityChance; }
    public boolean hasLegacyLavaLakeFilter() {
        return this.legacyLavaLakeFilter;
    }
    public boolean hasOnlyBiomeFilter() { return this.biomeOnly; }
}
