package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

/**
 * Bootstrap class that registers all vanilla loot tables.
 * Loot tables are registered to Registries.LOOT_TABLE.
 */
public final class LootTables {
    // Dungeon loot tables (separate loot for simple vs monster dungeon variants)
    public static final ResourceLocation DUNGEON = new ResourceLocation("minecraft", "chests/dungeon");
    public static final ResourceLocation SIMPLE_DUNGEON_LOOT = new ResourceLocation("minecraft", "chests/simple_dungeon");
    public static final ResourceLocation MONSTER_DUNGEON_LOOT = new ResourceLocation("minecraft", "chests/monster_dungeon");
    
    private static boolean initialized = false;
    
    private LootTables() {}
    
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        
        registerDungeon();
        registerSimpleDungeonLoot();
        registerMonsterDungeonLoot();
        
        System.out.println("[LootTables] Registered " + Registries.LOOT_TABLE.keys().size() + " loot tables");
    }
    
    private static void registerDungeon() {
        // Default dungeon loot table (same as simple dungeon)
        LootTable table = LootTable.builder(DUNGEON)
            .pool(LootPool.builder("main")
                .rolls(1, 3)
                // Common items
                .add(LootEntry.builder("saddle").weight(10))
                .add(LootEntry.builder("iron_ingot").weight(10).count(1, 4))
                .add(LootEntry.builder("bread").weight(10))
                .add(LootEntry.builder("wheat").weight(10).count(1, 4))
                .add(LootEntry.builder("gunpowder").weight(10).count(1, 4))
                .add(LootEntry.builder("string").weight(10).count(1, 4))
                .add(LootEntry.builder("bucket").weight(10))
                .add(LootEntry.builder("redstone").weight(5).count(1, 4))
                .add(LootEntry.builder("cocoa_beans").weight(10).metadata(3))
                .build())
            .pool(LootPool.builder("rare")
                .rolls(0, 1)
                .add(LootEntry.builder("golden_apple").weight(1))
                .add(LootEntry.builder("music_disc_13").weight(2))
                .add(LootEntry.builder("music_disc_cat").weight(2))
                .build())
            .build();
        
        Registries.LOOT_TABLE.register(DUNGEON, table);
    }
    
    private static void registerSimpleDungeonLoot() {
        // Classic dungeon loot - saddles, iron, bread, wheat, gunpowder, string, buckets, redstone, cocoa beans
        LootTable table = LootTable.builder(SIMPLE_DUNGEON_LOOT)
            .pool(LootPool.builder("main")
                .rolls(1, 3)
                .add(LootEntry.builder("saddle").weight(10))
                .add(LootEntry.builder("iron_ingot").weight(10).count(1, 4))
                .add(LootEntry.builder("bread").weight(10))
                .add(LootEntry.builder("wheat").weight(10).count(1, 4))
                .add(LootEntry.builder("gunpowder").weight(10).count(1, 4))
                .add(LootEntry.builder("string").weight(10).count(1, 4))
                .add(LootEntry.builder("bucket").weight(10))
                .add(LootEntry.builder("redstone").weight(5).count(1, 4))
                .add(LootEntry.builder("cocoa_beans").weight(10).metadata(3))
                .build())
            .pool(LootPool.builder("rare")
                .rolls(0, 1)
                .add(LootEntry.builder("golden_apple").weight(1))
                .add(LootEntry.builder("music_disc_13").weight(2))
                .add(LootEntry.builder("music_disc_cat").weight(2))
                .build())
            .build();
        
        Registries.LOOT_TABLE.register(SIMPLE_DUNGEON_LOOT, table);
    }
    
    private static void registerMonsterDungeonLoot() {
        // Stone brick dungeon variant - slightly better loot
        LootTable table = LootTable.builder(MONSTER_DUNGEON_LOOT)
            .pool(LootPool.builder("main")
                .rolls(2, 4) // More rolls than simple dungeon
                .add(LootEntry.builder("saddle").weight(8))
                .add(LootEntry.builder("iron_ingot").weight(10).count(1, 4))
                .add(LootEntry.builder("bread").weight(8))
                .add(LootEntry.builder("wheat").weight(8).count(1, 4))
                .add(LootEntry.builder("gunpowder").weight(10).count(1, 4))
                .add(LootEntry.builder("string").weight(10).count(1, 4))
                .add(LootEntry.builder("bucket").weight(8))
                .add(LootEntry.builder("redstone").weight(6).count(1, 4))
                .add(LootEntry.builder("cocoa_beans").weight(8).metadata(3))
                // Additional items for monster dungeon
                .add(LootEntry.builder("gold_ingot").weight(5).count(1, 3))
                .add(LootEntry.builder("iron_sword").weight(3))
                .add(LootEntry.builder("iron_chestplate").weight(2))
                .build())
            .pool(LootPool.builder("rare")
                .rolls(0, 2) // Slightly better rare chance
                .add(LootEntry.builder("golden_apple").weight(2))
                .add(LootEntry.builder("diamond").weight(1).count(1, 2))
                .add(LootEntry.builder("music_disc_13").weight(3))
                .add(LootEntry.builder("music_disc_cat").weight(3))
                .build())
            .build();
        
        Registries.LOOT_TABLE.register(MONSTER_DUNGEON_LOOT, table);
    }
    
    /**
     * Gets a loot table by its resource location.
     */
    public static LootTable get(ResourceLocation id) {
        return Registries.LOOT_TABLE.get(id);
    }
}

