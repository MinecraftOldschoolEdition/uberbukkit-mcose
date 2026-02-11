package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

/**
 * Bootstrap for the Spawn Group registry.
 * Registers the built-in spawn groups for Beta 1.7.3.
 */
public final class SpawnGroupRegistryBootstrap {
    private static boolean initialized = false;
    
    private SpawnGroupRegistryBootstrap() {}
    
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        
        // Register built-in spawn groups
        SpawnGroupRegistryApi.register(
            new ResourceLocation("minecraft", "monster"),
            SpawnGroup.MONSTER
        );
        SpawnGroupRegistryApi.register(
            new ResourceLocation("minecraft", "creature"),
            SpawnGroup.CREATURE
        );
        SpawnGroupRegistryApi.register(
            new ResourceLocation("minecraft", "water_creature"),
            SpawnGroup.WATER_CREATURE
        );
        SpawnGroupRegistryApi.register(
            new ResourceLocation("minecraft", "ambient"),
            SpawnGroup.AMBIENT
        );
        
        System.out.println("[SpawnGroupRegistryBootstrap] Registered " + SpawnGroupRegistryApi.size() + " spawn groups");
    }
}
