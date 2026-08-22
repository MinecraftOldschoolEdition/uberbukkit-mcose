package net.minecraft.server.Classic;

import java.util.Random;

/**
 * Source-faithful port of the finite level generator from Minecraft Classic
 * c0.30 Survival. The original generator used an unseeded Random; this port
 * supplies the world's seed while preserving the original call order.
 */
final class ClassicLevelGenerator {
	static final int WIDTH = 256;
	static final int LENGTH = 256;
	static final int DEPTH = 64;
	static final int WATER_LEVEL = 32;

	private static final int AIR = 0;
	private static final int STONE = 1;
	private static final int GRASS = 2;
	private static final int DIRT = 3;
	private static final int WATER_MOVING = 8;
	private static final int WATER_STILL = 9;
	private static final int LAVA_MOVING = 10;
	private static final int LAVA_STILL = 11;
	private static final int SAND = 12;
	private static final int GRAVEL = 13;
	private static final int ORE_GOLD = 14;
	private static final int ORE_IRON = 15;
	private static final int ORE_COAL = 16;
	private static final int LOG = 17;
	private static final int LEAVES = 18;
	private static final int FLOWER_YELLOW = 37;
	private static final int FLOWER_RED = 38;
	private static final int MUSHROOM_BROWN = 39;
	private static final int MUSHROOM_RED = 40;
	private static final float PI = 3.1415927F;
	private static final long TREE_RANDOM_SALT = 0x9E3779B97F4A7C15L;
	private static final float[] SIN_TABLE = new float[65536];

	static {
		for(int i = 0; i < SIN_TABLE.length; ++i) {
			SIN_TABLE[i] = (float)Math.sin((double)i * Math.PI * 2.0D / 65536.0D);
		}
	}

	private final Random random;
	private final Random treeRandom;
	private final byte[] blocks = new byte[WIDTH * LENGTH * DEPTH];
	private final int[] heightMap = new int[WIDTH * LENGTH];
	private boolean generated;

	ClassicLevelGenerator(long seed) {
		this.random = new Random(seed);
		// Classic's Level owned a separate Random for tree shapes. Deriving that
		// stream from the world seed keeps client and server output identical.
		this.treeRandom = new Random(seed ^ TREE_RANDOM_SALT);
	}

	byte[] generate() {
		if(this.generated) {
			return this.blocks;
		}

		this.raiseTerrain();
		this.erodeTerrain();
		this.soilTerrain();
		this.carveCaves();
		this.generateOre(ORE_COAL, 90);
		this.generateOre(ORE_IRON, 70);
		this.generateOre(ORE_GOLD, 50);
		this.waterTerrain();
		this.meltTerrain();
		this.growSurface();
		this.plantFlowers();
		this.plantMushrooms();
		this.plantTrees();
		this.generated = true;
		return this.blocks;
	}

	void copyChunk(int chunkX, int chunkZ, byte[] chunkBlocks) {
		this.generate();
		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;
		for(int x = 0; x < 16; ++x) {
			for(int z = 0; z < 16; ++z) {
				for(int y = 0; y < DEPTH; ++y) {
					chunkBlocks[x << 11 | z << 7 | y] = this.blocks[index(baseX + x, y, baseZ + z)];
				}
			}
		}
	}

	private void raiseTerrain() {
		Noise heightNoiseA = new CombinedNoise(new OctaveNoise(this.random, 8), new OctaveNoise(this.random, 8));
		Noise heightNoiseB = new CombinedNoise(new OctaveNoise(this.random, 8), new OctaveNoise(this.random, 8));
		Noise selectorNoise = new OctaveNoise(this.random, 6);
		float scale = 1.3F;

		for(int x = 0; x < WIDTH; ++x) {
			for(int z = 0; z < LENGTH; ++z) {
				double first = heightNoiseA.compute((double)((float)x * scale), (double)((float)z * scale)) / 6.0D - 4.0D;
				double second = heightNoiseB.compute((double)((float)x * scale), (double)((float)z * scale)) / 5.0D + 10.0D - 4.0D;
				if(selectorNoise.compute(x, z) / 8.0D > 0.0D) {
					second = first;
				}
				double height = Math.max(first, second) / 2.0D;
				if(height < 0.0D) {
					height *= 0.8D;
				}
				this.heightMap[x + z * WIDTH] = (int)height;
			}
		}
	}

	private void erodeTerrain() {
		Noise erosionNoise = new CombinedNoise(new OctaveNoise(this.random, 8), new OctaveNoise(this.random, 8));
		Noise parityNoise = new CombinedNoise(new OctaveNoise(this.random, 8), new OctaveNoise(this.random, 8));

		for(int x = 0; x < WIDTH; ++x) {
			for(int z = 0; z < LENGTH; ++z) {
				double erosion = erosionNoise.compute(x << 1, z << 1) / 8.0D;
				int parity = parityNoise.compute(x << 1, z << 1) > 0.0D ? 1 : 0;
				if(erosion > 2.0D) {
					int height = this.heightMap[x + z * WIDTH];
					this.heightMap[x + z * WIDTH] = (height - parity) / 2 << 1 | parity;
				}
			}
		}
	}

	private void soilTerrain() {
		Noise soilNoise = new OctaveNoise(this.random, 8);
		for(int x = 0; x < WIDTH; ++x) {
			for(int z = 0; z < LENGTH; ++z) {
				int soilDepth = (int)(soilNoise.compute(x, z) / 24.0D) - 4;
				int dirtTop = this.heightMap[x + z * WIDTH] + WATER_LEVEL;
				int stoneTop = dirtTop + soilDepth;
				int surface = Math.max(dirtTop, stoneTop);
				if(surface > DEPTH - 2) {
					surface = DEPTH - 2;
				}
				if(surface < 1) {
					surface = 1;
				}
				this.heightMap[x + z * WIDTH] = surface;

				for(int y = 0; y < DEPTH; ++y) {
					int block = AIR;
					if(y <= dirtTop) {
						block = DIRT;
					}
					if(y <= stoneTop) {
						block = STONE;
					}
					if(y == 0) {
						block = LAVA_MOVING;
					}
					this.blocks[index(x, y, z)] = (byte)block;
				}
			}
		}
	}

	private void carveCaves() {
		int caveCount = WIDTH * LENGTH * DEPTH / 256 / 64 << 1;
		for(int cave = 0; cave < caveCount; ++cave) {
			float x = this.random.nextFloat() * WIDTH;
			float y = this.random.nextFloat() * DEPTH;
			float z = this.random.nextFloat() * LENGTH;
			int steps = (int)((this.random.nextFloat() + this.random.nextFloat()) * 200.0F);
			float yaw = this.random.nextFloat() * PI * 2.0F;
			float yawVelocity = 0.0F;
			float pitch = this.random.nextFloat() * PI * 2.0F;
			float pitchVelocity = 0.0F;
			float scale = this.random.nextFloat() * this.random.nextFloat();

			for(int step = 0; step < steps; ++step) {
				x += sin(yaw) * cos(pitch);
				z += cos(yaw) * cos(pitch);
				y += sin(pitch);
				yaw += yawVelocity * 0.2F;
				yawVelocity = yawVelocity * 0.9F + this.random.nextFloat() - this.random.nextFloat();
				pitch = (pitch + pitchVelocity * 0.5F) * 0.5F;
				pitchVelocity = pitchVelocity * 0.75F + this.random.nextFloat() - this.random.nextFloat();

				if(this.random.nextFloat() < 0.25F) {
					continue;
				}

				float centerX = x + (this.random.nextFloat() * 4.0F - 2.0F) * 0.2F;
				float centerY = y + (this.random.nextFloat() * 4.0F - 2.0F) * 0.2F;
				float centerZ = z + (this.random.nextFloat() * 4.0F - 2.0F) * 0.2F;
				float radius = (DEPTH - centerY) / DEPTH;
				radius = 1.2F + (radius * 3.5F + 1.0F) * scale;
				radius = sin((float)step * PI / (float)steps) * radius;
				this.carveStoneEllipsoid(centerX, centerY, centerZ, radius, AIR);
			}
		}
	}

	private void generateOre(int ore, int abundance) {
		int veinCount = WIDTH * LENGTH * DEPTH / 256 / 64 * abundance / 100;
		for(int vein = 0; vein < veinCount; ++vein) {
			float x = this.random.nextFloat() * WIDTH;
			float y = this.random.nextFloat() * DEPTH;
			float z = this.random.nextFloat() * LENGTH;
			int steps = (int)((this.random.nextFloat() + this.random.nextFloat()) * 75.0F * abundance / 100.0F);
			float yaw = this.random.nextFloat() * PI * 2.0F;
			float yawVelocity = 0.0F;
			float pitch = this.random.nextFloat() * PI * 2.0F;
			float pitchVelocity = 0.0F;

			for(int step = 0; step < steps; ++step) {
				x += sin(yaw) * cos(pitch);
				z += cos(yaw) * cos(pitch);
				y += sin(pitch);
				yaw += yawVelocity * 0.2F;
				yawVelocity = yawVelocity * 0.9F + this.random.nextFloat() - this.random.nextFloat();
				pitch = (pitch + pitchVelocity * 0.5F) * 0.5F;
				pitchVelocity = pitchVelocity * 0.9F + this.random.nextFloat() - this.random.nextFloat();
				float radius = sin((float)step * PI / (float)steps) * abundance / 100.0F + 1.0F;
				this.carveStoneEllipsoid(x, y, z, radius, ore);
			}
		}
	}

	private void carveStoneEllipsoid(float centerX, float centerY, float centerZ, float radius, int replacement) {
		for(int x = (int)(centerX - radius); x <= (int)(centerX + radius); ++x) {
			for(int y = (int)(centerY - radius); y <= (int)(centerY + radius); ++y) {
				for(int z = (int)(centerZ - radius); z <= (int)(centerZ + radius); ++z) {
					float deltaX = x - centerX;
					float deltaY = y - centerY;
					float deltaZ = z - centerZ;
					float distance = deltaX * deltaX + deltaY * deltaY * 2.0F + deltaZ * deltaZ;
					if(distance < radius * radius
						&& x >= 1 && y >= 1 && z >= 1
						&& x < WIDTH - 1 && y < DEPTH - 1 && z < LENGTH - 1) {
						int blockIndex = index(x, y, z);
						if(this.blocks[blockIndex] == STONE) {
							this.blocks[blockIndex] = (byte)replacement;
						}
					}
				}
			}
		}
	}

	private void waterTerrain() {
		for(int x = 0; x < WIDTH; ++x) {
			this.floodFill(x, DEPTH / 2 - 1, 0, WATER_STILL);
			this.floodFill(x, DEPTH / 2 - 1, LENGTH - 1, WATER_STILL);
		}
		for(int z = 0; z < LENGTH; ++z) {
			this.floodFill(0, DEPTH / 2 - 1, z, WATER_STILL);
			this.floodFill(WIDTH - 1, DEPTH / 2 - 1, z, WATER_STILL);
		}

		int sourceCount = WIDTH * LENGTH / 8000;
		for(int source = 0; source < sourceCount; ++source) {
			int x = this.random.nextInt(WIDTH);
			int y = WATER_LEVEL - 1 - this.random.nextInt(2);
			int z = this.random.nextInt(LENGTH);
			if(this.blocks[index(x, y, z)] == AIR) {
				this.floodFill(x, y, z, WATER_STILL);
			}
		}
	}

	private void meltTerrain() {
		int sourceCount = WIDTH * LENGTH * DEPTH / 20000;
		for(int source = 0; source < sourceCount; ++source) {
			int x = this.random.nextInt(WIDTH);
			int y = (int)(this.random.nextFloat() * this.random.nextFloat() * (WATER_LEVEL - 3));
			int z = this.random.nextInt(LENGTH);
			if(this.blocks[index(x, y, z)] == AIR) {
				this.floodFill(x, y, z, LAVA_STILL);
			}
		}
	}

	private long floodFill(int startX, int startY, int startZ, int liquid) {
		int start = index(startX, startY, startZ);
		if(this.blocks[start] != AIR) {
			return 0L;
		}

		IntStack stack = new IntStack(1048576);
		stack.push(start);
		long filled = 0L;
		int layerSize = WIDTH * LENGTH;
		boolean lava = liquid == LAVA_MOVING || liquid == LAVA_STILL;

		while(!stack.isEmpty()) {
			int packed = stack.pop();
			if(this.blocks[packed] != AIR) {
				continue;
			}
			int y = packed / layerSize;
			int inLayer = packed - y * layerSize;
			int z = inLayer / WIDTH;
			int x = inLayer - z * WIDTH;
			int rowStart = (y * LENGTH + z) * WIDTH;
			int left = x;
			while(left > 0 && this.blocks[rowStart + left - 1] == AIR) {
				--left;
			}
			int right = x;
			while(right < WIDTH && this.blocks[rowStart + right] == AIR) {
				++right;
			}

			boolean negativeZOpen = false;
			boolean positiveZOpen = false;
			boolean belowOpen = false;
			filled += right - left;
			for(int fillX = left; fillX < right; ++fillX) {
				int current = rowStart + fillX;
				this.blocks[current] = (byte)liquid;
				if(z > 0) {
					boolean open = this.blocks[current - WIDTH] == AIR;
					if(open && !negativeZOpen) {
						stack.push(current - WIDTH);
					}
					negativeZOpen = open;
				}
				if(z < LENGTH - 1) {
					boolean open = this.blocks[current + WIDTH] == AIR;
					if(open && !positiveZOpen) {
						stack.push(current + WIDTH);
					}
					positiveZOpen = open;
				}
				if(y > 0) {
					int below = current - layerSize;
					int belowBlock = this.blocks[below] & 255;
					if(lava && (belowBlock == WATER_MOVING || belowBlock == WATER_STILL)) {
						this.blocks[below] = (byte)STONE;
					}
					boolean open = belowBlock == AIR;
					if(open && !belowOpen) {
						stack.push(below);
					}
					belowOpen = open;
				}
			}
		}
		return filled;
	}

	private void growSurface() {
		Noise sandNoise = new OctaveNoise(this.random, 8);
		Noise gravelNoise = new OctaveNoise(this.random, 8);
		for(int x = 0; x < WIDTH; ++x) {
			for(int z = 0; z < LENGTH; ++z) {
				boolean sand = sandNoise.compute(x, z) > 8.0D;
				boolean gravel = gravelNoise.compute(x, z) > 12.0D;
				int surfaceY = this.heightMap[x + z * WIDTH];
				int surfaceIndex = index(x, surfaceY, z);
				int above = this.blocks[index(x, surfaceY + 1, z)] & 255;
				if((above == WATER_MOVING || above == WATER_STILL) && surfaceY <= DEPTH / 2 - 1 && gravel) {
					this.blocks[surfaceIndex] = (byte)GRAVEL;
				}
				if(above == AIR) {
					this.blocks[surfaceIndex] = (byte)(surfaceY <= DEPTH / 2 - 1 && sand ? SAND : GRASS);
				}
			}
		}
	}

	private void plantFlowers() {
		int clusterCount = WIDTH * LENGTH / 3000;
		for(int cluster = 0; cluster < clusterCount; ++cluster) {
			int type = this.random.nextInt(2);
			int originX = this.random.nextInt(WIDTH);
			int originZ = this.random.nextInt(LENGTH);
			for(int attempt = 0; attempt < 10; ++attempt) {
				int x = originX;
				int z = originZ;
				for(int walk = 0; walk < 5; ++walk) {
					x += this.random.nextInt(6) - this.random.nextInt(6);
					z += this.random.nextInt(6) - this.random.nextInt(6);
					if(x >= 0 && z >= 0 && x < WIDTH && z < LENGTH) {
						int y = this.heightMap[x + z * WIDTH] + 1;
						int target = index(x, y, z);
						if(this.blocks[target] == AIR && (this.blocks[index(x, y - 1, z)] & 255) == GRASS) {
							this.blocks[target] = (byte)(type == 0 ? FLOWER_YELLOW : FLOWER_RED);
						}
					}
				}
			}
		}
	}

	private void plantMushrooms() {
		int clusterCount = WIDTH * LENGTH * DEPTH / 2000;
		for(int cluster = 0; cluster < clusterCount; ++cluster) {
			int type = this.random.nextInt(2);
			int originX = this.random.nextInt(WIDTH);
			int originY = this.random.nextInt(DEPTH);
			int originZ = this.random.nextInt(LENGTH);
			for(int attempt = 0; attempt < 20; ++attempt) {
				int x = originX;
				int y = originY;
				int z = originZ;
				for(int walk = 0; walk < 5; ++walk) {
					x += this.random.nextInt(6) - this.random.nextInt(6);
					y += this.random.nextInt(2) - this.random.nextInt(2);
					z += this.random.nextInt(6) - this.random.nextInt(6);
					if(x >= 0 && z >= 0 && y >= 1 && x < WIDTH && z < LENGTH
						&& y < this.heightMap[x + z * WIDTH] - 1) {
						int target = index(x, y, z);
						if(this.blocks[target] == AIR && (this.blocks[index(x, y - 1, z)] & 255) == STONE) {
							this.blocks[target] = (byte)(type == 0 ? MUSHROOM_BROWN : MUSHROOM_RED);
						}
					}
				}
			}
		}
	}

	private void plantTrees() {
		int clusterCount = WIDTH * LENGTH / 4000;
		for(int cluster = 0; cluster < clusterCount; ++cluster) {
			int originX = this.random.nextInt(WIDTH);
			int originZ = this.random.nextInt(LENGTH);
			for(int attempt = 0; attempt < 20; ++attempt) {
				int x = originX;
				int z = originZ;
				for(int walk = 0; walk < 20; ++walk) {
					x += this.random.nextInt(6) - this.random.nextInt(6);
					z += this.random.nextInt(6) - this.random.nextInt(6);
					if(x >= 0 && z >= 0 && x < WIDTH && z < LENGTH && this.random.nextInt(4) == 0) {
						this.maybeGrowTree(x, this.heightMap[x + z * WIDTH] + 1, z);
					}
				}
			}
		}
	}

	private boolean maybeGrowTree(int x, int y, int z) {
		int height = this.treeRandom.nextInt(3) + 4;
		boolean clear = true;
		for(int checkY = y; checkY <= y + 1 + height && clear; ++checkY) {
			int radius = 1;
			if(checkY == y) {
				radius = 0;
			}
			if(checkY >= y + 1 + height - 2) {
				radius = 2;
			}
			for(int checkX = x - radius; checkX <= x + radius && clear; ++checkX) {
				for(int checkZ = z - radius; checkZ <= z + radius && clear; ++checkZ) {
					if(checkX < 0 || checkY < 0 || checkZ < 0 || checkX >= WIDTH || checkY >= DEPTH || checkZ >= LENGTH
						|| this.blocks[index(checkX, checkY, checkZ)] != AIR) {
						clear = false;
					}
				}
			}
		}
		if(!clear || (this.blocks[index(x, y - 1, z)] & 255) != GRASS || y >= DEPTH - height - 1) {
			return false;
		}

		this.blocks[index(x, y - 1, z)] = (byte)DIRT;
		for(int leafY = y - 3 + height; leafY <= y + height; ++leafY) {
			int layerOffset = leafY - (y + height);
			int radius = 1 - layerOffset / 2;
			for(int leafX = x - radius; leafX <= x + radius; ++leafX) {
				int deltaX = leafX - x;
				for(int leafZ = z - radius; leafZ <= z + radius; ++leafZ) {
					int deltaZ = leafZ - z;
					if(Math.abs(deltaX) != radius || Math.abs(deltaZ) != radius
						|| this.treeRandom.nextInt(2) != 0 && layerOffset != 0) {
						this.blocks[index(leafX, leafY, leafZ)] = (byte)LEAVES;
					}
				}
			}
		}
		for(int logY = 0; logY < height; ++logY) {
			this.blocks[index(x, y + logY, z)] = (byte)LOG;
		}
		return true;
	}

	private static int index(int x, int y, int z) {
		return (y * LENGTH + z) * WIDTH + x;
	}

	private static float sin(float angle) {
		return SIN_TABLE[(int)(angle * 10430.378F) & 65535];
	}

	private static float cos(float angle) {
		return SIN_TABLE[(int)(angle * 10430.378F + 16384.0F) & 65535];
	}

	private interface Noise {
		double compute(double x, double z);
	}

	private static final class CombinedNoise implements Noise {
		private final Noise first;
		private final Noise second;

		private CombinedNoise(Noise first, Noise second) {
			this.first = first;
			this.second = second;
		}

		public double compute(double x, double z) {
			return this.first.compute(x + this.second.compute(x, z), z);
		}
	}

	private static final class OctaveNoise implements Noise {
		private final PerlinNoise[] octaves;

		private OctaveNoise(Random random, int octaveCount) {
			this.octaves = new PerlinNoise[octaveCount];
			for(int octave = 0; octave < octaveCount; ++octave) {
				this.octaves[octave] = new PerlinNoise(random);
			}
		}

		public double compute(double x, double z) {
			double result = 0.0D;
			double scale = 1.0D;
			for(int octave = 0; octave < this.octaves.length; ++octave) {
				result += this.octaves[octave].compute(x / scale, z / scale) * scale;
				scale *= 2.0D;
			}
			return result;
		}
	}

	private static final class PerlinNoise implements Noise {
		private final int[] permutations = new int[512];

		private PerlinNoise(Random random) {
			for(int i = 0; i < 256; ++i) {
				this.permutations[i] = i;
			}
			for(int i = 0; i < 256; ++i) {
				int selected = random.nextInt(256 - i) + i;
				int swap = this.permutations[i];
				this.permutations[i] = this.permutations[selected];
				this.permutations[selected] = swap;
				this.permutations[i + 256] = this.permutations[i];
			}
		}

		public double compute(double x, double z) {
			double third = 0.0D;
			int floorX = (int)Math.floor(x) & 255;
			int floorZ = (int)Math.floor(z) & 255;
			int floorThird = (int)Math.floor(third) & 255;
			double localX = x - Math.floor(x);
			double localZ = z - Math.floor(z);
			double localThird = third - Math.floor(third);
			double fadeX = fade(localX);
			double fadeZ = fade(localZ);
			double fadeThird = fade(localThird);
			int first = this.permutations[floorX] + floorZ;
			int firstLow = this.permutations[first] + floorThird;
			int firstHigh = this.permutations[first + 1] + floorThird;
			int second = this.permutations[floorX + 1] + floorZ;
			int secondLow = this.permutations[second] + floorThird;
			int secondHigh = this.permutations[second + 1] + floorThird;
			return lerp(fadeThird,
				lerp(fadeZ,
					lerp(fadeX, grad(this.permutations[firstLow], localX, localZ, localThird), grad(this.permutations[secondLow], localX - 1.0D, localZ, localThird)),
					lerp(fadeX, grad(this.permutations[firstHigh], localX, localZ - 1.0D, localThird), grad(this.permutations[secondHigh], localX - 1.0D, localZ - 1.0D, localThird))),
				lerp(fadeZ,
					lerp(fadeX, grad(this.permutations[firstLow + 1], localX, localZ, localThird - 1.0D), grad(this.permutations[secondLow + 1], localX - 1.0D, localZ, localThird - 1.0D)),
					lerp(fadeX, grad(this.permutations[firstHigh + 1], localX, localZ - 1.0D, localThird - 1.0D), grad(this.permutations[secondHigh + 1], localX - 1.0D, localZ - 1.0D, localThird - 1.0D))));
		}

		private static double fade(double value) {
			return value * value * value * (value * (value * 6.0D - 15.0D) + 10.0D);
		}

		private static double lerp(double amount, double first, double second) {
			return first + amount * (second - first);
		}

		private static double grad(int hash, double x, double z, double third) {
			hash &= 15;
			double first = hash < 8 ? x : z;
			double second = hash < 4 ? z : hash != 12 && hash != 14 ? third : x;
			return ((hash & 1) == 0 ? first : -first) + ((hash & 2) == 0 ? second : -second);
		}
	}

	private static final class IntStack {
		private int[] values;
		private int size;

		private IntStack(int initialCapacity) {
			this.values = new int[initialCapacity];
		}

		private boolean isEmpty() {
			return this.size == 0;
		}

		private void push(int value) {
			if(this.size == this.values.length) {
				int[] expanded = new int[this.values.length << 1];
				System.arraycopy(this.values, 0, expanded, 0, this.values.length);
				this.values = expanded;
			}
			this.values[this.size++] = value;
		}

		private int pop() {
			return this.values[--this.size];
		}
	}
}
