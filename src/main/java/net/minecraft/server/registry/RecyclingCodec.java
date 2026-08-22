package net.minecraft.server.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.RecyclingManager;
import net.minecraft.server.util.ResourceLocation;

/** Strict decoder for {@code data/<namespace>/recycling/<input>.json}. */
public final class RecyclingCodec {
    public static final String TYPE = "mcose:recycling";

    /** Decoded highest-layer entry; tombstones never reach the live snapshot. */
    public static final class Entry {
        private final ResourceLocation inputKey;
        private final int inputItemId;
        private final RecyclingManager.Definition definition;

        private Entry(
                ResourceLocation inputKey,
                int inputItemId,
                RecyclingManager.Definition definition) {
            this.inputKey = inputKey;
            this.inputItemId = inputItemId;
            this.definition = definition;
        }

        public ResourceLocation getInputKey() {
            return this.inputKey;
        }

        public int getInputItemId() {
            return this.inputItemId;
        }

        public boolean isTombstone() {
            return this.definition == null;
        }

        public RecyclingManager.Definition getDefinition() {
            return this.definition;
        }
    }

    private RecyclingCodec() {}

    public static Entry decode(ResourceLocation inputKey, JsonObject json) {
        if (inputKey == null || json == null) {
            throw new IllegalArgumentException(
                    "Recycling input key and data are required");
        }

        // Discovery has already validated identifier syntax. Resolution here
        // pins the filename to the registry's one canonical identity.
        ItemRegistry.keys();
        Item input = ItemRegistry.get(inputKey);
        if (input == null) {
            throw new IllegalArgumentException(
                    "unknown recycling input '" + inputKey + "'");
        }
        ResourceLocation canonicalInput = ItemRegistry.getKey(input);
        if (!inputKey.equals(canonicalInput)) {
            throw new IllegalArgumentException(
                    "recycling input must use canonical key '" + canonicalInput
                            + "' instead of alias '" + inputKey + "'");
        }
        requireType(json);
        if (json.has("enabled")) {
            requireOnly(json, "recycling tombstone", "type", "enabled");
            JsonElement enabled = json.get("enabled");
            if (enabled == null || !enabled.isJsonPrimitive()
                    || !enabled.getAsJsonPrimitive().isBoolean()
                    || enabled.getAsBoolean()) {
                throw new IllegalArgumentException(
                        "recycling tombstone enabled must be false");
            }
            return new Entry(inputKey, input.id, null);
        }

        requireOnly(json, "recycling definition", "type", "method", "result",
                "legacy_inert");
        boolean legacyInert = false;
        if (json.has("legacy_inert")) {
            JsonElement inert = json.get("legacy_inert");
            if (inert == null || !inert.isJsonPrimitive()
                    || !inert.getAsJsonPrimitive().isBoolean()
                    || !inert.getAsBoolean()) {
                throw new IllegalArgumentException(
                        "recycling legacy_inert must be true when present");
            }
            legacyInert = true;
        }
        if (input.e() <= 0 && !legacyInert) {
            throw new IllegalArgumentException(
                    "non-damageable recycling input requires legacy_inert:true: "
                            + inputKey);
        }
        if (input.e() > 0 && legacyInert) {
            throw new IllegalArgumentException(
                    "damageable recycling input cannot use legacy_inert: "
                            + inputKey);
        }
        if (legacyInert && (input != Item.BOW
                || !new ResourceLocation("minecraft", "bow").equals(inputKey))) {
            throw new IllegalArgumentException(
                    "legacy_inert is reserved for the legacy bow definition");
        }
        String rawMethod = requiredString(json, "method");
        RecyclingManager.Method method;
        if ("craft".equals(rawMethod)) {
            method = RecyclingManager.Method.CRAFT;
        } else if ("smelt".equals(rawMethod)) {
            method = RecyclingManager.Method.SMELT;
        } else {
            throw new IllegalArgumentException(
                    "recycling method must be 'craft' or 'smelt'");
        }

        JsonObject result = requiredObject(json, "result");
        requireOnly(result, "recycling result", "id", "count", "legacy_metadata");
        ResourceLocation resultKey = parseStrictResourceLocation(
                requiredString(result, "id"), "recycling result id");
        int count = requiredInteger(result, "count", 1, 64);
        int legacyMetadata = requiredInteger(
                result, "legacy_metadata", Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (legacyMetadata != 0) {
            throw new IllegalArgumentException(
                    "recycling result legacy_metadata must be 0");
        }
        int resultItemId = resolveCanonicalResult(resultKey);

        RecyclingManager.Definition definition =
                new RecyclingManager.Definition(
                        inputKey,
                        input.id,
                        resultKey,
                        resultItemId,
                        count,
                        legacyMetadata,
                        method,
                        legacyInert);
        return new Entry(inputKey, input.id, definition);
    }

    private static void requireType(JsonObject json) {
        String type = requiredString(json, "type");
        if (!TYPE.equals(type)) {
            throw new IllegalArgumentException(
                    "recycling type must be '" + TYPE + "'");
        }
    }

    private static int resolveCanonicalResult(ResourceLocation key) {
        Item item = ItemRegistry.get(key);
        boolean canonicalItem = item != null && key.equals(ItemRegistry.getKey(item));
        Block block = BlockRegistry.get(key);
        boolean canonicalBlock = block != null && key.equals(BlockRegistry.getKey(block));

        if (!canonicalItem && !canonicalBlock) {
            if (item != null || block != null) {
                throw new IllegalArgumentException(
                        "recycling result must use a canonical item or block key: " + key);
            }
            throw new IllegalArgumentException(
                    "unknown recycling result item or block '" + key + "'");
        }

        int itemId = canonicalItem ? item.id : -1;
        int blockId = canonicalBlock ? block.id : -1;
        if (canonicalBlock && (blockId < 0 || blockId >= Item.byId.length
                || Item.byId[blockId] == null)) {
            throw new IllegalArgumentException(
                    "recycling result block has no item form: " + key);
        }
        if (canonicalItem && canonicalBlock && itemId != blockId) {
            throw new IllegalArgumentException(
                    "ambiguous recycling result key '" + key + "'");
        }
        return canonicalItem ? itemId : blockId;
    }

    private static ResourceLocation parseStrictResourceLocation(
            String identifier,
            String description) {
        if (identifier == null || identifier.length() == 0
                || identifier.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(
                    description + " has invalid identifier '" + identifier + "'");
        }
        int colon = identifier.indexOf(':');
        if (colon != identifier.lastIndexOf(':')) {
            throw new IllegalArgumentException(
                    description + " has invalid identifier '" + identifier + "'");
        }
        String namespace = colon < 0 ? "minecraft" : identifier.substring(0, colon);
        String path = colon < 0 ? identifier : identifier.substring(colon + 1);
        validateIdentifierPart(namespace, true, description);
        validateIdentifierPart(path, false, description);
        return new ResourceLocation(namespace, path);
    }

    private static void validateIdentifierPart(
            String value,
            boolean namespace,
            String description) {
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException(
                    description + " has an empty identifier component");
        }
        if (!namespace) {
            String[] segments = value.split("/", -1);
            for (int i = 0; i < segments.length; i++) {
                if (segments[i].length() == 0 || ".".equals(segments[i])
                        || "..".equals(segments[i])) {
                    throw new IllegalArgumentException(
                            description + " has invalid relative path '" + value + "'");
                }
            }
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.'
                    || (!namespace && c == '/');
            if (!valid) {
                throw new IllegalArgumentException(
                        description + " has invalid "
                                + (namespace ? "namespace" : "path")
                                + " '" + value + "'");
            }
        }
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
            throw new IllegalArgumentException(
                    "field '" + field + "' cannot be empty");
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
            throw new IllegalArgumentException(
                    "field '" + field + "' must be between " + minimum
                            + " and " + maximum + " (was " + value + ")");
        }
        return value;
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
