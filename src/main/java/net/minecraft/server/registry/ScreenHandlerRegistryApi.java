package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class ScreenHandlerRegistryApi {
    private ScreenHandlerRegistryApi() {}

    public static boolean register(ResourceLocation key, Class<?> value) {
        return RegistryApiSupport.register(Registries.SCREEN_HANDLER, key, value);
    }

    public static Class<?> get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.SCREEN_HANDLER, key);
    }

    public static Class<?> getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.SCREEN_HANDLER, any);
    }

    public static ResourceLocation getKey(Class<?> value) {
        return RegistryApiSupport.getKey(Registries.SCREEN_HANDLER, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.SCREEN_HANDLER);
    }

    public static Collection<Class<?>> values() {
        return RegistryApiSupport.values(Registries.SCREEN_HANDLER);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.SCREEN_HANDLER);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.SCREEN_HANDLER, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.SCREEN_HANDLER, any);
    }
}

