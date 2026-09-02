package net.minecraft.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.util.ResourceLocation;

/** Strict Java-8 decoder for the 26.3 damage-type JSON schema. */
public final class DamageTypeCodec {
    private DamageTypeCodec() {}

    public static DamageType decode(ResourceLocation key, JsonObject json) {
        if (key == null || json == null) {
            throw new IllegalArgumentException("Damage type key and data are required");
        }
        requireOnly(json, "message_id", "scaling", "exhaustion", "effects",
                "death_message_type");
        String messageId = requiredString(json, "message_id");
        DamageScaling scaling = DamageScaling.byName(requiredString(json, "scaling"));
        if (scaling == null) {
            throw new IllegalArgumentException("unknown damage scaling '"
                    + optionalText(json, "scaling") + "'");
        }
        JsonElement exhaustionValue = required(json, "exhaustion");
        if (!exhaustionValue.isJsonPrimitive()
                || !exhaustionValue.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("exhaustion must be a number");
        }
        float exhaustion = exhaustionValue.getAsFloat();
        if (Float.isNaN(exhaustion) || Float.isInfinite(exhaustion)) {
            throw new IllegalArgumentException("exhaustion must be finite");
        }
        DamageEffects effects = DamageEffects.byName(optionalString(
                json, "effects", DamageEffects.HURT.getSerializedName()));
        if (effects == null) {
            throw new IllegalArgumentException("unknown damage effects '"
                    + optionalText(json, "effects") + "'");
        }
        DeathMessageType deathMessageType = DeathMessageType.byName(optionalString(
                json, "death_message_type",
                DeathMessageType.DEFAULT.getSerializedName()));
        if (deathMessageType == null) {
            throw new IllegalArgumentException("unknown death_message_type '"
                    + optionalText(json, "death_message_type") + "'");
        }
        return new DamageType(
                messageId, scaling, exhaustion, effects, deathMessageType);
    }

    private static JsonElement required(JsonObject json, String field) {
        if (!json.has(field) || json.get(field).isJsonNull()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return json.get(field);
    }

    private static String requiredString(JsonObject json, String field) {
        JsonElement value = required(json, field);
        if (!value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static String optionalString(
            JsonObject json, String field, String fallback) {
        return json.has(field) ? requiredString(json, field) : fallback;
    }

    private static String optionalText(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() ? "" : value.toString();
    }

    private static void requireOnly(JsonObject json, String... fields) {
        Set<String> allowed = new HashSet<String>(Arrays.asList(fields));
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw new IllegalArgumentException(
                        "damage type contains unknown field '"
                                + entry.getKey() + "'");
            }
        }
    }
}
