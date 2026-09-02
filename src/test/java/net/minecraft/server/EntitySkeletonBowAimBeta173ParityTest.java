package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Random;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

public class EntitySkeletonBowAimBeta173ParityTest {
    private static final double EPSILON = 0.000000001D;
    private static final long HEADING_RANDOM_SEED = 0xB173L;

    @BeforeClass
    public static void initializeLegacyRegistries() {
        StatisticList.a();
    }

    @Test
    public void arrowTrajectoryMatchesBeta173ProductionOrderAndInputs() throws Exception {
        Shot shot = fireShot(false);
        EntitySkeleton skeleton = shot.skeleton;
        EntityZombie target = shot.target;
        EntityArrow arrow = shot.world.spawnedArrow;

        assertNotNull(arrow);
        assertNotSame(Entity.SHARED_RANDOM, arrow.random);
        double constructorY = skeleton.locY + (double) skeleton.t()
                - (double) 0.1F;
        assertEquals(constructorY + (double) 1.4F, arrow.locY, EPSILON);

        double targetX = target.locX - skeleton.locX;
        double targetZ = target.locZ - skeleton.locZ;
        double targetHeight = target.locY + (double) target.t()
                - (double) 0.2F - arrow.locY;
        double compensation = (double) (MathHelper.a(
                targetX * targetX + targetZ * targetZ) * 0.2F);
        double[] expectedMotion = expectedMotion(
                targetX,
                targetHeight + compensation,
                targetZ,
                0.6F,
                12.0F);

        assertEquals(expectedMotion[0], arrow.motX, EPSILON);
        assertEquals(expectedMotion[1], arrow.motY, EPSILON);
        assertEquals(expectedMotion[2], arrow.motZ, EPSILON);
        assertTrue(shot.world.randomSeededAtSpawn);
        assertEquals(30, skeleton.attackTicks);
    }

    @Test
    public void unrelatedEntityRandomTrafficCannotAlterProjectileTrajectory()
            throws Exception {
        Shot quiet = fireShot(false);
        Shot noisy = fireShot(true);

        assertEquals(quiet.world.spawnedArrow.motX,
                noisy.world.spawnedArrow.motX, 0.0D);
        assertEquals(quiet.world.spawnedArrow.motY,
                noisy.world.spawnedArrow.motY, 0.0D);
        assertEquals(quiet.world.spawnedArrow.motZ,
                noisy.world.spawnedArrow.motZ, 0.0D);
    }

    private static Shot fireShot(boolean consumeUnrelatedEntityRandom)
            throws Exception {
        RecordingWorld world = RecordingWorld.create(
                consumeUnrelatedEntityRandom);
        EntitySkeleton skeleton = new EntitySkeleton(world);
        EntityZombie target = new EntityZombie(world);
        skeleton.setPosition(4.0D, 64.0D, 8.0D);
        target.setPosition(9.5D, 66.25D, 2.75D);
        skeleton.attackTicks = 0;

        skeleton.a(target, 7.6F);
        return new Shot(world, skeleton, target);
    }

    private static double[] expectedMotion(
            double x,
            double y,
            double z,
            float speed,
            float inaccuracy) {
        float magnitude = MathHelper.a(x * x + y * y + z * z);
        x /= (double) magnitude;
        y /= (double) magnitude;
        z /= (double) magnitude;
        Random random = new Random(HEADING_RANDOM_SEED);
        x += random.nextGaussian() * 0.007499999832361937D
                * (double) inaccuracy;
        y += random.nextGaussian() * 0.007499999832361937D
                * (double) inaccuracy;
        z += random.nextGaussian() * 0.007499999832361937D
                * (double) inaccuracy;
        return new double[] {
                x * (double) speed,
                y * (double) speed,
                z * (double) speed
        };
    }

    private static final class Shot {
        private final RecordingWorld world;
        private final EntitySkeleton skeleton;
        private final EntityZombie target;

        private Shot(
                RecordingWorld world,
                EntitySkeleton skeleton,
                EntityZombie target) {
            this.world = world;
            this.skeleton = skeleton;
            this.target = target;
        }
    }

    private static final class RecordingWorld extends World {
        private boolean consumeUnrelatedEntityRandom;
        private EntityArrow spawnedArrow;
        private boolean randomSeededAtSpawn;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null,
                    org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld create(boolean consumeUnrelatedEntityRandom)
                throws Exception {
            RecordingWorld world = (RecordingWorld) unsafe()
                    .allocateInstance(RecordingWorld.class);
            world.consumeUnrelatedEntityRandom = consumeUnrelatedEntityRandom;
            return world;
        }

        public int getNextEntityId() {
            return World.getNextDetachedEntityId();
        }

        public void makeSound(
                Entity entity, String sound, float volume, float pitch) {
        }

        public void e(int effectId, int x, int y, int z, int data) {
        }

        public boolean addEntity(Entity entity) {
            if (entity instanceof EntityArrow) {
                this.spawnedArrow = (EntityArrow) entity;
                // Seed the production Random in place. The test must not
                // replace the arrow's RNG with a test double.
                this.spawnedArrow.random.setSeed(HEADING_RANDOM_SEED);
                this.randomSeededAtSpawn = true;
                if (this.consumeUnrelatedEntityRandom) {
                    EntityZombie unrelated = new EntityZombie(this);
                    for (int i = 0; i < 32; ++i) {
                        unrelated.random.nextLong();
                    }
                }
            }
            return true;
        }

        public void a(Entity entity, byte status) {
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
