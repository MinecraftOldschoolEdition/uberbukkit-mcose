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
    
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        
        // Trees
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "tree"),
            new WorldGenTrees()
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "big_tree"),
            new WorldGenBigTree()
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "forest_tree"),
            new WorldGenForest()
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "taiga_tree_1"),
            new WorldGenTaiga1()
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "taiga_tree_2"),
            new WorldGenTaiga2()
        );
        // Note: WorldGenHugeMushroom doesn't exist in Beta 1.7.3
        
        // Ores (registered as templates)
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "ore_coal"),
            new WorldGenMinable(Block.COAL_ORE.id, 16)
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "ore_iron"),
            new WorldGenMinable(Block.IRON_ORE.id, 8)
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "ore_gold"),
            new WorldGenMinable(Block.GOLD_ORE.id, 8)
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "ore_redstone"),
            new WorldGenMinable(Block.REDSTONE_ORE.id, 7)
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "ore_diamond"),
            new WorldGenMinable(Block.DIAMOND_ORE.id, 7)
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "ore_lapis"),
            new WorldGenMinable(Block.LAPIS_ORE.id, 6)
        );
        
        // Vegetation
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "flowers"),
            new WorldGenFlowers(Block.YELLOW_FLOWER.id)
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "tall_grass"),
            new WorldGenGrass(Block.LONG_GRASS.id, 1)
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "dead_bush"),
            new WorldGenDeadBush(Block.DEAD_BUSH.id)
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "reed"),
            new WorldGenReed()
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "cactus"),
            new WorldGenCactus()
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "pumpkin"),
            new WorldGenPumpkin()
        );
        
        // Terrain features
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "clay"),
            new WorldGenClay(32)
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "water_lake"),
            new WorldGenLakes(Block.STATIONARY_WATER.id)
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "lava_lake"),
            new WorldGenLakes(Block.STATIONARY_LAVA.id)
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "spring_water"),
            new WorldGenLiquids(Block.WATER.id)
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "spring_lava"),
            new WorldGenLiquids(Block.LAVA.id)
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "dungeon"),
            new WorldGenDungeons()
        );
        
        // Nether features
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "hell_lava"),
            new WorldGenHellLava(Block.LAVA.id)
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "glowstone"),
            new WorldGenLightStone1()
        );
        Registries.WORLD_FEATURE.register(
            new ResourceLocation("minecraft", "fire"),
            new WorldGenFire()
        );
        
        System.out.println("[WorldFeatureRegistryBootstrap] Registered " + Registries.WORLD_FEATURE.keys().size() + " world features");
    }
}

