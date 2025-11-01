package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public final class VariantDefaults {
    private static final Map<ResourceLocation, Integer> KEY_TO_DAMAGE = new HashMap<ResourceLocation, Integer>();

    private VariantDefaults() {}

    public static synchronized void put(ResourceLocation key, int damage) {
        if (key == null) return;
        KEY_TO_DAMAGE.put(key, Integer.valueOf(damage));
    }

    public static synchronized int get(String id) {
        if (id == null) return -1;
        try {
            ResourceLocation rl = new ResourceLocation(id);
            Integer v = KEY_TO_DAMAGE.get(rl);
            return v != null ? v.intValue() : -1;
        } catch (Throwable t) { return -1; }
    }
}


