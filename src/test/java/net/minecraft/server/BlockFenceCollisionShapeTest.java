package net.minecraft.server;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import sun.misc.Unsafe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockFenceCollisionShapeTest {
    @Test
    public void cornerFenceCollisionLeavesTheOuterCornerEmpty() throws Exception {
        BlockFence fence = (BlockFence)Block.FENCE;
        Field modernField = BlockFence.class.getDeclaredField("modernFencingBounding");
        modernField.setAccessible(true);
        boolean previousModernValue = modernField.getBoolean(fence);
        modernField.setBoolean(fence, true);
        try {
            RecordingWorld world = RecordingWorld.create();
            int x = 8;
            int y = 64;
            int z = 8;
            world.setBlock(x, y, z, Block.FENCE.id);
            world.setBlock(x, y, z - 1, Block.FENCE.id);
            world.setBlock(x + 1, y, z, Block.FENCE.id);

            ArrayList collisions = new ArrayList();
            AxisAlignedBB query = AxisAlignedBB.b(x, y, z, x + 1, y + 1.5D, z + 1);
            fence.a(world, x, y, z, query, collisions);

            assertEquals(3, collisions.size());
            assertTrue(contains(collisions, x + 0.5D, y + 0.5D, z + 0.125D));
            assertTrue(contains(collisions, x + 0.875D, y + 0.5D, z + 0.5D));
            assertFalse(contains(collisions, x + 0.875D, y + 0.5D, z + 0.125D));
        } finally {
            modernField.setBoolean(fence, previousModernValue);
        }
    }

    private static boolean contains(ArrayList boxes, double x, double y, double z) {
        for (Object value : boxes) {
            AxisAlignedBB box = (AxisAlignedBB)value;
            if (x > box.a && x < box.d && y > box.b && y < box.e && z > box.c && z < box.f) {
                return true;
            }
        }
        return false;
    }

    private static final class RecordingWorld extends World {
        private Map<String, Integer> blockIds;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null, org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld create() throws Exception {
            RecordingWorld world = (RecordingWorld)unsafe().allocateInstance(RecordingWorld.class);
            world.blockIds = new HashMap<String, Integer>();
            return world;
        }

        void setBlock(int x, int y, int z, int blockId) {
            this.blockIds.put(key(x, y, z), blockId);
        }

        public int getTypeId(int x, int y, int z) {
            Integer blockId = this.blockIds.get(key(x, y, z));
            return blockId == null ? 0 : blockId;
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
