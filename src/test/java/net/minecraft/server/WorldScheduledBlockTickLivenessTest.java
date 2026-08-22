package net.minecraft.server;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;
import sun.misc.Unsafe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WorldScheduledBlockTickLivenessTest {
    @Test
    public void realSandGravelAndWaterCallbacksQueueWhileOwnerHaloIsBlocked() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        world.blockOwner(0, 0);
        world.returnedBlockId = Block.WATER.id;

        Block.SAND.c(world, 3, 64, 3);
        Block.GRAVEL.c(world, 4, 64, 4);
        Block.WATER.c(world, 5, 64, 5);

        assertEquals(3, pendingCount(world, 0, 0));
        assertTrue(hasPendingTick(world, 0, 0, Block.SAND.id));
        assertTrue(hasPendingTick(world, 0, 0, Block.GRAVEL.id));
        assertTrue(hasPendingTick(world, 0, 0, Block.WATER.id));

        int setupTypeReads = world.typeReads;
        setLongField(world, "blockTickTime", 5L);
        assertTrue(world.a(false));
        assertEquals(3, pendingCount(world, 0, 0));
        assertEquals(setupTypeReads, world.typeReads);
    }

    @Test
    public void ownerLoadedTickWaitsForNeighborAndThenExecutes() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        world.blockOwner(0, 0);

        world.c(15, 64, 8, Block.STONE.id, 0);

        assertEquals(1, pendingCount(world, 0, 0));
        assertTrue(world.a(false));
        assertEquals(1, pendingCount(world, 0, 0));
        assertEquals(0, world.typeReads);

        world.unblockOwner(0, 0);

        assertFalse(world.a(false));
        assertNull(world.getPendingBlockTicksForChunk(0, 0));
        assertEquals(1, world.typeReads);
    }

    @Test
    public void duplicateScheduleRemainsDeduplicatedWhileDeferred() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        world.blockOwner(0, 0);

        world.c(4, 64, 4, Block.STONE.id, 0);
        world.c(4, 64, 4, Block.STONE.id, 0);
        assertEquals(1, pendingCount(world, 0, 0));

        assertTrue(world.a(false));
        world.c(4, 64, 4, Block.STONE.id, 0);

        assertEquals(1, pendingCount(world, 0, 0));
        world.unblockOwner(0, 0);
        assertFalse(world.a(false));
        assertEquals(1, world.typeReads);
    }

    @Test
    public void blockedPrefixDoesNotConsumeReadyWorkBudget() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        world.blockOwner(0, 0);

        for (int index = 0; index < 1000; ++index) {
            int x = index & 15;
            int z = index >> 4 & 15;
            int y = 1 + (index >> 8);
            world.c(x, y, z, Block.STONE.id, 0);
        }

        world.c(32, 64, 0, Block.STONE.id, 0);
        assertEquals(1000, pendingCount(world, 0, 0));
        assertEquals(1, pendingCount(world, 2, 0));

        assertTrue(world.a(false));

        assertEquals(1000, pendingCount(world, 0, 0));
        assertNull(world.getPendingBlockTicksForChunk(2, 0));
        assertEquals(1, world.typeReads);
        assertEquals(3, world.readinessChecks);
    }

    @Test
    public void staleReadyTicksStillRespectPerTickWorkCap() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        world.returnedBlockId = 0;

        for (int index = 0; index < 1001; ++index) {
            int x = index & 15;
            int z = index >> 4 & 15;
            int y = 1 + (index >> 8);
            world.c(x, y, z, Block.STONE.id, 0);
        }

        assertTrue(world.a(false));
        assertEquals(1000, world.typeReads);
        assertEquals(1, pendingCount(world, 0, 0));

        assertFalse(world.a(false));
        assertEquals(1001, world.typeReads);
        assertNull(world.getPendingBlockTicksForChunk(0, 0));
    }

    @Test
    public void earlierCallbackCanBlockLaterCollectedOwnerWithoutLosingItsTick() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        world.returnedBlockId = Block.SAND.id;

        world.c(0, 64, 0, Block.SAND.id, 0);
        world.c(32, 64, 0, Block.SAND.id, 1);
        setLongField(world, "blockTickTime", 1L);
        // The first read dispatches BlockSand's callback; its below-block probe
        // performs the second read and models a callback unloading owner 2's halo.
        world.blockOwnerAfterTypeReads(1, 2, 0);

        assertTrue(world.a(false));

        assertNull(world.getPendingBlockTicksForChunk(0, 0));
        assertEquals(1, pendingCount(world, 2, 0));
        assertEquals(1L, firstPendingTick(world, 2, 0).e);
        assertEquals(2, world.typeReads);
    }

    @Test
    public void deferralPreservesScheduledTimeForChunkPersistence() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        world.blockOwner(0, 0);
        setLongField(world, "blockTickTime", 100L);

        world.c(7, 64, 7, Block.STONE.id, 7);
        assertEquals(107L, firstPendingTick(world, 0, 0).e);

        setLongField(world, "blockTickTime", 107L);
        assertTrue(world.a(false));

        assertEquals(107L, firstPendingTick(world, 0, 0).e);
    }

    private static int pendingCount(World world, int chunkX, int chunkZ) {
        List ticks = world.getPendingBlockTicksForChunk(chunkX, chunkZ);
        return ticks == null ? 0 : ticks.size();
    }

    private static NextTickListEntry firstPendingTick(World world, int chunkX, int chunkZ) {
        List ticks = world.getPendingBlockTicksForChunk(chunkX, chunkZ);
        return (NextTickListEntry) ticks.get(0);
    }

    private static boolean hasPendingTick(World world, int chunkX, int chunkZ, int blockId) {
        List ticks = world.getPendingBlockTicksForChunk(chunkX, chunkZ);

        if (ticks != null) {
            for (int index = 0; index < ticks.size(); ++index) {
                if (((NextTickListEntry) ticks.get(index)).d == blockId) {
                    return true;
                }
            }
        }

        return false;
    }

    private static final class RecordingWorld extends World {
        private Set blockedOwners;
        private boolean ownerLoaded;
        private int typeReads;
        private int returnedBlockId;
        private int readinessChecks;
        private int blockOwnerOnTypeReadX;
        private int blockOwnerOnTypeReadZ;
        private int blockOwnerOnTypeReadCountdown;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null, org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld create() throws Exception {
            RecordingWorld world = (RecordingWorld) unsafe().allocateInstance(RecordingWorld.class);
            world.blockedOwners = new HashSet();
            world.ownerLoaded = true;
            world.returnedBlockId = Block.STONE.id;
            world.blockOwnerOnTypeReadX = Integer.MIN_VALUE;
            world.random = new Random(0L);
            setField(world, "E", new TreeSet());
            setField(world, "F", new HashSet());
            setField(world, "scheduledTickChunkIndex", new HashMap());
            setField(world, "scheduledTickDirtyChunks", new HashSet());
            setField(world, "scheduledTickLastSavedTimes", new HashMap());
            return world;
        }

        void blockOwner(int chunkX, int chunkZ) {
            this.blockedOwners.add(Long.valueOf(chunkKey(chunkX, chunkZ)));
        }

        void unblockOwner(int chunkX, int chunkZ) {
            this.blockedOwners.remove(Long.valueOf(chunkKey(chunkX, chunkZ)));
        }

        void blockOwnerAfterTypeReads(int readsBeforeBlock, int chunkX, int chunkZ) {
            this.blockOwnerOnTypeReadCountdown = readsBeforeBlock;
            this.blockOwnerOnTypeReadX = chunkX;
            this.blockOwnerOnTypeReadZ = chunkZ;
        }

        @Override
        public boolean isLoaded(int x, int y, int z) {
            return this.ownerLoaded && y >= 0 && y < 128;
        }

        @Override
        public boolean a(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            ++this.readinessChecks;
            int centerX = minX + (maxX - minX) / 2;
            int centerZ = minZ + (maxZ - minZ) / 2;
            return !this.blockedOwners.contains(Long.valueOf(chunkKey(centerX >> 4, centerZ >> 4)));
        }

        @Override
        public int getTypeId(int x, int y, int z) {
            ++this.typeReads;
            if (this.blockOwnerOnTypeReadX != Integer.MIN_VALUE) {
                if (this.blockOwnerOnTypeReadCountdown == 0) {
                    this.blockOwner(this.blockOwnerOnTypeReadX, this.blockOwnerOnTypeReadZ);
                    this.blockOwnerOnTypeReadX = Integer.MIN_VALUE;
                } else {
                    --this.blockOwnerOnTypeReadCountdown;
                }
            }

            return this.returnedBlockId;
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 4294967295L) | (((long) chunkZ & 4294967295L) << 32);
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
