package net.minecraft.server;

import java.util.List;
import java.util.Random;

// Basic flat world generator - Server-side adaptation (ported from Poseidon)
public class ChunkProviderFlat implements IChunkProvider {
    private World worldObj;
    private Random random;
    private final byte[] blockArrayTemplate = new byte[32768];
    private static final boolean DEBUG_LOGGING = false;

    public ChunkProviderFlat(World world, long seed, boolean mapFeaturesEnabled) {
        this.worldObj = world;
        this.random = new Random(seed);

        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                int index = (x * 16 + z) * 128 + 0;
                this.blockArrayTemplate[index] = (byte) Block.BEDROCK.id;
            }
        }
        for (int y = 1; y <= 2; ++y) {
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    int index = (x * 16 + z) * 128 + y;
                    this.blockArrayTemplate[index] = (byte) Block.DIRT.id;
                }
            }
        }
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                int index = (x * 16 + z) * 128 + 3;
                this.blockArrayTemplate[index] = (byte) Block.GRASS.id;
            }
        }
    }

    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        // This is a generation capability query. The outer ChunkProviderServer
        // owns loaded identity and retention for every generated coordinate.
        return true;
    }

    public Chunk getOrCreateChunk(int chunkX, int chunkZ) {
        boolean doLog = DEBUG_LOGGING && ((chunkX & 31) == 0) && ((chunkZ & 31) == 0);
        if (doLog) System.out.println("[FlatGenSrv] Creating chunk at " + chunkX + ", " + chunkZ);

        byte[] newBlockArray = new byte[32768];
        System.arraycopy(this.blockArrayTemplate, 0, newBlockArray, 0, this.blockArrayTemplate.length);
        Chunk chunk = new Chunk(this.worldObj, newBlockArray, chunkX, chunkZ);

        chunk.o = false; // not modified
        chunk.initLighting();
        chunk.done = true; // terrain populated

        if (doLog) System.out.println("[FlatGenSrv] Finished chunk at " + chunkX + ", " + chunkZ);
        return chunk;
    }

    public Chunk getChunkAt(int chunkX, int chunkZ) {
        return getOrCreateChunk(chunkX, chunkZ);
    }

    public void getChunkAt(IChunkProvider ichunkprovider, int chunkX, int chunkZ) {
        // No features to populate
    }

    public boolean saveChunks(boolean flag, IProgressUpdate iprogressupdate) {
        return true;
    }

    public boolean unloadChunks() {
        return false;
    }

    public boolean canSave() {
        return true;
    }

    public String getName() {
        return "FlatLevelSource";
    }
}

