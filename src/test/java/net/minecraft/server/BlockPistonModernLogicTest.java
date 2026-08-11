package net.minecraft.server;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import sun.misc.Unsafe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockPistonModernLogicTest {
    private static final int X = 8;
    private static final int Y = 64;
    private static final int Z = 8;
    private static final int EAST = 5;

    @Test
    public void fenceGateCanBePlacedWithoutFloorSupport() throws Exception {
        RecordingWorld world = RecordingWorld.create();

        assertTrue(Block.FENCE_GATE.canPlace(world, X, Y, Z));
    }

    @Test
    public void stalePistonEventsAreRejectedAgainstCurrentPower() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        BlockPiston piston = (BlockPiston)Block.PISTON;
        world.setState(X, Y, Z, Block.PISTON.id, EAST);
        world.powered = false;

        assertFalse(piston.playBlockEvent(world, X, Y, Z, 0, EAST));
        assertEquals(Block.PISTON.id, world.getTypeId(X, Y, Z));
        assertEquals(EAST, world.getData(X, Y, Z));
        assertEquals(0, world.fullBlockWrites);

        world.setState(X, Y, Z, Block.PISTON.id, EAST | 8);
        world.powered = true;

        assertFalse(piston.playBlockEvent(world, X, Y, Z, 1, EAST));
        assertEquals(Block.PISTON.id, world.getTypeId(X, Y, Z));
        assertEquals(EAST | 8, world.getData(X, Y, Z));
        assertEquals(0, world.fullBlockWrites);
    }

    @Test
    public void pistonHeadRequiresMatchingExtendedOrMovingBase() throws Exception {
        RecordingWorld retracted = RecordingWorld.create();
        retracted.isStatic = true;
        retracted.setState(X, Y, Z, Block.PISTON.id, EAST);
        retracted.setState(X + 1, Y, Z, Block.PISTON_EXTENSION.id, EAST);

        Block.PISTON_EXTENSION.doPhysics(retracted, X + 1, Y, Z, Block.STONE.id);

        assertEquals(0, retracted.getTypeId(X + 1, Y, Z));
        assertEquals(Block.PISTON.id, retracted.getTypeId(X, Y, Z));

        RecordingWorld wrongFacing = RecordingWorld.create();
        wrongFacing.isStatic = true;
        wrongFacing.setState(X, Y, Z, Block.PISTON.id, EAST | 8);
        wrongFacing.setState(X + 1, Y, Z, Block.PISTON_EXTENSION.id, 4);

        Block.PISTON_EXTENSION.doPhysics(wrongFacing, X + 1, Y, Z, Block.STONE.id);

        assertEquals(0, wrongFacing.getTypeId(X + 1, Y, Z));
        assertEquals(Block.PISTON.id, wrongFacing.getTypeId(X, Y, Z));

        RecordingWorld matching = RecordingWorld.create();
        matching.isStatic = true;
        matching.setState(X, Y, Z, Block.PISTON.id, EAST | 8);
        matching.setState(X + 1, Y, Z, Block.PISTON_EXTENSION.id, EAST);

        Block.PISTON_EXTENSION.doPhysics(matching, X + 1, Y, Z, Block.STONE.id);

        assertEquals(Block.PISTON_EXTENSION.id, matching.getTypeId(X + 1, Y, Z));
    }

    private static final class RecordingWorld extends World {
        private Map<String, Integer> blockIds;
        private Map<String, Integer> metadata;
        boolean powered;
        int fullBlockWrites;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null, org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld create() throws Exception {
            RecordingWorld world = (RecordingWorld)unsafe().allocateInstance(RecordingWorld.class);
            world.blockIds = new HashMap<String, Integer>();
            world.metadata = new HashMap<String, Integer>();
            return world;
        }

        void setState(int x, int y, int z, int blockId, int data) {
            this.blockIds.put(key(x, y, z), blockId);
            this.metadata.put(key(x, y, z), data);
        }

        public int getTypeId(int x, int y, int z) {
            Integer value = this.blockIds.get(key(x, y, z));
            return value == null ? 0 : value;
        }

        public int getData(int x, int y, int z) {
            Integer value = this.metadata.get(key(x, y, z));
            return value == null ? 0 : value;
        }

        public boolean setRawData(int x, int y, int z, int data) {
            this.metadata.put(key(x, y, z), data);
            return true;
        }

        public boolean setRawTypeIdAndData(int x, int y, int z, int blockId, int data) {
            ++this.fullBlockWrites;
            this.setState(x, y, z, blockId, data);
            return true;
        }

        public boolean setTypeId(int x, int y, int z, int blockId) {
            ++this.fullBlockWrites;
            this.setState(x, y, z, blockId, 0);
            return true;
        }

        public boolean isBlockFaceIndirectlyPowered(int x, int y, int z, int side) {
            return this.powered;
        }

        private static String key(int x, int y, int z) {
            return x + ":" + y + ":" + z;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }
}
