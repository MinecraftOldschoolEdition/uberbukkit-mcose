package net.minecraft.server.item.component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStackTemplate;
import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Strict legacy subset of the 26.3 item-stack-template codec. */
public final class CraftingRemainderCodec {
    private CraftingRemainderCodec() {}

    public static ItemStackTemplate decode(
            ResourceLocation consumedItemKey,
            JsonObject root) {
        if (consumedItemKey == null || root == null) {
            throw new IllegalArgumentException(
                    "Crafting-remainder item key and root are required");
        }
        requireOnly(root, "id", "count", "legacy_metadata");
        ResourceLocation outputKey = RegistryDataLoader.parseIdentifierStrict(
                requiredString(root, "id"),
                "crafting-remainder output id");
        int count = requiredInteger(root, "count", 1, 1);
        int metadata = requiredInteger(root, "legacy_metadata", 0, 0);

        ItemRegistry.keys();
        Item output = ItemRegistry.get(outputKey);
        ResourceLocation canonical = output == null
                ? null : ItemRegistry.getKey(output);
        if (output == null || !outputKey.equals(canonical)) {
            throw new IllegalArgumentException(
                    "Crafting remainder for " + consumedItemKey
                            + " targets missing or non-canonical item "
                            + outputKey);
        }
        return new ItemStackTemplate(output, count, metadata);
    }

    private static String requiredString(JsonObject root, String field) {
        JsonElement value = root.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    "Missing or invalid string field '" + field + "'");
        }
        String text = value.getAsString();
        if (text.length() == 0) {
            throw new IllegalArgumentException(
                    "Field '" + field + "' cannot be empty");
        }
        return text;
    }

    private static int requiredInteger(
            JsonObject root,
            String field,
            int minimum,
            int maximum) {
        JsonElement value = root.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(
                    "Missing or invalid integer field '" + field + "'");
        }
        int parsed;
        try {
            parsed = new BigDecimal(value.getAsString()).intValueExact();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(
                    "Field '" + field + "' must be an integer", failure);
        }
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException(
                    "Field '" + field + "' must be between " + minimum
                            + " and " + maximum + " (was " + parsed + ")");
        }
        return parsed;
    }

    private static void requireOnly(JsonObject root, String... fields) {
        Set<String> allowed = new HashSet<String>();
        for (int i = 0; i < fields.length; i++) allowed.add(fields[i]);
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw new IllegalArgumentException(
                        "Crafting-remainder component contains unsupported field '"
                                + entry.getKey() + "'");
            }
        }
    }
}
