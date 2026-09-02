package net.minecraft.server.registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.util.ResourceLocation;

/**
 * Immutable natural-spawn projection decoded from a modern biome resource.
 *
 * <p>The legacy biome still owns every non-spawn property. This value only
 * models the three categories consumed by the Beta natural-spawn loop.</p>
 */
public final class BiomeSpawnSettings {
    public static final String MONSTER = "monster";
    public static final String CREATURE = "creature";
    public static final String WATER_CREATURE = "water_creature";
    public static final List<String> CATEGORY_ORDER = Collections.unmodifiableList(
            Arrays.asList(MONSTER, CREATURE, WATER_CREATURE));

    private final ResourceLocation id;
    private final Map<String, List<SpawnEntry>> spawnsByCategory;

    public BiomeSpawnSettings(
            ResourceLocation id,
            Map<String, ? extends List<SpawnEntry>> spawnsByCategory) {
        if (id == null || spawnsByCategory == null) {
            throw new IllegalArgumentException("Biome spawn settings cannot be null");
        }
        LinkedHashMap<String, List<SpawnEntry>> copy =
                new LinkedHashMap<String, List<SpawnEntry>>();
        for (int i = 0; i < CATEGORY_ORDER.size(); i++) {
            String category = CATEGORY_ORDER.get(i);
            List<SpawnEntry> entries = spawnsByCategory.get(category);
            if (entries == null) {
                throw new IllegalArgumentException(
                        "Missing natural-spawn category '" + category + "'");
            }
            ArrayList<SpawnEntry> entryCopy = new ArrayList<SpawnEntry>(entries);
            for (int j = 0; j < entryCopy.size(); j++) {
                if (entryCopy.get(j) == null) {
                    throw new IllegalArgumentException(
                            "Natural-spawn entries cannot contain null");
                }
            }
            copy.put(category, Collections.unmodifiableList(entryCopy));
        }
        if (copy.size() != spawnsByCategory.size()) {
            throw new IllegalArgumentException("Unsupported natural-spawn category");
        }
        this.id = id;
        this.spawnsByCategory = Collections.unmodifiableMap(copy);
    }

    public ResourceLocation getId() {
        return this.id;
    }

    public Map<String, List<SpawnEntry>> getSpawnsByCategory() {
        return this.spawnsByCategory;
    }

    public List<SpawnEntry> getSpawns(String category) {
        List<SpawnEntry> entries = this.spawnsByCategory.get(category);
        return entries == null ? Collections.<SpawnEntry>emptyList() : entries;
    }

    /** One weighted legacy spawn choice with a deterministic group stop cap. */
    public static final class SpawnEntry {
        private final ResourceLocation entityType;
        private final Class<? extends EntityLiving> entityClass;
        private final int count;
        private final int weight;

        public SpawnEntry(
                ResourceLocation entityType,
                Class<? extends EntityLiving> entityClass,
                int count,
                int weight) {
            if (entityType == null || entityClass == null) {
                throw new IllegalArgumentException("Spawn entry entity type cannot be null");
            }
            if (count <= 0 || weight <= 0) {
                throw new IllegalArgumentException(
                        "Spawn entry count and weight must be positive");
            }
            this.entityType = entityType;
            this.entityClass = entityClass;
            this.count = count;
            this.weight = weight;
        }

        public ResourceLocation getEntityType() {
            return this.entityType;
        }

        public Class<? extends EntityLiving> getEntityClass() {
            return this.entityClass;
        }

        public int getCount() {
            return this.count;
        }

        public int getWeight() {
            return this.weight;
        }
    }
}
