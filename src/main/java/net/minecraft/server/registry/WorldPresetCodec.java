package net.minecraft.server.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.util.ResourceLocation;

/** Strict codec for symbolic legacy generators in the 26.3 world-preset shape. */
public final class WorldPresetCodec {
    private WorldPresetCodec() {}

    public static WorldPresetDefinition decode(ResourceLocation key, JsonObject root) {
        if (key == null || root == null) {
            throw new IllegalArgumentException("World preset key and root cannot be null");
        }
        requireOnlyFields(root, "world preset", "dimensions");
        JsonObject dimensions = requireObject(root, "dimensions", "world preset dimensions");
        if (dimensions.entrySet().isEmpty()) {
            throw new IllegalArgumentException("World preset dimensions cannot be empty");
        }

        LinkedHashMap<ResourceLocation, WorldPresetDefinition.DimensionStem> decoded =
                new LinkedHashMap<ResourceLocation, WorldPresetDefinition.DimensionStem>();
        for (Map.Entry<String, JsonElement> entry : dimensions.entrySet()) {
            ResourceLocation dimensionKey = identifier(entry.getKey(), "dimension key");
            if (decoded.containsKey(dimensionKey)) {
                throw new IllegalArgumentException(
                        "Duplicate normalized world preset dimension " + dimensionKey);
            }
            if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException(
                        "World preset dimension " + dimensionKey + " must be an object");
            }
            JsonObject stem = entry.getValue().getAsJsonObject();
            requireOnlyFields(stem, "world preset dimension", "type", "generator");
            ResourceLocation dimensionType = requireIdentifier(stem, "type");
            JsonObject generator = requireObject(stem, "generator", "chunk generator");
            requireOnlyFields(generator, "chunk generator", "type");
            ResourceLocation generatorType = requireIdentifier(generator, "type");

            DimensionTypeDefinition dimensionDefinition =
                    DimensionTypeRegistryApi.getDefinition(dimensionType);
            if (dimensionDefinition == null) {
                throw new IllegalArgumentException(
                        "Unknown dimension type " + dimensionType + " in " + key);
            }
            if (!dimensionType.equals(dimensionDefinition.getId())) {
                throw new IllegalArgumentException(
                        "Dimension type must be registered and canonical: "
                                + dimensionType);
            }
            Class<?> generatorClass = ChunkGeneratorTypeRegistryApi.get(generatorType);
            ResourceLocation canonicalGenerator = generatorClass == null
                    ? null : ChunkGeneratorTypeRegistryApi.getKey(generatorClass);
            if (generatorClass == null || !generatorType.equals(canonicalGenerator)) {
                throw new IllegalArgumentException(
                        "Chunk generator type must be registered and canonical: "
                                + generatorType);
            }
            decoded.put(dimensionKey, new WorldPresetDefinition.DimensionStem(
                    dimensionType, generatorType));
        }
        return new WorldPresetDefinition(key, decoded);
    }

    private static JsonObject requireObject(
            JsonObject parent, String field, String description) {
        JsonElement value = parent.get(field);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(description + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static ResourceLocation requireIdentifier(
            JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be an identifier string");
        }
        return identifier(value.getAsString(), field);
    }

    private static ResourceLocation identifier(String value, String field) {
        String normalized = RegistryKeyPolicy.normalizeIdentifier(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must be an identifier");
        }
        return new ResourceLocation(normalized);
    }

    private static void requireOnlyFields(
            JsonObject object, String description, String... allowedFields) {
        Set<String> allowed = new HashSet<String>(Arrays.asList(allowedFields));
        for (Map.Entry<String, JsonElement> field : object.entrySet()) {
            if (!allowed.contains(field.getKey())) {
                throw new IllegalArgumentException(
                        description + " contains unsupported field '" + field.getKey() + "'");
            }
        }
    }
}
