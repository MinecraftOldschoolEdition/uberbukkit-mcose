package net.minecraft.server.registry;

import net.minecraft.server.CraftingRecipe;
import net.minecraft.server.ShapedRecipes;
import net.minecraft.server.ShapelessRecipes;
import net.minecraft.server.FurnaceRecipes;

/**
 * Represents a type of recipe (crafting, smelting, etc.).
 * Modeled after Fabric API's RecipeType registry.
 * 
 * @param <T> The recipe interface type this recipe type handles
 */
public class RecipeType<T> {
    private final String name;
    
    public RecipeType(String name) {
        this.name = name;
    }
    
    public String getName() {
        return this.name;
    }
    
    @Override
    public String toString() {
        return "RecipeType{" + name + "}";
    }
    
    // Built-in recipe types for Beta 1.7.3
    public static final RecipeType<ShapedRecipes> CRAFTING_SHAPED = new RecipeType<ShapedRecipes>("crafting_shaped");
    public static final RecipeType<ShapelessRecipes> CRAFTING_SHAPELESS = new RecipeType<ShapelessRecipes>("crafting_shapeless");
    public static final RecipeType<FurnaceRecipes> SMELTING = new RecipeType<FurnaceRecipes>("smelting");
}

