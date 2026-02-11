package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class FluidRegistryApi {
    private FluidRegistryApi() {}

    public static boolean register(ResourceLocation key, Block value) {
        return RegistryApiSupport.register(Registries.FLUID, key, value);
    }

    public static Block get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.FLUID, key);
    }

    public static Block getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.FLUID, any);
    }

    public static ResourceLocation getKey(Block value) {
        return RegistryApiSupport.getKey(Registries.FLUID, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.FLUID);
    }

    public static Collection<Block> values() {
        return RegistryApiSupport.values(Registries.FLUID);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.FLUID);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.FLUID, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.FLUID, any);
    }
}

