package net.minecraft.server.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.server.Block;
import net.minecraft.server.BlockStateKey;
import net.minecraft.server.util.ResourceLocation;

/** Strict codec for the supported legacy subset of the 26.3 worldgen/feature schema. */
public final class ConfiguredFeatureCodec {
    public static final ResourceLocation ORE =
            new ResourceLocation("minecraft", "ore");
    public static final ResourceLocation LEGACY_CLAY =
            new ResourceLocation("minecraft", "legacy_clay");
    public static final ResourceLocation MONSTER_ROOM =
            new ResourceLocation("minecraft", "monster_room");
    public static final ResourceLocation SPRING_FEATURE =
            new ResourceLocation("minecraft", "spring_feature");
    public static final ResourceLocation LAKE =
            new ResourceLocation("minecraft", "lake");
    public static final ResourceLocation FREEZE_TOP_LAYER =
            new ResourceLocation("minecraft", "freeze_top_layer");
    public static final ResourceLocation BLOCK_MATCH =
            new ResourceLocation("minecraft", "block_match");
    public static final ResourceLocation SIMPLE_STATE_PROVIDER =
            new ResourceLocation("minecraft", "simple_state_provider");
    public static final ResourceLocation TRUE_BLOCK_PREDICATE =
            new ResourceLocation("minecraft", "true");

    private ConfiguredFeatureCodec() {}

    public static ConfiguredFeatureDefinition decode(
            ResourceLocation key, JsonObject root) {
        if (key == null || root == null) {
            throw new IllegalArgumentException(
                    "Configured feature key and root are required");
        }
        ResourceLocation type = identifier(requireString(root, "type"), "type");
        if (FeatureRegistryApi.get(type) == null) {
            throw new IllegalArgumentException(
                    "Unsupported or unregistered configured feature type " + type);
        }
        if (MONSTER_ROOM.equals(type) || FREEZE_TOP_LAYER.equals(type)) {
            requireOnlyFields(root, MONSTER_ROOM.equals(type)
                    ? "monster-room feature" : "freeze-top-layer feature", "type");
            return new ConfiguredFeatureDefinition(key, type);
        }
        if (SPRING_FEATURE.equals(type)) {
            return decodeSpring(key, type, root);
        }
        if (LAKE.equals(type)) {
            return decodeLake(key, type, root);
        }
        boolean ore = ORE.equals(type);
        boolean legacyClay = LEGACY_CLAY.equals(type);
        if (!ore && !legacyClay) {
            throw new IllegalArgumentException(
                    "Unsupported configured feature type " + type);
        }
        requireOnlyFields(root, legacyClay
                        ? "legacy-clay configured feature"
                        : "ore configured feature",
                "type", "discard_chance_on_air_exposure", "size", "targets");
        float discard = requireFloat(root, "discard_chance_on_air_exposure");
        if (Float.floatToIntBits(discard) != Float.floatToIntBits(0.0F)) {
            throw new IllegalArgumentException(
                    "Legacy ore features require zero air-exposure discard chance");
        }
        int size = requireInt(root, "size");
        if (size <= 0 || size > 64) {
            throw new IllegalArgumentException("Ore size must be between 1 and 64");
        }

        JsonArray targets = requireArray(root, "targets");
        if (targets.size() != 1 || !targets.get(0).isJsonObject()) {
            throw new IllegalArgumentException(
                    "Legacy ore features require exactly one target");
        }
        JsonObject target = targets.get(0).getAsJsonObject();
        requireOnlyFields(target, "ore target", "state", "target");
        ResourceLocation state = identifier(
                requireString(target, "state"), "ore state");
        requireCanonicalBlock(state, "ore state");

        JsonObject predicate = requireObject(target, "target");
        requireOnlyFields(predicate, "ore target predicate",
                "predicate_type", "block");
        ResourceLocation predicateType = identifier(
                requireString(predicate, "predicate_type"),
                "ore target predicate type");
        if (!BLOCK_MATCH.equals(predicateType)) {
            throw new IllegalArgumentException(
                    "Legacy ore target must use minecraft:block_match");
        }
        ResourceLocation targetBlock = identifier(
                requireString(predicate, "block"), "ore target block");
        requireCanonicalBlock(targetBlock, "ore target block");
        if (ore && !new ResourceLocation("minecraft", "stone").equals(targetBlock)) {
            throw new IllegalArgumentException(
                    "Legacy ore generator can only replace minecraft:stone");
        }
        return new ConfiguredFeatureDefinition(
                key, type, state, targetBlock, size, discard);
    }

    private static ConfiguredFeatureDefinition decodeSpring(
            ResourceLocation key,
            ResourceLocation type,
            JsonObject root) {
        requireOnlyFields(root, "spring configured feature",
                "type", "state", "requires_block_below", "rock_count",
                "hole_count", "valid_blocks");

        JsonObject stateRoot = requireObject(root, "state");
        requireOnlyFields(stateRoot, "spring fluid state", "id", "properties");
        ResourceLocation stateId = identifier(
                requireString(stateRoot, "id"), "spring fluid state");
        requireCanonicalBlock(stateId, "spring fluid state");
        Block stateBlock = BlockRegistry.get(stateId);
        if (stateBlock != Block.WATER && stateBlock != Block.LAVA) {
            throw new IllegalArgumentException(
                    "Legacy spring state must be minecraft:water or minecraft:lava");
        }

        JsonObject properties = requireObject(stateRoot, "properties");
        requireOnlyFields(properties, "spring fluid-state properties", "falling");
        String falling = requireString(properties, "falling");
        if (!"true".equals(falling) && !"false".equals(falling)) {
            throw new IllegalArgumentException(
                    "Spring fluid falling property must be 'true' or 'false'");
        }
        TreeMap<String, String> stateProperties = new TreeMap<String, String>();
        stateProperties.put("falling", falling);
        BlockStateKey springState = new BlockStateKey(stateId, stateProperties);

        boolean requiresBlockBelow = root.has("requires_block_below")
                ? requireBoolean(root, "requires_block_below") : true;
        int rockCount = root.has("rock_count")
                ? requireInt(root, "rock_count") : 4;
        int holeCount = root.has("hole_count")
                ? requireInt(root, "hole_count") : 1;
        if (rockCount < 0 || rockCount > 5
                || holeCount < 0 || holeCount > 5) {
            throw new IllegalArgumentException(
                    "Spring rock and hole counts must be between 0 and 5");
        }

        JsonArray validBlockArray = requireArray(root, "valid_blocks");
        if (validBlockArray.size() == 0) {
            throw new IllegalArgumentException(
                    "Spring valid_blocks cannot be empty");
        }
        ArrayList<ResourceLocation> validBlocks =
                new ArrayList<ResourceLocation>(validBlockArray.size());
        HashSet<ResourceLocation> unique = new HashSet<ResourceLocation>();
        for (int i = 0; i < validBlockArray.size(); i++) {
            JsonElement blockElement = validBlockArray.get(i);
            if (!blockElement.isJsonPrimitive()
                    || !blockElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(
                        "Spring valid_blocks entries must be identifiers");
            }
            ResourceLocation block = identifier(
                    blockElement.getAsString(), "spring valid block");
            requireCanonicalBlock(block, "spring valid block");
            if (!unique.add(block)) {
                throw new IllegalArgumentException(
                        "Spring valid_blocks contains duplicate " + block);
            }
            validBlocks.add(block);
        }
        return new ConfiguredFeatureDefinition(
                key, type, springState, requiresBlockBelow,
                rockCount, holeCount, validBlocks);
    }

    private static ConfiguredFeatureDefinition decodeLake(
            ResourceLocation key,
            ResourceLocation type,
            JsonObject root) {
        requireOnlyFields(root, "lake configured feature",
                "type", "fluid", "barrier", "can_place_feature",
                "can_replace_with_air_or_fluid", "can_replace_with_barrier");

        ResourceLocation fluid = decodeSimpleStateProvider(
                requireObject(root, "fluid"), "lake fluid provider");
        ResourceLocation barrier = decodeSimpleStateProvider(
                requireObject(root, "barrier"), "lake barrier provider");
        ResourceLocation water = new ResourceLocation("minecraft", "water");
        ResourceLocation lava = new ResourceLocation("minecraft", "lava");
        ResourceLocation air = new ResourceLocation("minecraft", "air");
        ResourceLocation stone = new ResourceLocation("minecraft", "stone");
        if (!water.equals(fluid) && !lava.equals(fluid)) {
            throw new IllegalArgumentException(
                    "Legacy lake fluid must be minecraft:water or minecraft:lava");
        }
        ResourceLocation expectedBarrier = water.equals(fluid) ? air : stone;
        if (!expectedBarrier.equals(barrier)) {
            throw new IllegalArgumentException("Legacy " + fluid.getPath()
                    + " lake barrier must be " + expectedBarrier);
        }

        ResourceLocation canPlace = decodeTruePredicate(
                requireObject(root, "can_place_feature"),
                "lake can_place_feature predicate");
        ResourceLocation canReplaceAirOrFluid = decodeTruePredicate(
                requireObject(root, "can_replace_with_air_or_fluid"),
                "lake can_replace_with_air_or_fluid predicate");
        ResourceLocation canReplaceBarrier = decodeTruePredicate(
                requireObject(root, "can_replace_with_barrier"),
                "lake can_replace_with_barrier predicate");

        TreeMap<String, String> fluidProperties =
                new TreeMap<String, String>();
        fluidProperties.put("variant", "still");
        return new ConfiguredFeatureDefinition(
                key, type,
                SIMPLE_STATE_PROVIDER,
                new BlockStateKey(fluid, fluidProperties),
                SIMPLE_STATE_PROVIDER,
                new BlockStateKey(barrier),
                canPlace, canReplaceAirOrFluid, canReplaceBarrier);
    }

    private static ResourceLocation decodeSimpleStateProvider(
            JsonObject root, String description) {
        requireOnlyFields(root, description, "type", "state");
        ResourceLocation providerType = identifier(
                requireString(root, "type"), description + " type");
        if (!SIMPLE_STATE_PROVIDER.equals(providerType)) {
            throw new IllegalArgumentException(
                    description + " must use minecraft:simple_state_provider");
        }
        ResourceLocation state = identifier(
                requireString(root, "state"), description + " state");
        if (!new ResourceLocation("minecraft", "air").equals(state)) {
            requireCanonicalBlock(state, description + " state");
        }
        return state;
    }

    private static ResourceLocation decodeTruePredicate(
            JsonObject root, String description) {
        requireOnlyFields(root, description, "type");
        ResourceLocation predicate = identifier(
                requireString(root, "type"), description + " type");
        if (!TRUE_BLOCK_PREDICATE.equals(predicate)) {
            throw new IllegalArgumentException(
                    description + " must use minecraft:true");
        }
        return predicate;
    }

    private static void requireCanonicalBlock(
            ResourceLocation key, String description) {
        Block block = BlockRegistry.get(key);
        if (block == null || !key.equals(BlockRegistry.getKey(block))) {
            throw new IllegalArgumentException(
                    description + " must be a registered canonical block: " + key);
        }
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

    private static float requireFloat(JsonObject root, String field) {
        JsonElement value = requireValue(root, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        float result = value.getAsFloat();
        if (Float.isNaN(result) || Float.isInfinite(result)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return result;
    }

    private static boolean requireBoolean(JsonObject root, String field) {
        JsonElement value = requireValue(root, field);
        if (!value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.getAsBoolean();
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
}
