package net.minecraft.server.registry;

import net.minecraft.server.EnumArt;
import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class PaintingMotiveRegistryApi {
    private PaintingMotiveRegistryApi() {}

    public static boolean register(ResourceLocation key, EnumArt value) {
        return RegistryApiSupport.register(Registries.PAINTING_MOTIVE, key, value);
    }

    public static EnumArt get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.PAINTING_MOTIVE, key);
    }

    public static EnumArt getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.PAINTING_MOTIVE, any);
    }

    public static ResourceLocation getKey(EnumArt value) {
        return RegistryApiSupport.getKey(Registries.PAINTING_MOTIVE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.PAINTING_MOTIVE);
    }

    public static Collection<EnumArt> values() {
        return RegistryApiSupport.values(Registries.PAINTING_MOTIVE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.PAINTING_MOTIVE);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.PAINTING_MOTIVE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.PAINTING_MOTIVE, any);
    }
}

