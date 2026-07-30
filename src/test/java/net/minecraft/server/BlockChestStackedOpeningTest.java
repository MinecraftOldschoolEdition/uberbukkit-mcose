package net.minecraft.server;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockChestStackedOpeningTest {
    @Test
    public void chestDirectlyAboveDoesNotBlockOpening() {
        assertFalse(BlockChest.blocksChestOpening(
                Block.CHEST.id,
                Block.CHEST.id,
                true
        ));
    }

    @Test
    public void ordinarySolidBlockAboveStillBlocksOpening() {
        assertTrue(BlockChest.blocksChestOpening(
                Block.STONE.id,
                Block.CHEST.id,
                true
        ));
    }

    @Test
    public void nonSolidBlockAboveDoesNotBlockOpening() {
        assertFalse(BlockChest.blocksChestOpening(
                Block.GLASS.id,
                Block.CHEST.id,
                false
        ));
    }
}
