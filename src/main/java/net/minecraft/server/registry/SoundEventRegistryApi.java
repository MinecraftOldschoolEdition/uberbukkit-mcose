package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class SoundEventRegistryApi {
    private static final RegistryAliasIndex ALIASES = new RegistryAliasIndex();

    private SoundEventRegistryApi() {}

    public static boolean register(ResourceLocation key, String value) {
        boolean added = RegistryApiSupport.register(Registries.SOUND_EVENT, key, value);
        if (added) {
            ALIASES.addAlias(key.toString(), key.toString());
            if (value != null && value.length() > 0) {
                ALIASES.addAlias(key.toString(), value);
            }
        }
        return added;
    }

    public static String get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.SOUND_EVENT, key);
    }

    public static String getByIdentifier(String any) {
        ResourceLocation key = resolve(any);
        return key == null ? null : get(key);
    }

    public static ResourceLocation getKey(String value) {
        return RegistryApiSupport.getKey(Registries.SOUND_EVENT, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.SOUND_EVENT);
    }

    public static Collection<String> values() {
        return RegistryApiSupport.values(Registries.SOUND_EVENT);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.SOUND_EVENT);
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

        String value = get(key);
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

        String legacy = getByIdentifier(key);
        if (legacy != null && legacy.length() > 0) {
            return legacy;
        }

        String fallbackLegacy = mapIdentifierToLegacyKey(key);
        ensureLegacyRegistered(fallbackLegacy);
        return fallbackLegacy;
    }

    public static void registerLegacySoundKey(String legacyKey) {
        ensureLegacyRegistered(legacyKey);
    }

    private static synchronized void ensureLegacyRegistered(String legacyKey) {
        if (legacyKey == null || legacyKey.length() == 0) {
            return;
        }

        String existing = getByIdentifier(legacyKey);
        if (existing != null) {
            return;
        }

        String normalizedPath = RegistryKeyPolicy.normalizePath(legacyKey.replace('.', '/'));
        if (normalizedPath == null || normalizedPath.length() == 0) {
            return;
        }

        ResourceLocation generated = new ResourceLocation("minecraft", "legacy/" + normalizedPath);
        String mine = get(generated);
        int suffix = 2;
        while (mine != null && !legacyKey.equals(mine)) {
            generated = new ResourceLocation("minecraft", "legacy/" + normalizedPath + "_" + suffix);
            mine = get(generated);
            suffix++;
        }

        if (mine == null) {
            register(generated, legacyKey);
        }
    }

    private static String mapIdentifierToLegacyKey(String key) {
        if (key == null) {
            return null;
        }
        try {
            ResourceLocation parsed = new ResourceLocation(key);
            if (parsed != null && parsed.getPath() != null && parsed.getPath().length() > 0) {
                return parsed.getPath().replace('/', '.');
            }
        } catch (Throwable ignored) {}
        return key;
    }

    private static ResourceLocation resolve(String any) {
        ResourceLocation resolved = RegistryApiSupport.resolveIdentifier(Registries.SOUND_EVENT, any);
        if (resolved != null) {
            return resolved;
        }

        String alias = ALIASES.resolve(any);
        if (alias == null) {
            return null;
        }

        return RegistryApiSupport.resolveIdentifier(Registries.SOUND_EVENT, alias);
    }
}
