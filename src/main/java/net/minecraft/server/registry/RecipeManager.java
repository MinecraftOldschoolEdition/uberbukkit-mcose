package net.minecraft.server.registry;

import net.minecraft.server.CraftingManager;
import net.minecraft.server.CraftingRecipe;
import net.minecraft.server.FurnaceRecipes;
import net.minecraft.server.InventoryCrafting;
import net.minecraft.server.ItemStack;
import net.minecraft.server.util.ResourceLocation;

import java.util.List;

/**
 * Central recipe registration and lookup manager.
 */
public final class RecipeManager {
    private RecipeManager() {}

    public static ResourceLocation registerShaped(ResourceLocation key, ItemStack output, Object... recipeShapeAndMappings) {
        if (output == null) {
            return null;
        }

        CraftingManager manager = CraftingManager.getInstance();
        int before = manager.b().size();
        manager.registerShapedRecipe(output, recipeShapeAndMappings);
        CraftingRecipe recipe = getLastRecipe(manager, before);
        if (recipe == null) {
            return null;
        }
        ResourceLocation resolved = key == null ? RecipeRegistryApi.generateRecipeId(recipe) : key;
        RecipeRegistryApi.register(resolved, recipe);
        return resolved;
    }

    public static ResourceLocation registerShapeless(ResourceLocation key, ItemStack output, Object... inputs) {
        if (output == null) {
            return null;
        }

        CraftingManager manager = CraftingManager.getInstance();
        int before = manager.b().size();
        manager.registerShapelessRecipe(output, inputs);
        CraftingRecipe recipe = getLastRecipe(manager, before);
        if (recipe == null) {
            return null;
        }
        ResourceLocation resolved = key == null ? RecipeRegistryApi.generateRecipeId(recipe) : key;
        RecipeRegistryApi.register(resolved, recipe);
        return resolved;
    }

    public static ResourceLocation registerSmelting(ResourceLocation key, int inputItemId, ItemStack output) {
        if (output == null || inputItemId < 0) {
            return null;
        }

        FurnaceRecipes.getInstance().registerRecipe(inputItemId, output.cloneItemStack());
        SmeltingRecipe recipe = new SmeltingRecipe(inputItemId, output.cloneItemStack());
        ResourceLocation resolved = key == null ? new ResourceLocation("minecraft", "smelting/item_" + inputItemId) : key;
        RecipeRegistryApi.register(resolved, recipe);
        return resolved;
    }

    public static ItemStack findCraftingResult(InventoryCrafting inventory) {
        return CraftingManager.getInstance().craft(inventory);
    }

    public static ItemStack getSmeltingResult(int inputItemId) {
        return FurnaceRecipes.getInstance().a(inputItemId);
    }

    private static CraftingRecipe getLastRecipe(CraftingManager manager, int sizeBefore) {
        List list = manager.b();
        if (list == null || list.size() <= sizeBefore || list.size() == 0) {
            return null;
        }
        return (CraftingRecipe) list.get(list.size() - 1);
    }
}
