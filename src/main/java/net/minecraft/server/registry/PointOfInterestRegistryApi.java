package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class PointOfInterestRegistryApi {
    private PointOfInterestRegistryApi() {}

    public static boolean register(ResourceLocation key, PointOfInterestType value) {
        return RegistryApiSupport.register(Registries.POINT_OF_INTEREST_TYPE, key, value);
    }

    public static PointOfInterestType get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.POINT_OF_INTEREST_TYPE, key);
    }

    public static PointOfInterestType getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.POINT_OF_INTEREST_TYPE, any);
    }

    public static ResourceLocation getKey(PointOfInterestType value) {
        return RegistryApiSupport.getKey(Registries.POINT_OF_INTEREST_TYPE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.POINT_OF_INTEREST_TYPE);
    }

    public static Collection<PointOfInterestType> values() {
        return RegistryApiSupport.values(Registries.POINT_OF_INTEREST_TYPE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.POINT_OF_INTEREST_TYPE);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.POINT_OF_INTEREST_TYPE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.POINT_OF_INTEREST_TYPE, any);
    }

    public static int[] getBlockIds(ResourceLocation key) {
        PointOfInterestType type = get(key);
        if (type == null) {
            return new int[0];
        }

        int[] ids = type.getBlockIds();
        if (ids == null || ids.length == 0) {
            return new int[0];
        }

        int[] copy = new int[ids.length];
        System.arraycopy(ids, 0, copy, 0, ids.length);
        return copy;
    }

    public static int[] getBlockIds(String any) {
        PointOfInterestType type = getByIdentifier(any);
        if (type == null) {
            return new int[0];
        }

        int[] ids = type.getBlockIds();
        if (ids == null || ids.length == 0) {
            return new int[0];
        }

        int[] copy = new int[ids.length];
        System.arraycopy(ids, 0, copy, 0, ids.length);
        return copy;
    }

    public static boolean matches(String any, int blockId) {
        int[] ids = getBlockIds(any);
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == blockId) {
                return true;
            }
        }
        return false;
    }
}

