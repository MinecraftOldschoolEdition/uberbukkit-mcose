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
import net.minecraft.server.util.ResourceLocation;

/** Decodes the legacy structure descriptor carried by a modern structure file. */
public final class StructureTypeCodec {
    private static final int MAX_SPAWNER_ENTRIES = 64;
    private static final int MAX_WEIGHT = 1000000;

    private StructureTypeCodec() {}

    public static StructureType decode(ResourceLocation key, JsonObject json) {
        if (key == null || json == null) {
            throw new IllegalArgumentException("Structure key and data are required");
        }
        requireOnlyFields(json, "structure", "display_name", "loot_table",
                "spawner_mobs");

        StructureType.Builder builder = StructureType.builder(key)
                .displayName(requiredString(json, "display_name"));
        if (json.has("loot_table")) {
            builder.lootTable(new ResourceLocation(requiredString(json, "loot_table")));
        }
        if (json.has("spawner_mobs")) {
            JsonArray entries = requiredArray(json, "spawner_mobs");
            if (entries.size() == 0 || entries.size() > MAX_SPAWNER_ENTRIES) {
                throw new IllegalArgumentException("spawner_mobs must contain 1-"
                        + MAX_SPAWNER_ENTRIES + " entries");
            }
            String[] mobs = new String[entries.size()];
            int[] weights = new int[entries.size()];
            long totalWeight = 0L;
            for (int i = 0; i < entries.size(); i++) {
                JsonElement raw = entries.get(i);
                if (raw == null || !raw.isJsonObject()) {
                    throw new IllegalArgumentException("spawner mob " + i + " must be an object");
                }
                JsonObject entry = raw.getAsJsonObject();
                requireOnlyFields(entry, "spawner mob " + i, "legacy_entity_id", "weight");
                mobs[i] = requiredString(entry, "legacy_entity_id");
                weights[i] = exactInteger(requiredValue(entry, "weight"), "spawner weight");
                if (weights[i] < 0 || weights[i] > MAX_WEIGHT) {
                    throw new IllegalArgumentException("spawner weight must be between 0 and "
                            + MAX_WEIGHT + " (was " + weights[i] + ")");
                }
                totalWeight += weights[i];
                if (totalWeight > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("spawner total weight exceeds integer range");
                }
            }
            if (totalWeight <= 0L) {
                throw new IllegalArgumentException("spawner total weight must be positive");
            }
            builder.spawnerMobs(mobs, weights);
        }
        return builder.build();
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

    private static String requiredString(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException(
                    "missing or invalid required string field '" + field + "'");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isString()) {
            throw new IllegalArgumentException(
                    "missing or invalid required string field '" + field + "'");
        }
        String string = primitive.getAsString();
        if (string.length() == 0) {
            throw new IllegalArgumentException(
                    "required string field '" + field + "' cannot be empty");
        }
        return string;
    }

    private static JsonArray requiredArray(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(
                    "missing or invalid required array field '" + field + "'");
        }
        return value.getAsJsonArray();
    }

    private static JsonElement requiredValue(JsonObject json, String field) {
        if (!json.has(field)) {
            throw new IllegalArgumentException("missing required field '" + field + "'");
        }
        return json.get(field);
    }

    private static void requireOnlyFields(
            JsonObject json,
            String description,
            String... allowedFields) {
        Set<String> allowed = new HashSet<String>(Arrays.asList(allowedFields));
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw new IllegalArgumentException(description + " contains unsupported field '"
                        + entry.getKey() + "'");
            }
        }
    }
}
