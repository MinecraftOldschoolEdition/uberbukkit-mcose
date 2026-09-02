package net.minecraft.server.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.util.ResourceLocation;

/**
 * Immutable, synchronous block-drop program.
 *
 * <p>Unlike container and entity loot, Beta block drops interleave random
 * decisions with entity spawn offsets.  {@link #generate} therefore emits each
 * stack directly to its sink instead of returning or batching a list.</p>
 */
public final class BlockLootTable extends LootTable {
    private static final List<LootPool> NO_LEGACY_POOLS =
            Collections.unmodifiableList(new ArrayList<LootPool>());

    private final List<Pool> blockPools;
    private final Adapter adapter;

    BlockLootTable(ResourceLocation id, List<Pool> pools, Adapter adapter) {
        super(require(id, "Block loot table id"));
        if (pools == null) {
            throw new IllegalArgumentException("Block loot table pools are required");
        }
        this.blockPools = immutableCopy(pools, "Block loot table pool");
        this.adapter = adapter;
    }

    /** Generates and emits drops in exact pool/roll order. */
    public void generate(int rawMetadata, float rawChance, Random random, DropSink sink) {
        if (random == null || sink == null) {
            throw new IllegalArgumentException("Block loot generation requires a random source and sink");
        }
        for (int i = 0; i < blockPools.size(); i++) {
            blockPools.get(i).generate(rawMetadata, rawChance, random, sink);
        }
    }

    public List<Pool> getBlockPools() {
        return blockPools;
    }

    /** Returns the special call-site adapter, or {@code null} for the central seam. */
    public Adapter getAdapter() {
        return adapter;
    }

    /** Block tables cannot be mutated through the legacy loot-table builder API. */
    @Override
    public LootTable addPool(LootPool pool) {
        throw new UnsupportedOperationException("Block loot tables are immutable");
    }

    /** Block pools are deliberately separate from the legacy mutable pool model. */
    @Override
    public List<LootPool> getPools() {
        return NO_LEGACY_POOLS;
    }

    /** Use {@link #generate}; collecting first would alter Beta RNG ordering. */
    @Override
    public List<ItemStack> generateLoot(Random random) {
        throw new UnsupportedOperationException("Block loot must be generated synchronously");
    }

    /** Receives every generated stack immediately, before the next roll begins. */
    public interface DropSink {
        void emit(ItemStack stack, SpawnMode mode);
    }

    public enum SpawnMode {
        BLOCK_DEFAULT("minecraft:block"),
        LEGACY_PLANT("minecraft:legacy_plant");

        private final ResourceLocation id;

        SpawnMode(String id) {
            this.id = new ResourceLocation(id);
        }

        public ResourceLocation getId() {
            return id;
        }
    }

    public enum Adapter {
        MOVING_PISTON("minecraft:moving_piston");

        private final ResourceLocation id;

        Adapter(String id) {
            this.id = new ResourceLocation(id);
        }

        public ResourceLocation getId() {
            return id;
        }
    }

    public static final class Pool {
        private final RollProvider rolls;
        private final List<Condition> conditions;
        private final boolean applyDropChance;
        private final SpawnMode placement;
        private final Entry entry;

        Pool(
                RollProvider rolls,
                List<Condition> conditions,
                boolean applyDropChance,
                SpawnMode placement,
                Entry entry) {
            this.rolls = require(rolls, "Block pool rolls");
            this.conditions = immutableCopy(conditions, "Block pool condition");
            this.applyDropChance = applyDropChance;
            this.placement = require(placement, "Block pool placement");
            this.entry = require(entry, "Block pool entry");
        }

        private void generate(
                int rawMetadata,
                float rawChance,
                Random random,
                DropSink sink) {
            if (!conditionsMatch(conditions, rawMetadata, random)) {
                return;
            }
            int sampledRolls = rolls.sample(random);
            for (int roll = 0; roll < sampledRolls; roll++) {
                if (applyDropChance && !(random.nextFloat() <= rawChance)) {
                    continue;
                }
                entry.generate(rawMetadata, random, placement, sink);
            }
        }

        public RollProvider getRolls() {
            return rolls;
        }

        public List<Condition> getConditions() {
            return conditions;
        }

        public boolean appliesDropChance() {
            return applyDropChance;
        }

        public SpawnMode getPlacement() {
            return placement;
        }

        public Entry getEntry() {
            return entry;
        }
    }

    public abstract static class RollProvider {
        private final ResourceLocation type;

        private RollProvider(String type) {
            this.type = new ResourceLocation(type);
        }

        abstract int sample(Random random);

        public final ResourceLocation getType() {
            return type;
        }
    }

    public static final class ConstantRollProvider extends RollProvider {
        private final int value;

        ConstantRollProvider(int value) {
            super("minecraft:constant");
            this.value = value;
        }

        @Override
        int sample(Random random) {
            return value;
        }

        public int getValue() {
            return value;
        }
    }

    public static final class UniformRollProvider extends RollProvider {
        private final int minimum;
        private final int maximum;

        UniformRollProvider(int minimum, int maximum) {
            super("minecraft:uniform");
            this.minimum = minimum;
            this.maximum = maximum;
        }

        @Override
        int sample(Random random) {
            return minimum == maximum
                    ? minimum
                    : minimum + random.nextInt(maximum - minimum + 1);
        }

        public int getMinimum() {
            return minimum;
        }

        public int getMaximum() {
            return maximum;
        }
    }

    public static final class LegacyOneInRollProvider extends RollProvider {
        private final int chance;

        LegacyOneInRollProvider(int chance) {
            super("minecraft:legacy_one_in");
            this.chance = chance;
        }

        @Override
        int sample(Random random) {
            return random.nextInt(chance) == 0 ? 1 : 0;
        }

        public int getChance() {
            return chance;
        }
    }

    public static final class LegacyShiftedClampedRollProvider extends RollProvider {
        private final int bound;
        private final int add;
        private final int minimum;

        LegacyShiftedClampedRollProvider(int bound, int add, int minimum) {
            super("minecraft:legacy_shifted_clamped");
            this.bound = bound;
            this.add = add;
            this.minimum = minimum;
        }

        @Override
        int sample(Random random) {
            return Math.max(minimum, random.nextInt(bound) + add);
        }

        public int getBound() {
            return bound;
        }

        public int getAdd() {
            return add;
        }

        public int getMinimum() {
            return minimum;
        }
    }

    public abstract static class Condition {
        private final ResourceLocation type;

        private Condition(String type) {
            this.type = new ResourceLocation(type);
        }

        abstract boolean test(int rawMetadata, Random random);

        public final ResourceLocation getType() {
            return type;
        }
    }

    public static final class LegacyMetadataCondition extends Condition {
        private final Integer mask;
        private final Integer equals;
        private final Integer minimum;
        private final Integer maximum;

        LegacyMetadataCondition(
                Integer mask,
                Integer equals,
                Integer minimum,
                Integer maximum) {
            super("minecraft:legacy_metadata");
            this.mask = mask;
            this.equals = equals;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        @Override
        boolean test(int rawMetadata, Random random) {
            int value = mask == null ? rawMetadata : rawMetadata & mask.intValue();
            if (equals != null) {
                return value == equals.intValue();
            }
            return (minimum == null || value >= minimum.intValue())
                    && (maximum == null || value <= maximum.intValue());
        }

        public Integer getMask() {
            return mask;
        }

        public Integer getEquals() {
            return equals;
        }

        public Integer getMinimum() {
            return minimum;
        }

        public Integer getMaximum() {
            return maximum;
        }
    }

    public static final class LegacyRandomIntegerCondition extends Condition {
        private final int bound;
        private final Integer equals;
        private final LessOrEqualMetadata lessOrEqualMetadata;

        LegacyRandomIntegerCondition(
                int bound,
                Integer equals,
                LessOrEqualMetadata lessOrEqualMetadata) {
            super("minecraft:legacy_random_integer");
            this.bound = bound;
            this.equals = equals;
            this.lessOrEqualMetadata = lessOrEqualMetadata;
        }

        @Override
        boolean test(int rawMetadata, Random random) {
            int sampled = random.nextInt(bound);
            if (equals != null) {
                return sampled == equals.intValue();
            }
            return sampled <= lessOrEqualMetadata.threshold(rawMetadata);
        }

        public int getBound() {
            return bound;
        }

        public Integer getEquals() {
            return equals;
        }

        public LessOrEqualMetadata getLessOrEqualMetadata() {
            return lessOrEqualMetadata;
        }
    }

    public static final class LessOrEqualMetadata {
        private final Integer mask;
        private final Integer maximum;

        LessOrEqualMetadata(Integer mask, Integer maximum) {
            this.mask = mask;
            this.maximum = maximum;
        }

        private int threshold(int rawMetadata) {
            int value = mask == null ? rawMetadata : rawMetadata & mask.intValue();
            return maximum == null ? value : Math.min(value, maximum.intValue());
        }

        public Integer getMask() {
            return mask;
        }

        public Integer getMaximum() {
            return maximum;
        }
    }

    public abstract static class Entry {
        private final ResourceLocation type;
        private final List<Condition> conditions;

        private Entry(String type, List<Condition> conditions) {
            this.type = new ResourceLocation(type);
            this.conditions = immutableCopy(conditions, "Block entry condition");
        }

        private boolean generate(
                int rawMetadata,
                Random random,
                SpawnMode placement,
                DropSink sink) {
            return conditionsMatch(conditions, rawMetadata, random)
                    && generateMatched(rawMetadata, random, placement, sink);
        }

        abstract boolean generateMatched(
                int rawMetadata,
                Random random,
                SpawnMode placement,
                DropSink sink);

        public final ResourceLocation getType() {
            return type;
        }

        public final List<Condition> getConditions() {
            return conditions;
        }
    }

    public static final class ItemEntry extends Entry {
        private final ResourceLocation itemId;
        private final Item item;
        private final MetadataProvider legacyMetadata;

        ItemEntry(
                ResourceLocation itemId,
                Item item,
                List<Condition> conditions,
                MetadataProvider legacyMetadata) {
            super("minecraft:item", conditions);
            this.itemId = require(itemId, "Block item entry id");
            this.item = require(item, "Block item entry item");
            this.legacyMetadata = require(legacyMetadata, "Block item entry metadata");
        }

        @Override
        boolean generateMatched(
                int rawMetadata,
                Random random,
                SpawnMode placement,
                DropSink sink) {
            sink.emit(new ItemStack(item, 1, legacyMetadata.resolve(rawMetadata)), placement);
            return true;
        }

        public ResourceLocation getItemId() {
            return itemId;
        }

        public MetadataProvider getLegacyMetadata() {
            return legacyMetadata;
        }
    }

    public static final class EmptyEntry extends Entry {
        EmptyEntry(List<Condition> conditions) {
            super("minecraft:empty", conditions);
        }

        @Override
        boolean generateMatched(
                int rawMetadata,
                Random random,
                SpawnMode placement,
                DropSink sink) {
            return true;
        }
    }

    public static final class AlternativesEntry extends Entry {
        private final List<Entry> children;

        AlternativesEntry(List<Condition> conditions, List<Entry> children) {
            super("minecraft:alternatives", conditions);
            this.children = immutableCopy(children, "Block alternative child");
        }

        @Override
        boolean generateMatched(
                int rawMetadata,
                Random random,
                SpawnMode placement,
                DropSink sink) {
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i).generate(rawMetadata, random, placement, sink)) {
                    return true;
                }
            }
            return false;
        }

        public List<Entry> getChildren() {
            return children;
        }
    }

    public abstract static class MetadataProvider {
        private final ResourceLocation type;

        private MetadataProvider(String type) {
            this.type = new ResourceLocation(type);
        }

        abstract int resolve(int rawMetadata);

        public final ResourceLocation getType() {
            return type;
        }
    }

    public static final class ConstantMetadataProvider extends MetadataProvider {
        private final int value;

        ConstantMetadataProvider(int value) {
            super("minecraft:constant");
            this.value = value;
        }

        @Override
        int resolve(int rawMetadata) {
            return value;
        }

        public int getValue() {
            return value;
        }
    }

    public static final class BlockStateMetadataProvider extends MetadataProvider {
        private final Integer mask;

        BlockStateMetadataProvider(Integer mask) {
            super("minecraft:block_state");
            this.mask = mask;
        }

        @Override
        int resolve(int rawMetadata) {
            return mask == null ? rawMetadata : rawMetadata & mask.intValue();
        }

        public Integer getMask() {
            return mask;
        }
    }

    public static final class LegacyThresholdMetadataProvider extends MetadataProvider {
        private final int threshold;
        private final int belowOrEqual;
        private final int above;

        LegacyThresholdMetadataProvider(int threshold, int belowOrEqual, int above) {
            super("minecraft:legacy_threshold");
            this.threshold = threshold;
            this.belowOrEqual = belowOrEqual;
            this.above = above;
        }

        @Override
        int resolve(int rawMetadata) {
            return rawMetadata <= threshold ? belowOrEqual : above;
        }

        public int getThreshold() {
            return threshold;
        }

        public int getBelowOrEqual() {
            return belowOrEqual;
        }

        public int getAbove() {
            return above;
        }
    }

    private static boolean conditionsMatch(
            List<Condition> conditions,
            int rawMetadata,
            Random random) {
        for (int i = 0; i < conditions.size(); i++) {
            if (!conditions.get(i).test(rawMetadata, random)) {
                return false;
            }
        }
        return true;
    }

    private static <T> T require(T value, String description) {
        if (value == null) {
            throw new IllegalArgumentException(description + " is required");
        }
        return value;
    }

    private static <T> List<T> immutableCopy(List<T> values, String description) {
        if (values == null) {
            throw new IllegalArgumentException(description + " list is required");
        }
        ArrayList<T> copy = new ArrayList<T>(values.size());
        for (int i = 0; i < values.size(); i++) {
            copy.add(require(values.get(i), description));
        }
        return Collections.unmodifiableList(copy);
    }
}
