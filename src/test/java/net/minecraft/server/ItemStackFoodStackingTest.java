package net.minecraft.server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ItemStackFoodStackingTest {
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
}
