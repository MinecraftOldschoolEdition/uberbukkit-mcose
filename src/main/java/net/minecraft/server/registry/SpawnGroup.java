package net.minecraft.server.registry;

/**
 * Represents a spawn group/category for entity spawning.
 * Modeled after Fabric API's SpawnGroup enum.
 */
public class SpawnGroup {
    private final String name;
    private final int spawnCap;
    private final boolean peaceful;
    
    public SpawnGroup(String name, int spawnCap, boolean peaceful) {
        this.name = name;
        this.spawnCap = spawnCap;
        this.peaceful = peaceful;
    }
    
    public String getName() {
        return this.name;
    }
    
    public int getSpawnCap() {
        return this.spawnCap;
    }
    
    public boolean isPeaceful() {
        return this.peaceful;
    }
    
    @Override
    public String toString() {
        return "SpawnGroup{" + name + ", cap=" + spawnCap + ", peaceful=" + peaceful + "}";
    }
    
    // Built-in spawn groups for Beta 1.7.3
    public static final SpawnGroup MONSTER = new SpawnGroup("monster", 70, false);
    public static final SpawnGroup CREATURE = new SpawnGroup("creature", 10, true);
    public static final SpawnGroup WATER_CREATURE = new SpawnGroup("water_creature", 5, true);
    public static final SpawnGroup AMBIENT = new SpawnGroup("ambient", 15, true);
}

