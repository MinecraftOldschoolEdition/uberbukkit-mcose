package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.*;

public final class SimpleRegistry<T> {
    private volatile Snapshot<T> snapshot = Snapshot.empty();
    private volatile boolean frozen;

    public synchronized void register(ResourceLocation key, T value) {
        ensureMutable();
        if (key == null || value == null) return;
        Snapshot<T> current = this.snapshot;
        T cur = current.byKey.get(key);
        if (cur != null && cur != value) return;
        LinkedHashMap<ResourceLocation, T> byKey = copyByKey(current);
        IdentityHashMap<T, ResourceLocation> keyOf = copyKeyOf(current);
        byKey.put(key, value);
        if (!keyOf.containsKey(value)) keyOf.put(value, key);
        this.snapshot = Snapshot.of(byKey, keyOf);
    }

    public synchronized void registerIfAbsent(ResourceLocation key, T value) {
        ensureMutable();
        if (key == null || value == null) return;
        Snapshot<T> current = this.snapshot;
        T existing = current.byKey.get(key);
        if (existing != null && existing != value) return;
        LinkedHashMap<ResourceLocation, T> byKey = copyByKey(current);
        IdentityHashMap<T, ResourceLocation> keyOf = copyKeyOf(current);
        byKey.put(key, value);
        if (!keyOf.containsKey(value)) keyOf.put(value, key);
        this.snapshot = Snapshot.of(byKey, keyOf);
    }

    /** Validates an entire batch before publishing any entry. */
    public synchronized boolean registerAllAtomic(Map<ResourceLocation, ? extends T> entries) {
        ensureMutable();
        if (entries == null || entries.isEmpty()) return false;
        Snapshot<T> current = this.snapshot;

        IdentityHashMap<T, ResourceLocation> stagedKeys =
                new IdentityHashMap<T, ResourceLocation>();

        for (Map.Entry<ResourceLocation, ? extends T> entry : entries.entrySet()) {
            ResourceLocation key = entry.getKey();
            T value = entry.getValue();
            if (key == null || value == null) return false;

            ResourceLocation stagedKey = stagedKeys.put(value, key);
            if (stagedKey != null && !stagedKey.equals(key)) return false;

            T currentValue = current.byKey.get(key);
            if (currentValue != null && currentValue != value) return false;

            ResourceLocation currentKey = current.keyOf.get(value);
            if (currentKey != null && !currentKey.equals(key)) return false;
        }

        LinkedHashMap<ResourceLocation, T> byKey = copyByKey(current);
        IdentityHashMap<T, ResourceLocation> keyOf = copyKeyOf(current);
        for (Map.Entry<ResourceLocation, ? extends T> entry : entries.entrySet()) {
            ResourceLocation key = entry.getKey();
            T value = entry.getValue();
            byKey.put(key, value);
            if (!keyOf.containsKey(value)) keyOf.put(value, key);
        }
        this.snapshot = Snapshot.of(byKey, keyOf);
        return true;
    }

    /** Replaces the complete registry contents with one immutable publication. */
    public synchronized boolean replaceAllAtomic(Map<ResourceLocation, ? extends T> entries) {
        ensureMutable();
        Snapshot<T> replacement = prepareReplacement(entries);
        if (replacement == null) return false;
        this.snapshot = replacement;
        return true;
    }

    /** Validates a complete replacement without changing the live generation. */
    public synchronized boolean canReplaceAllAtomic(
            Map<ResourceLocation, ? extends T> entries) {
        ensureMutable();
        return prepareReplacement(entries) != null;
    }

    private Snapshot<T> prepareReplacement(Map<ResourceLocation, ? extends T> entries) {
        if (entries == null) return null;
        LinkedHashMap<ResourceLocation, T> byKey =
                new LinkedHashMap<ResourceLocation, T>();
        IdentityHashMap<T, ResourceLocation> keyOf =
                new IdentityHashMap<T, ResourceLocation>();
        for (Map.Entry<ResourceLocation, ? extends T> entry : entries.entrySet()) {
            ResourceLocation key = entry.getKey();
            T value = entry.getValue();
            if (key == null || value == null) return null;
            ResourceLocation stagedKey = keyOf.put(value, key);
            if (stagedKey != null && !stagedKey.equals(key)) return null;
            byKey.put(key, value);
        }
        return Snapshot.of(byKey, keyOf);
    }

    public synchronized void freeze() {
        this.frozen = true;
    }

    public synchronized boolean isFrozen() {
        return this.frozen;
    }

    private void ensureMutable() {
        if (this.frozen) {
            throw new IllegalStateException("Registry is frozen");
        }
    }

    public T get(ResourceLocation key) { return this.snapshot.byKey.get(key); }
    public ResourceLocation getKey(T value) { return this.snapshot.keyOf.get(value); }
    public Set<ResourceLocation> keys() { return this.snapshot.byKey.keySet(); }
    public Collection<T> values() { return this.snapshot.byKey.values(); }

    public RegistryView<T> snapshotView() {
        Snapshot<T> current = this.snapshot;
        return new RegistryView<T>(current.byKey, current.keyOf);
    }

    private static <T> LinkedHashMap<ResourceLocation, T> copyByKey(Snapshot<T> snapshot) {
        return new LinkedHashMap<ResourceLocation, T>(snapshot.byKey);
    }

    private static <T> IdentityHashMap<T, ResourceLocation> copyKeyOf(Snapshot<T> snapshot) {
        return new IdentityHashMap<T, ResourceLocation>(snapshot.keyOf);
    }

    private static final class Snapshot<T> {
        private final Map<ResourceLocation, T> byKey;
        private final Map<T, ResourceLocation> keyOf;

        private Snapshot(Map<ResourceLocation, T> byKey,
                         Map<T, ResourceLocation> keyOf) {
            this.byKey = byKey;
            this.keyOf = keyOf;
        }

        private static <T> Snapshot<T> empty() {
            return of(new LinkedHashMap<ResourceLocation, T>(),
                    new IdentityHashMap<T, ResourceLocation>());
        }

        private static <T> Snapshot<T> of(Map<ResourceLocation, T> byKey,
                                          Map<T, ResourceLocation> keyOf) {
            return new Snapshot<T>(Collections.unmodifiableMap(byKey),
                    Collections.unmodifiableMap(keyOf));
        }
    }
}
