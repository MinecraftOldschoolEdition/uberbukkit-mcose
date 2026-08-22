package net.minecraft.server.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.Arrays;
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

    private LootTableCodec() {}

    public static LootTable decode(ResourceLocation key, JsonObject json) {
        if (key == null || json == null) {
            throw new IllegalArgumentException("Loot table key and data are required");
        }
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
                pool.addEntry(decodeEntry(entryJson, weight, name, entryIndex));
            }
            table.addPool(pool);
        }
        return table;
    }

    private static LootEntry decodeEntry(
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
        Item item = ItemRegistry.get(itemId);
        if (item != null) return true;
        Integer legacyId = ItemRegistry.getId(itemId.toString());
        return legacyId != null
                && legacyId.intValue() >= 0
                && legacyId.intValue() < Item.byId.length
                && Item.byId[legacyId.intValue()] != null;
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
