package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class ScheduleRegistryApi {
    private ScheduleRegistryApi() {}

    public static boolean register(ResourceLocation key, Schedule value) {
        return RegistryApiSupport.register(Registries.SCHEDULE, key, value);
    }

    public static Schedule get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.SCHEDULE, key);
    }

    public static Schedule getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.SCHEDULE, any);
    }

    public static ResourceLocation getKey(Schedule value) {
        return RegistryApiSupport.getKey(Registries.SCHEDULE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.SCHEDULE);
    }

    public static Collection<Schedule> values() {
        return RegistryApiSupport.values(Registries.SCHEDULE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.SCHEDULE);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.SCHEDULE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.SCHEDULE, any);
    }
}

