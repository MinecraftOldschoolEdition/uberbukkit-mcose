package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class SurfaceBuilderRegistryApi {
    private SurfaceBuilderRegistryApi() {}

    public static boolean register(ResourceLocation key, SurfaceBuilderType value) {
        return RegistryApiSupport.register(Registries.SURFACE_BUILDER, key, value);
    }

    public static SurfaceBuilderType get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.SURFACE_BUILDER, key);
    }

    public static SurfaceBuilderType getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.SURFACE_BUILDER, any);
    }

    public static ResourceLocation getKey(SurfaceBuilderType value) {
        return RegistryApiSupport.getKey(Registries.SURFACE_BUILDER, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.SURFACE_BUILDER);
    }

    public static Collection<SurfaceBuilderType> values() {
        return RegistryApiSupport.values(Registries.SURFACE_BUILDER);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.SURFACE_BUILDER);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.SURFACE_BUILDER, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.SURFACE_BUILDER, any);
    }
}

