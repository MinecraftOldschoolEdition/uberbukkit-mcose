package net.minecraft.server.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.ItemRecord;
import net.minecraft.server.util.ResourceLocation;

/** Decodes the legacy-compatible subset of the modern jukebox-song schema. */
public final class JukeboxSongCodec {
    private JukeboxSongCodec() {}

    public static JukeboxSong decode(
            ResourceLocation key,
            JsonObject json,
            ItemRecord record,
            int expectedItemId) {
        if (key == null || json == null || record == null) {
            throw new IllegalArgumentException(
                    "Jukebox song key, data, and record binding are required");
        }
        if (record.id != expectedItemId) {
            throw new IllegalArgumentException("song " + key + " must bind legacy item id "
                    + expectedItemId + " (was " + record.id + ")");
        }

        requireOnlyFields(json, "jukebox song", "sound_event", "description",
                "length_in_seconds", "comparator_output", "legacy_record_name",
                "legacy_sound_path");
        ResourceLocation soundEvent = new ResourceLocation(requiredString(json, "sound_event"));
        ResourceLocation expectedSound = new ResourceLocation(
                key.getNamespace(), "music_disc." + key.getPath());
        if (!expectedSound.equals(soundEvent)) {
            throw new IllegalArgumentException("song " + key + " sound_event must be "
                    + expectedSound + " (was " + soundEvent + ")");
        }

        JsonObject description = requiredObject(json, "description");
        requireOnlyFields(description, "description", "translate");
        String translationKey = requiredString(description, "translate");
        String expectedTranslation = "jukebox_song." + key.getNamespace() + "." + key.getPath();
        if (!expectedTranslation.equals(translationKey)) {
            throw new IllegalArgumentException("song " + key + " description must translate "
                    + expectedTranslation + " (was " + translationKey + ")");
        }

        int length = exactInteger(json.get("length_in_seconds"), "length_in_seconds");
        checkRange(length, "length_in_seconds", 0, 3600);
        int comparatorOutput = exactInteger(json.get("comparator_output"), "comparator_output");
        checkRange(comparatorOutput, "comparator_output", 0, 15);

        String legacyRecordName = requiredString(json, "legacy_record_name");
        if (!legacyRecordName.equals(record.getRecordName())) {
            throw new IllegalArgumentException("song " + key + " legacy_record_name must remain '"
                    + record.getRecordName() + "' (was '" + legacyRecordName + "')");
        }
        String legacySoundPath = requiredString(json, "legacy_sound_path");
        if (!legacySoundPath.startsWith("streaming/")
                || legacySoundPath.length() <= "streaming/".length()) {
            throw new IllegalArgumentException("song " + key
                    + " legacy_sound_path must name a streaming resource");
        }

        return new JukeboxSong(
                key.getPath(),
                soundEvent,
                translationKey,
                legacySoundPath,
                length,
                comparatorOutput,
                record.id,
                legacyRecordName);
    }

    private static int exactInteger(JsonElement raw, String description) {
        if (!isNumber(raw)) {
            throw new IllegalArgumentException(description + " must be an integer");
        }
        try {
            return new BigDecimal(raw.getAsJsonPrimitive().getAsString()).intValueExact();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(description + " must be an integer", failure);
        }
    }

    private static boolean isNumber(JsonElement raw) {
        return raw != null && raw.isJsonPrimitive()
                && raw.getAsJsonPrimitive().isNumber();
    }

    private static void checkRange(int value, String description, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(description + " must be between " + minimum
                    + " and " + maximum + " (was " + value + ")");
        }
    }

    private static String requiredString(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException(
                    "missing or invalid required string field '" + field + "'");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isString()) {
            throw new IllegalArgumentException(
                    "missing or invalid required string field '" + field + "'");
        }
        String string = primitive.getAsString();
        if (string.length() == 0) {
            throw new IllegalArgumentException(
                    "required string field '" + field + "' cannot be empty");
        }
        return string;
    }

    private static JsonObject requiredObject(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(
                    "missing or invalid required object field '" + field + "'");
        }
        return value.getAsJsonObject();
    }

    private static void requireOnlyFields(
            JsonObject json,
            String description,
            String... allowedFields) {
        Set<String> allowed = new HashSet<String>(Arrays.asList(allowedFields));
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw new IllegalArgumentException(description + " contains unsupported field '"
                        + entry.getKey() + "'");
            }
        }
    }
}
