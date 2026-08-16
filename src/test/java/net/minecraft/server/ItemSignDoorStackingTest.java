package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.BeforeClass;
import org.junit.Test;

public class ItemSignDoorStackingTest {
    @BeforeClass
    public static void initializeBlocksBeforeItems() {
        assertNotNull(Block.STONE);
    }

    @Test
    public void signsUseModernStackLimit() {
        ItemStack sign = new ItemStack(Item.SIGN);

        assertEquals(16, sign.getMaxStackSize());
        assertEquals(16, sign.getNetworkMaxStackSize());
    }

    @Test
    public void doorsUseModernStackLimit() {
        assertDoorStackLimit(Item.WOOD_DOOR);
        assertDoorStackLimit(Item.IRON_DOOR);
    }

    private static void assertDoorStackLimit(Item door) {
        ItemStack stack = new ItemStack(door);

        assertEquals(64, stack.getMaxStackSize());
        assertEquals(64, stack.getNetworkMaxStackSize());
    }
}
