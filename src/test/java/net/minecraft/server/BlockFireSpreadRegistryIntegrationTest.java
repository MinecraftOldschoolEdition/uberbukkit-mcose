package net.minecraft.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.server.registry.FireSpreadDataBootstrap;
import net.minecraft.server.registry.FireSpreadRegistryApi;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

/** Exercises the real server BlockFire lookup paths against published data. */
public class BlockFireSpreadRegistryIntegrationTest {
    private static final int X = 3;
    private static final int Y = 64;
    private static final int Z = 5;

    private static final RuleCase[] RULES = new RuleCase[]{
            new RuleCase(Block.WOOD, 5, 20),
            new RuleCase(Block.FENCE, 5, 20),
            new RuleCase(Block.WOOD_STAIRS, 5, 20),
            new RuleCase(Block.LOG, 5, 5),
            new RuleCase(Block.LEAVES, 30, 60),
            new RuleCase(Block.BOOKSHELF, 30, 20),
            new RuleCase(Block.TNT, 15, 100),
            new RuleCase(Block.LONG_GRASS, 60, 100),
            new RuleCase(Block.WOOL, 30, 60)
    };

    @BeforeClass
    public static void initializePublishedFireSpreadRules() {
        assertNotNull(Block.FIRE);
        FireSpreadDataBootstrap.initialize();
    }

    @After
    public void restoreBuiltInGeneration() {
        FireSpreadRegistryApi.removeRuntimeOverride(Block.STONE);
        FireSpreadDataBootstrap.reload();
    }

    @Test
    public void blockFireUsesExactIgniteOddsAndMaxSemantics()
            throws Exception {
        BlockFire fire = Block.FIRE;
        RecordingWorld world = RecordingWorld.create();
        SingleBlockAccess access = new SingleBlockAccess();
        for (RuleCase rule : RULES) {
            world.put(rule.block);
            access.put(rule.block);
            assertTrue(rule.label(), fire.b(access, X, Y, Z));
            assertEquals(rule.label(), rule.igniteOdds,
                    fire.f(world, X, Y, Z, 0));
            assertEquals(rule.label(), rule.igniteOdds,
                    fire.f(world, X, Y, Z, rule.igniteOdds - 1));
            assertEquals(rule.label(), rule.igniteOdds + 1,
                    fire.f(world, X, Y, Z, rule.igniteOdds + 1));
        }

        world.put(Block.STONE);
        access.put(Block.STONE);
        assertFalse(fire.b(access, X, Y, Z));
        assertEquals(0, fire.f(world, X, Y, Z, 0));
        assertEquals(37, fire.f(world, X, Y, Z, 37));
    }

    @Test
    public void burnAttemptReadsExactRegistryOddsBeforeOtherRandomDraws()
            throws Exception {
        BlockFire fire = Block.FIRE;
        Method attempt = BlockFire.class.getDeclaredMethod(
                "a", World.class, Integer.TYPE, Integer.TYPE, Integer.TYPE,
                Integer.TYPE, Random.class, Integer.TYPE);
        attempt.setAccessible(true);
        RecordingWorld world = RecordingWorld.create();

        for (RuleCase rule : RULES) {
            world.put(rule.block);
            RecordingRandom miss = new RecordingRandom(rule.burnOdds);
            attempt.invoke(fire, world, X, Y, Z, 300, miss, 7);
            assertEquals(rule.label(), rule.block.id,
                    world.getTypeId(X, Y, Z));
            assertArrayEquals(rule.label(), new int[]{300}, miss.bounds());
        }
    }

    @Test
    public void legacySetBurnRateBridgePublishesRegistryBackedOverride()
            throws Exception {
        BlockFire fire = Block.FIRE;
        Method bridge = BlockFire.class.getDeclaredMethod(
                "a", Integer.TYPE, Integer.TYPE, Integer.TYPE);
        bridge.setAccessible(true);
        RecordingWorld world = RecordingWorld.create();
        SingleBlockAccess access = new SingleBlockAccess();
        try {
            bridge.invoke(fire, Block.STONE.id, 117, 143);
            world.put(Block.STONE);
            access.put(Block.STONE);
            assertTrue(fire.b(access, X, Y, Z));
            assertEquals("the legacy bridge must retain values above 100",
                    117, fire.f(world, X, Y, Z, 0));
            assertEquals(143,
                    FireSpreadRegistryApi.get(Block.STONE).getBurnOdds());

            FireSpreadDataBootstrap.reload();
            assertEquals("a JSON reload must retain the mod API layer",
                    117, fire.f(world, X, Y, Z, 0));
        } finally {
            assertTrue(FireSpreadRegistryApi.removeRuntimeOverride(Block.STONE));
            FireSpreadDataBootstrap.reload();
        }
    }

    private static final class RuleCase {
        final Block block;
        final int igniteOdds;
        final int burnOdds;

        RuleCase(Block block, int igniteOdds, int burnOdds) {
            this.block = block;
            this.igniteOdds = igniteOdds;
            this.burnOdds = burnOdds;
        }

        String label() {
            return this.block.getClass().getSimpleName() + "#" + this.block.id;
        }
    }

    private static final class RecordingRandom extends Random {
        private static final long serialVersionUID = 1L;
        private final int[] values;
        private final List<Integer> requestedBounds =
                new ArrayList<Integer>();
        private int cursor;

        RecordingRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            this.requestedBounds.add(Integer.valueOf(bound));
            if (this.cursor >= this.values.length) {
                throw new AssertionError("Unexpected nextInt(" + bound + ")");
            }
            int value = this.values[this.cursor++];
            if (value < 0 || value >= bound) {
                throw new AssertionError("Queued value " + value
                        + " is outside nextInt(" + bound + ")");
            }
            return value;
        }

        int[] bounds() {
            int[] result = new int[this.requestedBounds.size()];
            for (int i = 0; i < result.length; ++i) {
                result[i] = this.requestedBounds.get(i).intValue();
            }
            return result;
        }
    }

    private static final class SingleBlockAccess implements IBlockAccess {
        private int blockId;

        void put(Block block) {
            this.blockId = block.id;
        }

        public int getTypeId(int x, int y, int z) {
            return this.blockId;
        }

        public TileEntity getTileEntity(int x, int y, int z) {
            return null;
        }

        public int getData(int x, int y, int z) {
            return 0;
        }

        public Material getMaterial(int x, int y, int z) {
            Block block = this.blockId >= 0 && this.blockId < Block.byId.length
                    ? Block.byId[this.blockId] : null;
            return block == null ? Material.AIR : block.material;
        }

        public boolean e(int x, int y, int z) {
            return false;
        }
    }

    private static final class RecordingWorld extends World {
        private int blockId;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null,
                    org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld create() throws Exception {
            return (RecordingWorld) unsafe().allocateInstance(
                    RecordingWorld.class);
        }

        void put(Block block) {
            this.blockId = block.id;
        }

        @Override
        public int getTypeId(int x, int y, int z) {
            return this.blockId;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
