package net.minecraft.server;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import sun.misc.Unsafe;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockDoorDoubleDoorRedstoneTest {
    @Test
    public void mirroredLeafFollowsRedstoneForEveryDoorRotation() throws Exception {
        BlockDoor[] doors = new BlockDoor[]{(BlockDoor)Block.WOODEN_DOOR, (BlockDoor)Block.IRON_DOOR_BLOCK};

        for (BlockDoor door : doors) {
            for (int rotation = 0; rotation < 4; ++rotation) {
                RecordingWorld world = RecordingWorld.create();
                int mirroredX = offsetX(rotation);
                int mirroredZ = offsetZ(rotation);
                int mirroredMetadata = (rotation - 1 & 3) | 4;
                world.putDoor(0, 64, 0, door.id, rotation);
                world.putDoor(mirroredX, 64, mirroredZ, door.id, mirroredMetadata);

                assertFalse(door.isDoorLogicallyOpen(world, 0, 64, 0));
                assertFalse(door.isDoorLogicallyOpen(world, mirroredX, 64, mirroredZ));

                door.setDoor(world, 0, 64, 0, true);
                door.setDoor(world, mirroredX, 64, mirroredZ, true);

                assertTrue(door.isDoorLogicallyOpen(world, 0, 64, 0));
                assertTrue(door.isDoorLogicallyOpen(world, mirroredX, 64, mirroredZ));
                assertTrue((world.getData(0, 64, 0) & 4) != 0);
                assertFalse((world.getData(mirroredX, 64, mirroredZ) & 4) != 0);

                door.setDoor(world, 0, 64, 0, false);
                door.setDoor(world, mirroredX, 65, mirroredZ, false);

                assertFalse(door.isDoorLogicallyOpen(world, 0, 64, 0));
                assertFalse(door.isDoorLogicallyOpen(world, mirroredX, 65, mirroredZ));
                assertFalse((world.getData(0, 64, 0) & 4) != 0);
                assertTrue((world.getData(mirroredX, 64, mirroredZ) & 4) != 0);
            }
        }
    }

    private static int offsetX(int rotation) {
        return rotation == 1 ? -1 : rotation == 3 ? 1 : 0;
    }

    private static int offsetZ(int rotation) {
        return rotation == 0 ? 1 : rotation == 2 ? -1 : 0;
    }

    private static final class RecordingWorld extends World {
        private Map<Long, Integer> blocks;
        private Map<Long, Integer> metadata;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null, org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld create() throws Exception {
            RecordingWorld world = (RecordingWorld)unsafe().allocateInstance(RecordingWorld.class);
            world.blocks = new HashMap<Long, Integer>();
            world.metadata = new HashMap<Long, Integer>();
            return world;
        }

        void putDoor(int x, int y, int z, int blockId, int lowerMetadata) {
            this.put(x, y, z, blockId, lowerMetadata);
            this.put(x, y + 1, z, blockId, lowerMetadata + 8);
        }

        private void put(int x, int y, int z, int blockId, int blockMetadata) {
            long key = key(x, y, z);
            this.blocks.put(Long.valueOf(key), Integer.valueOf(blockId));
            this.metadata.put(Long.valueOf(key), Integer.valueOf(blockMetadata & 15));
        }

        public int getTypeId(int x, int y, int z) {
            Integer value = this.blocks.get(Long.valueOf(key(x, y, z)));
            return value == null ? 0 : value.intValue();
        }

        public int getData(int x, int y, int z) {
            Integer value = this.metadata.get(Long.valueOf(key(x, y, z)));
            return value == null ? 0 : value.intValue();
        }

        public void setData(int x, int y, int z, int blockMetadata) {
            this.metadata.put(Long.valueOf(key(x, y, z)), Integer.valueOf(blockMetadata & 15));
        }

        public void b(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        }

        public void a(EntityHuman player, int effect, int x, int y, int z, int data) {
        }

        private static long key(int x, int y, int z) {
            return ((long)x & 0x3FFFFFFL) << 38 | ((long)z & 0x3FFFFFFL) << 12 | (long)y & 0xFFFL;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }
}
