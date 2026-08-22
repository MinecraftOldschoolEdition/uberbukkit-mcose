package net.minecraft.server.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable registry-layer stack with downstream invalidation on replacement. */
public final class LayeredRegistryAccess<L> {
    private final List<L> keys;
    private final List<RegistryAccess> values;
    private final RegistryAccess composite;

    public LayeredRegistryAccess(List<L> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("Registry layers cannot be empty");
        }
        ArrayList<L> copiedKeys = new ArrayList<L>(keys);
        if (copiedKeys.contains(null)) {
            throw new IllegalArgumentException("Registry layer cannot be null");
        }
        ArrayList<RegistryAccess> emptyValues = new ArrayList<RegistryAccess>();
        for (int i = 0; i < copiedKeys.size(); i++) emptyValues.add(RegistryAccess.EMPTY);
        this.keys = Collections.unmodifiableList(copiedKeys);
        this.values = Collections.unmodifiableList(emptyValues);
        this.composite = RegistryAccess.EMPTY;
    }

    private LayeredRegistryAccess(List<L> keys, List<RegistryAccess> values) {
        this.keys = keys;
        this.values = Collections.unmodifiableList(new ArrayList<RegistryAccess>(values));
        this.composite = RegistryAccess.merge(this.values);
    }

    public RegistryAccess getLayer(L layer) { return this.values.get(indexOf(layer)); }
    public RegistryAccess getAccessForLoading(L layer) { return composite(0, indexOf(layer)); }
    public RegistryAccess getAccessFrom(L layer) {
        return composite(indexOf(layer), this.values.size());
    }

    public LayeredRegistryAccess<L> replaceFrom(L layer, RegistryAccess... replacements) {
        if (replacements == null) {
            throw new IllegalArgumentException("Replacement layers cannot be null");
        }
        int index = indexOf(layer);
        if (replacements.length > this.values.size() - index) {
            throw new IllegalStateException("Too many registry layers to replace");
        }
        ArrayList<RegistryAccess> next = new ArrayList<RegistryAccess>();
        for (int i = 0; i < index; i++) next.add(this.values.get(i));
        for (int i = 0; i < replacements.length; i++) {
            if (replacements[i] == null) {
                throw new IllegalArgumentException("Replacement layer cannot be null");
            }
            next.add(replacements[i]);
        }
        while (next.size() < this.values.size()) next.add(RegistryAccess.EMPTY);
        return new LayeredRegistryAccess<L>(this.keys, next);
    }

    public RegistryAccess compositeAccess() { return this.composite; }

    private RegistryAccess composite(int from, int to) {
        return RegistryAccess.merge(this.values.subList(from, to));
    }

    private int indexOf(L layer) {
        int index = this.keys.indexOf(layer);
        if (index < 0) throw new IllegalStateException("Unknown registry layer " + layer);
        return index;
    }
}
