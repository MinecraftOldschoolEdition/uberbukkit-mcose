package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class RecipeTypeRegistryApi {
    private RecipeTypeRegistryApi() {}

    public static boolean register(ResourceLocation key, RecipeType<?> value) {
        return RegistryApiSupport.register(Registries.RECIPE_TYPE, key, value);
    }

    public static RecipeType<?> get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.RECIPE_TYPE, key);
    }

    public static RecipeType<?> getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.RECIPE_TYPE, any);
    }

    public static ResourceLocation getKey(RecipeType<?> value) {
        return RegistryApiSupport.getKey(Registries.RECIPE_TYPE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.RECIPE_TYPE);
    }

    public static Collection<RecipeType<?>> values() {
        return RegistryApiSupport.values(Registries.RECIPE_TYPE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.RECIPE_TYPE);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.RECIPE_TYPE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.RECIPE_TYPE, any);
    }
}

