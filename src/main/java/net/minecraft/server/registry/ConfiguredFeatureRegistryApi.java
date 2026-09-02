package net.minecraft.server.registry;

import java.util.Collection;
import java.util.Set;
import net.minecraft.server.util.ResourceLocation;

public final class ConfiguredFeatureRegistryApi {
    private ConfiguredFeatureRegistryApi() {}

    public static ConfiguredFeatureDefinition get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.CONFIGURED_FEATURE, key);
    }

    public static ConfiguredFeatureDefinition getByIdentifier(String key) {
        return RegistryApiSupport.getByIdentifier(Registries.CONFIGURED_FEATURE, key);
    }

    public static ResourceLocation getKey(ConfiguredFeatureDefinition value) {
        return RegistryApiSupport.getKey(Registries.CONFIGURED_FEATURE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.CONFIGURED_FEATURE);
    }

    public static Collection<ConfiguredFeatureDefinition> values() {
        return RegistryApiSupport.values(Registries.CONFIGURED_FEATURE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.CONFIGURED_FEATURE);
    }
}
