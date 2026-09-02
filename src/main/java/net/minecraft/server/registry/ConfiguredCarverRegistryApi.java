package net.minecraft.server.registry;

import java.util.Collection;
import java.util.Set;
import net.minecraft.server.util.ResourceLocation;

/** Read-only API for the data-pack-backed worldgen/carver registry. */
public final class ConfiguredCarverRegistryApi {
    private ConfiguredCarverRegistryApi() {}

    public static boolean register(
            ResourceLocation key, ConfiguredCarverDefinition value) {
        return RegistryApiSupport.register(Registries.CONFIGURED_CARVER, key, value);
    }

    public static ConfiguredCarverDefinition get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.CONFIGURED_CARVER, key);
    }

    public static ConfiguredCarverDefinition getByIdentifier(String key) {
        return RegistryApiSupport.getByIdentifier(Registries.CONFIGURED_CARVER, key);
    }

    public static ResourceLocation getKey(ConfiguredCarverDefinition value) {
        return RegistryApiSupport.getKey(Registries.CONFIGURED_CARVER, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.CONFIGURED_CARVER);
    }

    public static Collection<ConfiguredCarverDefinition> values() {
        return RegistryApiSupport.values(Registries.CONFIGURED_CARVER);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.CONFIGURED_CARVER);
    }
}
