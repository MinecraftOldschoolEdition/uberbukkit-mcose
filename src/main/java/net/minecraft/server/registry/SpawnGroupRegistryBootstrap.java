package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

/**
 * Bootstrap for the Spawn Group registry.
 * Registers the built-in spawn groups for Beta 1.7.3.
 */
public final class SpawnGroupRegistryBootstrap {
    private static boolean initialized = false;
    
    private SpawnGroupRegistryBootstrap() {}
    
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        
        // Register built-in spawn groups
        Registries.SPAWN_GROUP.register(
            new ResourceLocation("minecraft", "monster"),
            SpawnGroup.MONSTER
        );
        Registries.SPAWN_GROUP.register(
            new ResourceLocation("minecraft", "creature"),
            SpawnGroup.CREATURE
        );
        Registries.SPAWN_GROUP.register(
            new ResourceLocation("minecraft", "water_creature"),
            SpawnGroup.WATER_CREATURE
        );
        Registries.SPAWN_GROUP.register(
            new ResourceLocation("minecraft", "ambient"),
            SpawnGroup.AMBIENT
        );
        
        System.out.println("[SpawnGroupRegistryBootstrap] Registered " + Registries.SPAWN_GROUP.keys().size() + " spawn groups");
    }
}

