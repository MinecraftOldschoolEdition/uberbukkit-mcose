package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Shared helper methods for registry API wrappers.
 */
public final class RegistryApiSupport {
    private RegistryApiSupport() {}

    public static <T> boolean register(SimpleRegistry<T> registry, ResourceLocation key, T value) {
        if (registry == null || key == null || value == null) {
            return false;
        }

        T existing = registry.get(key);
        if (existing != null && existing != value) {
            return false;
        }

        registry.registerIfAbsent(key, value);
        if (registry.getKey(value) == null) {
            registry.register(key, value);
        }
        return true;
    }

    public static <T> T get(SimpleRegistry<T> registry, ResourceLocation key) {
        return registry == null || key == null ? null : registry.get(key);
    }

    public static <T> ResourceLocation getKey(SimpleRegistry<T> registry, T value) {
        return registry == null || value == null ? null : registry.getKey(value);
    }

    public static <T> Set<ResourceLocation> keys(SimpleRegistry<T> registry) {
        return registry == null ? Collections.<ResourceLocation>emptySet() : registry.keys();
    }

    public static <T> Collection<T> values(SimpleRegistry<T> registry) {
        return registry == null ? Collections.<T>emptyList() : registry.values();
    }

    public static <T> int size(SimpleRegistry<T> registry) {
        return registry == null ? 0 : registry.keys().size();
    }

    public static <T> T getByIdentifier(SimpleRegistry<T> registry, String any) {
        ResourceLocation key = resolveIdentifier(registry, any);
        return key == null ? null : registry.get(key);
    }

    public static <T> ResourceLocation resolveIdentifier(SimpleRegistry<T> registry, String any) {
        if (registry == null || any == null) {
            return null;
        }

        String normalized = RegistryKeyPolicy.normalizeIdentifier(any);
        if (normalized == null) {
            return null;
        }

        ResourceLocation direct = parse(normalized);
        if (direct != null && registry.get(direct) != null) {
            return direct;
        }

        int colon = normalized.indexOf(':');
        String namespace = colon >= 0 ? normalized.substring(0, colon) : RegistryKeyPolicy.DEFAULT_NAMESPACE;
        String path = colon >= 0 ? normalized.substring(colon + 1) : normalized;

        ResourceLocation dotted = new ResourceLocation(namespace, path.replace('/', '.'));
        if (registry.get(dotted) != null) {
            return dotted;
        }

        ResourceLocation slashed = new ResourceLocation(namespace, path.replace('.', '/'));
        if (registry.get(slashed) != null) {
            return slashed;
        }

        return null;
    }

    public static <T> String normalizeInputIdentifier(SimpleRegistry<T> registry, String any) {
        ResourceLocation key = resolveIdentifier(registry, any);
        return key == null ? null : key.toString();
    }

    public static <T> String canonicalizeIdentifier(SimpleRegistry<T> registry, String any) {
        ResourceLocation resolved = resolveIdentifier(registry, any);
        if (resolved == null) {
            return null;
        }

        T value = registry.get(resolved);
        if (value == null) {
            return null;
        }

        ResourceLocation canonical = registry.getKey(value);
        return canonical == null ? null : canonical.toString();
    }

    private static ResourceLocation parse(String id) {
        if (id == null || id.length() == 0) {
            return null;
        }

        try {
            return new ResourceLocation(id);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
