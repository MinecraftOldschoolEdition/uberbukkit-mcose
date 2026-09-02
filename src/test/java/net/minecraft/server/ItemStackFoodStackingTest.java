package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.BeforeClass;
import org.junit.Test;

public class ItemStackFoodStackingTest {
    @BeforeClass
    public static void initializeItems() {
        assertNotNull(Block.STONE);
    }

    @Test
    public void foodUsesLegacyLimitUntilRuleIsEnabled() {
        ItemStack apple = new ItemStack(Item.APPLE);
        assertEquals(1, apple.getMaxStackSize(false));
        assertEquals(4, apple.getMaxStackSize(true));
        assertEquals(4, apple.getNetworkMaxStackSize());
    }

    @Test
    public void enabledRuleCapsAlreadyStackableFoodAtFour() {
        ItemStack cookie = new ItemStack(Item.COOKIE);
        assertEquals(8, cookie.getMaxStackSize(false));
        assertEquals(4, cookie.getMaxStackSize(true));
        assertEquals(8, cookie.getNetworkMaxStackSize());
    }

    @Test
    public void explicitFoodMaxStackSetOrRemovalOverridesTheLegacyRule() {
        ItemStack explicitlyLarge = new ItemStack(Item.APPLE);
        explicitlyLarge.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, Integer.valueOf(99))
                .build());
        assertEquals(99, explicitlyLarge.getMaxStackSize(false));
        assertEquals(99, explicitlyLarge.getMaxStackSize(true));
        assertEquals(99, explicitlyLarge.getNetworkMaxStackSize());

        ItemStack explicitlyRemoved = new ItemStack(Item.APPLE);
        explicitlyRemoved.applyComponents(DataComponentPatch.builder()
                .remove(DataComponents.MAX_STACK_SIZE)
                .build());
        assertEquals(1, explicitlyRemoved.getMaxStackSize(false));
        assertEquals(1, explicitlyRemoved.getMaxStackSize(true));
        assertEquals(1, explicitlyRemoved.getNetworkMaxStackSize());
    }
}
