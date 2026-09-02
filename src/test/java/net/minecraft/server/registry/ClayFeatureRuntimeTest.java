package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.server.Alpha.AlphaWorldGenClay;
import net.minecraft.server.Block;
import net.minecraft.server.BlockStateBridge;
import net.minecraft.server.BlockStateKey;
import net.minecraft.server.Material;
import net.minecraft.server.World;
import net.minecraft.server.WorldGenClay;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

/** Runtime proofs for the data-backed normal clay generator and Alpha boundary. */
public class ClayFeatureRuntimeTest {
    @BeforeClass
    public static void initializeFeatures() {
        PlacedFeatureDataBootstrap.initialize();
    }

    @Test
    public void configuredClayRequiresWaterAndOnlyReplacesSand()
            throws Exception {
        long seed = 918273645L;

        RecordingWorld rejected = RecordingWorld.create(
                Material.LAVA, Block.SAND.id);
        Random rejectedRandom = new Random(seed);
        LegacyPlacedFeatureExecutor.generate(
                rejected, rejectedRandom, 0, 0,
                PlacedFeatureDataBootstrap.CLAY);
        Random expectedRejected = new Random(seed);
        consumePlacements(expectedRejected, false, 32);
        assertEquals(0, rejected.blockWrites);
        assertEquals(expectedRejected.nextLong(), rejectedRandom.nextLong());

        RecordingWorld accepted = RecordingWorld.create(
                Material.WATER, Block.SAND.id);
        Random acceptedRandom = new Random(seed);
        LegacyPlacedFeatureExecutor.generate(
                accepted, acceptedRandom, 0, 0,
                PlacedFeatureDataBootstrap.CLAY);
        Random expectedAccepted = new Random(seed);
        consumePlacements(expectedAccepted, true, 32);
        assertTrue(accepted.blockWrites > 0);
        assertTrue(accepted.onlyContains(Block.CLAY.id));
        assertEquals(expectedAccepted.nextLong(), acceptedRandom.nextLong());

        RecordingWorld nonSand = RecordingWorld.create(
                Material.WATER, Block.DIRT.id);
        Random nonSandRandom = new Random(seed);
        LegacyPlacedFeatureExecutor.generate(
                nonSand, nonSandRandom, 0, 0,
                PlacedFeatureDataBootstrap.CLAY);
        Random expectedNonSand = new Random(seed);
        consumePlacements(expectedNonSand, true, 32);
        assertEquals(0, nonSand.blockWrites);
        assertEquals(expectedNonSand.nextLong(), nonSandRandom.nextLong());
    }

    @Test
    public void dataConstructorConsumesOutputTargetSizeAndOriginFluid()
            throws Exception {
        WorldGenClay generator = new WorldGenClay(
                new ResourceLocation("minecraft", "gravel"),
                new ResourceLocation("minecraft", "dirt"),
                4,
                Material.LAVA);
        RecordingWorld world = RecordingWorld.create(
                Material.LAVA, Block.DIRT.id);
        Random actual = new Random(31415926L);
        assertTrue(generator.a(world, actual, 0, 24, 0));
        Random expected = new Random(31415926L);
        consumeAcceptedCallback(expected, 4);
        assertTrue(world.blockWrites > 0);
        assertTrue(world.onlyContains(Block.GRAVEL.id));
        assertEquals(expected.nextLong(), actual.nextLong());

        RecordingWorld wrongFluid = RecordingWorld.create(
                Material.WATER, Block.DIRT.id);
        Random rejected = new Random(27182818L);
        Random untouched = new Random(27182818L);
        assertFalse(generator.a(wrongFluid, rejected, 0, 24, 0));
        assertEquals(0, wrongFluid.blockWrites);
        assertEquals(untouched.nextLong(), rejected.nextLong());
    }

    @Test
    public void alphaRetainsTruncatingNegativeCoordinateBounds()
            throws Exception {
        RecordingWorld normalWorld = RecordingWorld.create(
                Material.WATER, Block.SAND.id);
        RecordingWorld alphaWorld = RecordingWorld.create(
                Material.WATER, Block.SAND.id);
        Random normalRandom = new Random(8675309L);
        Random alphaRandom = new Random(8675309L);

        assertTrue(new WorldGenClay(32).a(
                normalWorld, normalRandom, -24, 32, -24));
        assertTrue(new AlphaWorldGenClay(32).a(
                alphaWorld, alphaRandom, -24, 32, -24));

        assertTrue(normalWorld.blockWrites > 0);
        assertTrue(alphaWorld.blockWrites > 0);
        assertFalse(normalWorld.writtenCoordinates().equals(
                alphaWorld.writtenCoordinates()));
        assertEquals(normalRandom.nextLong(), alphaRandom.nextLong());
    }

    private static void consumePlacements(
            Random random, boolean acceptsOriginFluid, int size) {
        for (int attempt = 0; attempt < 10; attempt++) {
            random.nextInt(16);
            random.nextInt(128);
            random.nextInt(16);
            if (acceptsOriginFluid) {
                consumeAcceptedCallback(random, size);
            }
        }
    }

    private static void consumeAcceptedCallback(Random random, int size) {
        random.nextFloat();
        random.nextInt(3);
        random.nextInt(3);
        for (int step = 0; step <= size; step++) {
            random.nextDouble();
        }
    }

    private static final class RecordingWorld extends World {
        private Material originMaterial;
        private int defaultBlockId;
        private Map<String, Integer> blocks;
        private Set<String> written;
        private int blockWrites;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null,
                    org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld create(
                Material originMaterial, int defaultBlockId) throws Exception {
            RecordingWorld world = (RecordingWorld)unsafe()
                    .allocateInstance(RecordingWorld.class);
            world.originMaterial = originMaterial;
            world.defaultBlockId = defaultBlockId;
            world.blocks = new HashMap<String, Integer>();
            world.written = new HashSet<String>();
            world.random = new Random(1L);
            return world;
        }

        public Material getMaterial(int x, int y, int z) {
            return this.originMaterial;
        }

        public int getTypeId(int x, int y, int z) {
            Integer value = this.blocks.get(key(x, y, z));
            return value == null ? this.defaultBlockId : value.intValue();
        }

        public boolean setBlockState(
                int x, int y, int z, BlockStateKey state) {
            BlockStateBridge.LegacyBlockData legacy =
                    BlockStateBridge.toLegacy(state);
            String key = key(x, y, z);
            this.blocks.put(key, Integer.valueOf(legacy.blockId));
            this.written.add(key);
            this.blockWrites++;
            return true;
        }

        boolean onlyContains(int expectedBlockId) {
            if (this.written.isEmpty()) return false;
            for (String coordinate : this.written) {
                if (this.blocks.get(coordinate).intValue() != expectedBlockId) {
                    return false;
                }
            }
            return true;
        }

        Set<String> writtenCoordinates() {
            return new HashSet<String>(this.written);
        }

        private static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }
}
