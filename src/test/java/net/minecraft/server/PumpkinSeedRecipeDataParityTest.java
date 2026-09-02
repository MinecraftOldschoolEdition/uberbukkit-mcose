package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;
import net.minecraft.server.registry.RecipeRegistryApi;
import net.minecraft.server.registry.RecipeRegistryBootstrap;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

/** Pins modern pumpkin-seed crafting without erasing legacy pumpkin stacks. */
public class PumpkinSeedRecipeDataParityTest {
    private static final ResourceLocation RECIPE =
            new ResourceLocation("minecraft", "crafting/pumpkin_seeds");

    @BeforeClass
    public static void initializeDataRecipes() {
        assertNotNull(Block.STONE);
        assertNotNull(Item.PUMPKIN_SEED);
        RecipeRegistryBootstrap.initialize();
    }

    @Test
    public void dedicatedPlainPumpkinRecipeHasExactDataIdentity() {
        CraftingRecipe raw = RecipeRegistryApi.get(RECIPE);
        assertTrue(raw instanceof ShapelessRecipes);
        assertSame(raw, RecipeRegistryBootstrap.dataRecipe(RECIPE));

        List<ItemStack> ingredients =
                ((ShapelessRecipes)raw).getRecipeIngredients();
        assertEquals(1, ingredients.size());
        assertStack(Block.PUMPKIN_PLAIN.id, 1, 0, ingredients.get(0));
        assertStack(Item.PUMPKIN_SEED.id, 4, 0, raw.b());
    }

    @Test
    public void plainDedicatedAndLegacyPumpkinsCraftButDedicatedCarvedDoesNot() {
        assertSeedResult(craft(new ItemStack(
                Block.PUMPKIN_PLAIN.id, 1, 0)));
        assertNull(craft(new ItemStack(
                Block.PUMPKIN_PLAIN.id, 1, 1)));
        assertNull(craft(new ItemStack(
                Block.CARVED_PUMPKIN.id, 1, 0)));

        assertSeedResult(craft(new ItemStack(Block.PUMPKIN.id, 1, 0)));
        assertSeedResult(craft(new ItemStack(Block.PUMPKIN.id, 1, 1)));
    }

    @Test
    public void dedicatedPumpkinsKeepTheirOwnDropIdentities() {
        Random random = new Random(0L);
        assertEquals(Block.PUMPKIN_PLAIN.id,
                Block.PUMPKIN_PLAIN.a(0, random));
        assertEquals(0, Block.PUMPKIN_PLAIN.a_(0));
        assertEquals(Block.CARVED_PUMPKIN.id,
                Block.CARVED_PUMPKIN.a(0, random));
        assertEquals(0, Block.CARVED_PUMPKIN.a_(0));

        assertEquals(Block.PUMPKIN.id, Block.PUMPKIN.a(4, random));
        assertEquals(0, Block.PUMPKIN.a_(0));
        assertEquals(1, Block.PUMPKIN.a_(4));
    }

    private static ItemStack craft(ItemStack ingredient) {
        InventoryCrafting grid = new InventoryCrafting(new Container() {
            public boolean b(EntityHuman player) {
                return true;
            }
        }, 1, 1);
        grid.setItem(0, ingredient);
        return CraftingManager.getInstance().craft(grid);
    }

    private static void assertSeedResult(ItemStack result) {
        assertStack(Item.PUMPKIN_SEED.id, 4, 0, result);
    }

    private static void assertStack(
            int itemId, int count, int metadata, ItemStack stack) {
        assertNotNull(stack);
        assertEquals(itemId, stack.id);
        assertEquals(count, stack.count);
        assertEquals(metadata, stack.getData());
    }
}
