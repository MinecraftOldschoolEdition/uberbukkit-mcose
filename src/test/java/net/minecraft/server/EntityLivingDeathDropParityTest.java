package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.bukkit.Material;
import org.junit.BeforeClass;
import org.junit.Test;

public class EntityLivingDeathDropParityTest {
    @BeforeClass
    public static void initializeLegacyRegistries() {
        StatisticList.a();
    }

    @Test
    public void bukkitDeathDropsUseTheExactBetaEntityPosition() {
        RecordingLiving entity = new RecordingLiving();

        entity.spawnDeathDropAtEntityPosition(
                new org.bukkit.inventory.ItemStack(Material.IRON_INGOT, 3, (short) 2)
        );

        assertEquals(Item.IRON_INGOT.id, entity.dropped.id);
        assertEquals(3, entity.dropped.count);
        assertEquals(2, entity.dropped.getData());
        assertEquals("Beta entityDropItem uses no extra death-position offset", 0.0F, entity.offset, 0.0F);
    }

    @Test
    public void nullEventEntriesDoNotCreateEntities() {
        RecordingLiving entity = new RecordingLiving();

        assertNull(entity.spawnDeathDropAtEntityPosition(null));
        assertNull(entity.dropped);
    }

    @Test
    public void eventDropCountsKeepTheExistingCraftBukkitContract() {
        RecordingLiving zero = new RecordingLiving();
        zero.spawnDeathDropAtEntityPosition(
                new org.bukkit.inventory.ItemStack(Material.IRON_INGOT, 0));
        assertEquals(0, zero.dropped.count);

        RecordingLiving negative = new RecordingLiving();
        negative.spawnDeathDropAtEntityPosition(
                new org.bukkit.inventory.ItemStack(Material.IRON_INGOT, -1));
        assertEquals(-1, negative.dropped.count);
    }

    private static final class RecordingLiving extends EntityLiving {
        ItemStack dropped;
        float offset = Float.NaN;

        RecordingLiving() {
            super(null);
        }

        public EntityItem a(ItemStack stack, float yOffset) {
            this.dropped = stack;
            this.offset = yOffset;
            return null;
        }
    }
}
