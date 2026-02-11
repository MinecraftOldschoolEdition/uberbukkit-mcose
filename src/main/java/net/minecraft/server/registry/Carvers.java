package net.minecraft.server.registry;

import net.minecraft.server.*;
import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class Carvers {
    private Carvers() {}

    public static boolean register(ResourceLocation key, CarverType value) {
        return CarverRegistryApi.register(key, value);
    }

    public static CarverType get(ResourceLocation key) {
        return CarverRegistryApi.get(key);
    }

    public static CarverType getByIdentifier(String any) {
        return CarverRegistryApi.getByIdentifier(any);
    }

    public static ResourceLocation getKey(CarverType value) {
        return CarverRegistryApi.getKey(value);
    }

    public static Set<ResourceLocation> keys() {
        return CarverRegistryApi.keys();
    }

    public static Collection<CarverType> values() {
        return CarverRegistryApi.values();
    }

    public static int size() {
        return CarverRegistryApi.size();
    }

    public static String normalizeInputIdentifier(String any) {
        return CarverRegistryApi.normalizeInputIdentifier(any);
    }

    public static String canonicalizeIdentifier(String any) {
        return CarverRegistryApi.canonicalizeIdentifier(any);
    }

    public static MapGenBase create(ResourceLocation key) {
        if (key == null) return new MapGenCaves();
        String path = key.getPath();
        if ("nether_cave".equals(path)) return new MapGenCavesHell();
        return new MapGenCaves();
    }

    public static MapGenBase create(String namespaced) {
        ResourceLocation key = namespaced.indexOf(':') >= 0 ? new ResourceLocation(namespaced) : new ResourceLocation("minecraft", namespaced);
        CarverType t = CarverRegistryApi.get(key);
        if (t == null) return create(key);
        return create(key);
    }
}


