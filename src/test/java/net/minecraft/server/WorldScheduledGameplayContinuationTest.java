package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import org.junit.Test;
import sun.misc.Unsafe;

public class WorldScheduledGameplayContinuationTest {
    @Test
    public void sandAndGravelCreateAndLandFallingEntitiesAfterNeighborReadinessResumes()
            throws Exception {
        GameplayWorld world = GameplayWorld.create();
        world.putBlock(5, 60, 5, Block.STONE.id, 0);
        world.putBlock(5, 64, 5, Block.SAND.id, 0);
        world.putBlock(10, 60, 5, Block.STONE.id, 0);
        world.putBlock(10, 64, 5, Block.GRAVEL.id, 0);

        Block.SAND.c(world, 5, 64, 5);
        Block.GRAVEL.c(world, 10, 64, 5);
        setLongField(world, "blockTickTime", 3L);

        assertTrue(world.a(false));
        assertEquals("blocked neighbor readiness must retain both callbacks", 2,
                pendingCount(world, 0, 0));
        assertTrue(world.joinedEntities.isEmpty());

        world.neighborsReady = true;
        world.a(false);

        assertEquals(2, world.joinedEntities.size());
        assertTrue(world.joinedEntities.get(0) instanceof EntityFallingSand);
        assertTrue(world.joinedEntities.get(1) instanceof EntityFallingSand);
        assertEquals("Beta 1.7.3 leaves sand for the falling entity's first tick",
                Block.SAND.id, world.getTypeId(5, 64, 5));
        assertEquals("Beta 1.7.3 leaves gravel for the falling entity's first tick",
                Block.GRAVEL.id, world.getTypeId(10, 64, 5));
        for (int index = 0; index < world.joinedEntities.size(); ++index) {
            EntityFallingSand falling = (EntityFallingSand) world.joinedEntities.get(index);
            for (int tick = 0; tick < 100 && !falling.dead; ++tick) {
                falling.m_();
            }
            assertTrue("real falling entity must finish its landing lifecycle", falling.dead);
        }

        assertEquals(Block.SAND.id, world.getTypeId(5, 61, 5));
        assertEquals(Block.GRAVEL.id, world.getTypeId(10, 61, 5));
        assertEquals(0, world.getTypeId(5, 64, 5));
        assertEquals(0, world.getTypeId(10, 64, 5));
    }

    @Test
    public void sandAndGravelRetainBeta173PistonDuplicationWindow()
            throws Exception {
        assertLegacyPistonDuplicationWindow(Block.SAND.id, 5);
        assertLegacyPistonDuplicationWindow(Block.GRAVEL.id, 10);
    }

    @Test
    public void waterAndLavaFlowDownAfterNeighborReadinessResumes() throws Exception {
        GameplayWorld world = GameplayWorld.create();
        world.putBlock(5, 64, 5, Block.WATER.id, 0);
        world.putBlock(10, 64, 5, Block.LAVA.id, 0);

        Block.WATER.c(world, 5, 64, 5);
        Block.LAVA.c(world, 10, 64, 5);
        setLongField(world, "blockTickTime", 30L);

        assertTrue(world.a(false));
        assertEquals("blocked neighbor readiness must retain both fluid callbacks", 2,
                pendingCount(world, 0, 0));
        assertEquals(0, world.getTypeId(5, 63, 5));
        assertEquals(0, world.getTypeId(10, 63, 5));

        world.neighborsReady = true;
        world.a(false);

        assertEquals(Block.WATER.id, world.getTypeId(5, 63, 5));
        assertEquals(8, world.getData(5, 63, 5));
        assertEquals(Block.LAVA.id, world.getTypeId(10, 63, 5));
        assertEquals(8, world.getData(10, 63, 5));
    }

    private static int pendingCount(World world, int chunkX, int chunkZ) {
        List ticks = world.getPendingBlockTicksForChunk(chunkX, chunkZ);
        return ticks == null ? 0 : ticks.size();
    }

    private static void assertLegacyPistonDuplicationWindow(int blockId, int x)
            throws Exception {
        GameplayWorld world = GameplayWorld.create();
        world.neighborsReady = true;
        world.putBlock(x, 60, 5, Block.STONE.id, 0);
        world.putBlock(x, 64, 5, blockId, 0);

        Block.byId[blockId].a(world, x, 64, 5, new Random(0L));

        assertEquals(1, world.joinedEntities.size());
        assertEquals("the piston must be able to claim the source before entity tick",
                blockId, world.getTypeId(x, 64, 5));
        EntityFallingSand falling =
                (EntityFallingSand) world.joinedEntities.get(0);

        // Model the synchronous Beta piston extension between the scheduled
        // gravity callback and the entity phase: the source becomes a moving
        // piston while the carried block is restored at its destination.
        world.putBlock(x, 64, 5, Block.PISTON_MOVING.id, 5);
        world.putBlock(x + 1, 64, 5, blockId, 0);
        falling.m_();

        assertEquals("the first falling-entity tick must not erase block 36",
                Block.PISTON_MOVING.id, world.getTypeId(x, 64, 5));
        assertEquals(blockId, world.getTypeId(x + 1, 64, 5));

        // Let the piston head leave the source and finish the falling entity.
        world.putBlock(x, 64, 5, 0, 0);
        for (int tick = 0; tick < 100 && !falling.dead; ++tick) {
            falling.m_();
        }

        assertTrue("the duplicated falling entity must finish", falling.dead);
        assertEquals("the piston-retained copy must survive",
                blockId, world.getTypeId(x + 1, 64, 5));
        assertEquals("the falling entity must create the duplicated copy",
                blockId, world.getTypeId(x, 61, 5));
    }

    private static final class GameplayWorld extends World {
        private Map<String, Integer> blockIds;
        private Map<String, Integer> blockData;
        private List<Entity> joinedEntities;
        private boolean neighborsReady;

        private GameplayWorld() {
            super(null, "unused", 0L, null, null, org.bukkit.World.Environment.NORMAL);
        }

        static GameplayWorld create() throws Exception {
            GameplayWorld world = (GameplayWorld) unsafe().allocateInstance(GameplayWorld.class);
            world.blockIds = new HashMap<String, Integer>();
            world.blockData = new HashMap<String, Integer>();
            world.joinedEntities = new ArrayList<Entity>();
            world.entityList = new ArrayList();
            world.random = new Random(0L);
            world.worldProvider = new WorldProviderNormal();
            setField(world, "E", new TreeSet());
            setField(world, "F", new HashSet());
            setField(world, "scheduledTickChunkIndex", new HashMap());
            setField(world, "scheduledTickDirtyChunks", new HashSet());
            setField(world, "scheduledTickLastSavedTimes", new HashMap());
            setField(world, "u", new ArrayList());
            return world;
        }

        void putBlock(int x, int y, int z, int blockId, int data) {
            String key = key(x, y, z);
            if (blockId == 0) {
                this.blockIds.remove(key);
                this.blockData.remove(key);
            } else {
                this.blockIds.put(key, Integer.valueOf(blockId));
                this.blockData.put(key, Integer.valueOf(data));
            }
        }

        @Override
        public boolean isLoaded(int x, int y, int z) {
            return y >= 0 && y < 128;
        }

        @Override
        public boolean a(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            return this.neighborsReady;
        }

        @Override
        public int getTypeId(int x, int y, int z) {
            Integer value = this.blockIds.get(key(x, y, z));
            return value == null ? 0 : value.intValue();
        }

        @Override
        public int getTypeIdIfLoaded(int x, int y, int z) {
            return this.getTypeId(x, y, z);
        }

        @Override
        public int getData(int x, int y, int z) {
            Integer value = this.blockData.get(key(x, y, z));
            return value == null ? 0 : value.intValue();
        }

        @Override
        public int getDataIfLoaded(int x, int y, int z) {
            return this.getData(x, y, z);
        }

        @Override
        public boolean setRawTypeIdAndData(int x, int y, int z, int blockId, int data) {
            int oldId = this.getTypeId(x, y, z);
            int oldData = this.getData(x, y, z);
            this.putBlock(x, y, z, blockId, data);
            return oldId != blockId || oldData != data;
        }

        @Override
        public boolean setRawTypeId(int x, int y, int z, int blockId) {
            return this.setRawTypeIdAndData(x, y, z, blockId, 0);
        }

        @Override
        public boolean setTypeId(int x, int y, int z, int blockId) {
            return this.setRawTypeId(x, y, z, blockId);
        }

        @Override
        public boolean setTypeIdAndData(int x, int y, int z, int blockId, int data) {
            return this.setRawTypeIdAndData(x, y, z, blockId, data);
        }

        @Override
        public void setData(int x, int y, int z, int data) {
            this.putBlock(x, y, z, this.getTypeId(x, y, z), data);
        }

        @Override
        public boolean setRawData(int x, int y, int z, int data) {
            int old = this.getData(x, y, z);
            this.setData(x, y, z, data);
            return old != data;
        }

        @Override
        public void notify(int x, int y, int z) {
        }

        @Override
        public void b(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        }

        @Override
        public void applyPhysics(int x, int y, int z, int blockId) {
        }

        @Override
        public boolean addEntity(Entity entity) {
            this.joinedEntities.add(entity);
            return true;
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            return null;
        }

        @Override
        public List getEntities(Entity entity, AxisAlignedBB bounds) {
            if (bounds.b < 61.0D && bounds.e > 60.0D) {
                return Collections.singletonList(
                        AxisAlignedBB.b(-1024.0D, 60.0D, -1024.0D,
                                1024.0D, 61.0D, 1024.0D));
            }
            return Collections.emptyList();
        }

        @Override
        public boolean a(int blockId, int x, int y, int z, boolean replace, int side) {
            return true;
        }

        private static String key(int x, int y, int z) {
            return x + ":" + y + ":" + z;
        }
    }

    private static void setField(World world, String name, Object value) throws Exception {
        Field field = World.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(world, value);
    }

    private static void setLongField(World world, String name, long value) throws Exception {
        Field field = World.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(world, value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
