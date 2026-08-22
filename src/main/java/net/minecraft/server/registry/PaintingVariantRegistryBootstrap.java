package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.EnumArt;
import net.minecraft.server.PaintingVariant;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Loads the built-in painting variant registry from 26.3-style JSON data. */
public final class PaintingVariantRegistryBootstrap {
    private static boolean initialized;
    private static final Map<EnumArt, PaintingVariant> BY_LEGACY_ART =
            new IdentityHashMap<EnumArt, PaintingVariant>();
    private static final Map<String, PaintingVariant> BY_LEGACY_TITLE =
            new LinkedHashMap<String, PaintingVariant>();

    private PaintingVariantRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;

        final LinkedHashMap<ResourceLocation, EnumArt> bridges =
                new LinkedHashMap<ResourceLocation, EnumArt>();
        EnumArt[] arts = EnumArt.values();
        List<ResourceLocation> keys = RegistryDataLoader.loadRequiredTag(
                "painting_variant", new ResourceLocation("minecraft", "placeable"));
        for (int i = 0; i < arts.length; i++) {
            ResourceLocation key = keyFor(arts[i]);
            if (bridges.put(key, arts[i]) != null) {
                throw new IllegalStateException("Duplicate painting compatibility key " + key);
            }
        }

        if (keys.size() != arts.length) {
            throw new IllegalStateException("Painting placeable tag has " + keys.size()
                    + " entries, expected " + arts.length);
        }
        for (int i = 0; i < keys.size(); i++) {
            ResourceLocation expected = keyFor(arts[i]);
            if (!expected.equals(keys.get(i))) {
                throw new IllegalStateException("Painting placeable order changed at " + i
                        + ": expected " + expected + ", found " + keys.get(i));
            }
            if (!bridges.containsKey(keys.get(i))) {
                throw new IllegalStateException(
                        "Painting placeable tag has no legacy bridge for " + keys.get(i));
            }
        }

        Map<ResourceLocation, PaintingVariant> decoded = RegistryDataLoader.loadRequired(
                "painting_variant",
                keys,
                new RegistryDataLoader.Decoder<PaintingVariant>() {
                    public PaintingVariant decode(ResourceLocation key, JsonObject json) {
                        return PaintingVariant.decode(key, json, bridges.get(key));
                    }
                });

        if (!decoded.containsKey(new ResourceLocation("minecraft", "kebab"))) {
            throw new IllegalStateException(
                    "Painting variant registry is missing minecraft:kebab");
        }

        LinkedHashMap<String, PaintingVariant> titles =
                new LinkedHashMap<String, PaintingVariant>();
        for (Map.Entry<ResourceLocation, PaintingVariant> entry : decoded.entrySet()) {
            PaintingVariant variant = entry.getValue();
            if (titles.put(variant.getLegacyTitle(), variant) != null) {
                throw new IllegalStateException(
                        "Duplicate painting legacy title " + variant.getLegacyTitle());
            }
            if (Registries.PAINTING_VARIANT.get(entry.getKey()) != null) {
                throw new IllegalStateException(
                        "Painting variant key already exists " + entry.getKey());
            }
        }

        if (!PaintingVariantRegistryApi.publishAtomic(decoded)) {
            throw new IllegalStateException(
                    "Painting variant registry batch could not be published atomically");
        }
        PaintingVariantRegistryApi.freeze();

        for (Map.Entry<ResourceLocation, PaintingVariant> entry : decoded.entrySet()) {
            PaintingVariant variant = entry.getValue();
            BY_LEGACY_ART.put(variant.getLegacyArt(), variant);
            BY_LEGACY_TITLE.put(variant.getLegacyTitle(), variant);
        }
        initialized = true;
    }

    public static synchronized PaintingVariant getByLegacyArt(EnumArt art) {
        return BY_LEGACY_ART.get(art);
    }

    public static synchronized PaintingVariant getByLegacyTitle(String title) {
        return title == null ? null : BY_LEGACY_TITLE.get(title);
    }

    public static ResourceLocation keyFor(EnumArt art) {
        if (art == null) return null;
        return new ResourceLocation("minecraft", toSnake(art.A));
    }

    private static String toSnake(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) out.append('_');
                out.append(Character.toLowerCase(c));
            } else if (c == ' ' || c == '-') {
                out.append('_');
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }
}
