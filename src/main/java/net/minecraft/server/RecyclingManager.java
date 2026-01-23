package net.minecraft.server;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages item recycling - converting damaged tools, weapons, and armor
 * back into their base materials proportional to remaining durability.
 * 
 * Items are categorized into two types:
 * - CRAFT: Leather/cloth and wood/string items, recycled via crafting table
 * - SMELT: Ore-based items (iron, gold, diamond, stone), recycled via furnace
 */
public class RecyclingManager {
    private static final RecyclingManager instance = new RecyclingManager();
    
    /**
     * Type of recycling method required for an item.
     */
    public enum RecycleType {
        CRAFT,  // Recycled via crafting table (leather, wood, string)
        SMELT   // Recycled via furnace (iron, gold, diamond, stone)
    }
    
    /**
     * Maps item IDs to their recyclable material info.
     * Key: item ID
     * Value: RecycleInfo containing material item ID, base quantity, and recycle type
     */
    private Map<Integer, RecycleInfo> recycleMap = new HashMap<Integer, RecycleInfo>();
    
    public static RecyclingManager getInstance() {
        return instance;
    }
    
    private RecyclingManager() {
        // Register all recyclable items
        registerTools();
        registerWeapons();
        registerArmor();
        registerMiscItems();
    }
    
    private void registerTools() {
        // Pickaxes - 3 material
        // Wood tools: crafting table
        register(Item.WOOD_PICKAXE, Block.WOOD.id, 3, RecycleType.CRAFT);
        // Stone/Iron/Diamond/Gold tools: furnace
        register(Item.STONE_PICKAXE, Block.COBBLESTONE.id, 3, RecycleType.SMELT);
        register(Item.IRON_PICKAXE, Item.IRON_INGOT.id, 3, RecycleType.SMELT);
        register(Item.DIAMOND_PICKAXE, Item.DIAMOND.id, 3, RecycleType.SMELT);
        register(Item.GOLD_PICKAXE, Item.GOLD_INGOT.id, 3, RecycleType.SMELT);
        
        // Axes - 3 material
        register(Item.WOOD_AXE, Block.WOOD.id, 3, RecycleType.CRAFT);
        register(Item.STONE_AXE, Block.COBBLESTONE.id, 3, RecycleType.SMELT);
        register(Item.IRON_AXE, Item.IRON_INGOT.id, 3, RecycleType.SMELT);
        register(Item.DIAMOND_AXE, Item.DIAMOND.id, 3, RecycleType.SMELT);
        register(Item.GOLD_AXE, Item.GOLD_INGOT.id, 3, RecycleType.SMELT);
        
        // Shovels/Spades - 1 material
        register(Item.WOOD_SPADE, Block.WOOD.id, 1, RecycleType.CRAFT);
        register(Item.STONE_SPADE, Block.COBBLESTONE.id, 1, RecycleType.SMELT);
        register(Item.IRON_SPADE, Item.IRON_INGOT.id, 1, RecycleType.SMELT);
        register(Item.DIAMOND_SPADE, Item.DIAMOND.id, 1, RecycleType.SMELT);
        register(Item.GOLD_SPADE, Item.GOLD_INGOT.id, 1, RecycleType.SMELT);
        
        // Hoes - 2 material
        register(Item.WOOD_HOE, Block.WOOD.id, 2, RecycleType.CRAFT);
        register(Item.STONE_HOE, Block.COBBLESTONE.id, 2, RecycleType.SMELT);
        register(Item.IRON_HOE, Item.IRON_INGOT.id, 2, RecycleType.SMELT);
        register(Item.DIAMOND_HOE, Item.DIAMOND.id, 2, RecycleType.SMELT);
        register(Item.GOLD_HOE, Item.GOLD_INGOT.id, 2, RecycleType.SMELT);
    }
    
    private void registerWeapons() {
        // Swords - 2 material
        register(Item.WOOD_SWORD, Block.WOOD.id, 2, RecycleType.CRAFT);
        register(Item.STONE_SWORD, Block.COBBLESTONE.id, 2, RecycleType.SMELT);
        register(Item.IRON_SWORD, Item.IRON_INGOT.id, 2, RecycleType.SMELT);
        register(Item.DIAMOND_SWORD, Item.DIAMOND.id, 2, RecycleType.SMELT);
        register(Item.GOLD_SWORD, Item.GOLD_INGOT.id, 2, RecycleType.SMELT);
        
        // Bow - 3 string, no sticks returned (crafting table)
        register(Item.BOW, Item.STRING.id, 3, RecycleType.CRAFT);
    }
    
    private void registerArmor() {
        // Leather armor (crafting table)
        register(Item.LEATHER_HELMET, Item.LEATHER.id, 5, RecycleType.CRAFT);
        register(Item.LEATHER_CHESTPLATE, Item.LEATHER.id, 8, RecycleType.CRAFT);
        register(Item.LEATHER_LEGGINGS, Item.LEATHER.id, 7, RecycleType.CRAFT);
        register(Item.LEATHER_BOOTS, Item.LEATHER.id, 4, RecycleType.CRAFT);
        
        // Chain armor - skip since fire is unobtainable in survival
        // (chainmail cannot be recycled)
        
        // Iron armor (furnace)
        register(Item.IRON_HELMET, Item.IRON_INGOT.id, 5, RecycleType.SMELT);
        register(Item.IRON_CHESTPLATE, Item.IRON_INGOT.id, 8, RecycleType.SMELT);
        register(Item.IRON_LEGGINGS, Item.IRON_INGOT.id, 7, RecycleType.SMELT);
        register(Item.IRON_BOOTS, Item.IRON_INGOT.id, 4, RecycleType.SMELT);
        
        // Diamond armor (furnace)
        register(Item.DIAMOND_HELMET, Item.DIAMOND.id, 5, RecycleType.SMELT);
        register(Item.DIAMOND_CHESTPLATE, Item.DIAMOND.id, 8, RecycleType.SMELT);
        register(Item.DIAMOND_LEGGINGS, Item.DIAMOND.id, 7, RecycleType.SMELT);
        register(Item.DIAMOND_BOOTS, Item.DIAMOND.id, 4, RecycleType.SMELT);
        
        // Gold armor (furnace)
        register(Item.GOLD_HELMET, Item.GOLD_INGOT.id, 5, RecycleType.SMELT);
        register(Item.GOLD_CHESTPLATE, Item.GOLD_INGOT.id, 8, RecycleType.SMELT);
        register(Item.GOLD_LEGGINGS, Item.GOLD_INGOT.id, 7, RecycleType.SMELT);
        register(Item.GOLD_BOOTS, Item.GOLD_INGOT.id, 4, RecycleType.SMELT);
    }
    
    private void registerMiscItems() {
        // Shears - 2 iron (furnace)
        register(Item.SHEARS, Item.IRON_INGOT.id, 2, RecycleType.SMELT);
        
        // Flint and Steel - 1 iron (furnace, flint not returned)
        register(Item.FLINT_AND_STEEL, Item.IRON_INGOT.id, 1, RecycleType.SMELT);
        
        // Fishing Rod - 2 string (crafting table, sticks not returned)
        register(Item.FISHING_ROD, Item.STRING.id, 2, RecycleType.CRAFT);
    }
    
    private void register(Item item, int materialId, int baseQuantity, RecycleType type) {
        recycleMap.put(item.id, new RecycleInfo(materialId, baseQuantity, type));
    }
    
    /**
     * Checks if the given crafting inventory contains exactly one damageable item
     * that can be recycled via crafting and returns the recycled materials.
     * Only returns results for CRAFT type items (leather, wood, string).
     * 
     * @param inventory The crafting inventory to check
     * @return The recycled ItemStack, or null if not a valid craft recycle operation
     */
    public ItemStack getRecycleResult(InventoryCrafting inventory) {
        ItemStack singleItem = null;
        int itemCount = 0;
        
        // Check for exactly one item in the crafting grid
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null) {
                itemCount++;
                if (itemCount > 1) {
                    return null; // More than one item, not a recycle operation
                }
                singleItem = stack;
            }
        }
        
        if (singleItem == null || itemCount != 1) {
            return null;
        }
        
        // Check if this item is recyclable via crafting
        RecycleInfo info = recycleMap.get(singleItem.id);
        if (info == null || info.type != RecycleType.CRAFT) {
            return null; // Item is not craft-recyclable
        }
        
        // Check if item is damageable (has durability)
        // d() returns true if item has durability
        if (!singleItem.d()) {
            return null;
        }
        
        // Calculate output based on remaining durability
        // i() returns max damage, g() returns current damage
        int maxDamage = singleItem.i();
        int currentDamage = singleItem.g();
        int remainingDurability = maxDamage - currentDamage;
        
        // Calculate output count: floor(baseQuantity * remainingDurability / maxDurability)
        float durabilityPercent = (float) remainingDurability / (float) maxDamage;
        int outputCount = (int) Math.floor(info.baseQuantity * durabilityPercent);
        
        // Only return if at least 1 material would be returned
        if (outputCount < 1) {
            return null;
        }
        
        return new ItemStack(info.materialId, outputCount, 0);
    }
    
    /**
     * Gets the recycled materials for an item smelted in a furnace.
     * Only returns results for SMELT type items (iron, gold, diamond, stone).
     * 
     * @param itemStack The item being smelted
     * @return The recycled ItemStack, or null if not smelt-recyclable
     */
    public ItemStack getSmeltRecycleResult(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        
        // Check if this item is recyclable via smelting
        RecycleInfo info = recycleMap.get(itemStack.id);
        if (info == null || info.type != RecycleType.SMELT) {
            return null; // Item is not smelt-recyclable
        }
        
        // Check if item is damageable (has durability)
        // d() returns true if item has durability
        if (!itemStack.d()) {
            return null;
        }
        
        // Calculate output based on remaining durability
        // i() returns max damage, g() returns current damage
        int maxDamage = itemStack.i();
        int currentDamage = itemStack.g();
        int remainingDurability = maxDamage - currentDamage;
        
        // Calculate output count: floor(baseQuantity * remainingDurability / maxDurability)
        float durabilityPercent = (float) remainingDurability / (float) maxDamage;
        int outputCount = (int) Math.floor(info.baseQuantity * durabilityPercent);
        
        // Only return if at least 1 material would be returned
        if (outputCount < 1) {
            return null;
        }
        
        return new ItemStack(info.materialId, outputCount, 0);
    }
    
    /**
     * Checks if an item can be recycled via crafting table.
     * 
     * @param itemId The item ID to check
     * @return true if the item can be craft-recycled
     */
    public boolean canCraftRecycle(int itemId) {
        RecycleInfo info = recycleMap.get(itemId);
        return info != null && info.type == RecycleType.CRAFT;
    }
    
    /**
     * Checks if an item can be recycled via furnace smelting.
     * 
     * @param itemId The item ID to check
     * @return true if the item can be smelt-recycled
     */
    public boolean canSmeltRecycle(int itemId) {
        RecycleInfo info = recycleMap.get(itemId);
        return info != null && info.type == RecycleType.SMELT;
    }
    
    /**
     * Checks if an item can be recycled (either via crafting or smelting).
     * 
     * @param itemId The item ID to check
     * @return true if the item can be recycled
     */
    public boolean canRecycle(int itemId) {
        return recycleMap.containsKey(itemId);
    }
    
    /**
     * Stores information about how to recycle an item.
     */
    private static class RecycleInfo {
        final int materialId;
        final int baseQuantity;
        final RecycleType type;
        
        RecycleInfo(int materialId, int baseQuantity, RecycleType type) {
            this.materialId = materialId;
            this.baseQuantity = baseQuantity;
            this.type = type;
        }
    }
}
