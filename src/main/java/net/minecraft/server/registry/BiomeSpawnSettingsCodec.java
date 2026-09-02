package net.minecraft.server.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.server.EntityAnimal;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.EntityWaterAnimal;
import net.minecraft.server.IMonster;
import net.minecraft.server.util.ResourceLocation;

/** Strict codec for the narrow 26.3 natural-mob-spawn biome projection. */
public final class BiomeSpawnSettingsCodec {
    private static final String NATURAL_MOB_SPAWNS =
            "minecraft:gameplay/natural_mob_spawns";

    private BiomeSpawnSettingsCodec() {}

    public static BiomeSpawnSettings decode(ResourceLocation key, JsonObject root) {
        if (key == null || root == null) {
            throw new IllegalArgumentException("Biome spawn key and root cannot be null");
        }
        requireFields(root, "biome root", "attributes");
        JsonObject attributes = requireObject(root, "attributes", "biome attributes");
        requireFields(attributes, "biome attributes", NATURAL_MOB_SPAWNS);
        JsonObject natural = requireObject(
                attributes, NATURAL_MOB_SPAWNS, "natural mob spawns attribute");
        requireFields(natural, "natural mob spawns attribute", "modifier", "argument");
        requireString(natural, "modifier", "overlay");

        JsonObject argument = requireObject(natural, "argument", "natural mob spawns argument");
        requireFields(argument, "natural mob spawns argument",
                "spawns_by_category", "spawn_costs");
        JsonObject spawnCosts = requireObject(argument, "spawn_costs", "spawn costs");
        if (!spawnCosts.entrySet().isEmpty()) {
            throw new IllegalArgumentException("spawn_costs must be empty");
        }

        JsonObject categories = requireObject(
                argument, "spawns_by_category", "spawns by category");
        requireFields(categories, "spawns by category",
                BiomeSpawnSettings.MONSTER,
                BiomeSpawnSettings.CREATURE,
                BiomeSpawnSettings.WATER_CREATURE);

        LinkedHashMap<String, List<BiomeSpawnSettings.SpawnEntry>> decoded =
                new LinkedHashMap<String, List<BiomeSpawnSettings.SpawnEntry>>();
        for (int i = 0; i < BiomeSpawnSettings.CATEGORY_ORDER.size(); i++) {
            String category = BiomeSpawnSettings.CATEGORY_ORDER.get(i);
            JsonElement categoryValue = categories.get(category);
            if (categoryValue == null || !categoryValue.isJsonArray()) {
                throw new IllegalArgumentException(
                        "Natural-spawn category '" + category + "' must be an array");
            }
            decoded.put(category, decodeEntries(category, categoryValue.getAsJsonArray()));
        }
        return new BiomeSpawnSettings(key, decoded);
    }

    private static List<BiomeSpawnSettings.SpawnEntry> decodeEntries(
            String category, JsonArray values) {
        ArrayList<BiomeSpawnSettings.SpawnEntry> entries =
                new ArrayList<BiomeSpawnSettings.SpawnEntry>();
        long totalWeight = 0L;
        for (int i = 0; i < values.size(); i++) {
            JsonElement value = values.get(i);
            if (value == null || !value.isJsonObject()) {
                throw new IllegalArgumentException(
                        "Natural-spawn entry " + category + "[" + i + "] must be an object");
            }
            JsonObject object = value.getAsJsonObject();
            requireFields(object, "natural-spawn entry", "type", "count", "weight");
            ResourceLocation entityKey = readEntityType(object.get("type"));
            int count = readPositiveInt(object.get("count"), "spawn count");
            int weight = readPositiveInt(object.get("weight"), "spawn weight");
            totalWeight += (long)weight;
            if (totalWeight > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Total spawn weight overflows a positive integer in category '"
                                + category + "'");
            }
            Class<?> entityClass = EntityTypeRegistry.get(entityKey);
            ResourceLocation canonical = entityClass == null
                    ? null : EntityTypeRegistry.getKey(entityClass);
            if (entityClass == null || !EntityLiving.class.isAssignableFrom(entityClass)
                    || !entityKey.equals(canonical)) {
                throw new IllegalArgumentException(
                        "Spawn entity type must be a registered canonical living entity: "
                                + entityKey);
            }
            requireCategoryMatch(category, entityKey, entityClass);
            @SuppressWarnings("unchecked")
            Class<? extends EntityLiving> livingClass =
                    (Class<? extends EntityLiving>)entityClass;
            entries.add(new BiomeSpawnSettings.SpawnEntry(
                    entityKey, livingClass, count, weight));
        }
        return entries;
    }

    private static void requireCategoryMatch(
            String category, ResourceLocation entityKey, Class<?> entityClass) {
        boolean matches;
        if (BiomeSpawnSettings.MONSTER.equals(category)) {
            matches = IMonster.class.isAssignableFrom(entityClass);
        } else if (BiomeSpawnSettings.CREATURE.equals(category)) {
            matches = EntityAnimal.class.isAssignableFrom(entityClass);
        } else if (BiomeSpawnSettings.WATER_CREATURE.equals(category)) {
            matches = EntityWaterAnimal.class.isAssignableFrom(entityClass);
        } else {
            matches = false;
        }
        if (!matches) {
            throw new IllegalArgumentException(
                    "Spawn entity type " + entityKey
                            + " does not belong in category '" + category + "'");
        }
    }

    private static ResourceLocation readEntityType(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Spawn entity type must be a string identifier");
        }
        try {
            return new ResourceLocation(value.getAsString());
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Invalid spawn entity type identifier", failure);
        }
    }

    private static int readPositiveInt(JsonElement value, String description) {
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException(description + " must be a positive integer");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(description + " must be a positive integer");
        }
        final int decoded;
        try {
            decoded = new BigDecimal(primitive.getAsString()).intValueExact();
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(description + " must be a positive integer", failure);
        }
        if (decoded <= 0) {
            throw new IllegalArgumentException(description + " must be a positive integer");
        }
        return decoded;
    }

    private static JsonObject requireObject(
            JsonObject parent, String field, String description) {
        JsonElement value = parent.get(field);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(description + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static void requireString(JsonObject parent, String field, String expected) {
        JsonElement value = parent.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || !expected.equals(value.getAsString())) {
            throw new IllegalArgumentException(field + " must be '" + expected + "'");
        }
    }

    private static void requireFields(
            JsonObject object, String description, String... expectedFields) {
        Set<String> expected = new HashSet<String>(Arrays.asList(expectedFields));
        for (Map.Entry<String, JsonElement> field : object.entrySet()) {
            if (!expected.remove(field.getKey())) {
                throw new IllegalArgumentException(
                        description + " contains unsupported field '" + field.getKey() + "'");
            }
        }
        if (!expected.isEmpty()) {
            throw new IllegalArgumentException(
                    description + " is missing required field(s) " + expected);
        }
    }
}
