package net.minecraft.server.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.Item;
import net.minecraft.server.util.ResourceLocation;

/** Decodes the legacy-supported subset of the modern loot-table schema. */
public final class LootTableCodec {
    private static final int MAX_POOLS = 1024;
    private static final int MAX_ENTRIES_PER_POOL = 4096;
    private static final int MAX_ROLLS = 1024;
    private static final int MAX_STACK_COUNT = 64;
    private static final int MAX_WEIGHT = 1000000;
    private static final int MAX_ENTRY_DEPTH = 32;
    private static final String[] SHEEP_COLORS = new String[] {
            "white", "orange", "magenta", "light_blue",
            "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black"
    };

    private LootTableCodec() {}

    public static LootTable decode(ResourceLocation key, JsonObject json) {
        if (key == null || json == null) {
            throw new IllegalArgumentException("Loot table key and data are required");
        }
        String type = requiredString(json, "type");
        if ("minecraft:chest".equals(type)) {
            return decodeChest(key, json);
        }
        if ("minecraft:entity".equals(type)) {
            return decodeEntity(key, json);
        }
        if ("minecraft:block".equals(type)) {
            return decodeBlock(key, json);
        }
        throw new IllegalArgumentException("loot table type must be 'minecraft:chest', "
                + "'minecraft:entity', or 'minecraft:block' (was '" + type + "')");
    }

    private static BlockLootTable decodeBlock(ResourceLocation key, JsonObject json) {
        requireOnlyFields(json, "block loot table", "type", "pools", "adapter");
        requireValue("minecraft:block", requiredString(json, "type"), "block loot table type");
        if (!key.getPath().startsWith("blocks/")
                || key.getPath().length() == "blocks/".length()) {
            throw new IllegalArgumentException(
                    "block loot table key must have a non-empty blocks/ path (was '"
                            + key + "')");
        }

        JsonArray poolsJson = requiredArray(json, "pools");
        if (poolsJson.size() > MAX_POOLS) {
            throw new IllegalArgumentException("block pools must contain at most "
                    + MAX_POOLS + " entries");
        }
        List<BlockLootTable.Pool> pools = new ArrayList<BlockLootTable.Pool>();
        for (int poolIndex = 0; poolIndex < poolsJson.size(); poolIndex++) {
            pools.add(decodeBlockPool(
                    objectAt(poolsJson, poolIndex, "block pool"),
                    "block pool " + poolIndex));
        }

        BlockLootTable.Adapter adapter = null;
        if (json.has("adapter")) {
            String adapterId = requiredString(json, "adapter");
            if (!"minecraft:moving_piston".equals(adapterId)) {
                throw new IllegalArgumentException("unsupported block loot adapter '"
                        + adapterId + "'");
            }
            adapter = BlockLootTable.Adapter.MOVING_PISTON;
        }
        if (adapter == BlockLootTable.Adapter.MOVING_PISTON && !pools.isEmpty()) {
            throw new IllegalArgumentException(
                    "minecraft:moving_piston adapter tables must contain zero pools");
        }
        return new BlockLootTable(key, pools, adapter);
    }

    private static BlockLootTable.Pool decodeBlockPool(
            JsonObject json,
            String description) {
        requireOnlyFields(json, description,
                "rolls", "conditions", "apply_drop_chance", "placement", "entries");
        BlockLootTable.RollProvider rolls = decodeBlockRolls(
                requiredValue(json, "rolls"), description + " rolls");
        List<BlockLootTable.Condition> conditions = decodeBlockConditions(
                json, "conditions", description + " conditions");

        boolean applyDropChance = true;
        if (json.has("apply_drop_chance")) {
            applyDropChance = requiredBoolean(json, "apply_drop_chance");
        }

        BlockLootTable.SpawnMode placement = BlockLootTable.SpawnMode.BLOCK_DEFAULT;
        if (json.has("placement")) {
            String placementId = requiredString(json, "placement");
            if ("minecraft:block".equals(placementId)) {
                placement = BlockLootTable.SpawnMode.BLOCK_DEFAULT;
            } else if ("minecraft:legacy_plant".equals(placementId)) {
                placement = BlockLootTable.SpawnMode.LEGACY_PLANT;
            } else {
                throw new IllegalArgumentException(description
                        + " has unsupported placement '" + placementId + "'");
            }
        }

        JsonArray entries = requiredArray(json, "entries");
        if (entries.size() != 1) {
            throw new IllegalArgumentException(description
                    + " entries must contain exactly one root entry");
        }
        BlockLootTable.Entry entry = decodeBlockEntry(
                objectAt(entries, 0, description + " entry"),
                description + " entry",
                0);
        return new BlockLootTable.Pool(
                rolls, conditions, applyDropChance, placement, entry);
    }

    private static BlockLootTable.RollProvider decodeBlockRolls(
            JsonElement raw,
            String description) {
        if (isNumber(raw)) {
            int value = exactInteger(raw, description);
            checkRange(value, description, 0, MAX_ROLLS);
            return new BlockLootTable.ConstantRollProvider(value);
        }
        if (raw == null || !raw.isJsonObject()) {
            throw new IllegalArgumentException(description
                    + " must be an integer or supported roll provider");
        }
        JsonObject provider = raw.getAsJsonObject();
        String type = requiredString(provider, "type");
        if ("minecraft:uniform".equals(type)) {
            requireOnlyFields(provider, description, "type", "min", "max");
            int minimum = exactInteger(requiredValue(provider, "min"),
                    description + " minimum");
            int maximum = exactInteger(requiredValue(provider, "max"),
                    description + " maximum");
            checkRange(minimum, description + " minimum", 0, MAX_ROLLS);
            checkRange(maximum, description + " maximum", 0, MAX_ROLLS);
            if (maximum < minimum) {
                throw new IllegalArgumentException(description
                        + " maximum cannot be below its minimum");
            }
            return new BlockLootTable.UniformRollProvider(minimum, maximum);
        }
        if ("minecraft:legacy_one_in".equals(type)) {
            requireOnlyFields(provider, description, "type", "chance");
            int chance = exactInteger(requiredValue(provider, "chance"),
                    description + " chance");
            checkRange(chance, description + " chance", 1, Integer.MAX_VALUE);
            return new BlockLootTable.LegacyOneInRollProvider(chance);
        }
        if ("minecraft:legacy_shifted_clamped".equals(type)) {
            requireOnlyFields(provider, description,
                    "type", "bound", "add", "minimum");
            int bound = exactInteger(requiredValue(provider, "bound"),
                    description + " bound");
            int add = exactInteger(requiredValue(provider, "add"),
                    description + " add");
            int minimum = exactInteger(requiredValue(provider, "minimum"),
                    description + " minimum");
            checkRange(bound, description + " bound", 1, Integer.MAX_VALUE);
            checkRange(minimum, description + " minimum", 0, MAX_ROLLS);
            long lowest = Math.max((long)minimum, (long)add);
            long highest = Math.max((long)minimum, (long)add + (long)bound - 1L);
            if (lowest < 0L || highest > MAX_ROLLS) {
                throw new IllegalArgumentException(description
                        + " must always produce between 0 and " + MAX_ROLLS + " rolls");
            }
            return new BlockLootTable.LegacyShiftedClampedRollProvider(
                    bound, add, minimum);
        }
        throw new IllegalArgumentException(description
                + " has unsupported roll provider type '" + type + "'");
    }

    private static BlockLootTable.Entry decodeBlockEntry(
            JsonObject json,
            String description,
            int depth) {
        if (depth > MAX_ENTRY_DEPTH) {
            throw new IllegalArgumentException(description + " exceeds maximum nesting depth");
        }
        String type = requiredString(json, "type");
        if ("minecraft:item".equals(type)) {
            requireOnlyFields(json, description,
                    "type", "name", "conditions", "legacy_metadata");
            ResourceLocation itemId = new ResourceLocation(requiredString(json, "name"));
            Item item = resolveItem(itemId);
            if (item == null) {
                throw new IllegalArgumentException(description
                        + " references unknown item " + itemId);
            }
            return new BlockLootTable.ItemEntry(
                    itemId,
                    item,
                    decodeBlockConditions(json, "conditions", description + " conditions"),
                    decodeBlockMetadata(
                            requiredValue(json, "legacy_metadata"),
                            description + " legacy_metadata"));
        }
        if ("minecraft:empty".equals(type)) {
            requireOnlyFields(json, description, "type", "conditions");
            return new BlockLootTable.EmptyEntry(
                    decodeBlockConditions(json, "conditions", description + " conditions"));
        }
        if ("minecraft:alternatives".equals(type)) {
            requireOnlyFields(json, description, "type", "children", "conditions");
            JsonArray childrenJson = requiredArray(json, "children");
            if (childrenJson.size() == 0
                    || childrenJson.size() > MAX_ENTRIES_PER_POOL) {
                throw new IllegalArgumentException(description + " children must contain 1-"
                        + MAX_ENTRIES_PER_POOL + " entries");
            }
            List<BlockLootTable.Entry> children = new ArrayList<BlockLootTable.Entry>();
            for (int childIndex = 0; childIndex < childrenJson.size(); childIndex++) {
                children.add(decodeBlockEntry(
                        objectAt(childrenJson, childIndex, "alternative child"),
                        description + " child " + childIndex,
                        depth + 1));
            }
            return new BlockLootTable.AlternativesEntry(
                    decodeBlockConditions(json, "conditions", description + " conditions"),
                    children);
        }
        throw new IllegalArgumentException(description
                + " has unsupported entry type '" + type + "'");
    }

    private static BlockLootTable.MetadataProvider decodeBlockMetadata(
            JsonElement raw,
            String description) {
        if (isNumber(raw)) {
            int value = exactInteger(raw, description);
            checkRange(value, description, 0, 32767);
            return new BlockLootTable.ConstantMetadataProvider(value);
        }
        if (raw == null || !raw.isJsonObject()) {
            throw new IllegalArgumentException(description
                    + " must be an integer or supported metadata provider");
        }
        JsonObject provider = raw.getAsJsonObject();
        String type = requiredString(provider, "type");
        if ("minecraft:block_state".equals(type)) {
            requireOnlyFields(provider, description, "type", "mask");
            Integer mask = optionalNonNegativeInteger(provider, "mask", description + " mask");
            return new BlockLootTable.BlockStateMetadataProvider(mask);
        }
        if ("minecraft:legacy_threshold".equals(type)) {
            requireOnlyFields(provider, description,
                    "type", "threshold", "below_or_equal", "above");
            int threshold = exactInteger(requiredValue(provider, "threshold"),
                    description + " threshold");
            int belowOrEqual = exactInteger(requiredValue(provider, "below_or_equal"),
                    description + " below_or_equal");
            int above = exactInteger(requiredValue(provider, "above"),
                    description + " above");
            checkRange(belowOrEqual, description + " below_or_equal", 0, 32767);
            checkRange(above, description + " above", 0, 32767);
            return new BlockLootTable.LegacyThresholdMetadataProvider(
                    threshold, belowOrEqual, above);
        }
        throw new IllegalArgumentException(description
                + " has unsupported metadata provider type '" + type + "'");
    }

    private static List<BlockLootTable.Condition> decodeBlockConditions(
            JsonObject owner,
            String field,
            String description) {
        List<BlockLootTable.Condition> conditions =
                new ArrayList<BlockLootTable.Condition>();
        if (!owner.has(field)) {
            return conditions;
        }
        JsonArray conditionsJson = requiredArray(owner, field);
        if (conditionsJson.size() > MAX_ENTRIES_PER_POOL) {
            throw new IllegalArgumentException(description + " must contain at most "
                    + MAX_ENTRIES_PER_POOL + " entries");
        }
        for (int conditionIndex = 0;
                conditionIndex < conditionsJson.size();
                conditionIndex++) {
            conditions.add(decodeBlockCondition(
                    objectAt(conditionsJson, conditionIndex, "condition"),
                    description + " " + conditionIndex));
        }
        return conditions;
    }

    private static BlockLootTable.Condition decodeBlockCondition(
            JsonObject json,
            String description) {
        String type = requiredString(json, "condition");
        if ("minecraft:legacy_metadata".equals(type)) {
            requireOnlyFields(json, description,
                    "condition", "mask", "equals", "min", "max");
            Integer mask = optionalNonNegativeInteger(json, "mask", description + " mask");
            Integer equals = optionalExactInteger(json, "equals", description + " equals");
            Integer minimum = optionalExactInteger(json, "min", description + " minimum");
            Integer maximum = optionalExactInteger(json, "max", description + " maximum");
            boolean hasRange = minimum != null || maximum != null;
            if ((equals != null) == hasRange) {
                throw new IllegalArgumentException(description
                        + " must define exactly equals or a min/max range");
            }
            if (minimum != null && maximum != null
                    && minimum.intValue() > maximum.intValue()) {
                throw new IllegalArgumentException(description
                        + " maximum cannot be below its minimum");
            }
            return new BlockLootTable.LegacyMetadataCondition(
                    mask, equals, minimum, maximum);
        }
        if ("minecraft:legacy_random_integer".equals(type)) {
            requireOnlyFields(json, description,
                    "condition", "bound", "equals", "less_or_equal_metadata");
            int bound = exactInteger(requiredValue(json, "bound"), description + " bound");
            checkRange(bound, description + " bound", 1, Integer.MAX_VALUE);
            boolean hasEquals = json.has("equals");
            boolean hasMetadata = json.has("less_or_equal_metadata");
            if (hasEquals == hasMetadata) {
                throw new IllegalArgumentException(description
                        + " must define exactly equals or less_or_equal_metadata");
            }
            if (hasEquals) {
                int equals = exactInteger(json.get("equals"), description + " equals");
                checkRange(equals, description + " equals", 0, bound - 1);
                return new BlockLootTable.LegacyRandomIntegerCondition(
                        bound, Integer.valueOf(equals), null);
            }
            JsonObject thresholdJson = requiredObject(json, "less_or_equal_metadata");
            requireOnlyFields(thresholdJson, description + " less_or_equal_metadata",
                    "mask", "maximum");
            BlockLootTable.LessOrEqualMetadata threshold =
                    new BlockLootTable.LessOrEqualMetadata(
                            optionalNonNegativeInteger(
                                    thresholdJson,
                                    "mask",
                                    description + " less_or_equal_metadata mask"),
                            optionalExactInteger(
                                    thresholdJson,
                                    "maximum",
                                    description + " less_or_equal_metadata maximum"));
            return new BlockLootTable.LegacyRandomIntegerCondition(bound, null, threshold);
        }
        throw new IllegalArgumentException(description
                + " has unsupported condition type '" + type + "'");
    }


    private static LootTable decodeChest(ResourceLocation key, JsonObject json) {
        requireOnlyFields(json, "loot table", "type", "random_sequence", "pools");
        requireValue("minecraft:chest", requiredString(json, "type"), "loot table type");
        if (json.has("random_sequence")) {
            requireValue(key.toString(), requiredString(json, "random_sequence"), "random_sequence");
        }

        JsonArray pools = requiredArray(json, "pools");
        if (pools.size() == 0 || pools.size() > MAX_POOLS) {
            throw new IllegalArgumentException("pools must contain 1-" + MAX_POOLS + " entries");
        }

        LootTable table = new LootTable(key);
        Set<String> names = new HashSet<String>();
        for (int poolIndex = 0; poolIndex < pools.size(); poolIndex++) {
            JsonObject poolJson = objectAt(pools, poolIndex, "pool");
            requireOnlyFields(poolJson, "pool " + poolIndex, "name", "rolls", "entries");
            String name = requiredString(poolJson, "name").trim();
            if (name.length() == 0 || !names.add(name)) {
                throw new IllegalArgumentException("pool " + poolIndex + " has an empty or duplicate name");
            }
            IntRange rolls = numberProvider(poolJson.get("rolls"), "pool '" + name + "' rolls", 0, MAX_ROLLS);
            JsonArray entries = requiredArray(poolJson, "entries");
            if (entries.size() == 0 || entries.size() > MAX_ENTRIES_PER_POOL) {
                throw new IllegalArgumentException("pool '" + name + "' entries must contain 1-"
                        + MAX_ENTRIES_PER_POOL + " values");
            }

            LootPool pool = new LootPool(name, rolls.minimum, rolls.maximum);
            long totalWeight = 0L;
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                JsonObject entryJson = objectAt(entries, entryIndex, "entry");
                int weight = optionalInteger(entryJson, "weight", 1, 1, MAX_WEIGHT);
                totalWeight += weight;
                if (totalWeight > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("pool '" + name + "' total weight exceeds integer range");
                }
                pool.addEntry(decodeChestEntry(entryJson, weight, name, entryIndex));
            }
            table.addPool(pool);
        }
        return table;
    }

    private static EntityLootTable decodeEntity(ResourceLocation key, JsonObject json) {
        requireOnlyFields(json, "entity loot table", "type", "random_sequence", "pools");
        requireValue("minecraft:entity", requiredString(json, "type"), "loot table type");
        requireValue(key.toString(), requiredString(json, "random_sequence"), "random_sequence");

        JsonArray pools = requiredArray(json, "pools");
        if (pools.size() == 0 || pools.size() > MAX_POOLS) {
            throw new IllegalArgumentException("pools must contain 1-" + MAX_POOLS + " entries");
        }

        EntityLootTable table = new EntityLootTable(key);
        for (int poolIndex = 0; poolIndex < pools.size(); poolIndex++) {
            JsonObject poolJson = objectAt(pools, poolIndex, "pool");
            requireOnlyFields(poolJson, "entity pool " + poolIndex,
                    "rolls", "entries", "condition");
            IntRange rolls = numberProvider(
                    poolJson.get("rolls"),
                    "entity pool " + poolIndex + " rolls",
                    1,
                    1);
            if (rolls.minimum != 1 || rolls.maximum != 1) {
                throw new IllegalArgumentException("entity pool rolls must be exactly 1");
            }

            JsonArray entries = requiredArray(poolJson, "entries");
            if (entries.size() != 1) {
                throw new IllegalArgumentException(
                        "entity pool " + poolIndex + " must contain exactly one entry");
            }
            EntityLootTable.EntityCondition condition = poolJson.has("condition")
                    ? decodeEntityCondition(requiredObject(poolJson, "condition"),
                            "entity pool " + poolIndex + " condition")
                    : EntityLootTable.EntityCondition.ALWAYS;
            table.addEntityPool(new EntityLootTable.EntityPool(
                    condition,
                    decodeEntityEntry(objectAt(entries, 0, "entity entry"),
                            "entity pool " + poolIndex + " entry", 0)));
        }
        return table;
    }

    private static EntityLootTable.EntityEntry decodeEntityEntry(
            JsonObject json,
            String description,
            int depth) {
        if (depth > MAX_ENTRY_DEPTH) {
            throw new IllegalArgumentException(description + " exceeds maximum nesting depth");
        }
        String type = requiredString(json, "type");
        EntityLootTable.EntityCondition condition = json.has("condition")
                ? decodeEntityCondition(requiredObject(json, "condition"),
                        description + " condition")
                : EntityLootTable.EntityCondition.ALWAYS;

        if ("minecraft:alternatives".equals(type)) {
            requireOnlyFields(json, description, "type", "condition", "children");
            JsonArray childrenJson = requiredArray(json, "children");
            if (childrenJson.size() == 0 || childrenJson.size() > MAX_ENTRIES_PER_POOL) {
                throw new IllegalArgumentException(description + " children must contain 1-"
                        + MAX_ENTRIES_PER_POOL + " entries");
            }
            List<EntityLootTable.EntityEntry> children =
                    new ArrayList<EntityLootTable.EntityEntry>();
            for (int i = 0; i < childrenJson.size(); i++) {
                children.add(decodeEntityEntry(
                        objectAt(childrenJson, i, "alternative child"),
                        description + " child " + i,
                        depth + 1));
            }
            return new EntityLootTable.AlternativesEntry(condition, children);
        }

        requireValue("minecraft:item", type, "entity entry type");
        requireOnlyFields(json, description,
                "type", "name", "condition", "modifier", "legacy_metadata");
        ResourceLocation itemId = new ResourceLocation(requiredString(json, "name"));
        if (!resolvesItem(itemId)) {
            throw new IllegalArgumentException(description + " references unknown item " + itemId);
        }
        IntRange count = new IntRange(1, 1);
        if (json.has("modifier")) {
            JsonElement rawModifier = json.get("modifier");
            JsonObject modifier;
            if (rawModifier != null && rawModifier.isJsonObject()) {
                modifier = rawModifier.getAsJsonObject();
            } else if (rawModifier != null && rawModifier.isJsonArray()) {
                JsonArray modifiers = rawModifier.getAsJsonArray();
                if (modifiers.size() != 1) {
                    throw new IllegalArgumentException(
                            description + " modifier array must contain exactly one modifier");
                }
                modifier = objectAt(modifiers, 0, "modifier");
            } else {
                throw new IllegalArgumentException(description + " modifier must be an object "
                        + "or one-element array");
            }
            requireOnlyFields(modifier, description + " modifier", "type", "count");
            requireValue("minecraft:set_count", requiredString(modifier, "type"),
                    "entity modifier type");
            count = numberProvider(
                    modifier.get("count"),
                    description + " set_count count",
                    0,
                    MAX_STACK_COUNT);
        }
        int metadata = optionalInteger(json, "legacy_metadata", 0, 0, 32767);
        return new EntityLootTable.ItemEntry(
                condition,
                itemId,
                new EntityLootTable.EntityCountProvider(count.minimum, count.maximum),
                metadata);
    }

    private static EntityLootTable.EntityCondition decodeEntityCondition(
            JsonObject condition,
            String description) {
        requireOnlyFields(condition, description, "type", "entity", "predicate");
        requireValue("minecraft:entity_properties", requiredString(condition, "type"),
                description + " type");
        requireValue("this", requiredString(condition, "entity"), description + " entity");

        JsonObject predicate = requiredObject(condition, "predicate");
        requireOnlyFields(predicate, description + " predicate",
                "minecraft:flags",
                "minecraft:type_specific/cube_mob",
                "minecraft:type_specific/sheep",
                "minecraft:components");
        if (predicate.entrySet().isEmpty()) {
            throw new IllegalArgumentException(description + " predicate cannot be empty");
        }

        Boolean onFire = null;
        Integer cubeSize = null;
        Boolean sheepSheared = null;
        Integer sheepColor = null;

        if (predicate.has("minecraft:flags")) {
            JsonObject flags = requiredObject(predicate, "minecraft:flags");
            requireOnlyFields(flags, description + " flags", "is_on_fire");
            if (flags.entrySet().isEmpty()) {
                throw new IllegalArgumentException(description + " flags cannot be empty");
            }
            onFire = Boolean.valueOf(requiredBoolean(flags, "is_on_fire"));
        }
        if (predicate.has("minecraft:type_specific/cube_mob")) {
            JsonObject cube = requiredObject(predicate, "minecraft:type_specific/cube_mob");
            requireOnlyFields(cube, description + " cube mob", "size");
            cubeSize = Integer.valueOf(exactInteger(
                    cube.get("size"), description + " cube mob size"));
            checkRange(cubeSize.intValue(), description + " cube mob size", 1, 127);
        }
        if (predicate.has("minecraft:type_specific/sheep")) {
            JsonObject sheep = requiredObject(predicate, "minecraft:type_specific/sheep");
            requireOnlyFields(sheep, description + " sheep", "sheared");
            sheepSheared = Boolean.valueOf(requiredBoolean(sheep, "sheared"));
        }
        if (predicate.has("minecraft:components")) {
            JsonObject components = requiredObject(predicate, "minecraft:components");
            requireOnlyFields(components, description + " components", "minecraft:sheep/color");
            sheepColor = Integer.valueOf(decodeSheepColor(
                    requiredString(components, "minecraft:sheep/color"),
                    description + " sheep color"));
        }

        return new EntityLootTable.EntityCondition(
                onFire, cubeSize, sheepSheared, sheepColor);
    }

    private static int decodeSheepColor(String color, String description) {
        for (int i = 0; i < SHEEP_COLORS.length; i++) {
            if (SHEEP_COLORS[i].equals(color)) return i;
        }
        throw new IllegalArgumentException(description + " is unsupported: " + color);
    }

    private static boolean requiredBoolean(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(
                    "missing or invalid required boolean field '" + field + "'");
        }
        return value.getAsBoolean();
    }

    private static LootEntry decodeChestEntry(
            JsonObject json,
            int weight,
            String poolName,
            int entryIndex) {
        String type = requiredString(json, "type");
        if ("minecraft:empty".equals(type)) {
            requireOnlyFields(json, "empty entry " + entryIndex + " in pool '"
                    + poolName + "'", "type", "weight");
            return new LootEntry(new ResourceLocation("minecraft", "air"), weight, 1, 1, 0);
        }
        requireValue("minecraft:item", type, "entry type");
        requireOnlyFields(json, "item entry " + entryIndex + " in pool '"
                + poolName + "'", "type", "name", "weight", "modifier", "legacy_metadata");

        ResourceLocation itemId = new ResourceLocation(requiredString(json, "name"));
        if (!resolvesItem(itemId)) {
            throw new IllegalArgumentException("entry " + entryIndex + " in pool '" + poolName
                    + "' references unknown item " + itemId);
        }

        IntRange count = new IntRange(1, 1);
        if (json.has("modifier")) {
            JsonObject modifier = requiredObject(json, "modifier");
            requireOnlyFields(modifier, "modifier", "type", "count");
            requireValue("minecraft:set_count", requiredString(modifier, "type"), "modifier type");
            count = numberProvider(modifier.get("count"), "set_count count", 1, MAX_STACK_COUNT);
        }
        int metadata = optionalInteger(json, "legacy_metadata", 0, 0, 32767);
        return new LootEntry(itemId, weight, count.minimum, count.maximum, metadata);
    }

    private static boolean resolvesItem(ResourceLocation itemId) {
        return resolveItem(itemId) != null;
    }

    private static Item resolveItem(ResourceLocation itemId) {
        Item item = ItemRegistry.get(itemId);
        if (item != null) return item;
        Integer legacyId = ItemRegistry.getId(itemId.toString());
        if (legacyId != null
                && legacyId.intValue() >= 0
                && legacyId.intValue() < Item.byId.length
                && Item.byId[legacyId.intValue()] != null) {
            return Item.byId[legacyId.intValue()];
        }
        return null;
    }

    private static IntRange numberProvider(
            JsonElement raw,
            String description,
            int minimum,
            int maximum) {
        if (isNumber(raw)) {
            int value = exactInteger(raw, description);
            checkRange(value, description, minimum, maximum);
            return new IntRange(value, value);
        }
        if (raw == null || !raw.isJsonObject()) {
            throw new IllegalArgumentException(description + " must be an integer or number provider");
        }
        JsonObject provider = raw.getAsJsonObject();
        requireOnlyFields(provider, description + " provider", "type", "min", "max");
        requireValue("minecraft:uniform", requiredString(provider, "type"), description + " provider type");
        int min = exactInteger(provider.get("min"), description + " minimum");
        int max = exactInteger(provider.get("max"), description + " maximum");
        checkRange(min, description + " minimum", minimum, maximum);
        checkRange(max, description + " maximum", minimum, maximum);
        if (max < min) {
            throw new IllegalArgumentException(description + " maximum cannot be below its minimum");
        }
        return new IntRange(min, max);
    }

    private static int optionalInteger(
            JsonObject json,
            String field,
            int fallback,
            int minimum,
            int maximum) {
        if (!json.has(field)) return fallback;
        int value = exactInteger(json.get(field), field);
        checkRange(value, field, minimum, maximum);
        return value;
    }

    private static Integer optionalExactInteger(
            JsonObject json,
            String field,
            String description) {
        return json.has(field)
                ? Integer.valueOf(exactInteger(json.get(field), description))
                : null;
    }

    private static Integer optionalNonNegativeInteger(
            JsonObject json,
            String field,
            String description) {
        Integer value = optionalExactInteger(json, field, description);
        if (value != null && value.intValue() < 0) {
            throw new IllegalArgumentException(description + " must be non-negative");
        }
        return value;
    }

    private static int exactInteger(JsonElement raw, String description) {
        if (!isNumber(raw)) {
            throw new IllegalArgumentException(description + " must be an integer");
        }
        try {
            return new BigDecimal(raw.getAsJsonPrimitive().getAsString()).intValueExact();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(description + " must be an integer", failure);
        }
    }

    private static boolean isNumber(JsonElement raw) {
        return raw != null && raw.isJsonPrimitive()
                && raw.getAsJsonPrimitive().isNumber();
    }

    private static void checkRange(int value, String description, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(description + " must be between " + minimum
                    + " and " + maximum + " (was " + value + ")");
        }
    }

    private static String requiredString(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("missing or invalid required string field '" + field + "'");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isString()) {
            throw new IllegalArgumentException("missing or invalid required string field '" + field + "'");
        }
        return primitive.getAsString();
    }

    private static JsonObject requiredObject(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("missing or invalid required object field '" + field + "'");
        }
        return value.getAsJsonObject();
    }

    private static JsonElement requiredValue(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("missing required field '" + field + "'");
        }
        return value;
    }

    private static JsonArray requiredArray(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("missing or invalid required array field '" + field + "'");
        }
        return value.getAsJsonArray();
    }

    private static JsonObject objectAt(JsonArray array, int index, String description) {
        JsonElement raw = array.get(index);
        if (raw == null || !raw.isJsonObject()) {
            throw new IllegalArgumentException(description + " " + index + " must be an object");
        }
        return raw.getAsJsonObject();
    }

    private static void requireValue(String expected, String actual, String description) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(description + " must be '" + expected + "' (was '"
                    + actual + "')");
        }
    }

    private static void requireOnlyFields(
            JsonObject json,
            String description,
            String... allowedFields) {
        Set<String> allowed = new HashSet<String>(Arrays.asList(allowedFields));
        for (Map.Entry<String, JsonElement> field : json.entrySet()) {
            if (!allowed.contains(field.getKey())) {
                throw new IllegalArgumentException(description + " has unsupported field '"
                        + field.getKey() + "'");
            }
        }
    }

    private static final class IntRange {
        final int minimum;
        final int maximum;

        IntRange(int minimum, int maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }
}
