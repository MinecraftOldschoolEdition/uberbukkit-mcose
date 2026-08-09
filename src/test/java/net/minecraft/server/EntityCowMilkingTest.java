package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

public class EntityCowMilkingTest {
    @BeforeClass
    public static void initializeBlocksBeforeItems() {
        assertNotNull(Block.STONE);
    }

    @Test
    public void milkingStackedBucketsKeepsTheRemainingEmptyBuckets() throws Exception {
        TestHuman player = (TestHuman) unsafe().allocateInstance(TestHuman.class);
        player.inventory = new InventoryPlayer(player);
        ItemStack buckets = new ItemStack(Item.BUCKET, 16);
        player.inventory.setItem(0, buckets);

        EntityCow.giveMilkBucket(player, buckets, new ItemStack(Item.MILK_BUCKET));

        assertEquals(Item.BUCKET.id, player.inventory.getItemInHand().id);
        assertEquals(15, player.inventory.getItemInHand().count);
        assertTrue(containsMilkBucket(player.inventory));
    }

    private static boolean containsMilkBucket(InventoryPlayer inventory) {
        for (ItemStack stack : inventory.items) {
            if (stack != null && stack.id == Item.MILK_BUCKET.id && stack.count == 1) {
                return true;
            }
        }
        return false;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class TestHuman extends EntityHuman {
        private TestHuman() {
            super(null);
        }
    }
}
