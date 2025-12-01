package net.minecraft.server;

import net.minecraft.server.registry.Registries;
import net.minecraft.server.registry.StructureType;
import net.minecraft.server.registry.StructureTypes;
import net.minecraft.server.util.ResourceLocation;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Generates the Herobrine Shrine structure.
 * 
 * Structure:
 * - 3x3 gold block platform base
 * - Redstone torches on N/S/E/W edges
 * - Netherrack block on top of center
 * 
 * Spawns only in deserts with 1/750000 chance.
 */
public class WorldGenHerobrineShrine extends WorldGenerator {
    
    // Track activated shrines to prevent re-triggering
    private static Set<String> activatedShrines = new HashSet<String>();
    
    // Structure type cached from registry
    private static StructureType shrineType = null;
    
    private static void ensureInitialized() {
        if (shrineType == null) {
            StructureTypes.initialize();
            shrineType = StructureTypes.get(StructureTypes.HEROBRINE_SHRINE);
        }
    }
    
    /**
     * Generates the shrine at the specified position.
     */
    public boolean a(World world, Random random, int x, int y, int z) {
        ensureInitialized();
        
        // Check all corners of the 3x3 platform for valid land placement
        if (!isValidLandLocation(world, x, z)) {
            return false;
        }
        
        // Find the actual surface level
        int surfaceY = findSurfaceY(world, x, z);
        if (surfaceY < 4 || surfaceY > 250) {
            return false;
        }
        
        // Double-check we're not underwater
        int blockAbove = world.getTypeId(x, surfaceY + 1, z);
        if (blockAbove == Block.WATER.id || blockAbove == Block.STATIONARY_WATER.id) {
            return false;
        }
        
        // The platform goes ON TOP of the surface
        int platformY = surfaceY + 1;
        
        System.out.println("[HerobrineShrine] Generating at " + x + ", " + platformY + ", " + z);
        
        int goldId = Block.GOLD_BLOCK.id;
        int torchId = Block.REDSTONE_TORCH_ON.id;
        int netherrackId = Block.NETHERRACK.id;
        
        // Clear any obstacles and build the 3x3 gold platform
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                // Clear blocks above the platform area
                for (int clearY = platformY; clearY <= platformY + 3; clearY++) {
                    int blockId = world.getTypeId(x + dx, clearY, z + dz);
                    if (blockId != 0) {
                        world.setTypeId(x + dx, clearY, z + dz, 0);
                    }
                }
                // Place gold platform
                world.setTypeId(x + dx, platformY, z + dz, goldId);
            }
        }
        
        // Place redstone torches on the four edges (N/S/E/W)
        world.setTypeIdAndData(x, platformY + 1, z - 1, torchId, 0); // North
        world.setTypeIdAndData(x, platformY + 1, z + 1, torchId, 0); // South
        world.setTypeIdAndData(x + 1, platformY + 1, z, torchId, 0); // East
        world.setTypeIdAndData(x - 1, platformY + 1, z, torchId, 0); // West
        
        // Place netherrack on top of center gold block
        world.setTypeId(x, platformY + 1, z, netherrackId);
        
        return true;
    }
    
    /**
     * Checks if the location is valid land for shrine placement.
     */
    private static boolean isValidLandLocation(World world, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int surfaceY = findSurfaceY(world, x + dx, z + dz);
                if (surfaceY < 4) {
                    return false;
                }
                
                int surfaceBlockId = world.getTypeId(x + dx, surfaceY, z + dz);
                Block surfaceBlock = Block.byId[surfaceBlockId];
                if (surfaceBlock == null) {
                    return false;
                }
                
                Material mat = surfaceBlock.material;
                if (mat == Material.WATER || mat == Material.LAVA) {
                    return false;
                }
                
                int aboveBlockId = world.getTypeId(x + dx, surfaceY + 1, z + dz);
                if (aboveBlockId == Block.WATER.id || aboveBlockId == Block.STATIONARY_WATER.id ||
                    aboveBlockId == Block.LAVA.id || aboveBlockId == Block.STATIONARY_LAVA.id) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * Finds the surface Y coordinate at the given X,Z position.
     */
    private static int findSurfaceY(World world, int x, int z) {
        for (int y = 128; y > 1; y--) {
            int blockId = world.getTypeId(x, y, z);
            if (blockId == 0) continue;
            
            Block block = Block.byId[blockId];
            if (block == null) continue;
            
            Material mat = block.material;
            
            // Skip non-solid blocks
            if (mat == Material.PLANT || mat == Material.CACTUS || mat == Material.SNOW_LAYER || mat == Material.FIRE) {
                continue;
            }
            
            // Skip water and lava
            if (mat == Material.WATER || mat == Material.LAVA) {
                continue;
            }
            
            if (mat.isBuildable()) {
                return y;
            }
        }
        return -1;
    }
    
    /**
     * Gets the unique key for a shrine location.
     */
    public static String getShrineKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
    
    /**
     * Checks if a shrine at the given location has been activated.
     */
    public static boolean isShrineActivated(int x, int y, int z) {
        return activatedShrines.contains(getShrineKey(x, y, z));
    }
    
    /**
     * Marks a shrine at the given location as activated.
     */
    public static void activateShrine(int x, int y, int z) {
        activatedShrines.add(getShrineKey(x, y, z));
    }
    
    /**
     * Checks if a position is a valid Herobrine shrine.
     */
    public static boolean isHerobrineShrine(World world, int x, int y, int z) {
        // Must be netherrack
        if (world.getTypeId(x, y, z) != Block.NETHERRACK.id) {
            return false;
        }
        
        // Check gold block below
        if (world.getTypeId(x, y - 1, z) != Block.GOLD_BLOCK.id) {
            return false;
        }
        
        // Check 3x3 gold platform
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (world.getTypeId(x + dx, y - 1, z + dz) != Block.GOLD_BLOCK.id) {
                    return false;
                }
            }
        }
        
        // Check redstone torches at cardinal directions
        int torchOnId = Block.REDSTONE_TORCH_ON.id;
        int torchOffId = Block.REDSTONE_TORCH_OFF.id;
        
        boolean northTorch = world.getTypeId(x, y, z - 1) == torchOnId || world.getTypeId(x, y, z - 1) == torchOffId;
        boolean southTorch = world.getTypeId(x, y, z + 1) == torchOnId || world.getTypeId(x, y, z + 1) == torchOffId;
        boolean eastTorch = world.getTypeId(x + 1, y, z) == torchOnId || world.getTypeId(x + 1, y, z) == torchOffId;
        boolean westTorch = world.getTypeId(x - 1, y, z) == torchOnId || world.getTypeId(x - 1, y, z) == torchOffId;
        
        return northTorch && southTorch && eastTorch && westTorch;
    }
    
    /**
     * Clears all activated shrines.
     */
    public static void clearActivatedShrines() {
        activatedShrines.clear();
    }
    
    /**
     * Loads activated shrines from NBT data.
     */
    public static void loadFromNBT(NBTTagCompound nbt) {
        activatedShrines.clear();
        if (nbt.hasKey("HerobrineShrines")) {
            NBTTagList list = nbt.l("HerobrineShrines");
            for (int i = 0; i < list.c(); i++) {
                NBTTagString tag = (NBTTagString) list.a(i);
                activatedShrines.add(tag.a);
            }
        }
    }
    
    /**
     * Saves activated shrines to NBT data.
     */
    public static NBTTagList saveToNBT() {
        NBTTagList list = new NBTTagList();
        for (String key : activatedShrines) {
            list.a(new NBTTagString(key));
        }
        return list;
    }
    
    /**
     * Finds the nearest potential Herobrine Shrine location based on world seed.
     */
    public static int[] findNearestShrine(World world, int playerX, int playerZ, int maxChunkRadius) {
        long worldSeed = world.getSeed();
        WorldChunkManager wcm = world.getWorldChunkManager();
        
        int playerChunkX = playerX >> 4;
        int playerChunkZ = playerZ >> 4;
        
        int[] closest = null;
        double closestDist = Double.MAX_VALUE;
        
        // Search in expanding rings from player position
        for (int radius = 0; radius <= maxChunkRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    
                    int chunkX = playerChunkX + dx;
                    int chunkZ = playerChunkZ + dz;
                    
                    int[] shrinePos = checkChunkForShrine(wcm, chunkX, chunkZ, worldSeed);
                    if (shrinePos != null) {
                        double dist = Math.sqrt(
                            (shrinePos[0] - playerX) * (shrinePos[0] - playerX) +
                            (shrinePos[2] - playerZ) * (shrinePos[2] - playerZ)
                        );
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = shrinePos;
                        }
                    }
                }
            }
            
            if (closest != null) {
                return closest;
            }
        }
        
        return closest;
    }
    
    /**
     * Checks if a specific chunk would contain a Herobrine Shrine based on seed.
     */
    private static int[] checkChunkForShrine(WorldChunkManager wcm, int chunkX, int chunkZ, long worldSeed) {
        int blockX = chunkX * 16;
        int blockZ = chunkZ * 16;
        
        // Check biome - shrine only spawns in desert
        BiomeBase biome = wcm.getBiome(blockX + 16, blockZ + 16);
        if (biome != BiomeBase.DESERT) {
            return null;
        }
        
        // Use deterministic random for shrine spawning
        long shrineSeed = (long)chunkX * 341873128712L + (long)chunkZ * 132897987541L + worldSeed + 777777777L;
        Random shrineRand = new Random(shrineSeed);
        
        // Check if shrine spawns (1/750000 chance)
        if (shrineRand.nextInt(750000) == 0) {
            int shrineX = blockX + shrineRand.nextInt(16) + 8;
            int shrineZ = blockZ + shrineRand.nextInt(16) + 8;
            int shrineY = 72; // Estimated desert surface height
            
            return new int[] { shrineX, shrineY, shrineZ };
        }
        
        return null;
    }
}

