package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class CarverRegistryApi {
    private CarverRegistryApi() {}

    public static boolean register(ResourceLocation key, CarverType value) {
        return RegistryApiSupport.register(Registries.CARVER, key, value);
    }

    public static CarverType get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.CARVER, key);
    }

    public static CarverType getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.CARVER, any);
    }

    public static ResourceLocation getKey(CarverType value) {
        return RegistryApiSupport.getKey(Registries.CARVER, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.CARVER);
    }

    public static Collection<CarverType> values() {
        return RegistryApiSupport.values(Registries.CARVER);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.CARVER);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.CARVER, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.CARVER, any);
    }
}

