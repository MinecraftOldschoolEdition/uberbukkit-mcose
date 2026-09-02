package net.minecraft.server.registry;

import java.util.Collection;
import java.util.Set;
import net.minecraft.server.DamageType;
import net.minecraft.server.util.ResourceLocation;

/** Public namespaced interface for the data-backed damage-type registry. */
public final class DamageTypeRegistryApi {
    private DamageTypeRegistryApi() {}

    public static boolean register(ResourceLocation key, DamageType value) {
        return RegistryApiSupport.register(Registries.DAMAGE_TYPE, key, value);
    }

    public static DamageType get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.DAMAGE_TYPE, key);
    }

    public static DamageType getByIdentifier(String identifier) {
        return RegistryApiSupport.getByIdentifier(
                Registries.DAMAGE_TYPE, identifier);
    }

    public static ResourceLocation getKey(DamageType value) {
        return RegistryApiSupport.getKey(Registries.DAMAGE_TYPE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.DAMAGE_TYPE);
    }

    public static Collection<DamageType> values() {
        return RegistryApiSupport.values(Registries.DAMAGE_TYPE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.DAMAGE_TYPE);
    }
}
