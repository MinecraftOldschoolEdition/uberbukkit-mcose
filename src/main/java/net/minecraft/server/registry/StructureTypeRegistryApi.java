package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class StructureTypeRegistryApi {
    private StructureTypeRegistryApi() {}

    public static boolean register(ResourceLocation key, StructureType value) {
        return RegistryApiSupport.register(Registries.STRUCTURE_TYPE, key, value);
    }

    public static StructureType get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.STRUCTURE_TYPE, key);
    }

    public static StructureType getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.STRUCTURE_TYPE, any);
    }

    public static ResourceLocation getKey(StructureType value) {
        return RegistryApiSupport.getKey(Registries.STRUCTURE_TYPE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.STRUCTURE_TYPE);
    }

    public static Collection<StructureType> values() {
        return RegistryApiSupport.values(Registries.STRUCTURE_TYPE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.STRUCTURE_TYPE);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.STRUCTURE_TYPE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.STRUCTURE_TYPE, any);
    }
}

