package net.minecraft.server.registry.number;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import net.minecraft.server.util.ResourceLocation;

/** Strict 26.3-shaped direct codec for constants and inline scalar constants. */
public final class NumberProviderCodec {
    private NumberProviderCodec() {}

    public static NumberProvider decode(ResourceLocation key, JsonElement json) {
        if (key == null || json == null || json.isJsonNull()) {
            throw new IllegalArgumentException("Number provider key and value cannot be null");
        }
        if (isNumber(json)) {
            return new ConstantNumberProvider(readFiniteFloat(json, "number provider"));
        }
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException(
                    "Number provider root must be a number or minecraft:constant object");
        }

        JsonObject object = json.getAsJsonObject();
        for (Map.Entry<String, JsonElement> field : object.entrySet()) {
            if (!"type".equals(field.getKey()) && !"value".equals(field.getKey())) {
                throw new IllegalArgumentException(
                        "Number provider contains unsupported field '" + field.getKey() + "'");
            }
        }
        JsonElement type = object.get("type");
        if (type == null || !type.isJsonPrimitive()
                || !type.getAsJsonPrimitive().isString()
                || !ConstantNumberProvider.TYPE.toString().equals(type.getAsString())) {
            throw new IllegalArgumentException(
                    "Number provider type must be minecraft:constant");
        }
        JsonElement value = object.get("value");
        if (!isNumber(value)) {
            throw new IllegalArgumentException(
                    "minecraft:constant value must be a JSON number");
        }
        return new ConstantNumberProvider(readFiniteFloat(value, "minecraft:constant value"));
    }

    private static boolean isNumber(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) return false;
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        return primitive.isNumber();
    }

    private static float readFiniteFloat(JsonElement value, String description) {
        double decoded;
        try {
            decoded = value.getAsDouble();
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(description + " must be a JSON number", failure);
        }
        float narrowed = (float)decoded;
        if (Double.isNaN(decoded) || Double.isInfinite(decoded)
                || Float.isNaN(narrowed) || Float.isInfinite(narrowed)) {
            throw new IllegalArgumentException(description + " must be finite");
        }
        return narrowed;
    }
}
