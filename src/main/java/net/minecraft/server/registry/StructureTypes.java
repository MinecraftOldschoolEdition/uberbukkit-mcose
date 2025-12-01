package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

/**
 * Bootstrap class that registers all vanilla structure types.
 * Structure types are registered to Registries.STRUCTURE_TYPE.
 */
public final class StructureTypes {
    // Dungeon structure type (includes both cobblestone and stone brick variants)
    public static final ResourceLocation DUNGEON = new ResourceLocation("minecraft", "dungeon");
    
    // Special structures
    public static final ResourceLocation HEROBRINE_SHRINE = new ResourceLocation("minecraft", "herobrine_shrine");
    
    private static boolean initialized = false;
    
    private StructureTypes() {}
    
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        
        // Make sure loot tables are registered first
        LootTables.initialize();
        
        registerDungeon();
        registerHerobrineShrine();
        
        System.out.println("[StructureTypes] Registered " + Registries.STRUCTURE_TYPE.keys().size() + " structure types");
    }
    
    private static void registerDungeon() {
        // Dungeon structure (cobblestone or stone brick variant, 25% monster dungeon chance)
        StructureType type = StructureType.builder(DUNGEON)
            .displayName("Dungeon")
            .lootTable(LootTables.DUNGEON)
            .spawnerMobs(
                new String[] { "Skeleton", "Zombie", "Zombie", "Spider" },
                new int[] { 1, 2, 0, 1 } // Skeleton: 25%, Zombie: 50%, Spider: 25%
            )
            .build();
        
        Registries.STRUCTURE_TYPE.register(DUNGEON, type);
    }
    
    private static void registerHerobrineShrine() {
        // Rare desert structure that triggers the Herobrine event when lit
        StructureType type = StructureType.builder(HEROBRINE_SHRINE)
            .displayName("Herobrine Shrine")
            .build();
        
        Registries.STRUCTURE_TYPE.register(HEROBRINE_SHRINE, type);
    }
    
    /**
     * Gets a structure type by its resource location.
     */
    public static StructureType get(ResourceLocation id) {
        return Registries.STRUCTURE_TYPE.get(id);
    }
}

