package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class FeatureRegistryApi {
    private FeatureRegistryApi() {}

    public static boolean register(ResourceLocation key, FeatureType value) {
        return RegistryApiSupport.register(Registries.FEATURE, key, value);
    }

    public static FeatureType get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.FEATURE, key);
    }

    public static FeatureType getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.FEATURE, any);
    }

    public static ResourceLocation getKey(FeatureType value) {
        return RegistryApiSupport.getKey(Registries.FEATURE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.FEATURE);
    }

    public static Collection<FeatureType> values() {
        return RegistryApiSupport.values(Registries.FEATURE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.FEATURE);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.FEATURE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.FEATURE, any);
    }
}

