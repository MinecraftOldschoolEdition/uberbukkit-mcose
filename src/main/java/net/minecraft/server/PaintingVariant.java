package net.minecraft.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import net.minecraft.server.util.ResourceLocation;

/** Immutable, data-decoded painting definition matching the modern registry. */
public final class PaintingVariant {
    private final int width;
    private final int height;
    private final ResourceLocation assetId;
    private final String legacyTitle;
    private final EnumArt legacyArt;

    private PaintingVariant(
            int width,
            int height,
            ResourceLocation assetId,
            String legacyTitle,
            EnumArt legacyArt) {
        this.width = width;
        this.height = height;
        this.assetId = assetId;
        this.legacyTitle = legacyTitle;
        this.legacyArt = legacyArt;
    }

    public static PaintingVariant decode(ResourceLocation key, JsonObject json, EnumArt legacyArt) {
        if (key == null || json == null || legacyArt == null) {
            throw new IllegalArgumentException(
                    "Painting variant key, data, and legacy bridge are required");
        }

        int width = ranged(json, "width", 1, 16);
        int height = ranged(json, "height", 1, 16);
        String asset = requiredString(json, "asset_id").trim();
        if (asset.length() == 0) {
            throw new IllegalArgumentException(
                    "asset_id must be a non-empty namespaced identifier");
        }
        ResourceLocation assetId = new ResourceLocation(asset);
        if (assetId.getPath().length() == 0) {
            throw new IllegalArgumentException("asset_id path cannot be empty");
        }

        String legacyTitle = requiredString(json, "legacy_title").trim();
        if (legacyTitle.length() == 0 || legacyTitle.length() > EnumArt.z) {
            throw new IllegalArgumentException(
                    "legacy_title must contain 1-" + EnumArt.z + " characters");
        }
        if (!legacyArt.A.equals(legacyTitle)) {
            throw new IllegalArgumentException("legacy_title '" + legacyTitle
                    + "' does not match compatibility art '" + legacyArt.A + "'");
        }
        if (width * 16 != legacyArt.B || height * 16 != legacyArt.C) {
            throw new IllegalArgumentException("data dimensions " + width + "x" + height
                    + " do not preserve legacy dimensions " + (legacyArt.B / 16)
                    + "x" + (legacyArt.C / 16));
        }
        if (!assetId.equals(key)) {
            throw new IllegalArgumentException("asset_id '" + assetId
                    + "' does not preserve the built-in texture id '" + key + "'");
        }

        return new PaintingVariant(width, height, assetId, legacyTitle, legacyArt);
    }

    private static int ranged(JsonObject json, String field, int minimum, int maximum) {
        JsonElement element = json.get(field);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("missing or invalid required field '" + field + "'");
        }

        int value;
        try {
            BigDecimal number = element.getAsBigDecimal();
            value = number.intValueExact();
        } catch (Throwable failure) {
            throw new IllegalArgumentException(field + " must be an integer", failure);
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    field + " must be between " + minimum + " and " + maximum + " (was " + value + ")");
        }
        return value;
    }

    private static String requiredString(JsonObject json, String field) {
        JsonElement element = json.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("missing required field '" + field + "'");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return primitive.getAsString();
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getPixelWidth() {
        return this.width * 16;
    }

    public int getPixelHeight() {
        return this.height * 16;
    }

    public int area() {
        return this.width * this.height;
    }

    public ResourceLocation getAssetId() {
        return this.assetId;
    }

    public EnumArt getLegacyArt() {
        return this.legacyArt;
    }

    public String getLegacyTitle() {
        return this.legacyTitle;
    }

    public String getTexturePath() {
        return "/assets/" + this.assetId.getNamespace()
                + "/textures/entity/painting/" + this.assetId.getPath() + ".png";
    }
}
