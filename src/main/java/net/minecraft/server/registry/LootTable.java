package net.minecraft.server.registry;

import net.minecraft.server.IInventory;
import net.minecraft.server.ItemStack;
import net.minecraft.server.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A loot table containing multiple pools. Used for generating loot in chests,
 * mob drops, fishing, etc.
 */
public class LootTable {
    private final ResourceLocation id;
    private final List<LootPool> pools;
    
    public LootTable(ResourceLocation id) {
        this.id = id;
        this.pools = new ArrayList<LootPool>();
    }
    
    /**
     * Adds a pool to this loot table.
     * @param pool The pool to add
     * @return This table for chaining
     */
    public LootTable addPool(LootPool pool) {
        pools.add(pool);
        return this;
    }
    
    public ResourceLocation getId() {
        return id;
    }
    
    public List<LootPool> getPools() {
        return pools;
    }
    
    /**
     * Generates loot from all pools in this table.
     * @param random The random source
     * @return A list of generated ItemStacks
     */
    public List<ItemStack> generateLoot(Random random) {
        List<ItemStack> result = new ArrayList<ItemStack>();
        
        for (int i = 0; i < pools.size(); i++) {
            result.addAll(pools.get(i).generateLoot(random));
        }
        
        return result;
    }
    
    /**
     * Fills a container (like a chest) with generated loot.
     * @param inventory The inventory to fill
     * @param random The random source
     */
    public void fillInventory(IInventory inventory, Random random) {
        List<ItemStack> loot = generateLoot(random);
        
        // Distribute loot across random slots
        for (int i = 0; i < loot.size(); i++) {
            ItemStack stack = loot.get(i);
            if (stack != null) {
                int slot = random.nextInt(inventory.getSize());
                // Find an empty slot if this one is taken
                int attempts = 0;
                while (inventory.getItem(slot) != null && attempts < inventory.getSize()) {
                    slot = random.nextInt(inventory.getSize());
                    attempts++;
                }
                if (inventory.getItem(slot) == null) {
                    inventory.setItem(slot, stack);
                }
            }
        }
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
        private final List<LootPool> pools = new ArrayList<LootPool>();
        
        public Builder(ResourceLocation id) {
            this.id = id;
        }
        
        public Builder pool(LootPool pool) {
            pools.add(pool);
            return this;
        }
        
        public Builder pool(LootPool.Builder poolBuilder) {
            pools.add(poolBuilder.build());
            return this;
        }
        
        public LootTable build() {
            LootTable table = new LootTable(id);
            for (int i = 0; i < pools.size(); i++) {
                table.addPool(pools.get(i));
            }
            return table;
        }
    }
}

