package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class TreeDecoratorTypeRegistryApi {
    private TreeDecoratorTypeRegistryApi() {}

    public static boolean register(ResourceLocation key, TreeDecoratorType value) {
        return RegistryApiSupport.register(Registries.TREE_DECORATOR_TYPE, key, value);
    }

    public static TreeDecoratorType get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.TREE_DECORATOR_TYPE, key);
    }

    public static TreeDecoratorType getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.TREE_DECORATOR_TYPE, any);
    }

    public static ResourceLocation getKey(TreeDecoratorType value) {
        return RegistryApiSupport.getKey(Registries.TREE_DECORATOR_TYPE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.TREE_DECORATOR_TYPE);
    }

    public static Collection<TreeDecoratorType> values() {
        return RegistryApiSupport.values(Registries.TREE_DECORATOR_TYPE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.TREE_DECORATOR_TYPE);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.TREE_DECORATOR_TYPE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.TREE_DECORATOR_TYPE, any);
    }
}

