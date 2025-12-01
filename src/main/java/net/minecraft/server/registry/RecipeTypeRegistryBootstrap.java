package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

/**
 * Bootstrap for the Recipe Type registry.
 * Registers the built-in recipe types for Beta 1.7.3.
 */
public final class RecipeTypeRegistryBootstrap {
    private static boolean initialized = false;
    
    private RecipeTypeRegistryBootstrap() {}
    
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        
        // Register crafting recipe types
        Registries.RECIPE_TYPE.register(
            new ResourceLocation("minecraft", "crafting_shaped"),
            RecipeType.CRAFTING_SHAPED
        );
        Registries.RECIPE_TYPE.register(
            new ResourceLocation("minecraft", "crafting_shapeless"),
            RecipeType.CRAFTING_SHAPELESS
        );
        Registries.RECIPE_TYPE.register(
            new ResourceLocation("minecraft", "smelting"),
            RecipeType.SMELTING
        );
        
        System.out.println("[RecipeTypeRegistryBootstrap] Registered " + Registries.RECIPE_TYPE.keys().size() + " recipe types");
    }
}

