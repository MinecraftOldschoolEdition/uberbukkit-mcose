package net.minecraft.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class ItemHoeGrassSeedsGameRuleTest {
    @Test
    public void onlyEnabledGrassWithTheLegacyOneInEightRollDropsSeeds() {
        assertFalse(ItemHoe.shouldDropGrassSeeds(Block.GRASS.id, false, new FixedRandom(0)));
        assertFalse(ItemHoe.shouldDropGrassSeeds(Block.DIRT.id, true, new FixedRandom(0)));
        assertFalse(ItemHoe.shouldDropGrassSeeds(Block.GRASS.id, true, new FixedRandom(7)));
        assertTrue(ItemHoe.shouldDropGrassSeeds(Block.GRASS.id, true, new FixedRandom(0)));
    }

    private static final class FixedRandom extends Random {
        private final int value;

        FixedRandom(int value) {
            this.value = value;
        }

        public int nextInt(int bound) {
            if (this.value < 0 || this.value >= bound) {
                throw new AssertionError("fixed random value " + this.value + " outside bound " + bound);
            }
            return this.value;
        }
    }
}
