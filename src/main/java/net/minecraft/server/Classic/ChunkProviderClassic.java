package net.minecraft.server.Classic;

import net.minecraft.server.Block;
import net.minecraft.server.Chunk;
import net.minecraft.server.IChunkProvider;
import net.minecraft.server.IProgressUpdate;
import net.minecraft.server.World;

/**
 * Chunk adapter for the complete 256x64x256 c0.30 Survival level generator.
 * Classic generated the finite level in one pass; chunks only expose slices of
 * that finished level so caves, fluids, ores, plants, and trees keep their
 * original cross-chunk behavior.
 */
public class ChunkProviderClassic implements IChunkProvider {
	private static final int SEA_LEVEL = ClassicLevelGenerator.WATER_LEVEL;
	private final World worldObj;
	private final ClassicLevelGenerator levelGenerator;

	public ChunkProviderClassic(World world, long seed) {
		this.worldObj = world;
		this.levelGenerator = new ClassicLevelGenerator(seed);
		this.levelGenerator.generate();
	}

	public Chunk getOrCreateChunk(int chunkX, int chunkZ) {
		if(chunkX < 0 || chunkZ < 0 || chunkX > 15 || chunkZ > 15) {
			return this.makeExteriorChunk(chunkX, chunkZ);
		}

		byte[] blocks = new byte[16 * 16 * 128];
		this.levelGenerator.copyChunk(chunkX, chunkZ, blocks);
		Chunk chunk = new Chunk(this.worldObj, blocks, chunkX, chunkZ);
		chunk.initLighting();
		return chunk;
	}

	public void populate(IChunkProvider chunkProvider, int chunkX, int chunkZ) {
		// c0.30 generated all population during its single finite-level pass.
	}

	public boolean isChunkLoaded(int chunkX, int chunkZ) {
		return true;
	}

	public boolean saveChunks(boolean saveAll, IProgressUpdate progress) {
		return true;
	}

	public boolean unloadChunks() {
		return false;
	}

	public boolean canSave() {
		return true;
	}

	public Chunk getChunkAt(int chunkX, int chunkZ) {
		return this.getOrCreateChunk(chunkX, chunkZ);
	}

	public void getChunkAt(IChunkProvider chunkProvider, int chunkX, int chunkZ) {
		this.populate(chunkProvider, chunkX, chunkZ);
	}

	public String makeString() {
		return "ClassicLevelSource";
	}

	public Chunk prepareChunk(int chunkX, int chunkZ) {
		return this.getOrCreateChunk(chunkX, chunkZ);
	}

	private Chunk makeExteriorChunk(int chunkX, int chunkZ) {
		byte[] blocks = new byte[16 * 16 * 128];
		int rockTop = Math.max(0, SEA_LEVEL - 3);
		for(int x = 0; x < 16; ++x) {
			for(int z = 0; z < 16; ++z) {
				for(int y = 0; y <= rockTop; ++y) {
					blocks[x << 11 | z << 7 | y] = (byte)Block.BEDROCK.id;
				}
				for(int y = rockTop + 1; y < SEA_LEVEL; ++y) {
					blocks[x << 11 | z << 7 | y] = (byte)Block.STATIONARY_WATER.id;
				}
			}
		}
		Chunk chunk = new Chunk(this.worldObj, blocks, chunkX, chunkZ);
		chunk.initLighting();
		return chunk;
	}
}
