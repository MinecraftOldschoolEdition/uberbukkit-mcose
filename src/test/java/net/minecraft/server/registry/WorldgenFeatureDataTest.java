package net.minecraft.server.registry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.server.Alpha.AlphaWorldGenLiquids;
import net.minecraft.server.Block;
import net.minecraft.server.BlockStateBridge;
import net.minecraft.server.BlockStateKey;
import net.minecraft.server.World;
import net.minecraft.server.WorldData;
import net.minecraft.server.WorldGenClay;
import net.minecraft.server.WorldGenLiquids;
import net.minecraft.server.WorldGenMinable;
import net.minecraft.server.WorldGenerator;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

public class WorldgenFeatureDataTest {
    private static final ResourceLocation[] KEYS = {
            ConfiguredFeatureDataBootstrap.ORE_DIRT,
            ConfiguredFeatureDataBootstrap.ORE_GRAVEL,
            ConfiguredFeatureDataBootstrap.ORE_COAL,
            ConfiguredFeatureDataBootstrap.ORE_IRON,
            ConfiguredFeatureDataBootstrap.ORE_GOLD,
            ConfiguredFeatureDataBootstrap.ORE_REDSTONE,
            ConfiguredFeatureDataBootstrap.ORE_DIAMOND,
            ConfiguredFeatureDataBootstrap.ORE_LAPIS
    };
    private static final String[] STATES = {
            "minecraft:dirt", "minecraft:gravel", "minecraft:coal_ore",
            "minecraft:iron_ore", "minecraft:gold_ore",
            "minecraft:redstone_ore", "minecraft:diamond_ore",
            "minecraft:lapis_ore"
    };
    private static final int[] SIZES = {32, 32, 16, 8, 8, 7, 7, 6};
    private static final int[] LEGACY_BLOCK_IDS = {3, 13, 16, 15, 14, 73, 56, 21};
    private static final int[] COUNTS = {20, 10, 20, 20, 2, 8, 1, 1};
    private static final int[] MAXIMUM_Y = {127, 127, 127, 63, 31, 15, 15, 30};
    private static final String[] SKY_ORE_CONSTANTS = {
            "ORE_DIRT", "ORE_GRAVEL", "ORE_COAL", "ORE_IRON",
            "ORE_GOLD", "ORE_REDSTONE", "ORE_DIAMOND", "ORE_LAPIS"
    };

    @BeforeClass
    public static void initializeFeatures() {
        PlacedFeatureDataBootstrap.initialize();
    }

    @Test
    public void allLegacyOreConstantsAreOwnedByTypedModernPathRegistries() {
        assertEquals(15, ConfiguredFeatureDataBootstrap.size());
        assertEquals(16, PlacedFeatureDataBootstrap.size());
        assertEquals(15, ConfiguredFeatureRegistryApi.size());
        assertEquals(16, PlacedFeatureRegistryApi.size());

        for (int i = 0; i < KEYS.length; i++) {
            ConfiguredFeatureDefinition configured =
                    ConfiguredFeatureDataBootstrap.get(KEYS[i]);
            PlacedFeatureDefinition placed =
                    PlacedFeatureDataBootstrap.get(KEYS[i]);
            assertNotNull(configured);
            assertNotNull(placed);
            assertSame(configured, Registries.CONFIGURED_FEATURE.get(KEYS[i]));
            assertSame(placed, Registries.PLACED_FEATURE.get(KEYS[i]));
            assertEquals(ConfiguredFeatureCodec.ORE, configured.getType());
            assertEquals(new ResourceLocation(STATES[i]), configured.getState());
            assertEquals(new ResourceLocation("minecraft", "stone"),
                    configured.getTargetBlock());
            assertEquals(SIZES[i], configured.getSize());
            assertEquals(0.0F,
                    configured.getDiscardChanceOnAirExposure(), 0.0F);
            assertEquals(KEYS[i], placed.getFeature());
            assertEquals(COUNTS[i], placed.getCount());
            assertEquals(0, placed.getHeight().getMinimumInclusive());
            assertEquals(MAXIMUM_Y[i],
                    placed.getHeight().getMaximumInclusive());
            assertEquals(i == KEYS.length - 1
                            ? PlacedFeatureDefinition.TRAPEZOID
                            : PlacedFeatureDefinition.UNIFORM,
                    placed.getHeight().getType());
            assertEquals(0, placed.getHeight().getPlateau());
        }
    }

    @Test
    public void lakesUseStrictModernDataWithExactLegacyProjection() {
        ConfiguredFeatureDefinition water = ConfiguredFeatureDataBootstrap.get(
                ConfiguredFeatureDataBootstrap.LAKE_WATER);
        ConfiguredFeatureDefinition lava = ConfiguredFeatureDataBootstrap.get(
                ConfiguredFeatureDataBootstrap.LAKE_LAVA);
        PlacedFeatureDefinition waterPlaced = PlacedFeatureDataBootstrap.get(
                PlacedFeatureDataBootstrap.LAKE_WATER);
        PlacedFeatureDefinition lavaPlaced = PlacedFeatureDataBootstrap.get(
                PlacedFeatureDataBootstrap.LAKE_LAVA);

        assertLakeConfigured(water, "minecraft:water", "minecraft:air");
        assertLakeConfigured(lava, "minecraft:lava", "minecraft:stone");

        assertEquals(ConfiguredFeatureDataBootstrap.LAKE_WATER,
                waterPlaced.getFeature());
        assertEquals(1, waterPlaced.getCount());
        assertTrue(waterPlaced.hasRarityFilter());
        assertEquals(4, waterPlaced.getRarityChance());
        assertEquals(PlacedFeatureDefinition.UNIFORM,
                waterPlaced.getHeight().getType());
        assertEquals(0, waterPlaced.getHeight().getMinimumInclusive());
        assertEquals(127, waterPlaced.getHeight().getMaximumInclusive());
        assertEquals(0, waterPlaced.getHeight().getInner());
        assertFalse(waterPlaced.hasBlockPredicateFilter());
        assertFalse(waterPlaced.hasLegacyLavaLakeFilter());

        assertEquals(ConfiguredFeatureDataBootstrap.LAKE_LAVA,
                lavaPlaced.getFeature());
        assertEquals(1, lavaPlaced.getCount());
        assertTrue(lavaPlaced.hasRarityFilter());
        assertEquals(8, lavaPlaced.getRarityChance());
        assertEquals(PlacedFeatureDefinition.BIASED_TO_BOTTOM,
                lavaPlaced.getHeight().getType());
        assertEquals(0, lavaPlaced.getHeight().getMinimumInclusive());
        assertEquals(127, lavaPlaced.getHeight().getMaximumInclusive());
        assertEquals(8, lavaPlaced.getHeight().getInner());
        assertFalse(lavaPlaced.hasBlockPredicateFilter());
        assertTrue(lavaPlaced.hasLegacyLavaLakeFilter());
    }

    @Test
    public void clayUsesModernDataWithExactLegacyPlacementAndCallbackRng() {
        ConfiguredFeatureDefinition configured =
                ConfiguredFeatureDataBootstrap.get(
                        ConfiguredFeatureDataBootstrap.CLAY);
        final PlacedFeatureDefinition placed =
                PlacedFeatureDataBootstrap.get(
                        PlacedFeatureDataBootstrap.CLAY);
        assertNotNull(configured);
        assertNotNull(placed);
        assertEquals(ConfiguredFeatureCodec.LEGACY_CLAY,
                configured.getType());
        assertEquals(new ResourceLocation("minecraft", "clay"),
                configured.getState());
        assertEquals(new ResourceLocation("minecraft", "sand"),
                configured.getTargetBlock());
        assertEquals(32, configured.getSize());
        assertEquals(0.0F,
                configured.getDiscardChanceOnAirExposure(), 0.0F);
        assertEquals(ConfiguredFeatureDataBootstrap.CLAY,
                placed.getFeature());
        assertEquals(10, placed.getCount());
        assertEquals(PlacedFeatureDefinition.UNIFORM,
                placed.getHeight().getType());
        assertEquals(0, placed.getHeight().getMinimumInclusive());
        assertEquals(127, placed.getHeight().getMaximumInclusive());
        assertEquals(0, placed.getHeight().getPlateau());
        assertEquals(0, placed.getHeight().getInner());
        assertTrue(placed.hasBlockPredicateFilter());
        assertEquals(new ResourceLocation("minecraft", "water"),
                placed.getRequiredOriginFluid());
        assertTrue(LegacyPlacedFeatureExecutor.createClayGenerator(
                configured, placed) instanceof WorldGenClay);

        long[] seeds = {0L, 1L, -1L, 123456789L, Long.MAX_VALUE};
        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
            Random actualRandom = new Random(seeds[seedIndex]);
            final List<int[]> actual = new ArrayList<int[]>();
            LegacyPlacedFeatureExecutor.generatePlacements(
                    actualRandom, 160, -64, placed,
                    new LegacyPlacedFeatureExecutor.Placement() {
                        public void generate(
                                Random source, int x, int y, int z) {
                            actual.add(new int[] {x, y, z});
                            consumeAcceptedClayCallback(source, 32);
                        }
                    });

            Random expectedRandom = new Random(seeds[seedIndex]);
            assertEquals(10, actual.size());
            for (int attempt = 0; attempt < 10; attempt++) {
                assertArrayEquals(new int[] {
                        160 + expectedRandom.nextInt(16),
                        expectedRandom.nextInt(128),
                        -64 + expectedRandom.nextInt(16)
                }, actual.get(attempt));
                consumeAcceptedClayCallback(expectedRandom, 32);
            }
            assertEquals(expectedRandom.nextLong(), actualRandom.nextLong());
        }
    }

    @Test
    public void springsUseModernDataWithExactLegacyPlacementSequences() {
        ResourceLocation[] configuredKeys = {
                ConfiguredFeatureDataBootstrap.SPRING_WATER,
                ConfiguredFeatureDataBootstrap.SPRING_LAVA_OVERWORLD
        };
        ResourceLocation[] placedKeys = {
                PlacedFeatureDataBootstrap.SPRING_WATER,
                PlacedFeatureDataBootstrap.SPRING_LAVA
        };
        String[] states = {"minecraft:water", "minecraft:lava"};
        int[] counts = {50, 20};
        int[] maximums = {127, 119};
        ResourceLocation[] heightTypes = {
                PlacedFeatureDefinition.BIASED_TO_BOTTOM,
                PlacedFeatureDefinition.VERY_BIASED_TO_BOTTOM
        };

        for (int featureIndex = 0; featureIndex < configuredKeys.length;
                featureIndex++) {
            ConfiguredFeatureDefinition configured =
                    ConfiguredFeatureDataBootstrap.get(
                            configuredKeys[featureIndex]);
            final PlacedFeatureDefinition placed =
                    PlacedFeatureDataBootstrap.get(placedKeys[featureIndex]);
            assertNotNull(configured);
            assertNotNull(placed);
            assertEquals(ConfiguredFeatureCodec.SPRING_FEATURE,
                    configured.getType());
            assertTrue(configured.hasSpringConfiguration());
            assertEquals(new ResourceLocation(states[featureIndex]),
                    configured.getSpringState().getBlockKey());
            assertEquals("false",
                    configured.getSpringState().getProperty("falling"));
            assertTrue(configured.requiresBlockBelow());
            assertEquals(4, configured.getRockCount());
            assertEquals(1, configured.getHoleCount());
            assertEquals(1, configured.getValidBlocks().size());
            assertEquals(new ResourceLocation("minecraft", "stone"),
                    configured.getValidBlocks().get(0));
            assertEquals(configuredKeys[featureIndex], placed.getFeature());
            assertEquals(counts[featureIndex], placed.getCount());
            assertEquals(heightTypes[featureIndex],
                    placed.getHeight().getType());
            assertEquals(0, placed.getHeight().getMinimumInclusive());
            assertEquals(maximums[featureIndex],
                    placed.getHeight().getMaximumInclusive());
            assertEquals(8, placed.getHeight().getInner());

            WorldGenerator normal =
                    LegacyPlacedFeatureExecutor.createSpringGenerator(
                            configured, false);
            WorldGenerator alpha =
                    LegacyPlacedFeatureExecutor.createSpringGenerator(
                            configured, true);
            assertTrue(normal instanceof WorldGenLiquids);
            assertTrue(alpha instanceof AlphaWorldGenLiquids);

            long[] seeds = {0L, 1L, -1L, 123456789L, Long.MAX_VALUE};
            for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
                Random actualRandom = new Random(seeds[seedIndex]);
                final List<int[]> actual = new ArrayList<int[]>();
                LegacyPlacedFeatureExecutor.generatePlacements(
                        actualRandom, 168, -56, placed,
                        new LegacyPlacedFeatureExecutor.Placement() {
                            public void generate(
                                    Random source, int x, int y, int z) {
                                actual.add(new int[] {x, y, z});
                            }
                        });

                Random expectedRandom = new Random(seeds[seedIndex]);
                assertEquals(counts[featureIndex], actual.size());
                for (int attempt = 0; attempt < counts[featureIndex]; attempt++) {
                    int x = 168 + expectedRandom.nextInt(16);
                    int y;
                    if (featureIndex == 0) {
                        y = expectedRandom.nextInt(
                                expectedRandom.nextInt(120) + 8);
                    } else {
                        y = expectedRandom.nextInt(expectedRandom.nextInt(
                                expectedRandom.nextInt(112) + 8) + 8);
                    }
                    int z = -56 + expectedRandom.nextInt(16);
                    assertArrayEquals(new int[] {x, y, z}, actual.get(attempt));
                }
                assertEquals(expectedRandom.nextLong(), actualRandom.nextLong());
            }
        }
    }

    @Test
    public void netherOpenSpringUsesExactLegacyDataAndPlacementSequence() {
        ConfiguredFeatureDefinition configured =
                ConfiguredFeatureDataBootstrap.get(
                        ConfiguredFeatureDataBootstrap.SPRING_NETHER_OPEN);
        final PlacedFeatureDefinition placed =
                PlacedFeatureDataBootstrap.get(
                        PlacedFeatureDataBootstrap.SPRING_OPEN);
        assertNotNull(configured);
        assertNotNull(placed);
        assertEquals(ConfiguredFeatureCodec.SPRING_FEATURE,
                configured.getType());
        assertEquals(new ResourceLocation("minecraft", "lava"),
                configured.getSpringState().getBlockKey());
        assertEquals("false",
                configured.getSpringState().getProperty("falling"));
        assertFalse(configured.requiresBlockBelow());
        assertEquals(4, configured.getRockCount());
        assertEquals(1, configured.getHoleCount());
        assertEquals(1, configured.getValidBlocks().size());
        assertEquals(new ResourceLocation("minecraft", "netherrack"),
                configured.getValidBlocks().get(0));
        assertEquals(ConfiguredFeatureDataBootstrap.SPRING_NETHER_OPEN,
                placed.getFeature());
        assertEquals(8, placed.getCount());
        assertEquals(PlacedFeatureDefinition.UNIFORM,
                placed.getHeight().getType());
        assertEquals(4, placed.getHeight().getMinimumInclusive());
        assertEquals(123, placed.getHeight().getMaximumInclusive());

        long[] seeds = {0L, 1L, -1L, 123456789L, Long.MAX_VALUE};
        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
            Random actualRandom = new Random(seeds[seedIndex]);
            final List<int[]> actual = new ArrayList<int[]>();
            LegacyPlacedFeatureExecutor.generatePlacements(
                    actualRandom, 168, -56, placed,
                    new LegacyPlacedFeatureExecutor.Placement() {
                        public void generate(
                                Random source, int x, int y, int z) {
                            actual.add(new int[] {x, y, z});
                        }
                    });

            Random expectedRandom = new Random(seeds[seedIndex]);
            assertEquals(8, actual.size());
            for (int attempt = 0; attempt < 8; attempt++) {
                assertArrayEquals(new int[] {
                        168 + expectedRandom.nextInt(16),
                        expectedRandom.nextInt(120) + 4,
                        -56 + expectedRandom.nextInt(16)
                }, actual.get(attempt));
            }
            assertEquals(expectedRandom.nextLong(), actualRandom.nextLong());
        }
    }

    @Test
    public void classicHellLavaSpringUsesExactLegacyPlacementSequence() {
        final PlacedFeatureDefinition placed = PlacedFeatureDataBootstrap.get(
                PlacedFeatureDataBootstrap.SPRING_LAVA_CLASSIC_HELL);
        assertNotNull(placed);
        assertEquals(ConfiguredFeatureDataBootstrap.SPRING_LAVA_OVERWORLD,
                placed.getFeature());
        assertEquals(5, placed.getCount());
        assertEquals(PlacedFeatureDefinition.BIASED_TO_BOTTOM,
                placed.getHeight().getType());
        assertEquals(0, placed.getHeight().getMinimumInclusive());
        assertEquals(55, placed.getHeight().getMaximumInclusive());
        assertEquals(8, placed.getHeight().getInner());

        long[] seeds = {0L, 1L, -1L, 123456789L, Long.MAX_VALUE};
        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
            Random actualRandom = new Random(seeds[seedIndex]);
            final List<int[]> actual = new ArrayList<int[]>();
            LegacyPlacedFeatureExecutor.generatePlacements(
                    actualRandom, 168, -56, placed,
                    new LegacyPlacedFeatureExecutor.Placement() {
                        public void generate(
                                Random source, int x, int y, int z) {
                            actual.add(new int[] {x, y, z});
                        }
                    });

            Random expectedRandom = new Random(seeds[seedIndex]);
            assertEquals(5, actual.size());
            for (int attempt = 0; attempt < 5; attempt++) {
                int x = 168 + expectedRandom.nextInt(16);
                int y = expectedRandom.nextInt(expectedRandom.nextInt(48) + 8);
                int z = -56 + expectedRandom.nextInt(16);
                assertArrayEquals(new int[] {x, y, z}, actual.get(attempt));
            }
            assertEquals(expectedRandom.nextLong(), actualRandom.nextLong());
        }
    }

    @Test
    public void monsterRoomUsesTheModernTypeAndExactLegacyPlacementSequence() {
        ConfiguredFeatureDefinition configured = ConfiguredFeatureDataBootstrap.get(
                ConfiguredFeatureDataBootstrap.MONSTER_ROOM);
        PlacedFeatureDefinition placed = PlacedFeatureDataBootstrap.get(
                ConfiguredFeatureDataBootstrap.MONSTER_ROOM);
        assertNotNull(configured);
        assertNotNull(placed);
        assertEquals(ConfiguredFeatureCodec.MONSTER_ROOM, configured.getType());
        assertFalse(configured.hasOreConfiguration());
        assertEquals(8, placed.getCount());
        assertEquals(PlacedFeatureDefinition.UNIFORM,
                placed.getHeight().getType());
        assertEquals(0, placed.getHeight().getMinimumInclusive());
        assertEquals(127, placed.getHeight().getMaximumInclusive());

        long[] seeds = {0L, 1L, -1L, 123456789L, Long.MAX_VALUE};
        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
            Random actualRandom = new Random(seeds[seedIndex]);
            final List<int[]> actual = new ArrayList<int[]>();
            LegacyPlacedFeatureExecutor.generatePlacements(
                    actualRandom, 168, -56, placed,
                    new LegacyPlacedFeatureExecutor.Placement() {
                        public void generate(Random source, int x, int y, int z) {
                            actual.add(new int[] {x, y, z});
                        }
                    });

            Random expectedRandom = new Random(seeds[seedIndex]);
            for (int attempt = 0; attempt < 8; attempt++) {
                assertArrayEquals(new int[] {
                        168 + expectedRandom.nextInt(16),
                        expectedRandom.nextInt(128),
                        -56 + expectedRandom.nextInt(16)
                }, actual.get(attempt));
            }
            assertEquals(expectedRandom.nextLong(), actualRandom.nextLong());
        }
    }

    @Test
    public void placementAdapterConsumesRandomExactlyLikeTheRemovedLoops() {
        long[] seeds = {0L, 1L, -1L, 123456789L, Long.MAX_VALUE};
        for (int featureIndex = 0; featureIndex < KEYS.length; featureIndex++) {
            final PlacedFeatureDefinition placed =
                    PlacedFeatureDataBootstrap.get(KEYS[featureIndex]);
            for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
                Random actualRandom = new Random(seeds[seedIndex]);
                final List<int[]> actual = new ArrayList<int[]>();
                LegacyPlacedFeatureExecutor.generatePlacements(
                        actualRandom, 160, -64, placed,
                        new LegacyPlacedFeatureExecutor.Placement() {
                            public void generate(
                                    Random source, int x, int y, int z) {
                                actual.add(new int[] {x, y, z});
                            }
                        });

                Random expectedRandom = new Random(seeds[seedIndex]);
                assertEquals(COUNTS[featureIndex], actual.size());
                for (int attempt = 0; attempt < COUNTS[featureIndex]; attempt++) {
                    int x = 160 + expectedRandom.nextInt(16);
                    int y = featureIndex == KEYS.length - 1
                            ? expectedRandom.nextInt(16)
                                    + expectedRandom.nextInt(16)
                            : expectedRandom.nextInt(MAXIMUM_Y[featureIndex] + 1);
                    int z = -64 + expectedRandom.nextInt(16);
                    assertArrayEquals(new int[] {x, y, z}, actual.get(attempt));
                }
                assertEquals(expectedRandom.nextLong(), actualRandom.nextLong());
            }
        }
    }

    @Test
    public void customOreCallbacksMatchLegacyBlocksSizesOutputAndRandom()
            throws Exception {
        long[] seeds = {0L, 1L, -1L, 246813579L};
        for (int feature = 0; feature < KEYS.length; feature++) {
            ConfiguredFeatureDefinition configured =
                    ConfiguredFeatureDataBootstrap.get(KEYS[feature]);
            assertEquals(SIZES[feature], configured.getSize());
            assertEquals(LEGACY_BLOCK_IDS[feature], BlockStateBridge.toLegacy(
                    new BlockStateKey(configured.getState())).blockId);

            for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
                RecordingOreWorld directWorld = RecordingOreWorld.create();
                RecordingOreWorld dataWorld = RecordingOreWorld.create();
                Random directRandom = new Random(seeds[seedIndex]);
                Random dataRandom = new Random(seeds[seedIndex]);

                boolean direct = new WorldGenMinable(
                        STATES[feature], SIZES[feature]).a(
                        directWorld, directRandom, -24, 48, 40);
                boolean data = LegacyPlacedFeatureExecutor.generateOreAt(
                        dataWorld, dataRandom, -24, 48, 40, KEYS[feature]);

                assertEquals(direct, data);
                assertFalse(dataWorld.states.isEmpty());
                assertTrue(dataWorld.onlyContains(LEGACY_BLOCK_IDS[feature]));
                assertEquals(directWorld.states, dataWorld.states);
                assertEquals(directRandom.nextLong(), dataRandom.nextLong());
            }
        }
    }

    @Test
    public void skyProviderHasExactlyEightConfiguredOreCallbacks()
            throws Exception {
        Path provider = findCheckoutRoot().resolve(
                "src/main/java/net/minecraft/server/ChunkProviderSky.java");
        String source = readSource(provider);

        assertEquals(8, occurrences(
                source, "LegacyPlacedFeatureExecutor.generateOreAt("));
        assertFalse(source.contains("new WorldGenMinable"));

        int cursor = 0;
        for (int i = 0; i < SKY_ORE_CONSTANTS.length; i++) {
            String key = "ConfiguredFeatureDataBootstrap."
                    + SKY_ORE_CONSTANTS[i];
            assertEquals(1, occurrences(source, key));
            int next = source.indexOf(key, cursor);
            assertTrue("Missing or reordered Sky ore key " + key,
                    next >= cursor);
            cursor = next + key.length();
        }
    }

    @Test
    public void strictCodecsRejectBehaviorChangingShapes() {
        JsonObject configured = parse(validConfigured("minecraft:coal_ore", 16));
        configured.addProperty("discard_chance_on_air_exposure", 0.5F);
        assertConfiguredFails(configured);

        configured = parse(validConfigured("minecraft:coal_ore", 16));
        configured.addProperty("legacy_attempts", 20);
        assertConfiguredFails(configured);

        configured = parse(validConfigured("minecraft:missing_ore", 16));
        assertConfiguredFails(configured);

        configured = parse("{\"type\":\"minecraft:monster_room\","
                + "\"size\":1}");
        assertConfiguredFails(configured);

        configured = parse(validSpring("minecraft:water", "false"));
        configured.getAsJsonObject("state").getAsJsonObject("properties")
                .addProperty("falling", "sideways");
        assertConfiguredFails(configured);

        configured = parse(validLake("minecraft:water", "minecraft:air"));
        configured.getAsJsonObject("fluid").addProperty(
                "type", "minecraft:weighted_state_provider");
        assertConfiguredFails(configured);

        configured = parse(validLake("minecraft:water", "minecraft:stone"));
        assertConfiguredFails(configured);

        configured = parse(validLake("minecraft:lava", "minecraft:stone"));
        configured.getAsJsonObject("can_place_feature").addProperty(
                "type", "minecraft:not");
        assertConfiguredFails(configured);

        JsonObject placed = parse(validPlaced("minecraft:ore_coal", 20, 127, false));
        com.google.gson.JsonArray modifiers = placed.getAsJsonArray("placement");
        JsonObject first = modifiers.get(0).getAsJsonObject();
        modifiers.set(0, modifiers.get(1));
        modifiers.set(1, first);
        assertPlacedFails(placed);

        placed = parse(validPlaced("minecraft:ore_lapis", 1, 30, true));
        placed.getAsJsonArray("placement").get(2).getAsJsonObject()
                .getAsJsonObject("height").addProperty("plateau", 31);
        assertPlacedFails(placed);

        placed = parse(validSpringPlaced(
                "minecraft:spring_water", "biased_to_bottom", 127));
        placed.getAsJsonArray("placement").get(2).getAsJsonObject()
                .getAsJsonObject("height").addProperty("inner", 0);
        assertPlacedFails(placed);

        placed = parse(validClayPlaced("minecraft:water"));
        placed.getAsJsonArray("placement").get(3).getAsJsonObject()
                .getAsJsonObject("predicate")
                .addProperty("fluids", "minecraft:stone");
        assertPlacedFails(placed);

        placed = parse(validClayPlaced("minecraft:water"));
        placed.getAsJsonArray("placement").remove(3);
        assertPlacedFails(placed);

        placed = parse(validLakePlaced("minecraft:lake_water", 4, false));
        placed.getAsJsonArray("placement").get(0).getAsJsonObject()
                .addProperty("chance", 0);
        assertPlacedFails(placed);

        placed = parse(validLakePlaced("minecraft:lake_lava", 8, true));
        placed.getAsJsonArray("placement").remove(3);
        assertPlacedFails(placed);

        placed = parse(validLakePlaced("minecraft:lake_water", 4, true));
        assertPlacedFails(placed);

        placed = parse(validLakePlaced("minecraft:lake_lava", 8, true));
        placed.getAsJsonArray("placement").get(3).getAsJsonObject()
                .addProperty("chance", 10);
        assertPlacedFails(placed);
    }

    @Test
    public void layeredLoadersRetainCustomNamespacesWithoutPartialPublication()
            throws Exception {
        Map<ResourceLocation, ConfiguredFeatureDefinition> liveConfigured =
                ConfiguredFeatureDataBootstrap.rawFeatures();
        Map<ResourceLocation, PlacedFeatureDefinition> livePlaced =
                PlacedFeatureDataBootstrap.rawFeatures();
        Path root = Files.createTempDirectory("mcose-worldgen-feature-");
        try {
            Path customConfigured = root.resolve(
                    "data/example/worldgen/feature/rich_coal.json");
            Files.createDirectories(customConfigured.getParent());
            Files.write(customConfigured,
                    validConfigured("minecraft:coal_ore", 17)
                            .getBytes(StandardCharsets.UTF_8));
            Map<ResourceLocation, ConfiguredFeatureDefinition> decodedConfigured =
                    ConfiguredFeatureDataBootstrap.loadForTests(
                            RegistryDataLoader.createLayeredProvider(root.toFile()));
            assertEquals(17, decodedConfigured.get(
                    new ResourceLocation("example", "rich_coal")).getSize());

            Path customPlaced = root.resolve(
                    "data/example/worldgen/placed_feature/rich_coal.json");
            Files.createDirectories(customPlaced.getParent());
            Files.write(customPlaced,
                    validPlaced("minecraft:ore_coal", 3, 63, false)
                            .getBytes(StandardCharsets.UTF_8));
            Map<ResourceLocation, PlacedFeatureDefinition> decodedPlaced =
                    PlacedFeatureDataBootstrap.loadForTests(
                            RegistryDataLoader.createLayeredProvider(root.toFile()));
            assertEquals(3, decodedPlaced.get(
                    new ResourceLocation("example", "rich_coal")).getCount());

            Files.write(customConfigured,
                    validConfigured("minecraft:missing_ore", 17)
                            .getBytes(StandardCharsets.UTF_8));
            try {
                ConfiguredFeatureDataBootstrap.loadForTests(
                        RegistryDataLoader.createLayeredProvider(root.toFile()));
                fail("Unknown configured block was accepted");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("worldgen/feature"));
            }
            assertSame(liveConfigured,
                    ConfiguredFeatureDataBootstrap.rawFeatures());
            assertSame(livePlaced, PlacedFeatureDataBootstrap.rawFeatures());
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void clientServerAndStandaloneJsonAreByteIdentical() throws Exception {
        Path server = findCheckoutRoot();
        Path developer = server.getParent();
        assertNotNull(developer);
        Path client = developer.resolve("client-source/minecraft/resources");
        Path standalone = developer.resolve("resourcepack");
        for (int i = 0; i < KEYS.length; i++) {
            assertCopies(client, server.resolve("src/main/resources"), standalone,
                    "worldgen/feature", KEYS[i]);
            assertCopies(client, server.resolve("src/main/resources"), standalone,
                    "worldgen/placed_feature", KEYS[i]);
        }
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/feature", ConfiguredFeatureDataBootstrap.MONSTER_ROOM);
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/placed_feature", ConfiguredFeatureDataBootstrap.MONSTER_ROOM);
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/feature", ConfiguredFeatureDataBootstrap.LAKE_WATER);
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/feature", ConfiguredFeatureDataBootstrap.LAKE_LAVA);
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/placed_feature", PlacedFeatureDataBootstrap.LAKE_WATER);
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/placed_feature", PlacedFeatureDataBootstrap.LAKE_LAVA);
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/feature", ConfiguredFeatureDataBootstrap.SPRING_WATER);
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/feature",
                ConfiguredFeatureDataBootstrap.SPRING_LAVA_OVERWORLD);
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/placed_feature", PlacedFeatureDataBootstrap.SPRING_WATER);
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/placed_feature", PlacedFeatureDataBootstrap.SPRING_LAVA);
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/placed_feature",
                PlacedFeatureDataBootstrap.SPRING_LAVA_CLASSIC_HELL);
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/feature", ConfiguredFeatureDataBootstrap.CLAY);
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/placed_feature", PlacedFeatureDataBootstrap.CLAY);
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/feature",
                ConfiguredFeatureDataBootstrap.SPRING_NETHER_OPEN);
        assertCopies(client, server.resolve("src/main/resources"), standalone,
                "worldgen/placed_feature", PlacedFeatureDataBootstrap.SPRING_OPEN);
    }

    @Test
    public void standardAndForestySkyLakesUseTheIntendedDataBoundaries()
            throws Exception {
        Path sourceRoot = findCheckoutRoot().resolve("src/main/java");
        String normal = readSource(sourceRoot.resolve(
                "net/minecraft/server/ChunkProviderGenerate.java"));
        String sky = readSource(sourceRoot.resolve(
                "net/minecraft/server/ChunkProviderSky.java"));
        String infdev = readSource(sourceRoot.resolve(
                "net/minecraft/server/Infdev/InfdevChunkProvider.java"));
        String alpha = readSource(sourceRoot.resolve(
                "net/minecraft/server/Alpha/AlphaChunkProvider.java"));

        assertTrue(normal.contains("PlacedFeatureDataBootstrap.LAKE_WATER"));
        assertTrue(normal.contains("PlacedFeatureDataBootstrap.LAKE_LAVA"));
        assertFalse(normal.contains("new WorldGenLakes"));
        assertTrue(sky.contains("PlacedFeatureDataBootstrap.LAKE_WATER"));
        assertTrue(sky.contains("PlacedFeatureDataBootstrap.LAKE_LAVA"));
        assertEquals(3, occurrences(sky,
                "LegacyPlacedFeatureExecutor.generateLakeAt"));
        assertEquals(3, occurrences(sky,
                "ConfiguredFeatureDataBootstrap.LAKE_WATER"));
        assertTrue(sky.contains(
                "setBlockStateAndData(j3, l3, k3, \"minecraft:water\")"));
        assertFalse(sky.contains("new WorldGenLakes"));
        assertFalse(infdev.contains("LAKE_WATER"));
        assertFalse(infdev.contains("LAKE_LAVA"));
        assertFalse(alpha.contains("LAKE_WATER"));
        assertFalse(alpha.contains("LAKE_LAVA"));
    }

    @Test
    public void normalProvidersUseClayDataWhileAlphaRetainsItsGenerator()
            throws Exception {
        Path sourceRoot = findCheckoutRoot().resolve("src/main/java");
        String normal = readSource(sourceRoot.resolve(
                "net/minecraft/server/ChunkProviderGenerate.java"));
        String sky = readSource(sourceRoot.resolve(
                "net/minecraft/server/ChunkProviderSky.java"));
        String infdev = readSource(sourceRoot.resolve(
                "net/minecraft/server/Infdev/InfdevChunkProvider.java"));
        String alpha = readSource(sourceRoot.resolve(
                "net/minecraft/server/Alpha/AlphaChunkProvider.java"));

        assertTrue(normal.contains("PlacedFeatureDataBootstrap.CLAY"));
        assertTrue(sky.contains("PlacedFeatureDataBootstrap.CLAY"));
        assertTrue(infdev.contains("PlacedFeatureDataBootstrap.CLAY"));
        assertFalse(normal.contains("new WorldGenClay(32)"));
        assertFalse(sky.contains("new WorldGenClay(32)"));
        assertFalse(infdev.contains("clayGen"));
        assertTrue(alpha.contains("new AlphaWorldGenClay(32)"));
        assertFalse(alpha.contains("PlacedFeatureDataBootstrap.CLAY"));
    }

    @Test
    public void netherProvidersKeepTheirDistinctPlacementContracts()
            throws Exception {
        Path sourceRoot = findCheckoutRoot().resolve("src/main/java");
        String normal = readSource(sourceRoot.resolve(
                "net/minecraft/server/ChunkProviderHell.java"));
        String sky = readSource(sourceRoot.resolve(
                "net/minecraft/server/ChunkProviderNetherSky.java"));

        assertTrue(normal.contains("PlacedFeatureDataBootstrap.SPRING_OPEN"));
        assertFalse(normal.contains("new WorldGenHellLava"));
        assertTrue(sky.contains("LegacyPlacedFeatureExecutor.generateSpringAt"));
        assertTrue(sky.contains(
                "ConfiguredFeatureDataBootstrap.SPRING_NETHER_OPEN"));
        assertFalse(sky.contains("PlacedFeatureDataBootstrap.SPRING_OPEN"));
        assertFalse(sky.contains("new WorldGenHellLava"));

        int loop = sky.indexOf(
                "for (int i = 0; i < LAVA_SPRING_ATTEMPTS; ++i)");
        int x = sky.indexOf("int x = baseX + this.random.nextInt(16) + 8", loop);
        int z = sky.indexOf("int z = baseZ + this.random.nextInt(16) + 8", x);
        int y = sky.indexOf("int y = this.findInteriorFeatureY(x, z)", z);
        int condition = sky.indexOf("if (y > 0)", y);
        int callback = sky.indexOf(
                "LegacyPlacedFeatureExecutor.generateSpringAt", condition);
        assertTrue(loop >= 0 && loop < x && x < z && z < y
                && y < condition && condition < callback);

        int searchStart = sky.indexOf(
                "private int findInteriorFeatureY(int x, int z)");
        int searchEnd = sky.indexOf(
                "private int findUndersideFeatureY", searchStart);
        assertTrue(searchStart >= 0 && searchEnd > searchStart);
        String search = sky.substring(searchStart, searchEnd);
        assertTrue(search.contains("attempt < 16"));
        assertTrue(search.contains("this.random.nextInt(96) + 16"));
        assertTrue(search.contains("return y;"));
        assertTrue(search.contains("return this.findUndersideFeatureY(x, z);"));
    }

    @Test
    public void typedRegistriesAppearInTheWorldRuntimeView() {
        RegistryBootstrap.initialize();
        RegistryAccess world = RegistryRuntime.current().worldAccess();
        RegistryView<ConfiguredFeatureDefinition> configured = world.lookup(
                new ResourceLocation("minecraft", "worldgen/feature"));
        RegistryView<PlacedFeatureDefinition> placed = world.lookup(
                new ResourceLocation("minecraft", "worldgen/placed_feature"));
        assertNotNull(configured);
        assertNotNull(placed);
        assertSame(ConfiguredFeatureDataBootstrap.get(
                        ConfiguredFeatureDataBootstrap.ORE_COAL),
                configured.get(ConfiguredFeatureDataBootstrap.ORE_COAL));
        assertSame(PlacedFeatureDataBootstrap.get(
                        ConfiguredFeatureDataBootstrap.ORE_COAL),
                placed.get(ConfiguredFeatureDataBootstrap.ORE_COAL));
    }

    private static void assertConfiguredFails(JsonObject root) {
        try {
            ConfiguredFeatureCodec.decode(
                    new ResourceLocation("example", "invalid"), root);
            fail("Invalid configured feature was accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    private static void assertPlacedFails(JsonObject root) {
        try {
            PlacedFeatureCodec.decode(
                    new ResourceLocation("example", "invalid"), root);
            fail("Invalid placed feature was accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    private static JsonObject parse(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }

    private static void consumeAcceptedClayCallback(Random random, int size) {
        random.nextFloat();
        random.nextInt(3);
        random.nextInt(3);
        for (int step = 0; step <= size; step++) {
            random.nextDouble();
        }
    }

    private static String validConfigured(String state, int size) {
        return "{\"type\":\"minecraft:ore\","
                + "\"discard_chance_on_air_exposure\":0.0,"
                + "\"size\":" + size + ",\"targets\":[{"
                + "\"state\":\"" + state + "\",\"target\":{"
                + "\"predicate_type\":\"minecraft:block_match\","
                + "\"block\":\"minecraft:stone\"}}]}";
    }

    private static String validPlaced(
            String feature, int count, int maximum, boolean trapezoid) {
        return "{\"feature\":\"" + feature + "\",\"placement\":["
                + "{\"type\":\"minecraft:count\",\"count\":" + count + "},"
                + "{\"type\":\"minecraft:in_square\"},"
                + "{\"type\":\"minecraft:height_range\",\"height\":{"
                + "\"type\":\"minecraft:"
                + (trapezoid ? "trapezoid" : "uniform") + "\","
                + "\"min_inclusive\":{\"absolute\":0},"
                + "\"max_inclusive\":{\"absolute\":" + maximum + "}}},"
                + "{\"type\":\"minecraft:biome\"}]}";
    }

    private static String validSpring(String state, String falling) {
        return "{\"type\":\"minecraft:spring_feature\","
                + "\"hole_count\":1,\"requires_block_below\":true,"
                + "\"rock_count\":4,\"state\":{\"id\":\"" + state
                + "\",\"properties\":{\"falling\":\"" + falling
                + "\"}},\"valid_blocks\":[\"minecraft:stone\"]}";
    }

    private static String validSpringPlaced(
            String feature, String heightType, int maximum) {
        return "{\"feature\":\"" + feature + "\",\"placement\":["
                + "{\"type\":\"minecraft:count\",\"count\":20},"
                + "{\"type\":\"minecraft:in_square\"},"
                + "{\"type\":\"minecraft:height_range\",\"height\":{"
                + "\"type\":\"minecraft:" + heightType + "\","
                + "\"inner\":8,\"min_inclusive\":{\"absolute\":0},"
                + "\"max_inclusive\":{\"absolute\":" + maximum + "}}},"
                + "{\"type\":\"minecraft:biome\"}]}";
    }

    private static String validClayPlaced(String fluid) {
        return "{\"feature\":\"minecraft:clay\",\"placement\":["
                + "{\"type\":\"minecraft:count\",\"count\":10},"
                + "{\"type\":\"minecraft:in_square\"},"
                + "{\"type\":\"minecraft:height_range\",\"height\":{"
                + "\"type\":\"minecraft:uniform\","
                + "\"min_inclusive\":{\"absolute\":0},"
                + "\"max_inclusive\":{\"absolute\":127}}},"
                + "{\"type\":\"minecraft:block_predicate_filter\","
                + "\"predicate\":{\"type\":\"minecraft:matching_fluids\","
                + "\"fluids\":\"" + fluid + "\"}},"
                + "{\"type\":\"minecraft:biome\"}]}";
    }

    private static String validLake(String fluid, String barrier) {
        return "{\"type\":\"minecraft:lake\","
                + "\"fluid\":{\"type\":\"minecraft:simple_state_provider\","
                + "\"state\":\"" + fluid + "\"},"
                + "\"barrier\":{\"type\":\"minecraft:simple_state_provider\","
                + "\"state\":\"" + barrier + "\"},"
                + "\"can_place_feature\":{\"type\":\"minecraft:true\"},"
                + "\"can_replace_with_air_or_fluid\":{"
                + "\"type\":\"minecraft:true\"},"
                + "\"can_replace_with_barrier\":{"
                + "\"type\":\"minecraft:true\"}}";
    }

    private static String validLakePlaced(
            String feature, int chance, boolean lavaFilter) {
        return "{\"feature\":\"" + feature + "\",\"placement\":["
                + "{\"type\":\"minecraft:rarity_filter\",\"chance\":"
                + chance + "},"
                + "{\"type\":\"minecraft:in_square\"},"
                + "{\"type\":\"minecraft:height_range\",\"height\":{"
                + "\"type\":\"minecraft:"
                + (lavaFilter ? "biased_to_bottom" : "uniform") + "\","
                + (lavaFilter ? "\"inner\":8," : "")
                + "\"min_inclusive\":{\"absolute\":0},"
                + "\"max_inclusive\":{\"absolute\":127}}},"
                + (lavaFilter
                        ? "{\"type\":\"minecraft:legacy_lava_lake_filter\"},"
                        : "")
                + "{\"type\":\"minecraft:biome\"}]}";
    }

    private static void assertLakeConfigured(
            ConfiguredFeatureDefinition configured,
            String fluid,
            String barrier) {
        assertNotNull(configured);
        assertEquals(ConfiguredFeatureCodec.LAKE, configured.getType());
        assertTrue(configured.hasLakeConfiguration());
        assertEquals(ConfiguredFeatureCodec.SIMPLE_STATE_PROVIDER,
                configured.getLakeFluidProviderType());
        assertEquals(new ResourceLocation(fluid),
                configured.getLakeFluidState().getBlockKey());
        assertEquals("still",
                configured.getLakeFluidState().getProperty("variant"));
        assertEquals(ConfiguredFeatureCodec.SIMPLE_STATE_PROVIDER,
                configured.getLakeBarrierProviderType());
        assertEquals(new ResourceLocation(barrier),
                configured.getLakeBarrierState().getBlockKey());
        assertTrue(configured.getLakeBarrierState().getProperties().isEmpty());
        assertEquals(ConfiguredFeatureCodec.TRUE_BLOCK_PREDICATE,
                configured.getCanPlaceFeaturePredicate());
        assertEquals(ConfiguredFeatureCodec.TRUE_BLOCK_PREDICATE,
                configured.getCanReplaceWithAirOrFluidPredicate());
        assertEquals(ConfiguredFeatureCodec.TRUE_BLOCK_PREDICATE,
                configured.getCanReplaceWithBarrierPredicate());
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String readSource(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void assertCopies(
            Path client,
            Path server,
            Path standalone,
            String registry,
            ResourceLocation key) throws Exception {
        String relative = "data/" + key.getNamespace() + "/" + registry
                + "/" + key.getPath() + ".json";
        byte[] expected = Files.readAllBytes(client.resolve(relative));
        assertArrayEquals(expected, Files.readAllBytes(server.resolve(relative)));
        assertArrayEquals(expected, Files.readAllBytes(standalone.resolve(relative)));
    }

    private static Path findCheckoutRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("src/main/resources"))
                    && (Files.isRegularFile(current.resolve("build.gradle"))
                            || Files.isRegularFile(
                                    current.resolve("build.gradle.kts")))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate server checkout root");
    }

    private static final class RecordingOreWorld extends World {
        private Map<String, Integer> states;

        private RecordingOreWorld() {
            super(null, "unused", 0L, null, null,
                    org.bukkit.World.Environment.NORMAL);
        }

        static RecordingOreWorld create() throws Exception {
            RecordingOreWorld world = (RecordingOreWorld)unsafe()
                    .allocateInstance(RecordingOreWorld.class);
            world.states = new HashMap<String, Integer>();
            world.worldData = new WorldData(
                    31L, "__worldgen_feature_data_ore_parity__");
            return world;
        }

        public int getTypeId(int x, int y, int z) {
            Integer packed = this.states.get(position(x, y, z));
            return packed == null ? Block.STONE.id
                    : packed.intValue() >>> 4;
        }

        public boolean setBlockState(
                int x, int y, int z, BlockStateKey state) {
            BlockStateBridge.LegacyBlockData legacy =
                    BlockStateBridge.toLegacy(state);
            this.states.put(position(x, y, z), Integer.valueOf(
                    legacy.blockId << 4 | legacy.metadata & 15));
            return true;
        }

        boolean onlyContains(int expectedBlockId) {
            if (this.states.isEmpty()) return false;
            for (Integer packed : this.states.values()) {
                if (packed.intValue() >>> 4 != expectedBlockId) return false;
            }
            return true;
        }

        private static String position(int x, int y, int z) {
            return x + "," + y + "," + z;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        java.util.stream.Stream<Path> paths = Files.walk(root);
        try {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {}
            });
        } finally {
            paths.close();
        }
    }
}
