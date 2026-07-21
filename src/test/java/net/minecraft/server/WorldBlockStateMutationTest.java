package net.minecraft.server;

import java.lang.reflect.Field;
import org.junit.Test;
import sun.misc.Unsafe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WorldBlockStateMutationTest {
    @Test
    public void sameBlockStateChangesUseMetadataLifecyclePath() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        world.blockId = Block.PISTON.id;
        world.metadata = 5;

        BlockStateKey extended = BlockStateBridge.fromLegacy(Block.PISTON.id, 13);
        assertTrue(world.setBlockState(4, 64, 4, extended));
        assertEquals(13, world.metadata);
        assertEquals(1, world.rawMetadataWrites);
        assertEquals(0, world.rawFullBlockWrites);

        BlockStateKey retracted = BlockStateBridge.fromLegacy(Block.PISTON.id, 5);
        assertTrue(world.setBlockStateAndData(4, 64, 4, retracted));
        assertEquals(5, world.metadata);
        assertEquals(1, world.notifyingMetadataWrites);
        assertEquals(0, world.notifyingFullBlockWrites);
    }

    @Test
    public void blockIdentityChangesStillUseFullLifecyclePath() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        world.blockId = Block.REDSTONE_TORCH_OFF.id;
        world.metadata = 5;

        BlockStateKey lit = BlockStateBridge.fromLegacy(Block.REDSTONE_TORCH_ON.id, 5);
        assertTrue(world.setBlockStateAndData(4, 64, 4, lit));
        assertEquals(Block.REDSTONE_TORCH_ON.id, world.blockId);
        assertEquals(1, world.notifyingFullBlockWrites);
        assertEquals(0, world.notifyingMetadataWrites);
    }

    private static final class RecordingWorld extends World {
        int blockId;
        int metadata;
        int rawMetadataWrites;
        int notifyingMetadataWrites;
        int rawFullBlockWrites;
        int notifyingFullBlockWrites;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null, org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld create() throws Exception {
            return (RecordingWorld)unsafe().allocateInstance(RecordingWorld.class);
        }

        public int getTypeId(int x, int y, int z) {
            return this.blockId;
        }

        public int getData(int x, int y, int z) {
            return this.metadata;
        }

        public boolean setRawData(int x, int y, int z, int metadata) {
            ++this.rawMetadataWrites;
            this.metadata = metadata;
            return true;
        }

        public void setData(int x, int y, int z, int metadata) {
            ++this.notifyingMetadataWrites;
            this.metadata = metadata;
        }

        public boolean setRawTypeIdAndData(int x, int y, int z, int blockId, int metadata) {
            ++this.rawFullBlockWrites;
            this.blockId = blockId;
            this.metadata = metadata;
            return true;
        }

        public boolean setTypeIdAndData(int x, int y, int z, int blockId, int metadata) {
            ++this.notifyingFullBlockWrites;
            this.blockId = blockId;
            this.metadata = metadata;
            return true;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }
}
