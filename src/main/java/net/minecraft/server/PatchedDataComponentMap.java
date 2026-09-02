package net.minecraft.server;

import java.util.HashMap;
import java.util.Map;

public final class PatchedDataComponentMap {
    private final DataComponentMap base;
    private final DataComponentPatch patch;
    private final Map<DataComponentType<?>, Object> resolvedValues;

    public PatchedDataComponentMap(DataComponentMap base, DataComponentPatch patch) {
        this.base = base == null ? DataComponentMap.EMPTY : base;
        this.patch = patch == null ? DataComponentPatch.empty() : patch;
        this.resolvedValues = new HashMap<DataComponentType<?>, Object>(this.base.asMap());
        for (DataComponentType<?> removed : this.patch.getRemovedTypes()) {
            this.resolvedValues.remove(removed);
        }
        this.resolvedValues.putAll(this.patch.getSetValues());
    }

    public <T> T get(DataComponentType<T> type) {
        if (type == null) {
            return null;
        }
        if (this.patch.getRemovedTypes().contains(type)) {
            return null;
        }
        T patched = this.patch.get(type);
        if (patched != null) {
            return patched;
        }
        return this.base.get(type);
    }

    public DataComponentMap base() {
        return this.base;
    }

    public DataComponentPatch patch() {
        return this.patch;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PatchedDataComponentMap)) {
            return false;
        }
        PatchedDataComponentMap components = (PatchedDataComponentMap) other;
        return this.resolvedValues.equals(components.resolvedValues)
                && this.patch.getUnknownComponents().equals(components.patch.getUnknownComponents())
                && this.patch.getUnknownRemovedComponents().equals(
                        components.patch.getUnknownRemovedComponents());
    }

    @Override
    public int hashCode() {
        int result = 31 * this.resolvedValues.hashCode() + this.patch.getUnknownComponents().hashCode();
        return 31 * result + this.patch.getUnknownRemovedComponents().hashCode();
    }
}
