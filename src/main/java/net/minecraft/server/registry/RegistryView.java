package net.minecraft.server.registry;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.util.ResourceLocation;

/** Immutable point-in-time view of one registry generation. */
public final class RegistryView<T> {
    private final Map<ResourceLocation, T> byKey;
    private final Map<T, ResourceLocation> keyOf;

    RegistryView(Map<ResourceLocation, T> byKey, Map<T, ResourceLocation> keyOf) {
        this.byKey = byKey;
        this.keyOf = keyOf;
    }

    public T get(ResourceLocation key) { return this.byKey.get(key); }
    public ResourceLocation getKey(T value) { return this.keyOf.get(value); }
    public Set<ResourceLocation> keys() { return this.byKey.keySet(); }
    public Collection<T> values() { return this.byKey.values(); }
    public int size() { return this.byKey.size(); }
}
