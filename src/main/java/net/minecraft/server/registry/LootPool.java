package net.minecraft.server.registry;

import net.minecraft.server.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A pool of loot entries. Each pool rolls a number of times and picks entries.
 */
public class LootPool {
    private final String name;
    private final List<LootEntry> entries;
    private final int minRolls;
    private final int maxRolls;
    private int totalWeight;
    
    /**
     * Creates a loot pool with a fixed number of rolls.
     * @param name The name of this pool
     * @param rolls The number of times to roll
     */
    public LootPool(String name, int rolls) {
        this(name, rolls, rolls);
    }
    
    /**
     * Creates a loot pool with a range of rolls.
     * @param name The name of this pool
     * @param minRolls Minimum number of rolls
     * @param maxRolls Maximum number of rolls
     */
    public LootPool(String name, int minRolls, int maxRolls) {
        this.name = name;
        this.entries = new ArrayList<LootEntry>();
        this.minRolls = minRolls;
        this.maxRolls = maxRolls;
        this.totalWeight = 0;
    }
    
    /**
     * Adds an entry to this pool.
     * @param entry The entry to add
     * @return This pool for chaining
     */
    public LootPool addEntry(LootEntry entry) {
        entries.add(entry);
        totalWeight += entry.getWeight();
        return this;
    }
    
    public String getName() {
        return name;
    }
    
    public List<LootEntry> getEntries() {
        return entries;
    }
    
    public int getMinRolls() {
        return minRolls;
    }
    
    public int getMaxRolls() {
        return maxRolls;
    }
    
    /**
     * Generates loot from this pool.
     * @param random The random source
     * @return A list of generated ItemStacks
     */
    public List<ItemStack> generateLoot(Random random) {
        List<ItemStack> result = new ArrayList<ItemStack>();
        
        if (entries.isEmpty() || totalWeight <= 0) {
            return result;
        }
        
        int rolls = minRolls;
        if (maxRolls > minRolls) {
            rolls = minRolls + random.nextInt(maxRolls - minRolls + 1);
        }
        
        for (int i = 0; i < rolls; i++) {
            LootEntry selected = selectWeightedEntry(random);
            if (selected != null) {
                ItemStack stack = selected.generateStack(random);
                if (stack != null) {
                    result.add(stack);
                }
            }
        }
        
        return result;
    }
    
    private LootEntry selectWeightedEntry(Random random) {
        if (totalWeight <= 0) return null;
        
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        
        for (int i = 0; i < entries.size(); i++) {
            LootEntry entry = entries.get(i);
            cumulative += entry.getWeight();
            if (roll < cumulative) {
                return entry;
            }
        }
        
        // Fallback to last entry
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }
    
    // Builder pattern
    public static Builder builder(String name) {
        return new Builder(name);
    }
    
    public static class Builder {
        private final String name;
        private int minRolls = 1;
        private int maxRolls = 1;
        private final List<LootEntry> entries = new ArrayList<LootEntry>();
        
        public Builder(String name) {
            this.name = name;
        }
        
        public Builder rolls(int rolls) {
            this.minRolls = rolls;
            this.maxRolls = rolls;
            return this;
        }
        
        public Builder rolls(int min, int max) {
            this.minRolls = min;
            this.maxRolls = max;
            return this;
        }
        
        public Builder add(LootEntry entry) {
            entries.add(entry);
            return this;
        }
        
        public Builder add(LootEntry.Builder entryBuilder) {
            entries.add(entryBuilder.build());
            return this;
        }
        
        public LootPool build() {
            LootPool pool = new LootPool(name, minRolls, maxRolls);
            for (int i = 0; i < entries.size(); i++) {
                pool.addEntry(entries.get(i));
            }
            return pool;
        }
    }
}

