package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class SpawnGroupRegistryApi {
    private SpawnGroupRegistryApi() {}

    public static boolean register(ResourceLocation key, SpawnGroup value) {
        return RegistryApiSupport.register(Registries.SPAWN_GROUP, key, value);
    }

    public static SpawnGroup get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.SPAWN_GROUP, key);
    }

    public static SpawnGroup getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.SPAWN_GROUP, any);
    }

    public static ResourceLocation getKey(SpawnGroup value) {
        return RegistryApiSupport.getKey(Registries.SPAWN_GROUP, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.SPAWN_GROUP);
    }

    public static Collection<SpawnGroup> values() {
        return RegistryApiSupport.values(Registries.SPAWN_GROUP);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.SPAWN_GROUP);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.SPAWN_GROUP, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.SPAWN_GROUP, any);
    }
}

