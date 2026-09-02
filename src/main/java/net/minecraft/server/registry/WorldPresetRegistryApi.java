package net.minecraft.server.registry;

import java.util.Collection;
import java.util.Set;
import net.minecraft.server.util.ResourceLocation;

/** Public typed access to data-backed world preset definitions. */
public final class WorldPresetRegistryApi {
    private WorldPresetRegistryApi() {}

    public static boolean register(
            ResourceLocation key, WorldPresetDefinition value) {
        return RegistryApiSupport.register(Registries.WORLD_PRESET, key, value);
    }

    public static WorldPresetDefinition get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.WORLD_PRESET, key);
    }

    public static WorldPresetDefinition getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.WORLD_PRESET, any);
    }

    public static ResourceLocation getKey(WorldPresetDefinition value) {
        return RegistryApiSupport.getKey(Registries.WORLD_PRESET, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.WORLD_PRESET);
    }

    public static Collection<WorldPresetDefinition> values() {
        return RegistryApiSupport.values(Registries.WORLD_PRESET);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.WORLD_PRESET);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(
                Registries.WORLD_PRESET, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(
                Registries.WORLD_PRESET, any);
    }
}
