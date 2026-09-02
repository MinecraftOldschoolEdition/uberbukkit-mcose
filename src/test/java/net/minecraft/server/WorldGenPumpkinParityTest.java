package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

/** Pins the plain-pumpkin identity without changing Beta decoration RNG. */
public class WorldGenPumpkinParityTest {
    private static final BlockStateKey PLAIN_PUMPKIN =
            new BlockStateKey(new ResourceLocation("minecraft", "pumpkin"));

    @BeforeClass
    public static void initializeBlocks() {
        assertNotNull(Block.GRASS);
        assertNotNull(Block.PUMPKIN);
        assertNotNull(Block.PUMPKIN_PLAIN);
        assertNotNull(Block.CARVED_PUMPKIN);
    }

    @Test
    public void plainOutputRetainsBetaSuccessfulPlacementRandomDraws()
            throws Exception {
        long[] seeds = {0L, 1L, -1L, 246813579L, Long.MAX_VALUE};
        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
            RecordingWorld expectedWorld = RecordingWorld.create();
            RecordingWorld actualWorld = RecordingWorld.create();
            Random expectedRandom = new Random(seeds[seedIndex]);
            Random actualRandom = new Random(seeds[seedIndex]);

            generateBetaPlainOracle(
                    expectedWorld, expectedRandom, 8, 64, -8);
            assertTrue(new WorldGenPumpkin().a(
                    actualWorld, actualRandom, 8, 64, -8));

            assertFalse(actualWorld.states.isEmpty());
            assertTrue(actualWorld.onlyContains(Block.PUMPKIN_PLAIN.id, 0));
            assertEquals(expectedWorld.states, actualWorld.states);
            assertEquals(expectedRandom.nextLong(), actualRandom.nextLong());
            if (seeds[seedIndex] == 0L) {
                assertEquals(15, actualWorld.states.size());
            }
        }
    }

    private static void generateBetaPlainOracle(
            World world, Random random, int originX, int originY, int originZ) {
        for (int attempt = 0; attempt < 64; attempt++) {
            int x = originX + random.nextInt(8) - random.nextInt(8);
            int y = originY + random.nextInt(4) - random.nextInt(4);
            int z = originZ + random.nextInt(8) - random.nextInt(8);
            if (world.isEmpty(x, y, z)
                    && world.getTypeId(x, y - 1, z) == Block.GRASS.id
                    && Block.PUMPKIN.canPlace(world, x, y, z)) {
                random.nextInt(4);
                world.setBlockState(x, y, z, PLAIN_PUMPKIN);
            }
        }
    }

    private static final class RecordingWorld extends World {
        private Map<String, Integer> states;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null,
                    org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld create() throws Exception {
            RecordingWorld world = (RecordingWorld)unsafe()
                    .allocateInstance(RecordingWorld.class);
            world.states = new LinkedHashMap<String, Integer>();
            return world;
        }

        public int getTypeId(int x, int y, int z) {
            Integer packed = this.states.get(position(x, y, z));
            return packed != null
                    ? packed.intValue() >>> 4
                    : y == 63 ? Block.GRASS.id : 0;
        }

        public int getData(int x, int y, int z) {
            Integer packed = this.states.get(position(x, y, z));
            return packed == null ? 0 : packed.intValue() & 15;
        }

        public boolean setBlockState(
                int x, int y, int z, BlockStateKey state) {
            BlockStateBridge.LegacyBlockData legacy =
                    BlockStateBridge.toLegacy(state);
            this.states.put(position(x, y, z), Integer.valueOf(
                    legacy.blockId << 4 | legacy.metadata & 15));
            return true;
        }

        boolean onlyContains(int blockId, int metadata) {
            int expected = blockId << 4 | metadata & 15;
            for (Integer packed : this.states.values()) {
                if (packed.intValue() != expected) return false;
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
}
