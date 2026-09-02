package net.minecraft.server.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.util.ResourceLocation;

/** Strict decoder for the supported 26.3-shaped structure-set projection. */
public final class StructureSetCodec {
    private static final int MAX_STRUCTURES = 64;

    private StructureSetCodec() {}

    public static StructureSetDefinition decode(
            ResourceLocation key, JsonObject json) {
        if (key == null || json == null) {
            throw new IllegalArgumentException(
                    "Structure set key and data are required");
        }
        requireOnlyFields(json, "structure set", "structures", "placement");

        JsonArray structureData = requiredArray(json, "structures");
        if (structureData.size() == 0
                || structureData.size() > MAX_STRUCTURES) {
            throw new IllegalArgumentException(
                    "structures must contain 1-" + MAX_STRUCTURES + " entries");
        }
        ArrayList<StructureSetDefinition.StructureSelectionEntry> structures =
                new ArrayList<StructureSetDefinition.StructureSelectionEntry>();
        LinkedHashSet<ResourceLocation> seen =
                new LinkedHashSet<ResourceLocation>();
        for (int i = 0; i < structureData.size(); i++) {
            JsonElement raw = structureData.get(i);
            if (raw == null || !raw.isJsonObject()) {
                throw new IllegalArgumentException(
                        "structure selection entry " + i + " must be an object");
            }
            JsonObject entry = raw.getAsJsonObject();
            requireOnlyFields(entry, "structure selection entry " + i,
                    "structure", "weight");
            ResourceLocation structure = new ResourceLocation(
                    requiredString(entry, "structure"));
            if (!seen.add(structure)) {
                throw new IllegalArgumentException(
                        "duplicate structure selection entry " + structure);
            }
            int weight = exactInt(requiredValue(entry, "weight"), "weight");
            if (weight <= 0) {
                throw new IllegalArgumentException("weight must be positive");
            }
            structures.add(new StructureSetDefinition.StructureSelectionEntry(
                    structure, weight));
        }

        JsonObject placement = requiredObject(json, "placement");
        requireOnlyFields(placement, "placement",
                "type", "chance", "salt", "chunk_x_multiplier",
                "chunk_z_multiplier", "candidate_offset",
                "generation_y", "locate_y");
        ResourceLocation type = new ResourceLocation(
                requiredString(placement, "type"));
        if (!LegacyRandomChanceStructurePlacement.TYPE.equals(type)) {
            throw new IllegalArgumentException(
                    "unsupported structure placement type " + type);
        }

        int chance = exactInt(requiredValue(placement, "chance"), "chance");
        if (chance <= 0) {
            throw new IllegalArgumentException("chance must be positive");
        }
        long salt = exactLong(requiredValue(placement, "salt"), "salt");
        long chunkXMultiplier = exactLong(
                requiredValue(placement, "chunk_x_multiplier"),
                "chunk_x_multiplier");
        long chunkZMultiplier = exactLong(
                requiredValue(placement, "chunk_z_multiplier"),
                "chunk_z_multiplier");

        JsonObject offset = requiredObject(placement, "candidate_offset");
        requireOnlyFields(offset, "candidate_offset", "min", "bound");
        int candidateMin = exactInt(
                requiredValue(offset, "min"), "candidate_offset.min");
        int candidateBound = exactInt(
                requiredValue(offset, "bound"), "candidate_offset.bound");
        if (candidateBound <= 0) {
            throw new IllegalArgumentException(
                    "candidate_offset.bound must be positive");
        }
        int generationY = exactInt(
                requiredValue(placement, "generation_y"), "generation_y");
        int locateY = exactInt(
                requiredValue(placement, "locate_y"), "locate_y");

        return new StructureSetDefinition(
                key,
                structures,
                new LegacyRandomChanceStructurePlacement(
                        chance,
                        salt,
                        chunkXMultiplier,
                        chunkZMultiplier,
                        candidateMin,
                        candidateBound,
                        generationY,
                        locateY));
    }

    private static int exactInt(JsonElement raw, String description) {
        if (!isNumber(raw)) {
            throw new IllegalArgumentException(description + " must be an integer");
        }
        try {
            return new BigDecimal(raw.getAsJsonPrimitive().getAsString())
                    .intValueExact();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(
                    description + " must be an integer", failure);
        }
    }

    private static long exactLong(JsonElement raw, String description) {
        if (!isNumber(raw)) {
            throw new IllegalArgumentException(description + " must be an integer");
        }
        try {
            return new BigDecimal(raw.getAsJsonPrimitive().getAsString())
                    .longValueExact();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(
                    description + " must be an integer", failure);
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

    private static JsonObject requiredObject(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(
                    "missing or invalid required object field '" + field + "'");
        }
        return value.getAsJsonObject();
    }

    private static JsonElement requiredValue(JsonObject json, String field) {
        if (!json.has(field)) {
            throw new IllegalArgumentException(
                    "missing required field '" + field + "'");
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
                throw new IllegalArgumentException(
                        description + " contains unsupported field '"
                                + entry.getKey() + "'");
            }
        }
    }
}
