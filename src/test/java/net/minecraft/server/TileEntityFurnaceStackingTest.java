package net.minecraft.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import org.junit.BeforeClass;
import org.junit.Test;

public class TileEntityFurnaceStackingTest {
    @BeforeClass
    public static void initializeBlocksBeforeItems() {
        assertNotNull(Block.STONE);
    }

    @Test
    public void foodOutputStacksTo64IndependentlyOfWorldFoodRules() throws Exception {
        TileEntityFurnace furnace = new TileEntityFurnace();
        furnace.setItem(0, new ItemStack(Item.PORK, 1));
        furnace.setItem(2, new ItemStack(Item.GRILLED_PORK, 63));

        assertTrue(canBurn(furnace));

        furnace.setItem(2, new ItemStack(Item.GRILLED_PORK, 64));
        assertFalse(canBurn(furnace));
    }

    @Test
    public void everyRegisteredSmeltRecycleFitsItsMaterialOutput() throws Exception {
        for (Item recyclable : smeltRecyclables()) {
            ItemStack input = new ItemStack(recyclable, 1);
            ItemStack result = RecyclingManager.getInstance().getSmeltRecycleResult(input);
            assertNotNull(String.valueOf(recyclable.id), result);

            TileEntityFurnace furnace = new TileEntityFurnace();
            furnace.setItem(0, input);
            furnace.setItem(2, new ItemStack(result.id, 64 - result.count, result.getData()));
            assertTrue(String.valueOf(recyclable.id), canBurn(furnace));

            furnace.setItem(2, new ItemStack(result.id, 65 - result.count, result.getData()));
            assertFalse(String.valueOf(recyclable.id), canBurn(furnace));
        }
    }

    private static boolean canBurn(TileEntityFurnace furnace) throws Exception {
        Method method = TileEntityFurnace.class.getDeclaredMethod("canBurn");
        method.setAccessible(true);
        return ((Boolean)method.invoke(furnace)).booleanValue();
    }

    private static Item[] smeltRecyclables() {
        return new Item[] {
            Item.STONE_PICKAXE, Item.IRON_PICKAXE, Item.DIAMOND_PICKAXE, Item.GOLD_PICKAXE,
            Item.STONE_AXE, Item.IRON_AXE, Item.DIAMOND_AXE, Item.GOLD_AXE,
            Item.STONE_SPADE, Item.IRON_SPADE, Item.DIAMOND_SPADE, Item.GOLD_SPADE,
            Item.STONE_HOE, Item.IRON_HOE, Item.DIAMOND_HOE, Item.GOLD_HOE,
            Item.STONE_SWORD, Item.IRON_SWORD, Item.DIAMOND_SWORD, Item.GOLD_SWORD,
            Item.IRON_HELMET, Item.IRON_CHESTPLATE, Item.IRON_LEGGINGS, Item.IRON_BOOTS,
            Item.DIAMOND_HELMET, Item.DIAMOND_CHESTPLATE, Item.DIAMOND_LEGGINGS, Item.DIAMOND_BOOTS,
            Item.GOLD_HELMET, Item.GOLD_CHESTPLATE, Item.GOLD_LEGGINGS, Item.GOLD_BOOTS,
            Item.SHEARS, Item.FLINT_AND_STEEL
        };
    }
}
