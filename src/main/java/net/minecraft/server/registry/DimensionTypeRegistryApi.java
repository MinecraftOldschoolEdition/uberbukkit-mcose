package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class DimensionTypeRegistryApi {
    private DimensionTypeRegistryApi() {}

    public static boolean register(ResourceLocation key, Class<?> value) {
        return RegistryApiSupport.register(Registries.DIMENSION_TYPE, key, value);
    }

    public static Class<?> get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.DIMENSION_TYPE, key);
    }

    public static Class<?> getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.DIMENSION_TYPE, any);
    }

    public static ResourceLocation getKey(Class<?> value) {
        return RegistryApiSupport.getKey(Registries.DIMENSION_TYPE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.DIMENSION_TYPE);
    }

    public static Collection<Class<?>> values() {
        return RegistryApiSupport.values(Registries.DIMENSION_TYPE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.DIMENSION_TYPE);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.DIMENSION_TYPE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.DIMENSION_TYPE, any);
    }
}

