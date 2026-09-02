package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

public class WorldEntityTickLivenessTest {
    @BeforeClass
    public static void initializeLegacyRegistries() {
        StatisticList.a();
    }

    @Test
    public void loadedZombieKeepsTickingAndRemainsDamageableWithoutNeighborHalo() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        RecordingZombie zombie = attachedZombie(world, 8.5D, 8.5D);

        assertTrue("the visible mob remains punchable while neighbor chunks are unavailable",
                zombie.damageEntity(null, 1));
        world.entityJoinedWorld(zombie, true);

        assertEquals("a mob already owned by a loaded chunk must not freeze on neighbor readiness",
                1, zombie.updateCalls);
        assertEquals(1, zombie.damageCalls);
        assertEquals("the attached-owner fast path must not activate or probe the missing halo",
                0, world.haloChecks);
        assertEquals(0, world.provider.chunkRequests);
        assertTrue(zombie.bG);
    }

    @Test
    public void loadedEntityFinishesItsTickButIsNotAdmittedToAnUnloadedDestination() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        RecordingZombie zombie = attachedZombie(world, 8.5D, 8.5D);

        zombie.moveToX = 16.5D;
        world.entityJoinedWorld(zombie, true);

        assertEquals("the loaded owner grants exactly the current tick", 1, zombie.updateCalls);
        assertEquals(0, world.haloChecks);
        assertEquals(1, world.provider.ownerChunk.removals);
        assertEquals(0, world.provider.ownerChunk.additions);
        assertEquals("the missing destination must not be loaded as a side effect",
                0, world.provider.destinationChunkRequests);
        assertFalse("the entity must not be attached to an unloaded destination", zombie.bG);
        assertFalse(world.provider.isChunkLoaded(1, 0));

        world.entityJoinedWorld(zombie, true);

        assertEquals("an unattached entity waits for destination readiness on later ticks",
                1, zombie.updateCalls);
        assertEquals(1, world.haloChecks);
        assertEquals(0, world.provider.destinationChunkRequests);
    }

    @Test
    public void loadedVehicleAndPassengerKeepTheirSingleTickChainWithoutNeighborHalo() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        RecordingZombie vehicle = attachedZombie(world, 8.5D, 8.5D);
        RecordingZombie passenger = attachedZombie(world, 8.5D, 8.5D);
        vehicle.passenger = passenger;
        passenger.vehicle = vehicle;

        world.entityJoinedWorld(vehicle, true);

        assertEquals(1, vehicle.updateCalls);
        assertEquals(1, passenger.riddenUpdateCalls);
        assertEquals(1, passenger.updateCalls);
        assertEquals(0, world.haloChecks);
        assertEquals(0, world.provider.chunkRequests);
    }

    private static RecordingZombie attachedZombie(
            RecordingWorld world,
            double x,
            double z) {
        RecordingZombie zombie = new RecordingZombie(world);
        zombie.setPosition(x, 64.0D, z);
        zombie.bG = true;
        zombie.bH = MathHelper.floor(x / 16.0D);
        zombie.bI = 4;
        zombie.bJ = MathHelper.floor(z / 16.0D);
        return zombie;
    }

    private static final class RecordingZombie extends EntityZombie {
        private int updateCalls;
        private int damageCalls;
        private int riddenUpdateCalls;
        private double moveToX = Double.NaN;

        private RecordingZombie(World world) {
            super(world);
        }

        public void m_() {
            ++this.updateCalls;
            if (!Double.isNaN(this.moveToX)) {
                this.setPosition(this.moveToX, this.locY, this.locZ);
                this.moveToX = Double.NaN;
            }
        }

        public void E() {
            ++this.riddenUpdateCalls;
            super.E();
        }

        public boolean damageEntity(Entity attacker, int damage) {
            ++this.damageCalls;
            return true;
        }
    }

    private static final class RecordingWorld extends World {
        private ExistingOwnerProvider provider;
        private int haloChecks;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null, org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld create() throws Exception {
            RecordingWorld world = (RecordingWorld) unsafe().allocateInstance(RecordingWorld.class);
            world.provider = new ExistingOwnerProvider();
            world.chunkProvider = world.provider;
            return world;
        }

        public int getNextEntityId() {
            return World.getNextDetachedEntityId();
        }

        public Chunk getChunkAt(int chunkX, int chunkZ) {
            return this.provider.getOrCreateChunk(chunkX, chunkZ);
        }

        public boolean a(
                int minX,
                int minY,
                int minZ,
                int maxX,
                int maxY,
                int maxZ) {
            ++this.haloChecks;
            return false;
        }
    }

    private static final class ExistingOwnerProvider implements IChunkProvider {
        private int chunkRequests;
        private int destinationChunkRequests;
        private RecordingChunk ownerChunk;

        private ExistingOwnerProvider() throws Exception {
            this.ownerChunk = (RecordingChunk) unsafe().allocateInstance(RecordingChunk.class);
        }

        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return chunkX == 0 && chunkZ == 0;
        }

        public Chunk getOrCreateChunk(int chunkX, int chunkZ) {
            ++this.chunkRequests;
            if (this.isChunkLoaded(chunkX, chunkZ)) {
                return this.ownerChunk;
            }
            ++this.destinationChunkRequests;
            return null;
        }

        public Chunk getChunkAt(int chunkX, int chunkZ) {
            ++this.chunkRequests;
            if (this.isChunkLoaded(chunkX, chunkZ)) {
                return this.ownerChunk;
            }
            ++this.destinationChunkRequests;
            return null;
        }

        public void getChunkAt(IChunkProvider provider, int chunkX, int chunkZ) {
            ++this.chunkRequests;
        }

        public boolean saveChunks(boolean saveAll, IProgressUpdate progress) {
            return true;
        }

        public boolean unloadChunks() {
            return false;
        }

        public boolean canSave() {
            return true;
        }
    }

    private static final class RecordingChunk extends Chunk {
        private int removals;
        private int additions;

        private RecordingChunk() {
            super(null, 0, 0);
        }

        public void a(Entity entity, int slice) {
            ++this.removals;
        }

        public void a(Entity entity) {
            ++this.additions;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
