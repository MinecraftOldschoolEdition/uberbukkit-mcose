package net.minecraft.server.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.util.ResourceLocation;

/** Strict decoder for the supported legacy projection of modern advancements. */
public final class LegacyAdvancementCodec {
    public static final ResourceLocation LEGACY_TRIGGER =
            new ResourceLocation("minecraft", "legacy_trigger");
    private static final Pattern IDENTIFIER = Pattern.compile(
            "[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern CRITERION_NAME = Pattern.compile("[a-z0-9_.-]+");

    private LegacyAdvancementCodec() {}

    public static LegacyAdvancementDefinition decode(
            ResourceLocation key,
            JsonObject json) {
        if (key == null || json == null) {
            throw new IllegalArgumentException("Advancement key and data are required");
        }
        requireOnlyFields(json, "advancement", "parent", "display", "criteria",
                "requirements", "legacy_layout");

        ResourceLocation parent = null;
        if (json.has("parent") && !json.get("parent").isJsonNull()) {
            parent = identifier(requiredString(json, "parent"), "parent");
            if (key.equals(parent)) {
                throw new IllegalArgumentException("advancement " + key + " cannot parent itself");
            }
        }

        JsonObject display = requiredObject(json, "display");
        requireOnlyFields(display, "display", "icon", "title", "description", "frame",
                "show_toast", "announce_to_chat", "hidden");
        JsonObject icon = requiredObject(display, "icon");
        requireOnlyFields(icon, "display icon", "id", "legacy_damage");
        ResourceLocation iconKey = identifier(requiredString(icon, "id"), "icon id");
        int legacyDamage = icon.has("legacy_damage")
                ? exactInteger(icon.get("legacy_damage"), "legacy_damage") : 0;
        if (legacyDamage < 0 || legacyDamage > 32767) {
            throw new IllegalArgumentException("legacy_damage must be within 0-32767");
        }
        int iconLegacyId = resolveIconLegacyId(iconKey);

        String title = translationKey(requiredObject(display, "title"), "title");
        String description = translationKey(
                requiredObject(display, "description"), "description");
        String frame = optionalString(display, "frame", "task");
        if (!"task".equals(frame) && !"challenge".equals(frame)) {
            throw new IllegalArgumentException("frame must be 'task' or 'challenge'");
        }
        boolean showToast = optionalBoolean(display, "show_toast", true);
        boolean announce = optionalBoolean(display, "announce_to_chat", true);
        boolean hidden = optionalBoolean(display, "hidden", false);

        JsonObject criteriaJson = requiredObject(json, "criteria");
        if (criteriaJson.entrySet().isEmpty()) {
            throw new IllegalArgumentException("advancement criteria cannot be empty");
        }
        Map<String, ResourceLocation> criteria =
                new LinkedHashMap<String, ResourceLocation>();
        for (Map.Entry<String, JsonElement> entry : criteriaJson.entrySet()) {
            String name = entry.getKey();
            if (!CRITERION_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("invalid criterion name '" + name + "'");
            }
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("criterion '" + name + "' must be an object");
            }
            JsonObject criterion = entry.getValue().getAsJsonObject();
            requireOnlyFields(criterion, "criterion '" + name + "'", "trigger");
            ResourceLocation trigger = identifier(
                    requiredString(criterion, "trigger"), "criterion trigger");
            if (!LEGACY_TRIGGER.equals(trigger)) {
                throw new IllegalArgumentException("unsupported criterion trigger " + trigger);
            }
            criteria.put(name, trigger);
        }

        List<List<String>> requirements = decodeRequirements(
                requiredArray(json, "requirements"), criteria.keySet());
        JsonObject layout = requiredObject(json, "legacy_layout");
        requireOnlyFields(layout, "legacy_layout", "column", "row");
        int column = exactInteger(requiredValue(layout, "column"), "column");
        int row = exactInteger(requiredValue(layout, "row"), "row");
        if (column < -64 || column > 64 || row < -64 || row > 64) {
            throw new IllegalArgumentException("legacy_layout coordinates must be within -64-64");
        }

        return new LegacyAdvancementDefinition(key, parent, iconKey, iconLegacyId,
                legacyDamage, title, description, frame, showToast, announce, hidden,
                column, row, criteria, requirements);
    }

    private static List<List<String>> decodeRequirements(
            JsonArray json,
            Set<String> criteria) {
        if (json.size() == 0) {
            throw new IllegalArgumentException("requirements cannot be empty");
        }
        List<List<String>> groups = new ArrayList<List<String>>();
        Set<String> referenced = new HashSet<String>();
        for (int i = 0; i < json.size(); i++) {
            JsonElement rawGroup = json.get(i);
            if (!rawGroup.isJsonArray()) {
                throw new IllegalArgumentException("requirement group " + i + " must be an array");
            }
            JsonArray groupJson = rawGroup.getAsJsonArray();
            if (groupJson.size() == 0) {
                throw new IllegalArgumentException("requirement group " + i + " cannot be empty");
            }
            List<String> group = new ArrayList<String>();
            for (int j = 0; j < groupJson.size(); j++) {
                JsonElement rawName = groupJson.get(j);
                if (!rawName.isJsonPrimitive()
                        || !rawName.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("requirement names must be strings");
                }
                String name = rawName.getAsString();
                if (!criteria.contains(name)) {
                    throw new IllegalArgumentException(
                            "requirement references missing criterion '" + name + "'");
                }
                referenced.add(name);
                group.add(name);
            }
            groups.add(group);
        }
        if (!referenced.equals(criteria)) {
            throw new IllegalArgumentException("requirements must reference every criterion");
        }
        return groups;
    }

    private static int resolveIconLegacyId(ResourceLocation key) {
        ItemRegistry.keys();
        Item item = ItemRegistry.get(key);
        if (item != null) {
            return item.id;
        }
        Block block = BlockRegistry.get(key);
        if (block != null) {
            return block.id;
        }
        throw new IllegalArgumentException("unknown advancement icon " + key);
    }

    private static String translationKey(JsonObject component, String description) {
        requireOnlyFields(component, description, "translate");
        return requiredString(component, "translate");
    }

    private static ResourceLocation identifier(String raw, String description) {
        if (!IDENTIFIER.matcher(raw).matches()) {
            throw new IllegalArgumentException(
                    description + " must be a lowercase namespaced identifier");
        }
        return new ResourceLocation(raw);
    }

    private static int exactInteger(JsonElement raw, String description) {
        if (raw == null || !raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(description + " must be an integer");
        }
        try {
            return new BigDecimal(raw.getAsJsonPrimitive().getAsString()).intValueExact();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(description + " must be an integer", failure);
        }
    }

    private static String requiredString(JsonObject json, String field) {
        JsonElement value = requiredValue(json, field);
        if (!value.isJsonPrimitive()) {
            throw new IllegalArgumentException(field + " must be a non-empty string");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isString() || primitive.getAsString().length() == 0) {
            throw new IllegalArgumentException(field + " must be a non-empty string");
        }
        return primitive.getAsString();
    }

    private static String optionalString(JsonObject json, String field, String fallback) {
        return json.has(field) ? requiredString(json, field) : fallback;
    }

    private static boolean optionalBoolean(JsonObject json, String field, boolean fallback) {
        if (!json.has(field)) return fallback;
        JsonElement value = json.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static JsonObject requiredObject(JsonObject json, String field) {
        JsonElement value = requiredValue(json, field);
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray requiredArray(JsonObject json, String field) {
        JsonElement value = requiredValue(json, field);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static JsonElement requiredValue(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("missing required field '" + field + "'");
        }
        return value;
    }

    private static void requireOnlyFields(
            JsonObject json,
            String description,
            String... fields) {
        Set<String> allowed = new HashSet<String>(Arrays.asList(fields));
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw new IllegalArgumentException(description
                        + " contains unsupported field '" + entry.getKey() + "'");
            }
        }
    }
}
