package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.*;

public final class SimpleRegistry<T> {
    private final Map<ResourceLocation, T> byKey = new LinkedHashMap<ResourceLocation, T>();
    private final Map<T, ResourceLocation> keyOf = new IdentityHashMap<T, ResourceLocation>();

    public synchronized void register(ResourceLocation key, T value) {
        if (key == null || value == null) return;
        T cur = byKey.get(key);
        if (cur != null && cur != value) return;
        byKey.put(key, value);
        if (!keyOf.containsKey(value)) keyOf.put(value, key);
    }

    public synchronized void registerIfAbsent(ResourceLocation key, T value) {
        if (key == null || value == null) return;
        if (!byKey.containsKey(key)) byKey.put(key, value);
        if (!keyOf.containsKey(value)) keyOf.put(value, key);
    }

    public T get(ResourceLocation key) { return byKey.get(key); }
    public ResourceLocation getKey(T value) { return keyOf.get(value); }
    public Set<ResourceLocation> keys() { return Collections.unmodifiableSet(byKey.keySet()); }
    public Collection<T> values() { return Collections.unmodifiableCollection(byKey.values()); }
}


