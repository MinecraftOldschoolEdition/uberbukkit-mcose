package net.minecraft.server.registry;

import net.minecraft.server.MapGenBase;
import net.minecraft.server.util.ResourceLocation;

/** Strict configured-carver lookup and runtime creation. */
public final class ConfiguredCarvers {
    private ConfiguredCarvers() {}

    public static MapGenBase create(ResourceLocation key) {
        if (key == null) {
            throw new IllegalArgumentException("Configured carver key cannot be null");
        }
        ConfiguredCarverDataBootstrap.initialize();
        ConfiguredCarverDefinition definition = ConfiguredCarverRegistryApi.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown configured carver " + key);
        }
        return definition.create();
    }

    public static MapGenBase create(String namespaced) {
        if (namespaced == null || namespaced.length() == 0) {
            throw new IllegalArgumentException(
                    "Configured carver identifier cannot be empty");
        }
        ResourceLocation key = namespaced.indexOf(':') >= 0
                ? new ResourceLocation(namespaced)
                : new ResourceLocation("minecraft", namespaced);
        return create(key);
    }
}
