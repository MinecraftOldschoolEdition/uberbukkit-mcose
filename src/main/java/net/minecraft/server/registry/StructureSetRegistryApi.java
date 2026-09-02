package net.minecraft.server.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.util.ResourceLocation;

/** Immutable, atomically replaced generations of structure-set data. */
public final class StructureSetRegistryApi {
    private static volatile State state = State.empty();

    private StructureSetRegistryApi() {}

    public static synchronized boolean publishReplacementAtomic(
            Map<ResourceLocation, StructureSetDefinition> staged) {
        State candidate = State.prepare(staged, state.generation + 1L);
        if (candidate == null) return false;
        state = candidate;
        return true;
    }

    public static StructureSetDefinition get(ResourceLocation key) {
        ensureInitialized();
        return key == null ? null : state.values.get(key);
    }

    public static Set<ResourceLocation> keys() {
        ensureInitialized();
        return state.values.keySet();
    }

    public static Collection<StructureSetDefinition> values() {
        ensureInitialized();
        return state.values.values();
    }

    public static Map<ResourceLocation, StructureSetDefinition> snapshot() {
        ensureInitialized();
        return state.values;
    }

    public static int size() {
        ensureInitialized();
        return state.values.size();
    }

    public static long generation() {
        ensureInitialized();
        return state.generation;
    }

    private static void ensureInitialized() {
        if (state.generation == 0L) {
            StructureSetDataBootstrap.initialize();
        }
    }

    private static final class State {
        final Map<ResourceLocation, StructureSetDefinition> values;
        final long generation;

        private State(
                Map<ResourceLocation, StructureSetDefinition> values,
                long generation) {
            this.values = values;
            this.generation = generation;
        }

        static State empty() {
            return new State(
                    Collections.<ResourceLocation, StructureSetDefinition>
                            emptyMap(),
                    0L);
        }

        static State prepare(
                Map<ResourceLocation, StructureSetDefinition> staged,
                long generation) {
            if (staged == null || staged.isEmpty()) return null;
            LinkedHashMap<ResourceLocation, StructureSetDefinition> copy =
                    new LinkedHashMap<ResourceLocation,
                            StructureSetDefinition>();
            for (Map.Entry<ResourceLocation, StructureSetDefinition> entry
                    : staged.entrySet()) {
                ResourceLocation key = entry.getKey();
                StructureSetDefinition definition = entry.getValue();
                if (key == null || definition == null
                        || !key.equals(definition.getId())
                        || copy.put(key, definition) != null) {
                    return null;
                }
            }
            return new State(
                    Collections.unmodifiableMap(copy), generation);
        }
    }
}
