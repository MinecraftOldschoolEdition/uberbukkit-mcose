package net.minecraft.server.registry;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.Block;
import net.minecraft.server.FireSpreadRule;
import net.minecraft.server.util.ResourceLocation;

/** Public lookup and revision-guarded publication surface for fire spread. */
public final class FireSpreadRegistryApi {
    private static volatile State state = State.empty();
    private static final LinkedHashMap<ResourceLocation, FireSpreadRule>
            runtimeOverrides =
                    new LinkedHashMap<ResourceLocation, FireSpreadRule>();

    private FireSpreadRegistryApi() {}

    /**
     * Publishes one complete generation through a single volatile state swap.
     * Data keys must be the canonical keys of the currently registered blocks.
     */
    public static synchronized boolean publishReplacementAtomic(
            final long blockRegistryRevision,
            Map<ResourceLocation, FireSpreadRule> staged) {
        if (BlockRegistry.registrationRevision() != blockRegistryRevision) {
            return false;
        }

        final long expectedGeneration = state.generation;
        final State candidate = prepareState(
                staged, blockRegistryRevision, expectedGeneration + 1L);
        if (candidate == null
                || BlockRegistry.registrationRevision()
                        != blockRegistryRevision) {
            return false;
        }

        return publishCandidate(
                blockRegistryRevision, expectedGeneration, candidate);
    }

    /**
     * Adds the legacy mod-API override layer without mutating the JSON base.
     * Later data reloads retain this layer, and synchronized fingerprints see
     * the effective rules that gameplay actually consumes.
     */
    public static synchronized boolean registerRuntimeOverride(
            Block block,
            FireSpreadRule rule) {
        if (block == null || rule == null) return false;
        long revision = BlockRegistry.registrationRevision();
        ResourceLocation key = BlockRegistry.getKey(block);
        if (key == null || BlockRegistry.get(key) != block) return false;

        FireSpreadRule previous = runtimeOverrides.put(key, rule);
        if (state.generation == 0L
                || state.blockRegistryRevision != revision) {
            return true;
        }
        if (republishCurrentBase(revision)) return true;
        restoreOverride(key, previous);
        return false;
    }

    /** Removes one legacy mod-API override and republishes the JSON base. */
    public static synchronized boolean removeRuntimeOverride(Block block) {
        if (block == null) return false;
        ResourceLocation key = BlockRegistry.getKey(block);
        if (key == null || !runtimeOverrides.containsKey(key)) return true;

        FireSpreadRule previous = runtimeOverrides.remove(key);
        long revision = BlockRegistry.registrationRevision();
        if (state.generation == 0L
                || state.blockRegistryRevision != revision) {
            return true;
        }
        if (republishCurrentBase(revision)) return true;
        runtimeOverrides.put(key, previous);
        return false;
    }

    public static FireSpreadRule get(ResourceLocation key) {
        if (key == null) return FireSpreadRule.ZERO;
        State current = state;
        Block block = BlockRegistry.get(key);
        if (block != null) {
            FireSpreadRule rule = current.byBlock.get(block);
            if (rule != null) return rule;
        }
        FireSpreadRule direct = current.byKey.get(key);
        return direct == null ? FireSpreadRule.ZERO : direct;
    }

    public static FireSpreadRule getByIdentifier(String identifier) {
        if (identifier == null) return FireSpreadRule.ZERO;
        String normalized = BlockRegistry.normalizeInputIdentifier(identifier);
        return normalized == null
                ? FireSpreadRule.ZERO : get(new ResourceLocation(normalized));
    }

    public static FireSpreadRule get(Block block) {
        if (block == null) return FireSpreadRule.ZERO;
        FireSpreadRule rule = state.byBlock.get(block);
        return rule == null ? FireSpreadRule.ZERO : rule;
    }

    public static int getIgniteOdds(Block block) {
        return get(block).getIgniteOdds();
    }

    public static int getBurnOdds(Block block) {
        return get(block).getBurnOdds();
    }

    public static Set<ResourceLocation> keys() {
        return state.byKey.keySet();
    }

    public static int size() {
        return state.byKey.size();
    }

    /** Immutable semantic snapshot used by parity and rollback tests. */
    public static Map<ResourceLocation, FireSpreadRule> snapshotRules() {
        return state.byKey;
    }

    public static long generation() {
        return state.generation;
    }

    public static long blockRegistryRevision() {
        return state.blockRegistryRevision;
    }

    public static boolean isCurrent() {
        State current = state;
        return current.generation > 0L
                && current.blockRegistryRevision
                        == BlockRegistry.registrationRevision();
    }

    private static State prepareState(
            Map<ResourceLocation, FireSpreadRule> staged,
            long blockRegistryRevision,
            long generation) {
        if (staged == null) return null;

        LinkedHashMap<ResourceLocation, FireSpreadRule> baseByKey =
                new LinkedHashMap<ResourceLocation, FireSpreadRule>();
        for (Map.Entry<ResourceLocation, FireSpreadRule> entry
                : staged.entrySet()) {
            ResourceLocation key = entry.getKey();
            FireSpreadRule rule = entry.getValue();
            if (key == null || rule == null || baseByKey.containsKey(key)) {
                return null;
            }

            Block block = BlockRegistry.get(key);
            ResourceLocation canonical = block == null
                    ? null : BlockRegistry.getKey(block);
            if (block == null || canonical == null || !key.equals(canonical)) {
                return null;
            }
            baseByKey.put(key, rule);
        }
        if (baseByKey.isEmpty()) return null;

        LinkedHashMap<ResourceLocation, FireSpreadRule> byKey =
                new LinkedHashMap<ResourceLocation, FireSpreadRule>(baseByKey);
        byKey.putAll(runtimeOverrides);
        IdentityHashMap<Block, FireSpreadRule> byBlock =
                new IdentityHashMap<Block, FireSpreadRule>();
        for (Map.Entry<ResourceLocation, FireSpreadRule> entry
                : byKey.entrySet()) {
            ResourceLocation key = entry.getKey();
            Block block = BlockRegistry.get(key);
            ResourceLocation canonical = block == null
                    ? null : BlockRegistry.getKey(block);
            if (block == null || canonical == null || !key.equals(canonical)
                    || byBlock.containsKey(block)) {
                return null;
            }
            byBlock.put(block, entry.getValue());
        }
        return State.of(
                baseByKey, byKey, byBlock,
                blockRegistryRevision, generation);
    }

    private static boolean republishCurrentBase(long blockRegistryRevision) {
        long expectedGeneration = state.generation;
        State candidate = prepareState(
                state.baseByKey,
                blockRegistryRevision,
                expectedGeneration + 1L);
        return candidate != null
                && publishCandidate(
                        blockRegistryRevision,
                        expectedGeneration,
                        candidate);
    }

    private static boolean publishCandidate(
            final long blockRegistryRevision,
            final long expectedGeneration,
            final State candidate) {
        final boolean[] published = new boolean[]{false};
        boolean revisionMatched = BlockRegistry.publishIfRevision(
                blockRegistryRevision,
                new Runnable() {
                    public void run() {
                        synchronized (FireSpreadRegistryApi.class) {
                            if (state.generation == expectedGeneration) {
                                state = candidate;
                                published[0] = true;
                            }
                        }
                    }
                });
        return revisionMatched && published[0];
    }

    private static void restoreOverride(
            ResourceLocation key,
            FireSpreadRule previous) {
        if (previous == null) {
            runtimeOverrides.remove(key);
        } else {
            runtimeOverrides.put(key, previous);
        }
    }

    private static final class State {
        final Map<ResourceLocation, FireSpreadRule> baseByKey;
        final Map<ResourceLocation, FireSpreadRule> byKey;
        final Map<Block, FireSpreadRule> byBlock;
        final long blockRegistryRevision;
        final long generation;

        private State(
                Map<ResourceLocation, FireSpreadRule> baseByKey,
                Map<ResourceLocation, FireSpreadRule> byKey,
                Map<Block, FireSpreadRule> byBlock,
                long blockRegistryRevision,
                long generation) {
            this.baseByKey = baseByKey;
            this.byKey = byKey;
            this.byBlock = byBlock;
            this.blockRegistryRevision = blockRegistryRevision;
            this.generation = generation;
        }

        static State empty() {
            return of(
                    new LinkedHashMap<ResourceLocation, FireSpreadRule>(),
                    new LinkedHashMap<ResourceLocation, FireSpreadRule>(),
                    new IdentityHashMap<Block, FireSpreadRule>(),
                    -1L,
                    0L);
        }

        static State of(
                Map<ResourceLocation, FireSpreadRule> baseByKey,
                Map<ResourceLocation, FireSpreadRule> byKey,
                Map<Block, FireSpreadRule> byBlock,
                long blockRegistryRevision,
                long generation) {
            return new State(
                    Collections.unmodifiableMap(
                            new LinkedHashMap<ResourceLocation, FireSpreadRule>(
                                    baseByKey)),
                    Collections.unmodifiableMap(
                            new LinkedHashMap<ResourceLocation, FireSpreadRule>(
                                    byKey)),
                    Collections.unmodifiableMap(
                            new IdentityHashMap<Block, FireSpreadRule>(byBlock)),
                    blockRegistryRevision,
                    generation);
        }
    }
}
