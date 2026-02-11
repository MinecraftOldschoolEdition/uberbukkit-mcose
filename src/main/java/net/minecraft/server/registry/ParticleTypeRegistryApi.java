package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class ParticleTypeRegistryApi {
    private static final RegistryAliasIndex ALIASES = new RegistryAliasIndex();

    private ParticleTypeRegistryApi() {}

    public static boolean register(ResourceLocation key, ParticleType value) {
        boolean added = RegistryApiSupport.register(Registries.PARTICLE_TYPE, key, value);
        if (added) {
            ALIASES.addAlias(key.toString(), key.toString());
            if (value != null && value.getLegacyKey() != null && value.getLegacyKey().length() > 0) {
                ALIASES.addAlias(key.toString(), value.getLegacyKey());
            }
        }
        return added;
    }

    public static ParticleType get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.PARTICLE_TYPE, key);
    }

    public static ParticleType getByIdentifier(String any) {
        ResourceLocation key = resolve(any);
        return key == null ? null : get(key);
    }

    public static ResourceLocation getKey(ParticleType value) {
        return RegistryApiSupport.getKey(Registries.PARTICLE_TYPE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.PARTICLE_TYPE);
    }

    public static Collection<ParticleType> values() {
        return RegistryApiSupport.values(Registries.PARTICLE_TYPE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.PARTICLE_TYPE);
    }

    public static String normalizeInputIdentifier(String any) {
        ResourceLocation key = resolve(any);
        return key == null ? null : key.toString();
    }

    public static String canonicalizeIdentifier(String any) {
        ResourceLocation key = resolve(any);
        if (key == null) {
            return null;
        }

        ParticleType value = get(key);
        if (value == null) {
            return null;
        }

        ResourceLocation canonical = getKey(value);
        return canonical == null ? null : canonical.toString();
    }

    public static String resolveLegacyKey(String key) {
        if (key == null) {
            return null;
        }

        ParticleType type = getByIdentifier(key);
        if (type != null && type.getLegacyKey() != null && type.getLegacyKey().length() > 0) {
            return type.getLegacyKey();
        }

        String fallbackLegacy = key;
        try {
            ResourceLocation parsed = new ResourceLocation(key);
            if (parsed != null && parsed.getPath() != null && parsed.getPath().length() > 0) {
                fallbackLegacy = parsed.getPath().replace('/', '.');
            }
        } catch (Throwable ignored) {}

        ensureLegacyRegistered(fallbackLegacy);
        return fallbackLegacy;
    }

    private static synchronized void ensureLegacyRegistered(String legacyKey) {
        if (legacyKey == null || legacyKey.length() == 0) {
            return;
        }

        ParticleType existing = getByIdentifier(legacyKey);
        if (existing != null) {
            return;
        }

        String normalizedPath = RegistryKeyPolicy.normalizePath(legacyKey.replace('.', '/'));
        if (normalizedPath == null || normalizedPath.length() == 0) {
            return;
        }

        ResourceLocation generated = new ResourceLocation("minecraft", "legacy/" + normalizedPath);
        ParticleType mine = get(generated);
        int suffix = 2;
        while (mine != null && !legacyKey.equals(mine.getLegacyKey())) {
            generated = new ResourceLocation("minecraft", "legacy/" + normalizedPath + "_" + suffix);
            mine = get(generated);
            suffix++;
        }

        if (mine == null) {
            register(generated, new ParticleType(legacyKey));
        }
    }

    private static ResourceLocation resolve(String any) {
        ResourceLocation resolved = RegistryApiSupport.resolveIdentifier(Registries.PARTICLE_TYPE, any);
        if (resolved != null) {
            return resolved;
        }

        String alias = ALIASES.resolve(any);
        if (alias == null) {
            return null;
        }

        return RegistryApiSupport.resolveIdentifier(Registries.PARTICLE_TYPE, alias);
    }
}
