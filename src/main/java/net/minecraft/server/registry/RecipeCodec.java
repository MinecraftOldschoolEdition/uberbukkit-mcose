package net.minecraft.server.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.Block;
import net.minecraft.server.CraftingRecipe;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.ShapedRecipes;
import net.minecraft.server.ShapelessRecipes;
import net.minecraft.server.util.ResourceLocation;

/** Strict decoder for the legacy-shaped subset of modern recipe JSON. */
public final class RecipeCodec {
    private RecipeCodec() {}

    public static CraftingRecipe decode(ResourceLocation key, JsonObject json) {
        if (key == null || json == null) {
            throw new IllegalArgumentException("Recipe key and data are required");
        }
        String type = requiredString(json, "type");
        if ("minecraft:crafting_shaped".equals(type)) {
            return decodeShaped(key, json);
        }
        if ("minecraft:crafting_shapeless".equals(type)) {
            return decodeShapeless(key, json);
        }
        if ("minecraft:smelting".equals(type)) {
            return decodeSmelting(key, json);
        }
        throw new IllegalArgumentException("unsupported recipe type '" + type + "'");
    }

    public static ShapedRecipes decodeShaped(ResourceLocation key, JsonObject json) {
        if (key == null || json == null) {
            throw new IllegalArgumentException("Recipe key and data are required");
        }
        requireOnly(json, "recipe", "type", "pattern", "key", "result");
        if (!"minecraft:crafting_shaped".equals(requiredString(json, "type"))) {
            throw new IllegalArgumentException(
                    "recipe type must be minecraft:crafting_shaped");
        }

        JsonArray patternJson = requiredArray(json, "pattern");
        if (patternJson.size() < 1 || patternJson.size() > 3) {
            throw new IllegalArgumentException("pattern must contain 1-3 rows");
        }
        String[] pattern = new String[patternJson.size()];
        int width = -1;
        Set<Character> usedSymbols = new HashSet<Character>();
        for (int row = 0; row < patternJson.size(); row++) {
            JsonElement raw = patternJson.get(row);
            if (raw == null || !raw.isJsonPrimitive()
                    || !raw.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("pattern row " + row + " must be a string");
            }
            pattern[row] = raw.getAsString();
            if (pattern[row].length() < 1 || pattern[row].length() > 3) {
                throw new IllegalArgumentException("pattern rows must contain 1-3 columns");
            }
            if (width < 0) width = pattern[row].length();
            if (pattern[row].length() != width) {
                throw new IllegalArgumentException("pattern rows must have equal width");
            }
            for (int column = 0; column < width; column++) {
                char symbol = pattern[row].charAt(column);
                if (symbol != ' ') usedSymbols.add(Character.valueOf(symbol));
            }
        }
        if (usedSymbols.isEmpty()) {
            throw new IllegalArgumentException("pattern must contain at least one ingredient");
        }

        JsonObject keyJson = requiredObject(json, "key");
        Map<Character, ItemStack> ingredients = new HashMap<Character, ItemStack>();
        for (Map.Entry<String, JsonElement> entry : keyJson.entrySet()) {
            String symbolText = entry.getKey();
            if (symbolText.length() != 1 || symbolText.charAt(0) == ' ') {
                throw new IllegalArgumentException(
                        "recipe symbols must be one non-space character");
            }
            Character symbol = Character.valueOf(symbolText.charAt(0));
            if (!usedSymbols.contains(symbol)) {
                throw new IllegalArgumentException("unused recipe symbol '" + symbol + "'");
            }
            JsonElement raw = entry.getValue();
            if (raw == null || !raw.isJsonObject()) {
                throw new IllegalArgumentException(
                        "ingredient '" + symbol + "' must be an object");
            }
            ingredients.put(symbol, decodeStack(raw.getAsJsonObject(), false));
        }
        if (!ingredients.keySet().equals(usedSymbols)) {
            throw new IllegalArgumentException("every pattern symbol must have one ingredient");
        }

        ItemStack result = decodeStack(requiredObject(json, "result"), true);
        ItemStack[] flattened = new ItemStack[width * pattern.length];
        for (int row = 0; row < pattern.length; row++) {
            for (int column = 0; column < width; column++) {
                char symbol = pattern[row].charAt(column);
                flattened[column + row * width] = symbol == ' '
                        ? null
                        : ingredients.get(Character.valueOf(symbol)).cloneItemStack();
            }
        }
        return new ShapedRecipes(width, pattern.length, flattened, result);
    }

    public static ShapelessRecipes decodeShapeless(
            ResourceLocation key,
            JsonObject json) {
        if (key == null || json == null) {
            throw new IllegalArgumentException("Recipe key and data are required");
        }
        requireOnly(json, "recipe", "type", "ingredients", "result");
        if (!"minecraft:crafting_shapeless".equals(requiredString(json, "type"))) {
            throw new IllegalArgumentException(
                    "recipe type must be minecraft:crafting_shapeless");
        }
        JsonArray rawIngredients = requiredArray(json, "ingredients");
        if (rawIngredients.size() < 1 || rawIngredients.size() > 9) {
            throw new IllegalArgumentException("ingredients must contain 1-9 entries");
        }
        List<ItemStack> ingredients = new ArrayList<ItemStack>(rawIngredients.size());
        for (int i = 0; i < rawIngredients.size(); i++) {
            JsonElement raw = rawIngredients.get(i);
            if (raw == null || !raw.isJsonObject()) {
                throw new IllegalArgumentException(
                        "ingredient " + i + " must be an object");
            }
            ingredients.add(decodeStack(raw.getAsJsonObject(), false));
        }
        return new ShapelessRecipes(
                decodeStack(requiredObject(json, "result"), true),
                ingredients);
    }

    public static SmeltingRecipe decodeSmelting(
            ResourceLocation key,
            JsonObject json) {
        if (key == null || json == null) {
            throw new IllegalArgumentException("Recipe key and data are required");
        }
        requireOnly(json, "recipe", "type", "ingredient", "result", "cookingtime");
        if (!"minecraft:smelting".equals(requiredString(json, "type"))) {
            throw new IllegalArgumentException("recipe type must be minecraft:smelting");
        }
        ItemStack ingredient = decodeStack(requiredObject(json, "ingredient"), false);
        int metadata = ingredient.getData();
        if (metadata < -1 || metadata > 255) {
            throw new IllegalArgumentException(
                    "smelting ingredient legacy_metadata must be between -1 and 255");
        }
        return new SmeltingRecipe(
                ingredient.id,
                metadata,
                decodeStack(requiredObject(json, "result"), true),
                optionalInteger(
                        json,
                        "cookingtime",
                        SmeltingRecipe.DEFAULT_COOKING_TIME,
                        1,
                        SmeltingRecipe.MAX_COOKING_TIME));
    }

    private static ItemStack decodeStack(JsonObject json, boolean result) {
        if (result) {
            requireOnly(json, "recipe result", "id", "count", "legacy_metadata");
        } else {
            requireOnly(json, "recipe ingredient", "id", "legacy_metadata");
        }
        ResourceLocation id = new ResourceLocation(requiredString(json, "id"));
        int metadata = requiredInteger(json, "legacy_metadata", result ? 0 : -1, 32767);
        int count = result ? requiredInteger(json, "count", 1, 64) : 1;

        // Loading can be triggered while Item static initialization is finishing.
        ItemRegistry.keys();
        Item item = ItemRegistry.get(id);
        if (item != null) {
            return new ItemStack(item, count, metadata);
        }
        Block block = BlockRegistry.get(id);
        if (block != null) {
            return new ItemStack(block, count, metadata);
        }
        throw new IllegalArgumentException("unknown recipe item or block '" + id + "'");
    }

    private static String requiredString(JsonObject json, String field) {
        JsonElement raw = json.get(field);
        if (raw == null || !raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    "missing or invalid string field '" + field + "'");
        }
        String value = raw.getAsString();
        if (value.length() == 0) {
            throw new IllegalArgumentException("field '" + field + "' cannot be empty");
        }
        return value;
    }

    private static int requiredInteger(
            JsonObject json,
            String field,
            int minimum,
            int maximum) {
        JsonElement raw = json.get(field);
        if (raw == null || !raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(
                    "missing or invalid integer field '" + field + "'");
        }
        int value;
        try {
            value = new BigDecimal(raw.getAsString()).intValueExact();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(
                    "field '" + field + "' must be an integer", failure);
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("field '" + field + "' must be between "
                    + minimum + " and " + maximum + " (was " + value + ")");
        }
        return value;
    }

    private static int optionalInteger(
            JsonObject json,
            String field,
            int fallback,
            int minimum,
            int maximum) {
        return json.has(field)
                ? requiredInteger(json, field, minimum, maximum)
                : fallback;
    }

    private static JsonArray requiredArray(JsonObject json, String field) {
        JsonElement raw = json.get(field);
        if (raw == null || !raw.isJsonArray()) {
            throw new IllegalArgumentException(
                    "missing or invalid array field '" + field + "'");
        }
        return raw.getAsJsonArray();
    }

    private static JsonObject requiredObject(JsonObject json, String field) {
        JsonElement raw = json.get(field);
        if (raw == null || !raw.isJsonObject()) {
            throw new IllegalArgumentException(
                    "missing or invalid object field '" + field + "'");
        }
        return raw.getAsJsonObject();
    }

    private static void requireOnly(
            JsonObject json,
            String description,
            String... fields) {
        Set<String> allowed = new HashSet<String>();
        for (int i = 0; i < fields.length; i++) allowed.add(fields[i]);
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw new IllegalArgumentException(
                        description + " contains unsupported field '"
                                + entry.getKey() + "'");
            }
        }
    }
}
