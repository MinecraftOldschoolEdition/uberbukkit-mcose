package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class FoliagePlacerTypeRegistryApi {
    private FoliagePlacerTypeRegistryApi() {}

    public static boolean register(ResourceLocation key, FoliagePlacerType value) {
        return RegistryApiSupport.register(Registries.FOLIAGE_PLACER_TYPE, key, value);
    }

    public static FoliagePlacerType get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.FOLIAGE_PLACER_TYPE, key);
    }

    public static FoliagePlacerType getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.FOLIAGE_PLACER_TYPE, any);
    }

    public static ResourceLocation getKey(FoliagePlacerType value) {
        return RegistryApiSupport.getKey(Registries.FOLIAGE_PLACER_TYPE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.FOLIAGE_PLACER_TYPE);
    }

    public static Collection<FoliagePlacerType> values() {
        return RegistryApiSupport.values(Registries.FOLIAGE_PLACER_TYPE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.FOLIAGE_PLACER_TYPE);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.FOLIAGE_PLACER_TYPE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.FOLIAGE_PLACER_TYPE, any);
    }
}

