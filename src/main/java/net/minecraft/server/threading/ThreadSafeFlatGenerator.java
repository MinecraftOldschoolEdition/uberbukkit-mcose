package net.minecraft.server.threading;

import net.minecraft.server.Block;

/**
 * Thread-safe flat world generator.
 * Simple and guaranteed safe - no world access.
 */
public class ThreadSafeFlatGenerator implements IThreadSafeGenerator {
    
    @Override
    public String getGeneratorName() {
        return "ThreadSafeFlat";
    }
    
    @Override
    public ChunkGenerationData generateChunkData(long seed, int chunkX, int chunkZ) {
        byte[] blocks = new byte[32768];
        byte[] heightMap = new byte[256];
        
        // Generate flat terrain: bedrock, 2 dirt, 1 grass
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int baseIdx = (z * 16 + x) * 128;
                
                // Layer 0: Bedrock
                blocks[baseIdx] = (byte) Block.BEDROCK.id;
                
                // Layers 1-2: Dirt
                blocks[baseIdx + 1] = (byte) Block.DIRT.id;
                blocks[baseIdx + 2] = (byte) Block.DIRT.id;
                
                // Layer 3: Grass
                blocks[baseIdx + 3] = (byte) Block.GRASS.id;
                
                // Heightmap
                heightMap[x + z * 16] = 4;
            }
        }
        
        return new ChunkGenerationData(
            chunkX, chunkZ,
            blocks,
            new byte[16384], // metadata
            new byte[16384], // skyLight
            new byte[16384], // blockLight
            heightMap,
            new byte[256],   // biomes
            false
        );
    }
}

