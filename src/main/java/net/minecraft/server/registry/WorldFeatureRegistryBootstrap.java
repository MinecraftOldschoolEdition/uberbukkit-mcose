package net.minecraft.server.registry;

import net.minecraft.server.*;
import net.minecraft.server.util.ResourceLocation;

/**
 * Bootstrap for the World Feature registry.
 * Registers the built-in world generation features for Beta 1.7.3.
 */
public final class WorldFeatureRegistryBootstrap {
    private static boolean initialized = false;
    
    private WorldFeatureRegistryBootstrap() {}
    
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        
        // Trees
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "tree"),
            new WorldGenTrees()
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "big_tree"),
            new WorldGenBigTree()
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "forest_tree"),
            new WorldGenForest()
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "taiga_tree_1"),
            new WorldGenTaiga1()
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "taiga_tree_2"),
            new WorldGenTaiga2()
        );
        // Note: WorldGenHugeMushroom doesn't exist in Beta 1.7.3
        
        // Ores (registered as templates)
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "ore_coal"),
            new WorldGenMinable("minecraft:coal_ore", 16)
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "ore_iron"),
            new WorldGenMinable("minecraft:iron_ore", 8)
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "ore_gold"),
            new WorldGenMinable("minecraft:gold_ore", 8)
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "ore_redstone"),
            new WorldGenMinable("minecraft:redstone_ore", 7)
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "ore_diamond"),
            new WorldGenMinable("minecraft:diamond_ore", 7)
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "ore_lapis"),
            new WorldGenMinable("minecraft:lapis_ore", 6)
        );
        
        // Vegetation
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "flowers"),
            new WorldGenFlowers("minecraft:dandelion")
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "tall_grass"),
            new WorldGenGrass(Block.LONG_GRASS.id, 1)
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "dead_bush"),
            new WorldGenDeadBush("minecraft:dead_bush")
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "reed"),
            new WorldGenReed()
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "cactus"),
            new WorldGenCactus()
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "pumpkin"),
            new WorldGenPumpkin()
        );
        
        // Terrain features
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "clay"),
            new WorldGenClay(32)
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "water_lake"),
            new WorldGenLakes("minecraft:water")
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "lava_lake"),
            new WorldGenLakes("minecraft:lava")
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "spring_water"),
            new WorldGenLiquids("minecraft:water")
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "spring_lava"),
            new WorldGenLiquids("minecraft:lava")
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "dungeon"),
            new WorldGenDungeons()
        );
        
        // Nether features
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "hell_lava"),
            new WorldGenHellLava("minecraft:lava")
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "glowstone"),
            new WorldGenLightStone1()
        );
        WorldFeatureRegistryApi.register(
            new ResourceLocation("minecraft", "fire"),
            new WorldGenFire()
        );
        
        System.out.println("[WorldFeatureRegistryBootstrap] Registered " + WorldFeatureRegistryApi.size() + " world features");
    }
}
