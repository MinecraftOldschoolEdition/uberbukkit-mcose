package net.minecraft.server.registry;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;
import net.minecraft.server.Entity;
import net.minecraft.server.util.ResourceLocation;

/**
 * Global, lightweight memory store keyed by entity instance and memory module key.
 */
public final class EntityMemory {
    private static final WeakHashMap<Entity, Map<ResourceLocation, Object>> store = new WeakHashMap<Entity, Map<ResourceLocation, Object>>();

    private EntityMemory() {}

    private static Map<ResourceLocation, Object> getMap(Entity e) {
        Map<ResourceLocation, Object> m = store.get(e);
        if (m == null) {
            m = new HashMap<ResourceLocation, Object>();
            store.put(e, m);
        }
        return m;
    }

    public static void set(Entity e, ResourceLocation key, Object value) {
        if (e == null || key == null) return;
        getMap(e).put(key, value);
    }

    public static Object get(Entity e, ResourceLocation key) {
        if (e == null || key == null) return null;
        Map<ResourceLocation, Object> m = store.get(e);
        return m != null ? m.get(key) : null;
    }

    public static void clear(Entity e, ResourceLocation key) {
        if (e == null || key == null) return;
        Map<ResourceLocation, Object> m = store.get(e);
        if (m != null) m.remove(key);
    }
}


