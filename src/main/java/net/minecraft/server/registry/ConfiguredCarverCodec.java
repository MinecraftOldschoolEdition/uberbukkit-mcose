package net.minecraft.server.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.Block;
import net.minecraft.server.MapGenBase;
import net.minecraft.server.MapGenCaves;
import net.minecraft.server.MapGenCavesHell;
import net.minecraft.server.util.ResourceLocation;

/** Strict type-dispatched codec for the Beta-compatible 26.3 carver seam. */
public final class ConfiguredCarverCodec {
    public static final ResourceLocation CAVE =
            new ResourceLocation("minecraft", "cave");
    public static final ResourceLocation NETHER_CAVE =
            new ResourceLocation("minecraft", "nether_cave");
    private static final ResourceLocation LEGACY_NESTED_RANDOM =
            new ResourceLocation("minecraft", "legacy_nested_random");
    private static final ResourceLocation LEGACY_ONE_IN =
            new ResourceLocation("minecraft", "legacy_one_in");
    private static final ResourceLocation UNIFORM =
            new ResourceLocation("minecraft", "uniform");

    private ConfiguredCarverCodec() {}

    public static ConfiguredCarverDefinition decode(
            ResourceLocation key, JsonObject root) {
        if (key == null || root == null) {
            throw new IllegalArgumentException(
                    "Configured carver key and JSON root are required");
        }
        ResourceLocation typeKey = identifier(requireString(root, "type"), "type");
        CarverType type = CarverRegistryApi.get(typeKey);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Unsupported or unregistered carver type " + typeKey);
        }
        return type.decode(key, typeKey, root);
    }

    public static CarverType caveType() {
        return new CarverType(
                "Legacy overworld cave carver",
                new CarverType.ConfigurationCodec() {
                    public ConfiguredCarverDefinition decode(
                            ResourceLocation id,
                            ResourceLocation type,
                            JsonObject root) {
                        return decodeLegacy(id, type, root, false);
                    }
                },
                new CarverType.Factory() {
                    public MapGenBase create(ConfiguredCarverDefinition definition) {
                        return new MapGenCaves(definition);
                    }
                });
    }

    public static CarverType netherCaveType() {
        return new CarverType(
                "Legacy Nether cave carver",
                new CarverType.ConfigurationCodec() {
                    public ConfiguredCarverDefinition decode(
                            ResourceLocation id,
                            ResourceLocation type,
                            JsonObject root) {
                        return decodeLegacy(id, type, root, true);
                    }
                },
                new CarverType.Factory() {
                    public MapGenBase create(ConfiguredCarverDefinition definition) {
                        return new MapGenCavesHell(definition);
                    }
                });
    }

    private static ConfiguredCarverDefinition decodeLegacy(
            ResourceLocation id,
            ResourceLocation type,
            JsonObject root,
            boolean nether) {
        if (nether && !NETHER_CAVE.equals(type)) {
            throw new IllegalArgumentException(
                    "Nether cave codec cannot decode " + type);
        }
        if (!nether && !CAVE.equals(type)) {
            throw new IllegalArgumentException("Cave codec cannot decode " + type);
        }
        if (nether) {
            requireOnlyFields(root, "Nether configured carver",
                    "type", "range", "count", "start_chance", "y",
                    "tunnel_thickness_multiplier", "horizontal_radius_multiplier",
                    "room_vertical_radius_multiplier", "vertical_radius_multiplier",
                    "floor_level", "maximum_carve_y", "replaceable_blocks",
                    "avoided_fluids");
        } else {
            requireOnlyFields(root, "Cave configured carver",
                    "type", "range", "count", "start_chance", "y",
                    "tunnel_thickness_multiplier", "horizontal_radius_multiplier",
                    "room_vertical_radius_multiplier", "vertical_radius_multiplier",
                    "floor_level", "maximum_carve_y", "lava_level",
                    "replaceable_blocks", "avoided_fluids");
        }

        int range = requireInt(root, "range");
        int countBound = decodeBound(
                requireObject(root, "count"), LEGACY_NESTED_RANDOM,
                "cave count");
        int startChanceBound = decodeBound(
                requireObject(root, "start_chance"), LEGACY_ONE_IN,
                "cave start chance");

        JsonObject y = requireObject(root, "y");
        ResourceLocation yType = identifier(
                requireString(y, "type"), "cave Y type");
        ConfiguredCarverDefinition.HeightDistribution distribution;
        int heightBound;
        int heightOffset;
        if (LEGACY_NESTED_RANDOM.equals(yType)) {
            requireOnlyFields(y, "Nested cave Y provider", "type", "bound", "offset");
            distribution =
                    ConfiguredCarverDefinition.HeightDistribution.LEGACY_NESTED_RANDOM;
            heightBound = requireInt(y, "bound");
            heightOffset = requireInt(y, "offset");
        } else if (UNIFORM.equals(yType)) {
            requireOnlyFields(y, "Uniform cave Y provider", "type", "bound");
            distribution = ConfiguredCarverDefinition.HeightDistribution.UNIFORM;
            heightBound = requireInt(y, "bound");
            heightOffset = 0;
        } else {
            throw new IllegalArgumentException(
                    "Unsupported cave Y provider " + yType);
        }
        if (nether
                && distribution != ConfiguredCarverDefinition.HeightDistribution.UNIFORM) {
            throw new IllegalArgumentException(
                    "Nether cave Y must use minecraft:uniform");
        }
        if (!nether && distribution
                != ConfiguredCarverDefinition.HeightDistribution.LEGACY_NESTED_RANDOM) {
            throw new IllegalArgumentException(
                    "Overworld cave Y must use minecraft:legacy_nested_random");
        }

        return new ConfiguredCarverDefinition(
                id,
                type,
                range,
                countBound,
                startChanceBound,
                distribution,
                heightBound,
                heightOffset,
                requireFloat(root, "tunnel_thickness_multiplier"),
                requireFloat(root, "horizontal_radius_multiplier"),
                requireDouble(root, "room_vertical_radius_multiplier"),
                requireDouble(root, "vertical_radius_multiplier"),
                requireDouble(root, "floor_level"),
                requireInt(root, "maximum_carve_y"),
                nether ? -1 : requireInt(root, "lava_level"),
                decodeBlockList(requireArray(root, "replaceable_blocks"),
                        "replaceable block"),
                decodeBlockList(requireArray(root, "avoided_fluids"),
                        "avoided fluid"));
    }

    private static ArrayList<ResourceLocation> decodeBlockList(
            JsonArray values, String description) {
        if (values.size() == 0) {
            throw new IllegalArgumentException(
                    "Configured carver " + description + " list cannot be empty");
        }
        ArrayList<ResourceLocation> decoded =
                new ArrayList<ResourceLocation>(values.size());
        HashSet<ResourceLocation> unique = new HashSet<ResourceLocation>();
        for (int i = 0; i < values.size(); i++) {
            JsonElement raw = values.get(i);
            if (!raw.isJsonPrimitive()
                    || !raw.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(
                        "Configured carver " + description
                                + " references must be identifiers");
            }
            ResourceLocation key = identifier(raw.getAsString(), description);
            Block block = BlockRegistry.get(key);
            if (block == null || !key.equals(BlockRegistry.getKey(block))) {
                throw new IllegalArgumentException(
                        "Configured carver " + description
                                + " must be a canonical registered block: " + key);
            }
            if (!unique.add(key)) {
                throw new IllegalArgumentException(
                        "Configured carver " + description
                                + " list contains duplicate " + key);
            }
            decoded.add(key);
        }
        return decoded;
    }

    private static int decodeBound(
            JsonObject root,
            ResourceLocation expectedType,
            String description) {
        requireOnlyFields(root, description + " provider", "type", "bound");
        ResourceLocation actualType = identifier(
                requireString(root, "type"), description + " provider type");
        if (!expectedType.equals(actualType)) {
            throw new IllegalArgumentException(
                    description + " must use " + expectedType);
        }
        return requireInt(root, "bound");
    }

    private static ResourceLocation identifier(String raw, String description) {
        String normalized = RegistryKeyPolicy.normalizeIdentifier(raw);
        if (normalized == null || !normalized.equals(raw)) {
            throw new IllegalArgumentException(
                    description + " must be a lowercase namespaced identifier");
        }
        return new ResourceLocation(normalized);
    }

    private static String requireString(JsonObject root, String field) {
        JsonElement value = requireValue(root, field);
        if (!value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || value.getAsString().length() == 0) {
            throw new IllegalArgumentException(
                    field + " must be a non-empty string");
        }
        return value.getAsString();
    }

    private static int requireInt(JsonObject root, String field) {
        JsonElement value = requireValue(root, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        double number = value.getAsDouble();
        int integer = value.getAsInt();
        if (number != (double)integer) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return integer;
    }

    private static float requireFloat(JsonObject root, String field) {
        double value = requireDouble(root, field);
        float result = (float)value;
        if (Float.isNaN(result) || Float.isInfinite(result)) {
            throw new IllegalArgumentException(field + " must fit a finite float");
        }
        return result;
    }

    private static double requireDouble(JsonObject root, String field) {
        JsonElement value = requireValue(root, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        double result = value.getAsDouble();
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return result;
    }

    private static JsonObject requireObject(JsonObject root, String field) {
        JsonElement value = requireValue(root, field);
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject root, String field) {
        JsonElement value = requireValue(root, field);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static JsonElement requireValue(JsonObject root, String field) {
        JsonElement value = root.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException(
                    "Missing required field '" + field + "'");
        }
        return value;
    }

    private static void requireOnlyFields(
            JsonObject root, String description, String... fields) {
        Set<String> allowed = new HashSet<String>(Arrays.asList(fields));
        for (Map.Entry<String, JsonElement> field : root.entrySet()) {
            if (!allowed.contains(field.getKey())) {
                throw new IllegalArgumentException(
                        description + " contains unsupported field '"
                                + field.getKey() + "'");
            }
        }
    }
}
