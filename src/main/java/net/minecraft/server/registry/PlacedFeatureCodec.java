package net.minecraft.server.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.Block;
import net.minecraft.server.Material;
import net.minecraft.server.util.ResourceLocation;

/** Strict codec for the supported legacy subset of the 26.3 placed-feature schema. */
public final class PlacedFeatureCodec {
    private static final ResourceLocation COUNT = key("count");
    private static final ResourceLocation RARITY_FILTER =
            PlacedFeatureDefinition.RARITY_FILTER;
    private static final ResourceLocation IN_SQUARE = key("in_square");
    private static final ResourceLocation HEIGHT_RANGE = key("height_range");
    private static final ResourceLocation BIOME =
            PlacedFeatureDefinition.BIOME;

    private PlacedFeatureCodec() {}

    public static PlacedFeatureDefinition decode(
            ResourceLocation key, JsonObject root) {
        if (key == null || root == null) {
            throw new IllegalArgumentException(
                    "Placed feature key and root are required");
        }
        requireOnlyFields(root, "placed feature", "feature", "placement");
        ResourceLocation feature = identifier(
                requireString(root, "feature"), "configured feature");
        ConfiguredFeatureDefinition configured =
                ConfiguredFeatureDataBootstrap.get(feature);
        if (configured == null || !feature.equals(configured.getId())) {
            throw new IllegalArgumentException(
                    "Placed feature references unknown configured feature " + feature);
        }

        JsonArray placement = requireArray(root, "placement");
        if (ConfiguredFeatureCodec.FREEZE_TOP_LAYER.equals(
                configured.getType())) {
            if (placement.size() != 1) {
                throw new IllegalArgumentException(
                        "Freeze-top-layer placement requires only biome");
            }
            JsonObject biomeModifier = modifier(placement, 0, BIOME);
            requireOnlyFields(biomeModifier, "biome placement", "type");
            return new PlacedFeatureDefinition(key, feature);
        }
        boolean legacyClay = ConfiguredFeatureCodec.LEGACY_CLAY.equals(
                configured.getType());
        boolean lake = ConfiguredFeatureCodec.LAKE.equals(configured.getType());
        if (lake && !configured.hasLakeConfiguration()) {
            throw new IllegalArgumentException(
                    "Placed lake references an incomplete configured feature "
                            + feature);
        }
        boolean lavaLake = lake && new ResourceLocation("minecraft", "lava")
                .equals(configured.getLakeFluidState().getBlockKey());
        int expectedModifiers = legacyClay || lavaLake ? 5 : 4;
        if (placement.size() != expectedModifiers) {
            throw new IllegalArgumentException(
                    legacyClay
                            ? "Legacy clay placement requires count, in_square, "
                                    + "height_range, block_predicate_filter, and biome"
                            : lavaLake
                                    ? "Legacy lava lake placement requires rarity_filter, "
                                            + "in_square, height_range, "
                                            + "legacy_lava_lake_filter, and biome"
                                    : "Legacy placement requires count, in_square, "
                                            + "height_range, and biome");
        }
        int count = 1;
        int rarityChance = 0;
        if (lake) {
            JsonObject rarityModifier = modifier(placement, 0, RARITY_FILTER);
            requireOnlyFields(
                    rarityModifier, "rarity-filter placement", "type", "chance");
            rarityChance = requireInt(rarityModifier, "chance");
            if (rarityChance <= 0 || rarityChance > 256) {
                throw new IllegalArgumentException(
                        "Placed feature rarity chance must be between 1 and 256");
            }
        } else {
            JsonObject countModifier = modifier(placement, 0, COUNT);
            requireOnlyFields(countModifier, "count placement", "type", "count");
            count = requireInt(countModifier, "count");
            if (count <= 0 || count > 256) {
                throw new IllegalArgumentException(
                        "Placed feature count must be between 1 and 256");
            }
        }

        JsonObject squareModifier = modifier(placement, 1, IN_SQUARE);
        requireOnlyFields(squareModifier, "in-square placement", "type");

        JsonObject heightModifier = modifier(placement, 2, HEIGHT_RANGE);
        requireOnlyFields(heightModifier, "height-range placement", "type", "height");
        PlacedFeatureDefinition.HeightProvider height =
                decodeHeight(requireObject(heightModifier, "height"));

        ResourceLocation requiredOriginFluid = null;
        int biomeIndex = 3;
        if (legacyClay) {
            JsonObject filterModifier = modifier(
                    placement, 3,
                    PlacedFeatureDefinition.BLOCK_PREDICATE_FILTER);
            requireOnlyFields(filterModifier, "block-predicate filter",
                    "type", "predicate");
            JsonObject predicate = requireObject(filterModifier, "predicate");
            requireOnlyFields(predicate, "matching-fluids predicate",
                    "type", "fluids");
            ResourceLocation predicateType = identifier(
                    requireString(predicate, "type"),
                    "block predicate type");
            if (!PlacedFeatureDefinition.MATCHING_FLUIDS.equals(predicateType)) {
                throw new IllegalArgumentException(
                        "Legacy clay placement must use minecraft:matching_fluids");
            }
            requiredOriginFluid = identifier(
                    requireString(predicate, "fluids"),
                    "matching fluid");
            requireCanonicalFluid(requiredOriginFluid);
            biomeIndex = 4;
        } else if (lavaLake) {
            JsonObject legacyFilter = modifier(
                    placement, 3,
                    PlacedFeatureDefinition.LEGACY_LAVA_LAKE_FILTER);
            requireOnlyFields(
                    legacyFilter, "legacy lava-lake placement filter", "type");
            biomeIndex = 4;
        }

        JsonObject biomeModifier = modifier(placement, biomeIndex, BIOME);
        requireOnlyFields(biomeModifier, "biome placement", "type");
        return new PlacedFeatureDefinition(
                key, feature, count, height, requiredOriginFluid,
                rarityChance, lavaLake);
    }

    private static PlacedFeatureDefinition.HeightProvider decodeHeight(
            JsonObject root) {
        requireOnlyFields(root, "height provider",
                "type", "min_inclusive", "max_inclusive", "plateau", "inner");
        ResourceLocation type = identifier(
                requireString(root, "type"), "height provider type");
        boolean biased = PlacedFeatureDefinition.BIASED_TO_BOTTOM.equals(type)
                || PlacedFeatureDefinition.VERY_BIASED_TO_BOTTOM.equals(type);
        if (biased && root.has("plateau")) {
            throw new IllegalArgumentException(
                    "Biased height providers do not accept plateau");
        }
        if (!biased && root.has("inner")) {
            throw new IllegalArgumentException(
                    "Uniform and trapezoid height providers do not accept inner");
        }
        int minimum = absoluteAnchor(root, "min_inclusive");
        int maximum = absoluteAnchor(root, "max_inclusive");
        int plateau = root.has("plateau") ? requireInt(root, "plateau") : 0;
        int inner = biased ? requireInt(root, "inner") : 0;
        return new PlacedFeatureDefinition.HeightProvider(
                type, minimum, maximum, plateau, inner);
    }

    private static int absoluteAnchor(JsonObject root, String field) {
        JsonObject anchor = requireObject(root, field);
        requireOnlyFields(anchor, "vertical anchor", "absolute");
        return requireInt(anchor, "absolute");
    }

    private static void requireCanonicalFluid(ResourceLocation key) {
        Block block = BlockRegistry.get(key);
        if (block == null || !key.equals(BlockRegistry.getKey(block))) {
            throw new IllegalArgumentException(
                    "matching fluid must be a registered canonical block: " + key);
        }
        if (block.material != Material.WATER
                && block.material != Material.LAVA) {
            throw new IllegalArgumentException(
                    "matching fluid must identify water or lava: " + key);
        }
    }

    private static JsonObject modifier(
            JsonArray placement, int index, ResourceLocation expectedType) {
        JsonElement value = placement.get(index);
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(
                    "Placement modifier " + index + " must be an object");
        }
        JsonObject modifier = value.getAsJsonObject();
        ResourceLocation actual = identifier(
                requireString(modifier, "type"), "placement modifier type");
        if (!expectedType.equals(actual)) {
            throw new IllegalArgumentException("Placement modifier " + index
                    + " must be " + expectedType + ", found " + actual);
        }
        return modifier;
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
            throw new IllegalArgumentException(field + " must be a non-empty string");
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
            throw new IllegalArgumentException("Missing required field '" + field + "'");
        }
        return value;
    }

    private static void requireOnlyFields(
            JsonObject root, String description, String... fields) {
        Set<String> allowed = new HashSet<String>(Arrays.asList(fields));
        for (Map.Entry<String, JsonElement> field : root.entrySet()) {
            if (!allowed.contains(field.getKey())) {
                throw new IllegalArgumentException(description
                        + " contains unsupported field '" + field.getKey() + "'");
            }
        }
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
