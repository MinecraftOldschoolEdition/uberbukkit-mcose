package net.minecraft.server;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import org.junit.Test;
import sun.misc.Unsafe;

public class PlayerWaterFallDamageTest {
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
    public void nonPlayerLivingEntitiesKeepLegacyFallHandling() throws Exception {
        TestLiving entity = (TestLiving) unsafe().allocateInstance(TestLiving.class);
        entity.fallDistance = 12.0F;

        entity.applyFallState(0.0D, true);

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

    private static final class TestLiving extends EntityLiving {
        private int fallCalls;
        private float lastFallDistance;

        private TestLiving() {
            super(null);
        }

        protected void a(float distance) {
            ++this.fallCalls;
            this.lastFallDistance = distance;
        }

        private void applyFallState(double yMotion, boolean onGround) {
            this.a(yMotion, onGround);
        }
    }
}
