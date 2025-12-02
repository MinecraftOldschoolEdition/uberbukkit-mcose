package net.minecraft.server.threading;

/**
 * Immutable data container for generated chunk terrain.
 * Contains only raw data - no world references.
 * Safe to create on any thread, apply only on main thread.
 */
public class ChunkGenerationData {
    
    public final int chunkX;
    public final int chunkZ;
    
    // Raw block data - 32768 bytes (16x128x16)
    public final byte[] blocks;
    
    // Block metadata - 16384 bytes (nibble array, 16x128x16 / 2)
    public final byte[] metadata;
    
    // Skylighting data - 16384 bytes (nibble array)
    public final byte[] skyLight;
    
    // Block lighting data - 16384 bytes (nibble array)  
    public final byte[] blockLight;
    
    // Heightmap - 256 bytes (16x16)
    public final byte[] heightMap;
    
    // Biome data - 256 bytes (16x16) - biome IDs
    public final byte[] biomes;
    
    // Whether this chunk has been populated (ores, trees, etc.)
    public final boolean terrainPopulated;
    
    // Generation timestamp for debugging
    public final long generatedAt;
    
    public ChunkGenerationData(int chunkX, int chunkZ, byte[] blocks, byte[] metadata,
                                byte[] skyLight, byte[] blockLight, byte[] heightMap,
                                byte[] biomes, boolean terrainPopulated) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blocks = blocks;
        this.metadata = metadata;
        this.skyLight = skyLight;
        this.blockLight = blockLight;
        this.heightMap = heightMap;
        this.biomes = biomes;
        this.terrainPopulated = terrainPopulated;
        this.generatedAt = System.currentTimeMillis();
    }
    
    /**
     * Create empty chunk data (air).
     */
    public static ChunkGenerationData createEmpty(int chunkX, int chunkZ) {
        return new ChunkGenerationData(
            chunkX, chunkZ,
            new byte[32768],    // blocks - all air
            new byte[16384],    // metadata
            new byte[16384],    // skyLight
            new byte[16384],    // blockLight
            new byte[256],      // heightMap
            new byte[256],      // biomes
            false
        );
    }
}

