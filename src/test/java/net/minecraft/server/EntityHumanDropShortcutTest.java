package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

public class EntityHumanDropShortcutTest {
    @BeforeClass
    public static void initializeBlocksBeforeItems() {
        assertNotNull(Block.STONE);
    }

    @Test
    public void dropCurrentStackRemovesAndDropsTheCompleteSelection() throws Exception {
        TestHuman player = (TestHuman)unsafe().allocateInstance(TestHuman.class);
        player.inventory = new InventoryPlayer(player);
        player.inventory.setItem(0, new ItemStack(Block.STONE, 12));

        player.dropCurrentStack();

        assertNull(player.inventory.getItemInHand());
        assertEquals(12, player.dropped.count);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }

    private static final class TestHuman extends EntityHuman {
        ItemStack dropped;

        private TestHuman() {
            super(null);
        }

        public void a(ItemStack stack, boolean randomMotion) {
            this.dropped = stack;
        }
    }
}
