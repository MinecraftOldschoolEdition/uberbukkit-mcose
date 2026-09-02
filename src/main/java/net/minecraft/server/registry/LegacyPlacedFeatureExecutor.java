package net.minecraft.server.registry;

import java.util.Random;
import net.minecraft.server.Alpha.AlphaWorldGenLiquids;
import net.minecraft.server.Block;
import net.minecraft.server.Material;
import net.minecraft.server.World;
import net.minecraft.server.WorldGenClay;
import net.minecraft.server.WorldGenDungeons;
import net.minecraft.server.WorldGenLiquids;
import net.minecraft.server.WorldGenLakes;
import net.minecraft.server.WorldGenMinable;
import net.minecraft.server.WorldGenerator;
import net.minecraft.server.util.ResourceLocation;

/** Executes data-defined placements while retaining Beta 1.7.3 Random call order. */
public final class LegacyPlacedFeatureExecutor {
    interface Placement {
        void generate(Random random, int x, int y, int z);
    }

    private LegacyPlacedFeatureExecutor() {}

    public static void generate(
            World world,
            Random random,
            int chunkMinimumX,
            int chunkMinimumZ,
            ResourceLocation placedFeatureKey) {
        if (world == null || random == null || placedFeatureKey == null) {
            throw new IllegalArgumentException(
                    "World, random source, and placed-feature key are required");
        }
        PlacedFeatureDefinition placed =
                PlacedFeatureDataBootstrap.get(placedFeatureKey);
        if (placed == null) {
            throw new IllegalStateException(
                    "Unknown placed feature " + placedFeatureKey);
        }
        ConfiguredFeatureDefinition configured =
                ConfiguredFeatureDataBootstrap.get(placed.getFeature());
        if (configured == null) {
            throw new IllegalStateException(
                    "Missing configured feature " + placed.getFeature());
        }
        final World targetWorld = world;
        if (ConfiguredFeatureCodec.ORE.equals(configured.getType())) {
            final ConfiguredFeatureDefinition targetFeature = configured;
            generatePlacements(random, chunkMinimumX, chunkMinimumZ, placed,
                    new Placement() {
                        public void generate(Random source, int x, int y, int z) {
                            new WorldGenMinable(
                                    targetFeature.getState().toString(),
                                    targetFeature.getSize())
                                    .a(targetWorld, source, x, y, z);
                        }
                    });
            return;
        }
        if (ConfiguredFeatureCodec.LEGACY_CLAY.equals(configured.getType())) {
            final WorldGenerator generator = createClayGenerator(
                    configured, placed);
            generatePlacements(random, chunkMinimumX, chunkMinimumZ, placed,
                    new Placement() {
                        public void generate(Random source, int x, int y, int z) {
                            generator.a(targetWorld, source, x, y, z);
                        }
                    });
            return;
        }
        if (ConfiguredFeatureCodec.MONSTER_ROOM.equals(configured.getType())) {
            final WorldGenerator generator = new WorldGenDungeons();
            generatePlacements(random, chunkMinimumX, chunkMinimumZ, placed,
                    new Placement() {
                        public void generate(Random source, int x, int y, int z) {
                            generator.a(targetWorld, source, x, y, z);
                        }
                    });
            return;
        }
        if (ConfiguredFeatureCodec.SPRING_FEATURE.equals(configured.getType())) {
            final WorldGenerator generator = createSpringGenerator(configured, false);
            generatePlacements(random, chunkMinimumX, chunkMinimumZ, placed,
                    new Placement() {
                        public void generate(Random source, int x, int y, int z) {
                            generator.a(targetWorld, source, x, y, z);
                        }
                    });
            return;
        }
        if (ConfiguredFeatureCodec.LAKE.equals(configured.getType())) {
            final WorldGenerator generator = createLakeGenerator(configured);
            generatePlacements(random, chunkMinimumX, chunkMinimumZ, placed,
                    new Placement() {
                        public void generate(Random source, int x, int y, int z) {
                            generator.a(targetWorld, source, x, y, z);
                        }
                    });
            return;
        }
        throw new IllegalStateException(
                "Unsupported legacy configured feature type "
                        + configured.getType());
    }

    /**
     * Executes the Beta 1.7.3 top-layer snow pass represented by the modern
     * freeze_top_layer keys. Water is deliberately never frozen here.
     *
     * @return the provider-owned temperature scratch array, allocated by the
     *         world's chunk manager only when the supplied array is unusable
     */
    public static double[] generateFreezeTopLayer(
            World world,
            int chunkMinimumX,
            int chunkMinimumZ,
            double[] temperatureScratch,
            ResourceLocation placedFeatureKey) {
        if (world == null || placedFeatureKey == null) {
            throw new IllegalArgumentException(
                    "World and placed-feature key are required");
        }
        PlacedFeatureDefinition placed =
                PlacedFeatureDataBootstrap.get(placedFeatureKey);
        ConfiguredFeatureDefinition configured = placed == null
                ? null : ConfiguredFeatureDataBootstrap.get(placed.getFeature());
        if (placed == null || configured == null
                || !ConfiguredFeatureCodec.FREEZE_TOP_LAYER.equals(
                        configured.getType())
                || !placed.hasOnlyBiomeFilter()
                || configured.hasOreConfiguration()
                || configured.hasSpringConfiguration()
                || configured.hasLakeConfiguration()) {
            throw new IllegalStateException(
                    "Freeze-top-layer placement must reference an unconfigured "
                            + "freeze_top_layer feature with only a biome filter: "
                            + placedFeatureKey);
        }

        double[] temperatures = world.getWorldChunkManager().a(
                temperatureScratch, chunkMinimumX, chunkMinimumZ, 16, 16);
        if (temperatures == null || temperatures.length < 256) {
            throw new IllegalStateException(
                    "Freeze-top-layer temperature sampler returned fewer than 256 values");
        }
        for (int x = chunkMinimumX; x < chunkMinimumX + 16; ++x) {
            for (int z = chunkMinimumZ; z < chunkMinimumZ + 16; ++z) {
                int xOffset = x - chunkMinimumX;
                int zOffset = z - chunkMinimumZ;
                int topY = world.e(x, z);
                double adjustedTemperature = temperatures[xOffset * 16 + zOffset]
                        - (double)(topY - 64) / 64.0D * 0.3D;
                if (adjustedTemperature < 0.5D
                        && topY > 0 && topY < 128
                        && world.isEmpty(x, topY, z)
                        && world.getMaterial(x, topY - 1, z).isSolid()
                        && world.getMaterial(x, topY - 1, z)
                                != Material.ICE) {
                    world.setBlockStateAndData(
                            x, topY, z, "minecraft:snow");
                }
            }
        }
        return temperatures;
    }

    /** Executes an Alpha spring without the normal generator's immediate liquid tick. */
    public static void generateAlphaSpring(
            World world,
            Random random,
            int chunkMinimumX,
            int chunkMinimumZ,
            ResourceLocation placedFeatureKey) {
        if (world == null || random == null || placedFeatureKey == null) {
            throw new IllegalArgumentException(
                    "World, random source, and placed-feature key are required");
        }
        PlacedFeatureDefinition placed =
                PlacedFeatureDataBootstrap.get(placedFeatureKey);
        if (placed == null) {
            throw new IllegalStateException(
                    "Unknown placed feature " + placedFeatureKey);
        }
        ConfiguredFeatureDefinition configured =
                ConfiguredFeatureDataBootstrap.get(placed.getFeature());
        if (configured == null
                || !ConfiguredFeatureCodec.SPRING_FEATURE.equals(
                        configured.getType())) {
            throw new IllegalStateException(
                    "Alpha spring placement must reference a spring feature: "
                            + placedFeatureKey);
        }
        final World targetWorld = world;
        final WorldGenerator generator = createSpringGenerator(configured, true);
        generatePlacements(random, chunkMinimumX, chunkMinimumZ, placed,
                new Placement() {
                    public void generate(Random source, int x, int y, int z) {
                        generator.a(targetWorld, source, x, y, z);
                    }
                });
    }

    /** Uses configured spring behavior at a provider-owned custom position. */
    public static boolean generateSpringAt(
            World world,
            Random random,
            int x,
            int y,
            int z,
            ResourceLocation configuredFeatureKey) {
        if (world == null || random == null || configuredFeatureKey == null) {
            throw new IllegalArgumentException(
                    "World, random source, and configured-feature key are required");
        }
        ConfiguredFeatureDefinition configured =
                ConfiguredFeatureDataBootstrap.get(configuredFeatureKey);
        WorldGenerator generator = createSpringGenerator(configured, false);
        return generator.a(world, random, x, y, z);
    }

    /** Uses configured lake behavior at a provider-owned custom position. */
    public static boolean generateLakeAt(
            World world,
            Random random,
            int x,
            int y,
            int z,
            ResourceLocation configuredFeatureKey) {
        if (world == null || random == null || configuredFeatureKey == null) {
            throw new IllegalArgumentException(
                    "World, random source, and configured-feature key are required");
        }
        ConfiguredFeatureDefinition configured =
                ConfiguredFeatureDataBootstrap.get(configuredFeatureKey);
        WorldGenerator generator = createLakeGenerator(configured);
        return generator.a(world, random, x, y, z);
    }

    /** Uses configured ore behavior at a provider-owned custom position. */
    public static boolean generateOreAt(
            World world,
            Random random,
            int x,
            int y,
            int z,
            ResourceLocation configuredFeatureKey) {
        if (world == null || random == null || configuredFeatureKey == null) {
            throw new IllegalArgumentException(
                    "World, random source, and configured-feature key are required");
        }
        ConfiguredFeatureDefinition configured =
                ConfiguredFeatureDataBootstrap.get(configuredFeatureKey);
        if (configured == null
                || !ConfiguredFeatureCodec.ORE.equals(configured.getType())
                || !configured.hasOreConfiguration()) {
            throw new IllegalArgumentException(
                    "A configured ore feature is required");
        }
        return new WorldGenMinable(
                configured.getState().toString(), configured.getSize())
                .a(world, random, x, y, z);
    }

    static WorldGenerator createSpringGenerator(
            ConfiguredFeatureDefinition configured, boolean alpha) {
        if (configured == null
                || !ConfiguredFeatureCodec.SPRING_FEATURE.equals(
                        configured.getType())
                || !configured.hasSpringConfiguration()) {
            throw new IllegalArgumentException(
                    "A configured spring feature is required");
        }
        if (alpha) {
            return new AlphaWorldGenLiquids(
                    configured.getSpringState(),
                    configured.requiresBlockBelow(),
                    configured.getRockCount(),
                    configured.getHoleCount(),
                    configured.getValidBlocks());
        }
        return new WorldGenLiquids(
                configured.getSpringState(),
                configured.requiresBlockBelow(),
                configured.getRockCount(),
                configured.getHoleCount(),
                configured.getValidBlocks());
    }

    static WorldGenLakes createLakeGenerator(
            ConfiguredFeatureDefinition configured) {
        if (configured == null
                || !ConfiguredFeatureCodec.LAKE.equals(configured.getType())
                || !configured.hasLakeConfiguration()) {
            throw new IllegalArgumentException(
                    "A configured lake feature is required");
        }
        return new WorldGenLakes(
                configured.getLakeFluidState(),
                configured.getLakeBarrierState());
    }

    static WorldGenClay createClayGenerator(
            ConfiguredFeatureDefinition configured,
            PlacedFeatureDefinition placed) {
        if (configured == null || placed == null
                || !ConfiguredFeatureCodec.LEGACY_CLAY.equals(
                        configured.getType())
                || !configured.hasOreConfiguration()
                || !configured.getId().equals(placed.getFeature())
                || !placed.hasBlockPredicateFilter()) {
            throw new IllegalArgumentException(
                    "A matching configured and placed legacy-clay feature is required");
        }
        Block fluid = BlockRegistry.get(placed.getRequiredOriginFluid());
        Material material = fluid == null ? null : fluid.material;
        if (material != Material.WATER && material != Material.LAVA) {
            throw new IllegalArgumentException(
                    "Legacy clay requires a water or lava origin predicate");
        }
        return new WorldGenClay(
                configured.getState(),
                configured.getTargetBlock(),
                configured.getSize(),
                material);
    }

    static void generatePlacements(
            Random random,
            int chunkMinimumX,
            int chunkMinimumZ,
            PlacedFeatureDefinition placed,
            Placement placement) {
        if (random == null || placed == null || placement == null) {
            throw new IllegalArgumentException(
                    "Placement sampling arguments cannot be null");
        }
        for (int attempt = 0; attempt < placed.getCount(); attempt++) {
            // Rarity is deliberately nextInt-based here. Modern rarity uses a
            // float, but the legacy adapter must preserve existing seed output.
            if (placed.hasRarityFilter()
                    && random.nextInt(placed.getRarityChance()) != 0) {
                continue;
            }
            // Preserve the original x, then y, then z Random consumption.
            int x = chunkMinimumX + random.nextInt(16);
            int y = placed.getHeight().sampleLegacy(random);
            int z = chunkMinimumZ + random.nextInt(16);
            // Keep the original short circuit: low lakes consume no nextInt(10).
            if (placed.hasLegacyLavaLakeFilter()
                    && !(y < 64 || random.nextInt(10) == 0)) {
                continue;
            }
            placement.generate(random, x, y, z);
        }
    }
}
