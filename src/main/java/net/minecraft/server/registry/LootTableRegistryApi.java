package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class LootTableRegistryApi {
    private LootTableRegistryApi() {}

    public static boolean register(ResourceLocation key, LootTable value) {
        return RegistryApiSupport.register(Registries.LOOT_TABLE, key, value);
    }

    public static LootTable get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.LOOT_TABLE, key);
    }

    public static LootTable getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.LOOT_TABLE, any);
    }

    public static ResourceLocation getKey(LootTable value) {
        return RegistryApiSupport.getKey(Registries.LOOT_TABLE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.LOOT_TABLE);
    }

    public static Collection<LootTable> values() {
        return RegistryApiSupport.values(Registries.LOOT_TABLE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.LOOT_TABLE);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.LOOT_TABLE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.LOOT_TABLE, any);
    }
}

