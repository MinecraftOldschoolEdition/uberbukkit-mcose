package net.minecraft.server.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.server.BiomeBase;
import net.minecraft.server.util.ResourceLocation;

import java.util.Random;

/**
 * Represents a type of structure that can generate in the world.
 * Structures can have associated loot tables, mob spawner types, and block palettes.
 */
public class StructureType {
    private final ResourceLocation id;
    private final String displayName;
    private final ResourceLocation lootTable;
    private final String[] spawnerMobs;
    private final int[] spawnerWeights;
    private final Set<ResourceLocation> biomes;
    
    private StructureType(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.lootTable = builder.lootTable;
        this.spawnerMobs = builder.spawnerMobs == null
                ? null : builder.spawnerMobs.clone();
        this.spawnerWeights = builder.spawnerWeights == null
                ? null : builder.spawnerWeights.clone();
        this.biomes = Collections.unmodifiableSet(
                new LinkedHashSet<ResourceLocation>(builder.biomes));
    }
    
    public ResourceLocation getId() {
        return id;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public ResourceLocation getLootTable() {
        return lootTable;
    }

    /** Ordered legacy entity identifiers consumed by the spawner picker. */
    public String[] getSpawnerMobIds() {
        return this.spawnerMobs == null
                ? new String[0] : this.spawnerMobs.clone();
    }

    /**
     * Ordered picker weights, or {@code null} when the picker is uniform.
     * The returned array is detached from this immutable descriptor.
     */
    public int[] getSpawnerMobWeights() {
        return this.spawnerWeights == null
                ? null : this.spawnerWeights.clone();
    }

    /** Empty means unrestricted; otherwise the biome must have a listed key. */
    public boolean isValidBiome(BiomeBase biome) {
        if (this.biomes.isEmpty()) return true;
        if (biome == null) return false;
        BiomeRegistryBootstrap.initialize();
        ResourceLocation key = BiomeRegistryApi.getKey(biome);
        return key != null && this.biomes.contains(key);
    }

    public Set<ResourceLocation> getBiomes() {
        return this.biomes;
    }
    
    /**
     * Picks a random mob type for spawners in this structure.
     * @param random The random source
     * @return The entity type name (e.g., "Zombie", "Skeleton")
     */
    public String pickSpawnerMob(Random random) {
        if (spawnerMobs == null || spawnerMobs.length == 0) {
            return "Pig"; // Fallback
        }
        
        if (spawnerWeights == null || spawnerWeights.length != spawnerMobs.length) {
            // No weights, pick uniformly
            return spawnerMobs[random.nextInt(spawnerMobs.length)];
        }
        
        // Weighted selection
        int totalWeight = 0;
        for (int i = 0; i < spawnerWeights.length; i++) {
            totalWeight += spawnerWeights[i];
        }
        
        if (totalWeight <= 0) {
            return spawnerMobs[0];
        }
        
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < spawnerWeights.length; i++) {
            cumulative += spawnerWeights[i];
            if (roll < cumulative) {
                return spawnerMobs[i];
            }
        }
        
        return spawnerMobs[spawnerMobs.length - 1];
    }
    
    /**
     * Gets the loot table for this structure.
     * @return The loot table, or null if not found
     */
    public LootTable getLootTableInstance() {
        if (lootTable == null) return null;
        return Registries.LOOT_TABLE.get(lootTable);
    }
    
    // Builder pattern
    public static Builder builder(String namespace, String path) {
        return new Builder(new ResourceLocation(namespace, path));
    }
    
    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }
    
    public static class Builder {
        private final ResourceLocation id;
        private String displayName;
        private ResourceLocation lootTable;
        private String[] spawnerMobs;
        private int[] spawnerWeights;
        private final LinkedHashSet<ResourceLocation> biomes =
                new LinkedHashSet<ResourceLocation>();
        
        public Builder(ResourceLocation id) {
            this.id = id;
            this.displayName = id.getPath();
        }
        
        public Builder displayName(String name) {
            this.displayName = name;
            return this;
        }
        
        public Builder lootTable(ResourceLocation lootTable) {
            this.lootTable = lootTable;
            return this;
        }
        
        public Builder lootTable(String path) {
            this.lootTable = new ResourceLocation("minecraft", path);
            return this;
        }
        
        /**
         * Sets spawner mobs with equal weights.
         */
        public Builder spawnerMobs(String... mobs) {
            this.spawnerMobs = mobs;
            this.spawnerWeights = null;
            return this;
        }
        
        /**
         * Sets spawner mobs with custom weights.
         */
        public Builder spawnerMobs(String[] mobs, int[] weights) {
            this.spawnerMobs = mobs;
            this.spawnerWeights = weights;
            return this;
        }

        public Builder biomes(Collection<ResourceLocation> values) {
            this.biomes.clear();
            if (values != null) this.biomes.addAll(values);
            return this;
        }
        
        public StructureType build() {
            return new StructureType(this);
        }
    }
}
