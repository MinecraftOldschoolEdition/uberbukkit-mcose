package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class SensorTypeRegistryApi {
    private SensorTypeRegistryApi() {}

    public static boolean register(ResourceLocation key, SensorType value) {
        return RegistryApiSupport.register(Registries.SENSOR_TYPE, key, value);
    }

    public static SensorType get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.SENSOR_TYPE, key);
    }

    public static SensorType getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.SENSOR_TYPE, any);
    }

    public static ResourceLocation getKey(SensorType value) {
        return RegistryApiSupport.getKey(Registries.SENSOR_TYPE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.SENSOR_TYPE);
    }

    public static Collection<SensorType> values() {
        return RegistryApiSupport.values(Registries.SENSOR_TYPE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.SENSOR_TYPE);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.SENSOR_TYPE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.SENSOR_TYPE, any);
    }
}

