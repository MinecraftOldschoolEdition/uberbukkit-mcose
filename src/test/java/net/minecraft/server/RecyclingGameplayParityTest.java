package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import net.minecraft.server.registry.RecipeRegistryBootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public class RecyclingGameplayParityTest {
    @BeforeClass
    public static void initializeData() {
        StatisticList.a();
        RecipeRegistryBootstrap.initialize();
    }

    @Test
    public void craftingUsesExactUnclampedFloatFloorFormulaAndOccupiedSlots() {
        RecyclingManager manager = RecyclingManager.getInstance();
        int maxDamage = Item.WOOD_PICKAXE.e();

        ItemStack full = manager.getRecycleResult(inventory(
                new ItemStack(Item.WOOD_PICKAXE, 23, 0)));
        assertStack(Block.WOOD.id, 3, 0, full);

        int damage = maxDamage / 2;
        ItemStack partial = manager.getRecycleResult(inventory(
                new ItemStack(Item.WOOD_PICKAXE, 23, damage)));
        int expected = (int)Math.floor(
                3.0F * (float)(maxDamage - damage) / (float)maxDamage);
        assertStack(Block.WOOD.id, expected, 0, partial);

        // Negative legacy damage remains deliberately unclamped, exactly as
        // the old manager calculated it.
        ItemStack overFull = manager.getRecycleResult(inventory(
                new ItemStack(Item.WOOD_PICKAXE, 23, -maxDamage)));
        assertStack(Block.WOOD.id, 6, 0, overFull);

        ItemStack exhausted = manager.getRecycleResult(inventory(
                new ItemStack(Item.WOOD_PICKAXE, 23, maxDamage)));
        assertNull(exhausted);

        assertNull(manager.getRecycleResult(inventory(
                new ItemStack(Item.WOOD_PICKAXE),
                new ItemStack(Item.STICK))));
    }

    @Test
    public void furnaceEventReplacementStillConsumesWholeInputStack() {
        RecordingFurnace furnace = new RecordingFurnace();
        ItemStack source = new ItemStack(Item.IRON_PICKAXE, 17, 0);
        furnace.setItem(0, source);
        furnace.replacement = new ItemStack(Item.DIAMOND, 2, 0);

        furnace.burn();

        assertEquals(1, furnace.eventCalls);
        assertSame(source, furnace.eventSource);
        assertStack(Item.IRON_INGOT.id, 3, 0, furnace.proposedResult);
        assertStack(Item.DIAMOND.id, 2, 0, furnace.getItem(2));
        assertNull("Legacy recycling must consume the whole input stack",
                furnace.getItem(0));
    }

    @Test
    public void cancelledFurnaceEventLeavesRecyclingInputUntouched() {
        RecordingFurnace furnace = new RecordingFurnace();
        ItemStack source = new ItemStack(Item.IRON_PICKAXE, 9, 0);
        furnace.setItem(0, source);
        furnace.cancel = true;

        furnace.burn();

        assertEquals(1, furnace.eventCalls);
        assertSame(source, furnace.getItem(0));
        assertEquals(9, furnace.getItem(0).count);
        assertNull(furnace.getItem(2));
    }

    private static InventoryCrafting inventory(ItemStack... stacks) {
        InventoryCrafting inventory = new InventoryCrafting(new Container() {
            public boolean b(EntityHuman player) {
                return true;
            }
        }, stacks.length, 1);
        for (int i = 0; i < stacks.length; i++) inventory.setItem(i, stacks[i]);
        return inventory;
    }

    private static void assertStack(
            int id,
            int count,
            int metadata,
            ItemStack actual) {
        assertNotNull(actual);
        assertEquals(id, actual.id);
        assertEquals(count, actual.count);
        assertEquals(metadata, actual.getData());
    }

    private static final class RecordingFurnace extends TileEntityFurnace {
        int eventCalls;
        boolean cancel;
        ItemStack replacement;
        ItemStack eventSource;
        ItemStack proposedResult;

        protected ItemStack fireFurnaceSmeltEvent(
                ItemStack sourceStack,
                ItemStack proposed) {
            this.eventCalls++;
            this.eventSource = sourceStack;
            this.proposedResult = proposed.cloneItemStack();
            if (this.cancel) return null;
            return this.replacement == null
                    ? proposed.cloneItemStack()
                    : this.replacement.cloneItemStack();
        }
    }
}
