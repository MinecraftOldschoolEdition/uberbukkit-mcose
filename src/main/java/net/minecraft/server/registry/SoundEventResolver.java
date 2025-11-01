package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

public final class SoundEventResolver {
    private SoundEventResolver() {}

    public static String resolve(String key) {
        if (key == null) return null;
        try {
            // If modern namespaced id, resolve via registry
            if (key.indexOf(':') >= 0) {
                String legacy = Registries.SOUND_EVENT.get(new ResourceLocation(key));
                if (legacy != null && legacy.length() > 0) return legacy;
            } else {
                // Try default minecraft namespace for modern ids without namespace
                String legacy = Registries.SOUND_EVENT.get(new ResourceLocation("minecraft", key));
                if (legacy != null && legacy.length() > 0) return legacy;
            }
        } catch (Throwable ignored) {}
        // Fallback: assume it's already a legacy pool key
        return key;
    }
}


