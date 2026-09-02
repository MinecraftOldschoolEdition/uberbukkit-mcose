package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.server.Block;
import net.minecraft.server.BlockStateBridge;
import net.minecraft.server.BlockStateKey;
import net.minecraft.server.EnumSkyBlock;
import net.minecraft.server.Material;
import net.minecraft.server.World;
import net.minecraft.server.WorldGenLakes;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

/** Runtime and Random-consumption proofs for data-backed legacy lakes. */
public class LakeFeatureRuntimeTest {
    @BeforeClass
    public static void initializeFeatures() {
        PlacedFeatureDataBootstrap.initialize();
    }

    @Test
    public void rarityHeightAndLavaFilterKeepExactLegacyIntegerDraws() {
        PlacedFeatureDefinition water = PlacedFeatureDataBootstrap.get(
                PlacedFeatureDataBootstrap.LAKE_WATER);
        PlacedFeatureDefinition lava = PlacedFeatureDataBootstrap.get(
                PlacedFeatureDataBootstrap.LAKE_LAVA);

        TracingRandom waterRejected = new TracingRandom(1);
        final List<int[]> rejectedWater = new ArrayList<int[]>();
        LegacyPlacedFeatureExecutor.generatePlacements(
                waterRejected, 100, 200, water, collector(rejectedWater));
        assertTrue(rejectedWater.isEmpty());
        assertEquals(Arrays.asList(Integer.valueOf(4)), waterRejected.bounds());

        TracingRandom waterAccepted = new TracingRandom(0, 2, 44, 9);
        final List<int[]> acceptedWater = new ArrayList<int[]>();
        LegacyPlacedFeatureExecutor.generatePlacements(
                waterAccepted, 100, 200, water, collector(acceptedWater));
        assertEquals(1, acceptedWater.size());
        assertCoordinates(acceptedWater.get(0), 102, 44, 209);
        assertEquals(Arrays.asList(4, 16, 128, 16), waterAccepted.bounds());

        TracingRandom lowLava = new TracingRandom(0, 2, 10, 7, 9);
        final List<int[]> acceptedLowLava = new ArrayList<int[]>();
        LegacyPlacedFeatureExecutor.generatePlacements(
                lowLava, 100, 200, lava, collector(acceptedLowLava));
        assertEquals(1, acceptedLowLava.size());
        assertCoordinates(acceptedLowLava.get(0), 102, 7, 209);
        assertEquals(Arrays.asList(8, 16, 120, 18, 16), lowLava.bounds());

        TracingRandom highLavaRejected =
                new TracingRandom(0, 2, 119, 100, 9, 1);
        final List<int[]> rejectedHighLava = new ArrayList<int[]>();
        LegacyPlacedFeatureExecutor.generatePlacements(
                highLavaRejected, 100, 200, lava,
                collector(rejectedHighLava));
        assertTrue(rejectedHighLava.isEmpty());
        assertEquals(Arrays.asList(8, 16, 120, 127, 16, 10),
                highLavaRejected.bounds());

        TracingRandom highLavaAccepted =
                new TracingRandom(0, 2, 119, 100, 9, 0);
        final List<int[]> acceptedHighLava = new ArrayList<int[]>();
        LegacyPlacedFeatureExecutor.generatePlacements(
                highLavaAccepted, 100, 200, lava,
                collector(acceptedHighLava));
        assertEquals(1, acceptedHighLava.size());
        assertCoordinates(acceptedHighLava.get(0), 102, 100, 209);
        assertEquals(Arrays.asList(8, 16, 120, 127, 16, 10),
                highLavaAccepted.bounds());
    }

    @Test
    public void configuredOnlyCallbackMatchesLegacyGeneratorBlockForBlock()
            throws Exception {
        assertCallbackParity(
                ConfiguredFeatureDataBootstrap.LAKE_WATER,
                "minecraft:water", 41414141L);
        assertCallbackParity(
                ConfiguredFeatureDataBootstrap.LAKE_LAVA,
                "minecraft:lava", 92929292L);
    }

    @Test
    public void configuredGeneratorUsesStillFluidAndAuthoredBarrier() {
        ConfiguredFeatureDefinition water = ConfiguredFeatureDataBootstrap.get(
                ConfiguredFeatureDataBootstrap.LAKE_WATER);
        ConfiguredFeatureDefinition lava = ConfiguredFeatureDataBootstrap.get(
                ConfiguredFeatureDataBootstrap.LAKE_LAVA);
        assertTrue(LegacyPlacedFeatureExecutor.createLakeGenerator(water)
                instanceof WorldGenLakes);
        assertTrue(LegacyPlacedFeatureExecutor.createLakeGenerator(lava)
                instanceof WorldGenLakes);
        assertEquals("still", water.getLakeFluidState().getProperty("variant"));
        assertEquals("still", lava.getLakeFluidState().getProperty("variant"));
        assertEquals(new ResourceLocation("minecraft", "air"),
                water.getLakeBarrierState().getBlockKey());
        assertEquals(new ResourceLocation("minecraft", "stone"),
                lava.getLakeBarrierState().getBlockKey());
    }

    private static void assertCallbackParity(
            ResourceLocation key,
            String legacyFluid,
            long seed) throws Exception {
        RecordingWorld directWorld = RecordingWorld.create();
        RecordingWorld dataWorld = RecordingWorld.create();
        Random directRandom = new Random(seed);
        Random dataRandom = new Random(seed);

        boolean direct = new WorldGenLakes(legacyFluid).a(
                directWorld, directRandom, 24, 72, 40);
        boolean data = LegacyPlacedFeatureExecutor.generateLakeAt(
                dataWorld, dataRandom, 24, 72, 40, key);

        assertEquals(direct, data);
        assertTrue(data);
        assertFalse(dataWorld.blocks.isEmpty());
        assertEquals(directWorld.blocks, dataWorld.blocks);
        assertEquals(directWorld.metadata, dataWorld.metadata);
        assertEquals(directRandom.nextLong(), dataRandom.nextLong());
    }

    private static LegacyPlacedFeatureExecutor.Placement collector(
            final List<int[]> coordinates) {
        return new LegacyPlacedFeatureExecutor.Placement() {
            public void generate(Random random, int x, int y, int z) {
                coordinates.add(new int[] {x, y, z});
            }
        };
    }

    private static void assertCoordinates(
            int[] actual, int x, int y, int z) {
        assertEquals(x, actual[0]);
        assertEquals(y, actual[1]);
        assertEquals(z, actual[2]);
    }

    private static final class TracingRandom extends Random {
        private final int[] values;
        private final List<Integer> bounds = new ArrayList<Integer>();
        private int index;

        TracingRandom(int... values) {
            super(0L);
            this.values = values;
        }

        public int nextInt(int bound) {
            this.bounds.add(Integer.valueOf(bound));
            if (this.index >= this.values.length) {
                throw new AssertionError("Unexpected nextInt(" + bound + ")");
            }
            int value = this.values[this.index++];
            if (value < 0 || value >= bound) {
                throw new AssertionError(
                        "Scripted value " + value + " is outside bound " + bound);
            }
            return value;
        }

        List<Integer> bounds() {
            assertEquals(this.values.length, this.index);
            return this.bounds;
        }
    }

    private static final class RecordingWorld extends World {
        private Map<String, Integer> blocks;
        private Map<String, Integer> metadata;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null,
                    org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld create() throws Exception {
            RecordingWorld world = (RecordingWorld)unsafe()
                    .allocateInstance(RecordingWorld.class);
            world.blocks = new LinkedHashMap<String, Integer>();
            world.metadata = new LinkedHashMap<String, Integer>();
            return world;
        }

        public boolean isEmpty(int x, int y, int z) {
            return this.getTypeId(x, y, z) == 0;
        }

        public int getTypeId(int x, int y, int z) {
            Integer block = this.blocks.get(coordinate(x, y, z));
            if (block != null) return block.intValue();
            return y > 60 ? 0 : Block.STONE.id;
        }

        public Material getMaterial(int x, int y, int z) {
            int blockId = this.getTypeId(x, y, z);
            Block block = blockId >= 0 && blockId < Block.byId.length
                    ? Block.byId[blockId] : null;
            return block == null ? Material.AIR : block.material;
        }

        public int a(EnumSkyBlock lightType, int x, int y, int z) {
            return 0;
        }

        public boolean setBlockState(
                int x, int y, int z, BlockStateKey state) {
            BlockStateBridge.LegacyBlockData legacy =
                    BlockStateBridge.toLegacy(state);
            String coordinate = coordinate(x, y, z);
            this.blocks.put(coordinate, Integer.valueOf(legacy.blockId));
            this.metadata.put(coordinate, Integer.valueOf(legacy.metadata));
            return true;
        }

        private static String coordinate(int x, int y, int z) {
            return x + "," + y + "," + z;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }
}
