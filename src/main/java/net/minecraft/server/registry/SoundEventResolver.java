package net.minecraft.server.registry;

public final class SoundEventResolver {
    private SoundEventResolver() {}

    public static String resolve(String key) {
        return SoundEventRegistryApi.resolveLegacyKey(key);
    }
}

