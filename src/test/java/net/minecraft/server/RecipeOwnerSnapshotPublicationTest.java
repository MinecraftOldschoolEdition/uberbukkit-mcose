package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.BeforeClass;

public class RecipeOwnerSnapshotPublicationTest {
    @BeforeClass
    public static void initializeLegacyStaticsInProductionOrder() {
        assertTrue(Block.STONE != null);
        assertTrue(Item.STICK != null);
    }

    @Test
    public void craftingPublicationIsOneCopyOnWriteSwapWithPluginBeforeTail()
            throws Exception {
        CraftingManager manager = newCraftingManager();
        List builtIns = manager.getBuiltInRecipeSnapshot();
        manager.registerShapelessRecipe(
                new ItemStack(Item.STICK), new Object[] {Item.FEATHER});
        CraftingRecipe plugin = (CraftingRecipe)manager.b().get(manager.b().size() - 1);
        CraftingRecipe addition = new ShapelessRecipes(
                new ItemStack(Item.COAL),
                Collections.singletonList(new ItemStack(Item.STICK)));

        List before = manager.b();
        CraftingManager.RecipePublication publication =
                manager.prepareDataRecipePublication(
                        new ArrayList<CraftingRecipe>(builtIns),
                        Collections.singletonList(addition));
        assertSame(before, manager.b());

        manager.publishPreparedDataRecipes(publication);
        List after = manager.b();
        assertNotSame(before, after);
        assertEquals(builtIns.size() + 2, after.size());
        assertSame(plugin, after.get(after.size() - 2));
        assertSame(addition, after.get(after.size() - 1));
        assertUnmodifiable(after);
    }

    @Test
    public void furnacePublicationPreservesGenericAndExactPluginPrecedence()
            throws Exception {
        FurnaceRecipes furnace = newFurnaceRecipes();
        Map originalBuiltIns = furnace.b();
        Map<Integer, ItemStack> replacements = new HashMap<Integer, ItemStack>();
        for (Object raw : originalBuiltIns.entrySet()) {
            Map.Entry entry = (Map.Entry)raw;
            replacements.put((Integer)entry.getKey(),
                    ((ItemStack)entry.getValue()).cloneItemStack());
        }

        ItemStack genericOverride = new ItemStack(Item.COAL, 3, 1);
        ItemStack genericExtension = new ItemStack(Item.STICK, 2, 0);
        ItemStack exactOverride = new ItemStack(Item.DIAMOND, 1, 0);
        furnace.registerRecipe(Block.IRON_ORE.id, genericOverride);
        furnace.registerRecipe(Item.FEATHER.id, genericExtension);
        furnace.registerRecipe(Block.IRON_ORE.id, 3, exactOverride);

        Map beforeGeneric = furnace.b();
        Map<Integer, ItemStack> beforeExact = furnace.getExactRecipeSnapshot();
        FurnaceRecipes.FurnacePublication publication =
                furnace.prepareBuiltInRecipePublication(replacements);
        assertSame(beforeGeneric, furnace.b());
        assertFalse(publication.getAppliedInputIds().contains(
                Integer.valueOf(Block.IRON_ORE.id)));

        furnace.publishPreparedRecipes(publication);
        assertNotSame(beforeGeneric, furnace.b());
        assertSame(genericOverride, furnace.a(Block.IRON_ORE.id));
        assertSame(genericExtension, furnace.a(Item.FEATHER.id));
        assertSame(exactOverride,
                furnace.a(new ItemStack(Block.IRON_ORE, 1, 3)));
        assertEquals(beforeExact.keySet(), furnace.getExactRecipeSnapshot().keySet());
        assertUnmodifiable(furnace.b());
    }

    private static CraftingManager newCraftingManager() throws Exception {
        Constructor<CraftingManager> constructor =
                CraftingManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static FurnaceRecipes newFurnaceRecipes() throws Exception {
        Constructor<FurnaceRecipes> constructor =
                FurnaceRecipes.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void assertUnmodifiable(List values) {
        try {
            values.add(null);
            fail("Expected immutable recipe snapshot");
        } catch (UnsupportedOperationException expected) {}
    }

    private static void assertUnmodifiable(Map values) {
        try {
            values.put(Integer.valueOf(-1), null);
            fail("Expected immutable recipe snapshot");
        } catch (UnsupportedOperationException expected) {}
    }
}
