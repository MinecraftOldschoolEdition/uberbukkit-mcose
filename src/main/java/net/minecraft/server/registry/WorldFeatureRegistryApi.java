package net.minecraft.server.registry;

import net.minecraft.server.WorldGenerator;
import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class WorldFeatureRegistryApi {
    private WorldFeatureRegistryApi() {}

    public static boolean register(ResourceLocation key, WorldGenerator value) {
        return RegistryApiSupport.register(Registries.WORLD_FEATURE, key, value);
    }

    public static WorldGenerator get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.WORLD_FEATURE, key);
    }

    public static WorldGenerator getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.WORLD_FEATURE, any);
    }

    public static ResourceLocation getKey(WorldGenerator value) {
        return RegistryApiSupport.getKey(Registries.WORLD_FEATURE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.WORLD_FEATURE);
    }

    public static Collection<WorldGenerator> values() {
        return RegistryApiSupport.values(Registries.WORLD_FEATURE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.WORLD_FEATURE);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.WORLD_FEATURE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.WORLD_FEATURE, any);
    }
}

