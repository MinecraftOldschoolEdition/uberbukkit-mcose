package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.server.registry.ItemRegistryBootstrap;
import net.minecraft.server.registry.ItemTagRegistryBootstrap;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

public class EntityWolfFoodDataParityTest {
    @BeforeClass
    public static void initializeWolfFoodTag() {
        assertTrue(Block.STONE != null);
        ItemRegistryBootstrap.initialize();
        ItemTagRegistryBootstrap.initialize();
    }

    @Test
    public void rawAndCookedPorkBothHealExactlyThreeForAnyPlayer()
            throws Exception {
        assertFeed(Item.PORK, 2, 1);
        assertFeed(Item.GRILLED_PORK, 1, 0);
    }

    @Test
    public void fullHealthAndNonTagFoodAreNotConsumed() throws Exception {
        RecordingWolf full = new RecordingWolf();
        full.setTamed(true);
        full.setOwnerName("owner");
        setSyncedHealth(full, 20);
        TestHuman guest = player("guest", new ItemStack(Item.PORK, 2));
        assertFalse(full.a(guest));
        assertEquals(2, guest.inventory.getItemInHand().count);
        assertEquals(0, full.healed);

        RecordingWolf fishWolf = new RecordingWolf();
        fishWolf.setTamed(true);
        fishWolf.setOwnerName("owner");
        setSyncedHealth(fishWolf, 10);
        TestHuman fishGuest = player(
                "guest", new ItemStack(Item.RAW_FISH, 2));
        assertFalse(fishWolf.a(fishGuest));
        assertEquals(2, fishGuest.inventory.getItemInHand().count);
        assertEquals(0, fishWolf.healed);
    }

    @Test
    public void interestUsesTagMembershipWithoutAHealthGate() throws Exception {
        assertTrue(EntityWolf.isWolfFoodStack(new ItemStack(Item.PORK)));
        assertTrue(EntityWolf.isWolfFoodStack(
                new ItemStack(Item.GRILLED_PORK)));
        assertFalse(EntityWolf.isWolfFoodStack(new ItemStack(Item.RAW_FISH)));
        assertFalse(EntityWolf.isWolfFoodStack(new ItemStack(Item.BONE)));
        assertFalse(EntityWolf.isWolfFoodStack(null));
    }

    private static void assertFeed(Item item, int count, int remaining)
            throws Exception {
        RecordingWolf wolf = new RecordingWolf();
        wolf.setTamed(true);
        wolf.setOwnerName("owner");
        setSyncedHealth(wolf, 10);
        TestHuman guest = player("guest", new ItemStack(item, count));

        assertTrue(wolf.a(guest));
        assertEquals(3, wolf.healed);
        assertEquals(RegainReason.EATING, wolf.reason);
        if (remaining == 0) {
            assertNull(guest.inventory.getItemInHand());
        } else {
            assertEquals(remaining,
                    guest.inventory.getItemInHand().count);
        }
    }

    private static TestHuman player(String name, ItemStack held)
            throws Exception {
        TestHuman player = (TestHuman)unsafe().allocateInstance(TestHuman.class);
        player.name = name;
        player.inventory = new InventoryPlayer(player);
        player.inventory.setItem(0, held);
        return player;
    }

    private static void setSyncedHealth(EntityWolf wolf, int health)
            throws Exception {
        wolf.health = health;
        Method method = EntityWolf.class.getDeclaredMethod(
                "setSyncedWolfHealth", Integer.TYPE);
        method.setAccessible(true);
        method.invoke(wolf, Integer.valueOf(health));
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }

    private static final class RecordingWolf extends EntityWolf {
        int healed;
        RegainReason reason;

        RecordingWolf() {
            super(null);
        }

        @Override
        public void b(int amount, RegainReason regainReason) {
            this.healed += amount;
            this.reason = regainReason;
        }
    }

    private static final class TestHuman extends EntityHuman {
        private TestHuman() {
            super(null);
        }
    }
}
