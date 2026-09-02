package net.minecraft.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Internal decoded form of one data/minecraft/death_message entry. */
final class DeathMessageRule {
    enum ItemPolicy {
        NEVER,
        CUSTOM_NAMED,
        ANY;

        static ItemPolicy decode(String value) {
            if (value == null || value.length() == 0
                    || "custom_named".equals(value)) {
                return CUSTOM_NAMED;
            }
            if ("never".equals(value)) return NEVER;
            if ("any".equals(value)) return ANY;
            throw new IllegalArgumentException(
                    "unknown item_policy '" + value + "'");
        }
    }

    static final class Template {
        final String defaultKey;
        final String actorKey;
        final String itemKey;
        final ItemPolicy itemPolicy;

        Template(
                String defaultKey,
                String actorKey,
                String itemKey,
                ItemPolicy itemPolicy) {
            this.defaultKey = requiredText(defaultKey, "default_key");
            this.actorKey = optionalText(actorKey);
            this.itemKey = optionalText(itemKey);
            this.itemPolicy = itemPolicy == null
                    ? ItemPolicy.CUSTOM_NAMED : itemPolicy;
            if (this.itemKey != null && this.actorKey == null) {
                throw new IllegalArgumentException(
                        "item_key requires actor_key");
            }
        }

        DeathMessage create(
                String victimName,
                String actorName,
                String itemName,
                boolean customNamedItem) {
            if (actorName == null || this.actorKey == null) {
                return new DeathMessage(this.defaultKey, victimName);
            }
            if (this.itemKey != null && itemName != null
                    && (this.itemPolicy == ItemPolicy.ANY
                            || this.itemPolicy == ItemPolicy.CUSTOM_NAMED
                                    && customNamedItem)) {
                return new DeathMessage(
                        this.itemKey, victimName, actorName, itemName);
            }
            return new DeathMessage(this.actorKey, victimName, actorName);
        }
    }

    static final class ProvenanceRule {
        final String action;
        final int maxAgeTicks;
        final double horizontalRadius;
        final double verticalRadius;
        final String verticalRelation;
        final Template template;

        ProvenanceRule(
                String action,
                int maxAgeTicks,
                double horizontalRadius,
                double verticalRadius,
                String verticalRelation,
                Template template) {
            this.action = requiredText(action, "action");
            if (maxAgeTicks < 1 || maxAgeTicks > 72000) {
                throw new IllegalArgumentException(
                        "max_age_ticks must be between 1 and 72000");
            }
            if (horizontalRadius < 0.0D || horizontalRadius > 128.0D
                    || verticalRadius < 0.0D || verticalRadius > 384.0D
                    || Double.isNaN(horizontalRadius)
                    || Double.isInfinite(horizontalRadius)
                    || Double.isNaN(verticalRadius)
                    || Double.isInfinite(verticalRadius)) {
                throw new IllegalArgumentException(
                        "provenance radius is outside supported bounds");
            }
            String relation = verticalRelation == null
                    ? "any" : verticalRelation;
            if (!"any".equals(relation)
                    && !"source_above".equals(relation)
                    && !"source_below".equals(relation)) {
                throw new IllegalArgumentException(
                        "unknown vertical_relation '" + relation + "'");
            }
            if (template == null) {
                throw new IllegalArgumentException(
                        "provenance template cannot be null");
            }
            this.maxAgeTicks = maxAgeTicks;
            this.horizontalRadius = horizontalRadius;
            this.verticalRadius = verticalRadius;
            this.verticalRelation = relation;
            this.template = template;
        }
    }

    final Template ordinary;
    final Template fallVariant;
    final List<ProvenanceRule> provenanceRules;

    private DeathMessageRule(
            Template ordinary,
            Template fallVariant,
            List<ProvenanceRule> provenanceRules) {
        this.ordinary = ordinary;
        this.fallVariant = fallVariant;
        this.provenanceRules = Collections.unmodifiableList(
                new ArrayList<ProvenanceRule>(provenanceRules));
    }

    static DeathMessageRule decode(JsonObject root) {
        if (root == null) {
            throw new IllegalArgumentException(
                    "death-message rule cannot be null");
        }
        requireOnly(root, "death-message rule", "default_key", "actor_key",
                "item_key", "item_policy", "fall_variant", "provenance");
        Template ordinary = template(root, true);
        Template fallVariant = null;
        if (root.has("fall_variant")) {
            JsonObject fall = requiredObject(root, "fall_variant");
            requireOnly(fall, "fall_variant", "default_key", "actor_key",
                    "item_key", "item_policy");
            fallVariant = template(fall, true);
        }

        ArrayList<ProvenanceRule> provenance =
                new ArrayList<ProvenanceRule>();
        if (root.has("provenance")) {
            JsonArray entries = requiredArray(root, "provenance");
            for (int i = 0; i < entries.size(); ++i) {
                JsonElement value = entries.get(i);
                if (!value.isJsonObject()) {
                    throw new IllegalArgumentException(
                            "provenance[" + i + "] must be an object");
                }
                JsonObject entry = value.getAsJsonObject();
                requireOnly(entry, "provenance[" + i + "]", "action",
                        "max_age_ticks", "horizontal_radius",
                        "vertical_radius", "vertical_relation",
                        "default_key", "actor_key", "item_key",
                        "item_policy");
                provenance.add(new ProvenanceRule(
                        requiredString(entry, "action"),
                        requiredInt(entry, "max_age_ticks"),
                        requiredDouble(entry, "horizontal_radius"),
                        requiredDouble(entry, "vertical_radius"),
                        optionalString(entry, "vertical_relation", "any"),
                        template(entry, true)));
            }
        }
        return new DeathMessageRule(ordinary, fallVariant, provenance);
    }

    void validateFor(DamageType damageType) {
        if (damageType == null) {
            throw new IllegalArgumentException(
                    "death-message rule has no damage type");
        }
        boolean fallVariants = damageType.getDeathMessageType()
                == DeathMessageType.FALL_VARIANTS;
        if (fallVariants != (this.fallVariant != null)) {
            throw new IllegalArgumentException(fallVariants
                    ? "fall_variants damage type requires fall_variant"
                    : "fall_variant requires a fall_variants damage type");
        }
    }

    ProvenanceRule provenance(String action) {
        if (action == null) return null;
        for (int i = 0; i < this.provenanceRules.size(); ++i) {
            ProvenanceRule rule = this.provenanceRules.get(i);
            if (rule.action.equals(action)) return rule;
        }
        return null;
    }

    private static Template template(JsonObject root, boolean requireDefault) {
        String defaultKey = requireDefault
                ? requiredString(root, "default_key")
                : optionalString(root, "default_key", null);
        return new Template(
                defaultKey,
                optionalString(root, "actor_key", null),
                optionalString(root, "item_key", null),
                ItemPolicy.decode(optionalString(
                        root, "item_policy", "custom_named")));
    }

    private static JsonElement required(JsonObject root, String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            throw new IllegalArgumentException("missing " + key);
        }
        return root.get(key);
    }

    private static String requiredString(JsonObject root, String key) {
        JsonElement value = required(root, key);
        if (!value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return requiredText(value.getAsString(), key);
    }

    private static String optionalString(
            JsonObject root, String key, String fallback) {
        return root.has(key) ? requiredString(root, key) : fallback;
    }

    private static int requiredInt(JsonObject root, String key) {
        JsonElement value = requiredNumber(root, key);
        double number = value.getAsDouble();
        int result = value.getAsInt();
        if (Double.isNaN(number) || Double.isInfinite(number)
                || number != (double) result) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return result;
    }

    private static double requiredDouble(JsonObject root, String key) {
        double result = requiredNumber(root, key).getAsDouble();
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new IllegalArgumentException(key + " must be finite");
        }
        return result;
    }

    private static JsonElement requiredNumber(JsonObject root, String key) {
        JsonElement value = required(root, key);
        if (!value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        return value;
    }

    private static JsonObject requiredObject(JsonObject root, String key) {
        JsonElement value = required(root, key);
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray requiredArray(JsonObject root, String key) {
        JsonElement value = required(root, key);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException(key + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String requiredText(String value, String field) {
        String result = optionalText(value);
        if (result == null) {
            throw new IllegalArgumentException("missing " + field);
        }
        return result;
    }

    private static String optionalText(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.length() == 0 ? null : result;
    }

    private static void requireOnly(
            JsonObject root, String description, String... allowed) {
        Set<String> names = new HashSet<String>(Arrays.asList(allowed));
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!names.contains(entry.getKey())) {
                throw new IllegalArgumentException(description
                        + " contains unknown field '" + entry.getKey() + "'");
            }
        }
    }
}
