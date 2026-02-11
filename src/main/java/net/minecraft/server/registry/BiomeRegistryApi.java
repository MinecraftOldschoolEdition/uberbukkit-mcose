package net.minecraft.server.registry;

import net.minecraft.server.BiomeBase;
import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class BiomeRegistryApi {
    private BiomeRegistryApi() {}

    public static boolean register(ResourceLocation key, BiomeBase value) {
        return RegistryApiSupport.register(Registries.BIOME, key, value);
    }

    public static BiomeBase get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.BIOME, key);
    }

    public static BiomeBase getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.BIOME, any);
    }

    public static ResourceLocation getKey(BiomeBase value) {
        return RegistryApiSupport.getKey(Registries.BIOME, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.BIOME);
    }

    public static Collection<BiomeBase> values() {
        return RegistryApiSupport.values(Registries.BIOME);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.BIOME);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.BIOME, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.BIOME, any);
    }
}
