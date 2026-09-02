package net.minecraft.server.registry;

import java.util.Collection;
import java.util.Set;
import net.minecraft.server.util.ResourceLocation;

public final class PlacedFeatureRegistryApi {
    private PlacedFeatureRegistryApi() {}

    public static PlacedFeatureDefinition get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.PLACED_FEATURE, key);
    }

    public static PlacedFeatureDefinition getByIdentifier(String key) {
        return RegistryApiSupport.getByIdentifier(Registries.PLACED_FEATURE, key);
    }

    public static ResourceLocation getKey(PlacedFeatureDefinition value) {
        return RegistryApiSupport.getKey(Registries.PLACED_FEATURE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.PLACED_FEATURE);
    }

    public static Collection<PlacedFeatureDefinition> values() {
        return RegistryApiSupport.values(Registries.PLACED_FEATURE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.PLACED_FEATURE);
    }
}
