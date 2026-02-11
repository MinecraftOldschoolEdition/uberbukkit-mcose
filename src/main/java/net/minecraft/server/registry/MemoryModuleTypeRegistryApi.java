package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class MemoryModuleTypeRegistryApi {
    private MemoryModuleTypeRegistryApi() {}

    public static boolean register(ResourceLocation key, MemoryModuleType value) {
        return RegistryApiSupport.register(Registries.MEMORY_MODULE_TYPE, key, value);
    }

    public static MemoryModuleType get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.MEMORY_MODULE_TYPE, key);
    }

    public static MemoryModuleType getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.MEMORY_MODULE_TYPE, any);
    }

    public static ResourceLocation getKey(MemoryModuleType value) {
        return RegistryApiSupport.getKey(Registries.MEMORY_MODULE_TYPE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.MEMORY_MODULE_TYPE);
    }

    public static Collection<MemoryModuleType> values() {
        return RegistryApiSupport.values(Registries.MEMORY_MODULE_TYPE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.MEMORY_MODULE_TYPE);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.MEMORY_MODULE_TYPE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.MEMORY_MODULE_TYPE, any);
    }
}

