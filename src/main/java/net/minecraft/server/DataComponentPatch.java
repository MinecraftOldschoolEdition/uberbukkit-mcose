package net.minecraft.server;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mutable delta against default item components.
 */
public final class DataComponentPatch {
    private static final DataComponentPatch EMPTY = new DataComponentPatch(
            Collections.<DataComponentType<?>, Object>emptyMap(),
            Collections.<DataComponentType<?>>emptySet(),
            Collections.<ResourceLocation, NBTBase>emptyMap(),
            Collections.<ResourceLocation>emptySet());

    private final Map<DataComponentType<?>, Object> setValues;
    private final Set<DataComponentType<?>> removedTypes;
    private final Map<ResourceLocation, NBTBase> unknownComponents;
    private final Set<ResourceLocation> unknownRemovedComponents;

    private DataComponentPatch(
            Map<DataComponentType<?>, Object> setValues,
            Set<DataComponentType<?>> removedTypes,
            Map<ResourceLocation, NBTBase> unknownComponents,
            Set<ResourceLocation> unknownRemovedComponents) {
        this.setValues = setValues;
        this.removedTypes = removedTypes;
        this.unknownComponents = unknownComponents;
        this.unknownRemovedComponents = unknownRemovedComponents;
    }

    public static DataComponentPatch empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DataComponentPatch merge(DataComponentPatch base, DataComponentPatch update) {
        if (base == null || base.isEmpty()) {
            return update == null ? empty() : update.copy();
        }
        if (update == null || update.isEmpty()) {
            return base.copy();
        }

        Builder merged = builder();
        for (Map.Entry<DataComponentType<?>, Object> entry : base.setValues.entrySet()) {
            merged.setUntyped(entry.getKey(), copyValue(entry.getValue()));
        }
        for (DataComponentType<?> removed : base.removedTypes) {
            merged.remove(removed);
        }
        for (Map.Entry<ResourceLocation, NBTBase> unknown : base.unknownComponents.entrySet()) {
            merged.setUnknown(unknown.getKey(), unknown.getValue().copy());
        }
        for (ResourceLocation removed : base.unknownRemovedComponents) {
            merged.removeUnknown(removed);
        }

        for (Map.Entry<DataComponentType<?>, Object> entry : update.setValues.entrySet()) {
            merged.setUntyped(entry.getKey(), copyValue(entry.getValue()));
        }
        for (DataComponentType<?> removed : update.removedTypes) {
            merged.remove(removed);
        }
        for (Map.Entry<ResourceLocation, NBTBase> unknown : update.unknownComponents.entrySet()) {
            merged.setUnknown(unknown.getKey(), unknown.getValue().copy());
        }
        for (ResourceLocation removed : update.unknownRemovedComponents) {
            merged.removeUnknown(removed);
        }

        return merged.build();
    }

    public DataComponentPatch copy() {
        if (this.isEmpty()) {
            return empty();
        }
        Map<DataComponentType<?>, Object> copiedValues = new HashMap<DataComponentType<?>, Object>();
        for (Map.Entry<DataComponentType<?>, Object> entry : this.setValues.entrySet()) {
            copiedValues.put(entry.getKey(), copyValue(entry.getValue()));
        }
        Map<ResourceLocation, NBTBase> copiedUnknown = new HashMap<ResourceLocation, NBTBase>();
        for (Map.Entry<ResourceLocation, NBTBase> entry : this.unknownComponents.entrySet()) {
            copiedUnknown.put(entry.getKey(), entry.getValue().copy());
        }
        return new DataComponentPatch(
                copiedValues,
                new HashSet<DataComponentType<?>>(this.removedTypes),
                copiedUnknown,
                new HashSet<ResourceLocation>(this.unknownRemovedComponents));
    }

    private static Object copyValue(Object value) {
        return value instanceof NBTBase ? ((NBTBase) value).copy() : value;
    }

    public boolean isEmpty() {
        return this.setValues.isEmpty()
                && this.removedTypes.isEmpty()
                && this.unknownComponents.isEmpty()
                && this.unknownRemovedComponents.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DataComponentPatch)) {
            return false;
        }
        DataComponentPatch patch = (DataComponentPatch) other;
        return this.setValues.equals(patch.setValues)
                && this.removedTypes.equals(patch.removedTypes)
                && this.unknownComponents.equals(patch.unknownComponents)
                && this.unknownRemovedComponents.equals(patch.unknownRemovedComponents);
    }

    @Override
    public int hashCode() {
        int result = this.setValues.hashCode();
        result = 31 * result + this.removedTypes.hashCode();
        result = 31 * result + this.unknownComponents.hashCode();
        return 31 * result + this.unknownRemovedComponents.hashCode();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(DataComponentType<T> type) {
        return (T)this.setValues.get(type);
    }

    public Map<DataComponentType<?>, Object> getSetValues() {
        return Collections.unmodifiableMap(this.setValues);
    }

    public Set<DataComponentType<?>> getRemovedTypes() {
        return Collections.unmodifiableSet(this.removedTypes);
    }

    public Map<ResourceLocation, NBTBase> getUnknownComponents() {
        return Collections.unmodifiableMap(this.unknownComponents);
    }

    public Set<ResourceLocation> getUnknownRemovedComponents() {
        return Collections.unmodifiableSet(this.unknownRemovedComponents);
    }

    public NBTTagCompound toNbt() {
        NBTTagCompound out = new NBTTagCompound();

        NBTTagCompound valuesTag = new NBTTagCompound();
        for (Map.Entry<DataComponentType<?>, Object> entry : this.setValues.entrySet()) {
            DataComponentType type = entry.getKey();
            Object value = entry.getValue();
            if (type == null || value == null) {
                continue;
            }
            NBTBase encoded = type.write(value);
            if (encoded == null) {
                continue;
            }
            valuesTag.a(type.id().toString(), encoded);
        }

        for (Map.Entry<ResourceLocation, NBTBase> unknown : this.unknownComponents.entrySet()) {
            if (unknown.getKey() == null || unknown.getValue() == null) {
                continue;
            }
            valuesTag.a(unknown.getKey().toString(), unknown.getValue());
        }

        if (!valuesTag.c().isEmpty()) {
            out.a("values", valuesTag);
        }

        if (!this.removedTypes.isEmpty() || !this.unknownRemovedComponents.isEmpty()) {
            NBTTagList removedTag = new NBTTagList();
            for (DataComponentType<?> removed : this.removedTypes) {
                if (removed == null || removed.id() == null) {
                    continue;
                }
                removedTag.a(new NBTTagString(removed.id().toString()));
            }
            for (ResourceLocation removed : this.unknownRemovedComponents) {
                if (removed != null) {
                    removedTag.a(new NBTTagString(removed.toString()));
                }
            }
            out.a("removed", (NBTBase)removedTag);
        }

        return out;
    }

    public static DataComponentPatch fromNbt(NBTTagCompound in) {
        if (in == null) {
            return empty();
        }

        Builder builder = builder();

        if (in.hasKey("values")) {
            NBTTagCompound valuesTag = in.k("values");
            Collection entries = valuesTag.c();
            for (Object entryObj : entries) {
                if (!(entryObj instanceof NBTBase)) {
                    continue;
                }
                NBTBase valueTag = (NBTBase)entryObj;
                String keyString = valueTag.b();
                if (keyString == null || keyString.length() == 0) {
                    continue;
                }

                DataComponentType<?> known = DataComponents.byKey(keyString);
                if (known == null) {
                    try {
                        builder.setUnknown(new ResourceLocation(keyString), valueTag);
                    } catch (Throwable ignored) {}
                    continue;
                }

                try {
                    Object parsed = known.read(valueTag);
                    if (parsed != null) {
                        builder.setUntyped(known, parsed);
                    } else {
                        builder.setUnknown(known.id(), valueTag);
                    }
                } catch (Throwable ignored) {
                    builder.setUnknown(known.id(), valueTag);
                }
            }
        }

        if (in.hasKey("removed")) {
            NBTTagList removedTag = in.l("removed");
            for (int i = 0; i < removedTag.c(); i++) {
                NBTBase tag = removedTag.a(i);
                if (!(tag instanceof NBTTagString)) {
                    continue;
                }
                String key = ((NBTTagString)tag).a;
                DataComponentType<?> known = DataComponents.byKey(key);
                if (known != null) {
                    builder.remove(known);
                } else {
                    try {
                        builder.removeUnknown(new ResourceLocation(key));
                    } catch (Throwable ignored) {}
                }
            }
        }

        return builder.build();
    }

    public static final class Builder {
        private final Map<DataComponentType<?>, Object> setValues = new HashMap<DataComponentType<?>, Object>();
        private final Set<DataComponentType<?>> removedTypes = new HashSet<DataComponentType<?>>();
        private final Map<ResourceLocation, NBTBase> unknownComponents = new HashMap<ResourceLocation, NBTBase>();
        private final Set<ResourceLocation> unknownRemovedComponents = new HashSet<ResourceLocation>();

        private Builder() {}

        public <T> Builder set(DataComponentType<T> type, T value) {
            if (type == null || value == null) {
                return this;
            }
            this.setValues.put(type, value);
            this.removedTypes.remove(type);
            this.unknownComponents.remove(type.id());
            this.unknownRemovedComponents.remove(type.id());
            return this;
        }

        Builder setUntyped(DataComponentType<?> type, Object value) {
            if (type == null || value == null) {
                return this;
            }
            this.setValues.put(type, value);
            this.removedTypes.remove(type);
            this.unknownComponents.remove(type.id());
            this.unknownRemovedComponents.remove(type.id());
            return this;
        }

        public Builder remove(DataComponentType<?> type) {
            if (type == null) {
                return this;
            }
            this.setValues.remove(type);
            this.removedTypes.add(type);
            this.unknownComponents.remove(type.id());
            this.unknownRemovedComponents.remove(type.id());
            return this;
        }

        public Builder setUnknown(ResourceLocation key, NBTBase value) {
            if (key == null || value == null) {
                return this;
            }
            DataComponentType<?> known = DataComponents.byKey(key.toString());
            if (known != null) {
                this.setValues.remove(known);
                this.removedTypes.remove(known);
            }
            this.unknownComponents.put(key, value);
            this.unknownRemovedComponents.remove(key);
            return this;
        }

        public Builder removeUnknown(ResourceLocation key) {
            if (key == null) {
                return this;
            }
            DataComponentType<?> known = DataComponents.byKey(key.toString());
            if (known != null) {
                return this.remove(known);
            }
            this.unknownComponents.remove(key);
            this.unknownRemovedComponents.add(key);
            return this;
        }

        public DataComponentPatch build() {
            if (this.setValues.isEmpty()
                    && this.removedTypes.isEmpty()
                    && this.unknownComponents.isEmpty()
                    && this.unknownRemovedComponents.isEmpty()) {
                return DataComponentPatch.empty();
            }
            return new DataComponentPatch(
                    new HashMap<DataComponentType<?>, Object>(this.setValues),
                    new HashSet<DataComponentType<?>>(this.removedTypes),
                    new HashMap<ResourceLocation, NBTBase>(this.unknownComponents),
                    new HashSet<ResourceLocation>(this.unknownRemovedComponents));
        }
    }
}
