package net.minecraft.server.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.util.ResourceLocation;

/** Strict codec for the legacy-safe core of the 26.3 dimension-type shape. */
public final class DimensionTypeCodec {
    private DimensionTypeCodec() {}

    public static DimensionTypeDefinition decode(ResourceLocation key, JsonObject root) {
        if (key == null || root == null) {
            throw new IllegalArgumentException("Dimension type key and root cannot be null");
        }
        requireOnlyFields(root, "dimension type",
                "has_fixed_time",
                "has_skylight",
                "has_ceiling",
                "has_ender_dragon_fight",
                "coordinate_scale",
                "min_y",
                "height",
                "logical_height",
                "infiniburn",
                "ambient_light",
                "monster_spawn_light_level",
                "monster_spawn_block_light_limit",
                "skybox",
                "cardinal_light");

        boolean fixedTime = optionalBoolean(root, "has_fixed_time", false);
        boolean skylight = requireBoolean(root, "has_skylight");
        boolean ceiling = requireBoolean(root, "has_ceiling");
        boolean dragon = requireBoolean(root, "has_ender_dragon_fight");
        double scale = requireDouble(root, "coordinate_scale");
        int minY = requireInt(root, "min_y");
        int height = requireInt(root, "height");
        int logicalHeight = requireInt(root, "logical_height");
        ResourceLocation infiniburn = requireTag(root, "infiniburn");
        float ambient = (float)requireDouble(root, "ambient_light");
        DimensionTypeDefinition.LightLevelProvider spawnLight =
                decodeLightProvider(require(root, "monster_spawn_light_level"));
        int blockLightLimit = requireInt(root, "monster_spawn_block_light_limit");
        String skybox = optionalString(root, "skybox", "overworld");
        String cardinalLight = optionalString(root, "cardinal_light", "default");

        return new DimensionTypeDefinition(
                key, fixedTime, skylight, ceiling, dragon, scale, minY, height,
                logicalHeight, infiniburn, ambient, spawnLight, blockLightLimit,
                skybox, cardinalLight);
    }

    private static DimensionTypeDefinition.LightLevelProvider decodeLightProvider(
            JsonElement raw) {
        if (raw != null && raw.isJsonPrimitive()
                && raw.getAsJsonPrimitive().isNumber()) {
            int value = exactInt(raw, "monster_spawn_light_level");
            return new DimensionTypeDefinition.LightLevelProvider(
                    DimensionTypeDefinition.LightLevelProvider.CONSTANT, value, value);
        }
        if (raw == null || !raw.isJsonObject()) {
            throw new IllegalArgumentException(
                    "monster_spawn_light_level must be an integer or provider object");
        }
        JsonObject object = raw.getAsJsonObject();
        ResourceLocation type = requireIdentifier(object, "type");
        if (DimensionTypeDefinition.LightLevelProvider.CONSTANT.equals(type)) {
            requireOnlyFields(object, "monster spawn light provider", "type", "value");
            int value = requireInt(object, "value");
            return new DimensionTypeDefinition.LightLevelProvider(type, value, value);
        }
        if (DimensionTypeDefinition.LightLevelProvider.UNIFORM.equals(type)) {
            requireOnlyFields(object, "monster spawn light provider",
                    "type", "min_inclusive", "max_inclusive");
            return new DimensionTypeDefinition.LightLevelProvider(
                    type,
                    requireInt(object, "min_inclusive"),
                    requireInt(object, "max_inclusive"));
        }
        throw new IllegalArgumentException(
                "Unsupported monster_spawn_light_level provider " + type);
    }

    private static ResourceLocation requireTag(JsonObject object, String field) {
        String value = requireString(object, field);
        if (!value.startsWith("#") || value.length() == 1) {
            throw new IllegalArgumentException(field + " must be a tag identifier");
        }
        return identifier(value.substring(1), field);
    }

    private static ResourceLocation requireIdentifier(JsonObject object, String field) {
        return identifier(requireString(object, field), field);
    }

    private static ResourceLocation identifier(String value, String field) {
        String normalized = RegistryKeyPolicy.normalizeIdentifier(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must be an identifier");
        }
        return new ResourceLocation(normalized);
    }

    private static JsonElement require(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("Missing required field '" + field + "'");
        }
        return value;
    }

    private static boolean requireBoolean(JsonObject object, String field) {
        JsonElement value = require(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static boolean optionalBoolean(
            JsonObject object, String field, boolean fallback) {
        return object.has(field) ? requireBoolean(object, field) : fallback;
    }

    private static String requireString(JsonObject object, String field) {
        JsonElement value = require(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static String optionalString(
            JsonObject object, String field, String fallback) {
        return object.has(field) ? requireString(object, field) : fallback;
    }

    private static int requireInt(JsonObject object, String field) {
        return exactInt(require(object, field), field);
    }

    private static int exactInt(JsonElement value, String field) {
        if (!value.isJsonPrimitive()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            return new BigDecimal(primitive.getAsString()).intValueExact();
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(field + " must be an integer", failure);
        }
    }

    private static double requireDouble(JsonObject object, String field) {
        JsonElement value = require(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        try {
            double decoded = Double.parseDouble(value.getAsString());
            if (Double.isInfinite(decoded) || Double.isNaN(decoded)) {
                throw new NumberFormatException("not finite");
            }
            return decoded;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(field + " must be a finite number", failure);
        }
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
