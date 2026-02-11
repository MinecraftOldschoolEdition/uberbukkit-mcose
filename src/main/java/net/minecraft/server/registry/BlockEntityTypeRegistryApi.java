package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class BlockEntityTypeRegistryApi {
    private BlockEntityTypeRegistryApi() {}

    public static boolean register(ResourceLocation key, Class<?> value) {
        return RegistryApiSupport.register(Registries.BLOCK_ENTITY_TYPE, key, value);
    }

    public static Class<?> get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.BLOCK_ENTITY_TYPE, key);
    }

    public static Class<?> getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.BLOCK_ENTITY_TYPE, any);
    }

    public static ResourceLocation getKey(Class<?> value) {
        return RegistryApiSupport.getKey(Registries.BLOCK_ENTITY_TYPE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.BLOCK_ENTITY_TYPE);
    }

    public static Collection<Class<?>> values() {
        return RegistryApiSupport.values(Registries.BLOCK_ENTITY_TYPE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.BLOCK_ENTITY_TYPE);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.BLOCK_ENTITY_TYPE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.BLOCK_ENTITY_TYPE, any);
    }
}
