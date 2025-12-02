package net.minecraft.server.threading;

/**
 * Interface for chunk generators that can produce terrain data
 * without accessing shared world state.
 * 
 * Implementations must be completely thread-safe and only use:
 * - The provided seed
 * - The chunk coordinates
 * - Their own internal (thread-local or immutable) state
 * 
 * They must NOT access:
 * - World object
 * - Other chunks
 * - Entity lists
 * - Shared Random instances
 */
public interface IThreadSafeGenerator {
    
    /**
     * Generate raw terrain data for a chunk.
     * This method must be completely thread-safe.
     * 
     * @param seed World seed
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return Generated chunk data (blocks, heightmap, etc.)
     */
    ChunkGenerationData generateChunkData(long seed, int chunkX, int chunkZ);
    
    /**
     * Get the name of this generator for logging.
     */
    String getGeneratorName();
}

