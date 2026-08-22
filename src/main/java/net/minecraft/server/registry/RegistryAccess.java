package net.minecraft.server.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.util.ResourceLocation;

/** Immutable collection of named registry views. */
public final class RegistryAccess {
    public static final RegistryAccess EMPTY =
            new RegistryAccess(Collections.<ResourceLocation, RegistryView<?>>emptyMap());

    private final Map<ResourceLocation, RegistryView<?>> registries;

    private RegistryAccess(Map<ResourceLocation, RegistryView<?>> registries) {
        this.registries = Collections.unmodifiableMap(
                new LinkedHashMap<ResourceLocation, RegistryView<?>>(registries));
    }

    @SuppressWarnings("unchecked")
    public <T> RegistryView<T> lookup(ResourceLocation registryKey) {
        return (RegistryView<T>)this.registries.get(registryKey);
    }

    public RegistryView<?> lookupUntyped(ResourceLocation registryKey) {
        return this.registries.get(registryKey);
    }

    public Set<ResourceLocation> registryKeys() { return this.registries.keySet(); }
    public int size() { return this.registries.size(); }

    static RegistryAccess merge(Iterable<RegistryAccess> accesses) {
        LinkedHashMap<ResourceLocation, RegistryView<?>> merged =
                new LinkedHashMap<ResourceLocation, RegistryView<?>>();
        for (RegistryAccess access : accesses) {
            if (access == null) {
                throw new IllegalArgumentException("Registry access layer cannot be null");
            }
            for (ResourceLocation key : access.registryKeys()) {
                if (merged.put(key, access.lookupUntyped(key)) != null) {
                    throw new IllegalStateException("Duplicated registry " + key);
                }
            }
        }
        return merged.isEmpty() ? EMPTY : new RegistryAccess(merged);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final LinkedHashMap<ResourceLocation, RegistryView<?>> registries =
                new LinkedHashMap<ResourceLocation, RegistryView<?>>();

        public <T> Builder add(ResourceLocation registryKey, SimpleRegistry<T> registry) {
            if (registry == null) throw new IllegalArgumentException("Registry cannot be null");
            return addView(registryKey, registry.snapshotView());
        }

        public Builder addView(ResourceLocation registryKey, RegistryView<?> registry) {
            if (registryKey == null || registry == null) {
                throw new IllegalArgumentException("Registry key and view cannot be null");
            }
            if (this.registries.put(registryKey, registry) != null) {
                throw new IllegalStateException("Duplicated registry " + registryKey);
            }
            return this;
        }

        public RegistryAccess build() {
            return this.registries.isEmpty() ? EMPTY : new RegistryAccess(this.registries);
        }
    }
}
