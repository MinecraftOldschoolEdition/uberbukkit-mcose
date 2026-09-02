package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

public class PlayerWaterFallDamageTest {
    @BeforeClass
    public static void initializeLegacyStaticsInProductionOrder() {
        StatisticList.a();
        assertTrue(Block.STONE != null);
        assertTrue(Item.STICK != null);
    }

    @Test
    public void networkPlayerFallDistanceIsClearedWhileInWater() throws Exception {
        TestPlayer player = (TestPlayer) unsafe().allocateInstance(TestPlayer.class);
        player.touchingWater = true;
        player.fallDistance = 12.0F;

        player.applyNetworkFallState(-1.0D, true);

        assertEquals(0.0F, player.fallDistance, 0.0F);
        assertEquals(0, player.fallCalls);
    }

    @Test
    public void networkPlayerStillTakesLegacyFallDamageOutsideWater() throws Exception {
        TestPlayer player = (TestPlayer) unsafe().allocateInstance(TestPlayer.class);
        player.fallDistance = 12.0F;

        player.applyNetworkFallState(0.0D, true);

        assertEquals(1, player.fallCalls);
        assertEquals(12.0F, player.lastFallDistance, 0.0F);
    }

    @Test
    public void shallowWaterEntryIsPreservedUntilOneLanding() throws Exception {
        TestEntity entity = new TestEntity();
        entity.world = (World) unsafe().allocateInstance(World.class);
        entity.touchingWater = true;
        entity.waterDepth = 2.499D;
        entity.motY = -1.0D;
        entity.fallDistance = 12.0F;

        entity.applyEntityUpdate();
        assertEquals(0.0F, entity.fallDistance, 0.0F);
        assertEquals(0, entity.fallCalls);

        entity.applyFallState(-0.35D, false);
        entity.applyEntityUpdate();
        assertEquals(0.0F, entity.fallDistance, 0.0F);

        entity.world = null;
        entity.applyFallState(0.0D, true);
        entity.applyFallState(0.0D, true);

        assertEquals(0.0F, entity.fallDistance, 0.0F);
        assertEquals(1, entity.fallCalls);
        assertEquals(12.0F, entity.lastFallDistance, 0.0F);
    }

    @Test
    public void legacyDeepWaterAlwaysClearsNonPlayerFallDistance() throws Exception {
        TestEntity entity = (TestEntity) unsafe().allocateInstance(TestEntity.class);
        entity.world = (World) unsafe().allocateInstance(World.class);
        entity.bA = true;
        entity.touchingWater = true;
        entity.waterDepth = 2.5D;
        entity.fallDistance = 12.0F;

        entity.applyEntityUpdate();

        assertEquals(0.0F, entity.fallDistance, 0.0F);
        assertEquals(0, entity.fallCalls);
    }

    @Test
    public void legacyDeepWaterAlsoClearsOnTheLandingTick() throws Exception {
        TestEntity entity = (TestEntity) unsafe().allocateInstance(TestEntity.class);
        entity.touchingWater = true;
        entity.waterDepth = 2.5D;
        entity.fallDistance = 12.0F;

        entity.applyFallState(0.0D, true);

        assertEquals(0.0F, entity.fallDistance, 0.0F);
        assertEquals(0, entity.fallCalls);
    }

    @Test
    public void legacyDepthBoundarySeparatesTwoAndThreeSourceBlocks() {
        assertFalse(Entity.calculateWaterDepthForFallDamage(0, 2) >= 2.5D);
        assertTrue(Entity.calculateWaterDepthForFallDamage(0, 3) >= 2.5D);
        assertTrue(Entity.calculateWaterDepthForFallDamage(3, 3) >= 2.5D);
        assertFalse(Entity.calculateWaterDepthForFallDamage(4, 3) >= 2.5D);
    }

    @Test
    public void shallowWaterLandingTickAppliesEntryFallOnce() throws Exception {
        TestEntity entity = (TestEntity) unsafe().allocateInstance(TestEntity.class);
        entity.touchingWater = true;
        entity.waterDepth = 2.499D;
        entity.fallDistance = 12.0F;

        entity.applyFallState(0.0D, true);
        entity.applyFallState(0.0D, true);

        assertEquals(0.0F, entity.fallDistance, 0.0F);
        assertEquals(1, entity.fallCalls);
        assertEquals(12.0F, entity.lastFallDistance, 0.0F);
    }

    @Test
    public void repeatedShallowWaterTicksThenExitDoNotLeakEntryFallDistance() throws Exception {
        TestEntity entity = new TestEntity();
        entity.world = (World) unsafe().allocateInstance(World.class);
        entity.touchingWater = true;
        entity.waterDepth = 2.499D;
        entity.motY = -1.0D;
        entity.fallDistance = 12.0F;

        entity.applyEntityUpdate();
        entity.applyFallState(-0.35D, false);
        entity.applyEntityUpdate();
        assertEquals(0.0F, entity.fallDistance, 0.0F);
        entity.applyFallState(0.04D, false);
        entity.applyFallState(0.0D, true);
        assertEquals(0, entity.fallCalls);

        entity.touchingWater = false;
        entity.applyEntityUpdate();
        entity.applyFallState(-0.75D, false);
        entity.world = null;
        entity.applyFallState(0.0D, true);

        assertEquals(0.0F, entity.fallDistance, 0.0F);
        assertEquals(1, entity.fallCalls);
        assertEquals(0.75F, entity.lastFallDistance, 0.0F);
    }

    @Test
    public void sameTickWaterExitDoesNotConsumePendingEntryFallOnShore() throws Exception {
        TestEntity entity = (TestEntity) unsafe().allocateInstance(TestEntity.class);
        entity.touchingWater = true;
        entity.waterDepth = 2.499D;
        entity.fallDistance = 12.0F;

        entity.applyFallState(-0.35D, false);
        assertEquals(0.0F, entity.fallDistance, 0.0F);

        // R sampled water before movement, but the post-move bounds have already
        // left it when move performs the shore landing update.
        entity.bA = true;
        entity.touchingWater = false;
        entity.applyFallState(0.0D, true);

        assertEquals(0.0F, entity.fallDistance, 0.0F);
        assertEquals(0, entity.fallCalls);
    }

    @Test
    public void edgeOnlyShoreOverlapDoesNotConsumePendingEntryFall() throws Exception {
        TestEntity entity = (TestEntity) unsafe().allocateInstance(TestEntity.class);
        entity.touchingWater = true;
        entity.waterDepth = 2.499D;
        entity.fallDistance = 12.0F;

        entity.applyFallState(-0.35D, false);
        assertEquals(0.0F, entity.fallDistance, 0.0F);

        // The bounds still clip the water block, but the landing/center column is
        // dry shore. The cached water-entry fall must not be resurrected here.
        entity.bA = true;
        entity.waterDepth = 0.0D;
        entity.applyFallState(0.0D, true);

        assertEquals(0.0F, entity.fallDistance, 0.0F);
        assertEquals(0, entity.fallCalls);
    }

    @Test
    public void freshHighFallOntoDryShoreStillDamagesWithEdgeWaterOverlap() throws Exception {
        TestEntity entity = new TestEntity();
        entity.world = (World) unsafe().allocateInstance(World.class);
        entity.touchingWater = true;
        entity.waterDepth = 0.0D;
        entity.motY = -1.0D;
        entity.fallDistance = 12.0F;

        entity.applyEntityUpdate();
        assertEquals(12.0F, entity.fallDistance, 0.0F);
        entity.world = null;
        entity.applyFallState(0.0D, true);

        assertEquals(0.0F, entity.fallDistance, 0.0F);
        assertEquals(1, entity.fallCalls);
        assertEquals(12.0F, entity.lastFallDistance, 0.0F);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class TestPlayer extends EntityPlayer {
        private boolean touchingWater;
        private int fallCalls;
        private float lastFallDistance;

        private TestPlayer() {
            super(null, null, null, null, 0);
        }

        protected boolean isInWaterForFallDamage() {
            return this.touchingWater;
        }

        protected void a(float distance) {
            ++this.fallCalls;
            this.lastFallDistance = distance;
        }

        private void applyNetworkFallState(double yMotion, boolean onGround) {
            this.b(yMotion, onGround);
        }
    }

    private static final class TestEntity extends Entity {
        private boolean touchingWater;
        private double waterDepth;
        private int fallCalls;
        private float lastFallDistance;

        private TestEntity() {
            super(null);
        }

        protected void b() {
        }

        protected void a(NBTTagCompound nbt) {
        }

        protected void b(NBTTagCompound nbt) {
        }

        public boolean f_() {
            return this.touchingWater;
        }

        public boolean ae() {
            return false;
        }

        protected boolean isPostMoveBoundingBoxInWaterForFallDamage() {
            return this.touchingWater;
        }

        protected double getWaterDepthForFallDamage() {
            return this.waterDepth;
        }

        protected void a(int flagIndex, boolean value) {
        }

        protected void a(float distance) {
            ++this.fallCalls;
            this.lastFallDistance = distance;
        }

        private void applyEntityUpdate() {
            this.R();
        }

        private void applyFallState(double yMotion, boolean onGround) {
            this.a(yMotion, onGround);
        }
    }
}
