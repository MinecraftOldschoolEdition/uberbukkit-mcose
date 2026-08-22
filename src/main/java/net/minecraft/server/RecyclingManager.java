package net.minecraft.server;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.registry.BlockRegistry;
import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.util.ResourceLocation;

/**
 * Resolves durability-proportional recycling from one immutable data snapshot.
 *
 * <p>The legacy numeric lookup remains the gameplay hot path, while the
 * canonical-key view is retained for deterministic synchronization and
 * diagnostics. Data is prepared before the recipe cutover and published in
 * the same API -> crafting -> furnace transaction.</p>
 */
public final class RecyclingManager {
    private static final RecyclingManager instance = new RecyclingManager();

    /** Data-authored recycling method. */
    public enum Method {
        CRAFT,
        SMELT
    }

    /**
     * Retained source/binary compatibility for plugins which referenced the
     * old public enum. New data APIs expose {@link Method}.
     */
    @Deprecated
    public enum RecycleType {
        CRAFT,
        SMELT
    }

    /** One resolved active data definition; tombstones are never published. */
    public static final class Definition {
        private final ResourceLocation inputKey;
        private final int inputItemId;
        private final ResourceLocation resultKey;
        private final int resultItemId;
        private final int count;
        private final int legacyMetadata;
        private final Method method;
        private final boolean legacyInert;

        public Definition(
                ResourceLocation inputKey,
                int inputItemId,
                ResourceLocation resultKey,
                int resultItemId,
                int count,
                int legacyMetadata,
                Method method,
                boolean legacyInert) {
            if (inputKey == null || resultKey == null || method == null) {
                throw new IllegalArgumentException(
                        "Recycling definition keys and method cannot be null");
            }
            if (inputItemId < 0 || resultItemId < 0) {
                throw new IllegalArgumentException(
                        "Recycling legacy item IDs cannot be negative");
            }
            if (count < 1 || count > 64) {
                throw new IllegalArgumentException(
                        "Recycling result count must be between 1 and 64");
            }
            if (legacyMetadata != 0) {
                throw new IllegalArgumentException(
                        "Recycling result legacy metadata must be zero");
            }
            this.inputKey = inputKey;
            this.inputItemId = inputItemId;
            this.resultKey = resultKey;
            this.resultItemId = resultItemId;
            this.count = count;
            this.legacyMetadata = legacyMetadata;
            this.method = method;
            this.legacyInert = legacyInert;
        }

        public ResourceLocation getInputKey() {
            return this.inputKey;
        }

        public int getInputItemId() {
            return this.inputItemId;
        }

        public ResourceLocation getResultKey() {
            return this.resultKey;
        }

        public int getResultItemId() {
            return this.resultItemId;
        }

        public int getCount() {
            return this.count;
        }

        public int getLegacyMetadata() {
            return this.legacyMetadata;
        }

        public Method getMethod() {
            return this.method;
        }

        public boolean isLegacyInert() {
            return this.legacyInert;
        }
    }

    /** Prepared state token used by RecipeRegistryBootstrap's atomic commit. */
    public static final class PreparedState {
        private final State expected;
        private final State next;

        private PreparedState(State expected, State next) {
            this.expected = expected;
            this.next = next;
        }
    }

    private static final class State {
        final Map<ResourceLocation, Definition> byKey;
        final Map<Integer, Definition> byLegacyId;

        State(
                Map<ResourceLocation, Definition> byKey,
                Map<Integer, Definition> byLegacyId) {
            this.byKey = Collections.unmodifiableMap(
                    new LinkedHashMap<ResourceLocation, Definition>(byKey));
            this.byLegacyId = Collections.unmodifiableMap(
                    new LinkedHashMap<Integer, Definition>(byLegacyId));
        }
    }

    /*
     * STARTUP plugins historically could query recycling before central
     * registry cutover. Keep an exact immutable Java oracle until the decoded
     * generation replaces it; reads must never force RecipeRegistryBootstrap.
     */
    private volatile State state = createLegacyOracleState();

    public static RecyclingManager getInstance() {
        return instance;
    }

    private RecyclingManager() {}

    /**
     * Builds an immutable key + legacy-ID candidate without changing live
     * gameplay state. Input map iteration order becomes snapshot order.
     */
    public synchronized PreparedState prepareDataPublication(
            Map<ResourceLocation, Definition> definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("Recycling definitions cannot be null");
        }
        State expected = this.state;
        LinkedHashMap<ResourceLocation, Definition> byKey =
                new LinkedHashMap<ResourceLocation, Definition>();
        LinkedHashMap<Integer, Definition> byLegacyId =
                new LinkedHashMap<Integer, Definition>();
        for (Map.Entry<ResourceLocation, Definition> entry : definitions.entrySet()) {
            ResourceLocation key = entry.getKey();
            Definition definition = entry.getValue();
            if (key == null || definition == null
                    || !key.equals(definition.getInputKey())) {
                throw new IllegalArgumentException(
                        "Recycling definition map key must equal its canonical input key");
            }
            validateResolvedDefinition(definition);
            if (byKey.put(key, definition) != null) {
                throw new IllegalArgumentException(
                        "Duplicate recycling input key " + key);
            }
            Integer legacyId = Integer.valueOf(definition.getInputItemId());
            Definition previous = byLegacyId.put(legacyId, definition);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate logical recycling input " + key + " and "
                                + previous.getInputKey() + " use legacy ID " + legacyId);
            }
        }
        return new PreparedState(expected, new State(byKey, byLegacyId));
    }

    /** Preflight check performed before any recipe owner is mutated. */
    public synchronized boolean canPublishPreparedState(PreparedState prepared) {
        return prepared != null && this.state == prepared.expected;
    }

    /** No-throw reference publication after a successful transaction preflight. */
    public synchronized void publishPreparedState(PreparedState prepared) {
        // A stale public token must never roll a newer generation backward.
        // The recipe transaction holds this monitor and preflights immediately
        // before its first mutation, so its assignment always takes this path.
        if (prepared != null && this.state == prepared.expected) {
            this.state = prepared.next;
        }
    }

    /**
     * Deterministic immutable active definition view. This accessor does not
     * implicitly bootstrap, which also makes failed-startup rollback observable.
     */
    public Map<ResourceLocation, Definition> definitions() {
        return this.state.byKey;
    }

    /**
     * Checks a crafting grid for exactly one occupied slot and applies a CRAFT
     * definition before ordinary recipes, preserving legacy stack semantics.
     */
    public ItemStack getRecycleResult(InventoryCrafting inventory) {
        if (inventory == null) return null;
        State snapshot = this.state;
        ItemStack singleItem = null;
        int occupiedSlots = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null) {
                occupiedSlots++;
                if (occupiedSlots > 1) return null;
                singleItem = stack;
            }
        }
        if (singleItem == null || occupiedSlots != 1) return null;

        Definition definition = snapshot.byLegacyId.get(
                Integer.valueOf(singleItem.id));
        if (definition == null || definition.getMethod() != Method.CRAFT) {
            return null;
        }
        return recycle(singleItem, definition);
    }

    /** Applies a SMELT definition before the ordinary furnace table. */
    public ItemStack getSmeltRecycleResult(ItemStack itemStack) {
        if (itemStack == null) return null;
        State snapshot = this.state;
        Definition definition = snapshot.byLegacyId.get(
                Integer.valueOf(itemStack.id));
        if (definition == null || definition.getMethod() != Method.SMELT) {
            return null;
        }
        return recycle(itemStack, definition);
    }

    public boolean canCraftRecycle(int itemId) {
        Definition definition = this.state.byLegacyId.get(
                Integer.valueOf(itemId));
        return definition != null && definition.getMethod() == Method.CRAFT;
    }

    public boolean canSmeltRecycle(int itemId) {
        Definition definition = this.state.byLegacyId.get(
                Integer.valueOf(itemId));
        return definition != null && definition.getMethod() == Method.SMELT;
    }

    public boolean canRecycle(int itemId) {
        return this.state.byLegacyId.containsKey(Integer.valueOf(itemId));
    }

    /** Exact legacy float/floor formula, deliberately unclamped. */
    private static ItemStack recycle(
            ItemStack itemStack,
            Definition definition) {
        if (!itemStack.d()) return null;
        int maxDamage = itemStack.i();
        int currentDamage = itemStack.g();
        int remainingDurability = maxDamage - currentDamage;
        float durabilityPercent =
                (float)remainingDurability / (float)maxDamage;
        int outputCount = (int)Math.floor(
                definition.getCount() * durabilityPercent);
        if (outputCount < 1) return null;
        return new ItemStack(
                definition.getResultItemId(),
                outputCount,
                definition.getLegacyMetadata());
    }

    private static void validateResolvedDefinition(Definition definition) {
        if (definition.getMethod() == null
                || definition.getCount() < 1 || definition.getCount() > 64
                || definition.getLegacyMetadata() != 0) {
            throw new IllegalArgumentException(
                    "Recycling definition has invalid method/count/metadata");
        }
        Item input = ItemRegistry.get(definition.getInputKey());
        if (input == null
                || !definition.getInputKey().equals(ItemRegistry.getKey(input))
                || input.id != definition.getInputItemId()
                || (input.e() <= 0) != definition.isLegacyInert()) {
            throw new IllegalArgumentException(
                    "Recycling definition has a non-canonical, mismatched, or "
                            + "incorrectly marked inert input "
                            + definition.getInputKey());
        }

        Item resultItem = ItemRegistry.get(definition.getResultKey());
        boolean canonicalItemKey = resultItem != null
                && definition.getResultKey().equals(ItemRegistry.getKey(resultItem));
        Block resultBlock = BlockRegistry.get(definition.getResultKey());
        boolean canonicalBlockKey = resultBlock != null
                && definition.getResultKey().equals(BlockRegistry.getKey(resultBlock));
        if (canonicalItemKey && canonicalBlockKey
                && resultItem.id != resultBlock.id) {
            throw new IllegalArgumentException(
                    "Recycling definition has an ambiguous result "
                            + definition.getResultKey());
        }
        if (canonicalBlockKey && (resultBlock.id < 0
                || resultBlock.id >= Item.byId.length
                || Item.byId[resultBlock.id] == null)) {
            throw new IllegalArgumentException(
                    "Recycling definition result block has no item form "
                            + definition.getResultKey());
        }
        boolean canonicalItem = canonicalItemKey
                && resultItem.id == definition.getResultItemId();
        boolean canonicalBlock = canonicalBlockKey
                && resultBlock.id == definition.getResultItemId();
        if (!canonicalItem && !canonicalBlock) {
            throw new IllegalArgumentException(
                    "Recycling definition has a non-canonical or mismatched result "
                            + definition.getResultKey());
        }
    }

    private static State createLegacyOracleState() {
        LinkedHashMap<ResourceLocation, Definition> byKey =
                new LinkedHashMap<ResourceLocation, Definition>();
        LinkedHashMap<Integer, Definition> byLegacyId =
                new LinkedHashMap<Integer, Definition>();

        addOracle(byKey, byLegacyId, "wooden_pickaxe", Item.WOOD_PICKAXE,
                "oak_planks", Block.WOOD.id, 3, Method.CRAFT);
        addOracle(byKey, byLegacyId, "stone_pickaxe", Item.STONE_PICKAXE,
                "cobblestone", Block.COBBLESTONE.id, 3, Method.SMELT);
        addOracle(byKey, byLegacyId, "iron_pickaxe", Item.IRON_PICKAXE,
                "iron_ingot", Item.IRON_INGOT.id, 3, Method.SMELT);
        addOracle(byKey, byLegacyId, "diamond_pickaxe", Item.DIAMOND_PICKAXE,
                "diamond", Item.DIAMOND.id, 3, Method.SMELT);
        addOracle(byKey, byLegacyId, "golden_pickaxe", Item.GOLD_PICKAXE,
                "gold_ingot", Item.GOLD_INGOT.id, 3, Method.SMELT);

        addOracle(byKey, byLegacyId, "wooden_axe", Item.WOOD_AXE,
                "oak_planks", Block.WOOD.id, 3, Method.CRAFT);
        addOracle(byKey, byLegacyId, "stone_axe", Item.STONE_AXE,
                "cobblestone", Block.COBBLESTONE.id, 3, Method.SMELT);
        addOracle(byKey, byLegacyId, "iron_axe", Item.IRON_AXE,
                "iron_ingot", Item.IRON_INGOT.id, 3, Method.SMELT);
        addOracle(byKey, byLegacyId, "diamond_axe", Item.DIAMOND_AXE,
                "diamond", Item.DIAMOND.id, 3, Method.SMELT);
        addOracle(byKey, byLegacyId, "golden_axe", Item.GOLD_AXE,
                "gold_ingot", Item.GOLD_INGOT.id, 3, Method.SMELT);

        addOracle(byKey, byLegacyId, "wooden_shovel", Item.WOOD_SPADE,
                "oak_planks", Block.WOOD.id, 1, Method.CRAFT);
        addOracle(byKey, byLegacyId, "stone_shovel", Item.STONE_SPADE,
                "cobblestone", Block.COBBLESTONE.id, 1, Method.SMELT);
        addOracle(byKey, byLegacyId, "iron_shovel", Item.IRON_SPADE,
                "iron_ingot", Item.IRON_INGOT.id, 1, Method.SMELT);
        addOracle(byKey, byLegacyId, "diamond_shovel", Item.DIAMOND_SPADE,
                "diamond", Item.DIAMOND.id, 1, Method.SMELT);
        addOracle(byKey, byLegacyId, "golden_shovel", Item.GOLD_SPADE,
                "gold_ingot", Item.GOLD_INGOT.id, 1, Method.SMELT);

        addOracle(byKey, byLegacyId, "wooden_hoe", Item.WOOD_HOE,
                "oak_planks", Block.WOOD.id, 2, Method.CRAFT);
        addOracle(byKey, byLegacyId, "stone_hoe", Item.STONE_HOE,
                "cobblestone", Block.COBBLESTONE.id, 2, Method.SMELT);
        addOracle(byKey, byLegacyId, "iron_hoe", Item.IRON_HOE,
                "iron_ingot", Item.IRON_INGOT.id, 2, Method.SMELT);
        addOracle(byKey, byLegacyId, "diamond_hoe", Item.DIAMOND_HOE,
                "diamond", Item.DIAMOND.id, 2, Method.SMELT);
        addOracle(byKey, byLegacyId, "golden_hoe", Item.GOLD_HOE,
                "gold_ingot", Item.GOLD_INGOT.id, 2, Method.SMELT);

        addOracle(byKey, byLegacyId, "wooden_sword", Item.WOOD_SWORD,
                "oak_planks", Block.WOOD.id, 2, Method.CRAFT);
        addOracle(byKey, byLegacyId, "stone_sword", Item.STONE_SWORD,
                "cobblestone", Block.COBBLESTONE.id, 2, Method.SMELT);
        addOracle(byKey, byLegacyId, "iron_sword", Item.IRON_SWORD,
                "iron_ingot", Item.IRON_INGOT.id, 2, Method.SMELT);
        addOracle(byKey, byLegacyId, "diamond_sword", Item.DIAMOND_SWORD,
                "diamond", Item.DIAMOND.id, 2, Method.SMELT);
        addOracle(byKey, byLegacyId, "golden_sword", Item.GOLD_SWORD,
                "gold_ingot", Item.GOLD_INGOT.id, 2, Method.SMELT);
        addOracle(byKey, byLegacyId, "bow", Item.BOW,
                "string", Item.STRING.id, 3, Method.CRAFT, true);

        addOracle(byKey, byLegacyId, "leather_helmet", Item.LEATHER_HELMET,
                "leather", Item.LEATHER.id, 5, Method.CRAFT);
        addOracle(byKey, byLegacyId, "leather_chestplate", Item.LEATHER_CHESTPLATE,
                "leather", Item.LEATHER.id, 8, Method.CRAFT);
        addOracle(byKey, byLegacyId, "leather_leggings", Item.LEATHER_LEGGINGS,
                "leather", Item.LEATHER.id, 7, Method.CRAFT);
        addOracle(byKey, byLegacyId, "leather_boots", Item.LEATHER_BOOTS,
                "leather", Item.LEATHER.id, 4, Method.CRAFT);

        addOracle(byKey, byLegacyId, "iron_helmet", Item.IRON_HELMET,
                "iron_ingot", Item.IRON_INGOT.id, 5, Method.SMELT);
        addOracle(byKey, byLegacyId, "iron_chestplate", Item.IRON_CHESTPLATE,
                "iron_ingot", Item.IRON_INGOT.id, 8, Method.SMELT);
        addOracle(byKey, byLegacyId, "iron_leggings", Item.IRON_LEGGINGS,
                "iron_ingot", Item.IRON_INGOT.id, 7, Method.SMELT);
        addOracle(byKey, byLegacyId, "iron_boots", Item.IRON_BOOTS,
                "iron_ingot", Item.IRON_INGOT.id, 4, Method.SMELT);

        addOracle(byKey, byLegacyId, "diamond_helmet", Item.DIAMOND_HELMET,
                "diamond", Item.DIAMOND.id, 5, Method.SMELT);
        addOracle(byKey, byLegacyId, "diamond_chestplate", Item.DIAMOND_CHESTPLATE,
                "diamond", Item.DIAMOND.id, 8, Method.SMELT);
        addOracle(byKey, byLegacyId, "diamond_leggings", Item.DIAMOND_LEGGINGS,
                "diamond", Item.DIAMOND.id, 7, Method.SMELT);
        addOracle(byKey, byLegacyId, "diamond_boots", Item.DIAMOND_BOOTS,
                "diamond", Item.DIAMOND.id, 4, Method.SMELT);

        addOracle(byKey, byLegacyId, "golden_helmet", Item.GOLD_HELMET,
                "gold_ingot", Item.GOLD_INGOT.id, 5, Method.SMELT);
        addOracle(byKey, byLegacyId, "golden_chestplate", Item.GOLD_CHESTPLATE,
                "gold_ingot", Item.GOLD_INGOT.id, 8, Method.SMELT);
        addOracle(byKey, byLegacyId, "golden_leggings", Item.GOLD_LEGGINGS,
                "gold_ingot", Item.GOLD_INGOT.id, 7, Method.SMELT);
        addOracle(byKey, byLegacyId, "golden_boots", Item.GOLD_BOOTS,
                "gold_ingot", Item.GOLD_INGOT.id, 4, Method.SMELT);

        addOracle(byKey, byLegacyId, "shears", Item.SHEARS,
                "iron_ingot", Item.IRON_INGOT.id, 2, Method.SMELT);
        addOracle(byKey, byLegacyId, "flint_and_steel", Item.FLINT_AND_STEEL,
                "iron_ingot", Item.IRON_INGOT.id, 1, Method.SMELT);
        addOracle(byKey, byLegacyId, "fishing_rod", Item.FISHING_ROD,
                "string", Item.STRING.id, 2, Method.CRAFT);
        return new State(byKey, byLegacyId);
    }

    private static void addOracle(
            Map<ResourceLocation, Definition> byKey,
            Map<Integer, Definition> byLegacyId,
            String inputPath,
            Item input,
            String resultPath,
            int resultItemId,
            int count,
            Method method) {
        addOracle(byKey, byLegacyId, inputPath, input, resultPath,
                resultItemId, count, method, false);
    }

    private static void addOracle(
            Map<ResourceLocation, Definition> byKey,
            Map<Integer, Definition> byLegacyId,
            String inputPath,
            Item input,
            String resultPath,
            int resultItemId,
            int count,
            Method method,
            boolean legacyInert) {
        ResourceLocation inputKey = new ResourceLocation("minecraft", inputPath);
        Definition definition = new Definition(
                inputKey,
                input.id,
                new ResourceLocation("minecraft", resultPath),
                resultItemId,
                count,
                0,
                method,
                legacyInert);
        if (byKey.put(inputKey, definition) != null
                || byLegacyId.put(Integer.valueOf(input.id), definition) != null) {
            throw new IllegalStateException(
                    "Duplicate legacy recycling oracle input " + inputKey);
        }
    }
}
