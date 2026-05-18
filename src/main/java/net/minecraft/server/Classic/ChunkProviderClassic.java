package net.minecraft.server.Classic;

import java.util.Random;

/**
 * Minecraft Classic 0.3-inspired world generator.
 *
 * This provider adapts the Classic LevelGenerator algorithm to a chunk-based world:
 * - Height shaping using CombinedNoise and OctaveNoise matching Classic math
 * - Simple cave pass via MapGenCaves (approximation)
 * - Water line at Y=32, lava at Y=0
 * - Surface growth for grass, sand, gravel similar to Classic "Growing" step
 * - Basic population for ores, flowers, reeds, and trees
 */
import net.minecraft.server.*;

public class ChunkProviderClassic implements IChunkProvider {
    private final World worldObj;
    private final Random rand;
    private final MapGenBase caveGenerator = new MapGenCaves();

    // Classic-style noise
    private final ClassicCombinedNoise heightNoiseA;
    private final ClassicCombinedNoise heightNoiseB;
    private final ClassicOctaveNoise beachNoise;
    private final ClassicOctaveNoise growNoiseA;
    private final ClassicOctaveNoise growNoiseB;
    private final ClassicCombinedNoise erodeNoiseA;
    private final ClassicCombinedNoise erodeNoiseB;
    private final ClassicOctaveNoise soilNoise;

    private static final int SEA_LEVEL = 32;   // Classic 0.3 waterline
    private static final int CLASSIC_HEIGHT = 64; // Classic height band used for terrain

    public ChunkProviderClassic(World world, long seed) {
        this.worldObj = world;
        this.rand = new Random(seed);
        // Height field uses CombinedNoise of two 8-octave fields and a 6-octave selector
        this.heightNoiseA = new ClassicCombinedNoise(new ClassicOctaveNoise(new Random(seed ^ 0x1A2B3C4DL), 8),
                                                     new ClassicOctaveNoise(new Random(seed ^ 0x5D6E7F10L), 8));
        this.heightNoiseB = new ClassicCombinedNoise(new ClassicOctaveNoise(new Random(seed ^ 0x9ABCDEEFL), 8),
                                                     new ClassicOctaveNoise(new Random(seed ^ 0x13579BDFL), 8));
        this.beachNoise   = new ClassicOctaveNoise(new Random(seed ^ 0x2468ACE0L), 6);
        this.growNoiseA   = new ClassicOctaveNoise(new Random(seed ^ 0x0F0F0F0FL), 8);
        this.growNoiseB   = new ClassicOctaveNoise(new Random(seed ^ 0xF0F0F0F0L), 8);
        this.erodeNoiseA  = new ClassicCombinedNoise(new ClassicOctaveNoise(new Random(seed ^ 0xCAFEBABEL), 8),
                                                     new ClassicOctaveNoise(new Random(seed ^ 0xDEADBEEFL), 8));
        this.erodeNoiseB  = new ClassicCombinedNoise(new ClassicOctaveNoise(new Random(seed ^ 0x0BADF00DL), 8),
                                                     new ClassicOctaveNoise(new Random(seed ^ 0x600DCA11L), 8));
        this.soilNoise    = new ClassicOctaveNoise(new Random(seed ^ 0xFACEFEEDL), 8);
    }

    public Chunk getOrCreateChunk(int chunkX, int chunkZ) {
        this.rand.setSeed((long)chunkX * 341873128712L + (long)chunkZ * 132897987541L);
        // Outside classic bounds (0..15 chunks each axis → 256x256): solid bedrock to form an outer border
        if (chunkX < 0 || chunkZ < 0 || chunkX > 15 || chunkZ > 15) {
            return makeSolidBedrockChunk(chunkX, chunkZ);
        }

        byte[] blocks = new byte[16 * 16 * 128];

        // --- Height shaping (Classic raising + eroding) ---
        int[] heights = new int[16 * 16];
        int[] topSolidY = new int[16 * 16];
        final float scale = 1.3F; // Classic var10
        for (int dx = 0; dx < 16; ++dx) {
            int wx = (chunkX << 4) + dx;
            for (int dz = 0; dz < 16; ++dz) {
                int wz = (chunkZ << 4) + dz;
                double a = heightNoiseA.compute(wx * scale, wz * scale) / 6.0D - 4.0D;
                double b = heightNoiseB.compute(wx * scale, wz * scale) / 5.0D + 10.0D - 4.0D;
                if (beachNoise.compute(wx, wz) / 8.0D > 0.0D) b = a;
                double h = Math.max(a, b) / 2.0D;
                if (h < 0.0D) h *= 0.8D;
                int idx = dx + dz * 16;
                heights[idx] = (int)h;
            }
        }

        // Simple erosion pass (approximation of Classic "Eroding")
        for (int dx = 0; dx < 16; ++dx) {
            int wx = (chunkX << 4) + dx;
            for (int dz = 0; dz < 16; ++dz) {
                int wz = (chunkZ << 4) + dz;
                double n = this.erodeNoiseA.compute(wx << 1, wz << 1) / 8.0D;
                int parity = this.erodeNoiseB.compute(wx << 1, wz << 1) > 0.0D ? 1 : 0;
                if (n > 2.0D) {
                    int v = heights[dx + dz * 16];
                    int e = ((v - parity) / 2 << 1) + parity;
                    heights[dx + dz * 16] = e;
                }
            }
        }

        // --- Soiling (place stone/dirt and lava at bedrock) ---
        for (int dx = 0; dx < 16; ++dx) {
            int wx = (chunkX << 4) + dx;
            for (int dz = 0; dz < 16; ++dz) {
                int wz = (chunkZ << 4) + dz;
                int idxH = dx + dz * 16;
                int base = heights[idxH] + SEA_LEVEL; // top of dirt band
                int extra = (int)(this.soilNoise.compute(wx, wz) / 24.0D) - 4; // Classic var12
                int stoneTop = base + extra; // top of stone band
                if (stoneTop > CLASSIC_HEIGHT - 2) stoneTop = CLASSIC_HEIGHT - 2;
                if (stoneTop < 1) stoneTop = 1;
                if (base < 1) base = 1;
                // Highest solid after soiling is the max of stone cap and dirt cap
                topSolidY[idxH] = (stoneTop > base ? stoneTop : base);

                for (int y = 0; y < CLASSIC_HEIGHT; ++y) {
                    int index = (dx << 11) | (dz << 7) | y; // (x * 2048) + (z * 128) + y
                    int id = 0;
                    if (y <= base) id = Block.DIRT.id;
                    if (y <= stoneTop) id = Block.STONE.id;
                    if (y == 0) id = Block.BEDROCK.id; // Changed from STATIONARY_LAVA to BEDROCK
                    blocks[index] = (byte)id;
                }
            }
        }

        // Carving disabled in Classic to avoid generation instability at world creation
        // (Classic 0.30 caves were minimal; we approximate without carving here)

        // Water fill: below sea level, fill air with still water
        for (int dx = 0; dx < 16; ++dx) {
            for (int dz = 0; dz < 16; ++dz) {
                for (int y = 1; y < SEA_LEVEL; ++y) {
                    int index = (dx << 11) | (dz << 7) | y;
                    if (blocks[index] == 0) {
                        blocks[index] = (byte)Block.STATIONARY_WATER.id;
                    }
                }
            }
        }

        // Surface pass: ensure top surface becomes grass/sand/gravel and two layers of dirt underneath
        for (int dx = 0; dx < 16; ++dx) {
            int wx = (chunkX << 4) + dx;
            for (int dz = 0; dz < 16; ++dz) {
                int wz = (chunkZ << 4) + dz;
                boolean beach = growNoiseA.compute(wx, wz) > 8.0D;   // Classic var57
                boolean gravel = growNoiseB.compute(wx, wz) > 12.0D; // Classic var60

                int idxCol = dx + dz * 16;
                int topY = topSolidY[idxCol];
                if (topY <= 0) continue;
                int topIndex = (dx << 11) | (dz << 7) | topY;

                boolean underWater = topY < SEA_LEVEL - 1;
                int surfId;
                if (underWater && gravel) {
                    surfId = Block.GRAVEL.id;
                } else if (underWater || (topY <= CLASSIC_HEIGHT / 2 - 1 && beach)) {
                    surfId = Block.SAND.id;
                } else {
                    surfId = Block.GRASS.id;
                }

                // Set the surface block; convert exposed stone to grass/sand
                blocks[topIndex] = (byte)surfId;

                // Ensure two layers below surface are dirt if they are stone
                for (int d = 1; d <= 2 && topY - d >= 1; ++d) {
                    int belowIdx = (dx << 11) | (dz << 7) | (topY - d);
                    int belowId = blocks[belowIdx] & 255;
                    if (belowId == Block.STONE.id) {
                        blocks[belowIdx] = (byte)Block.DIRT.id;
                    }
                }
            }
        }

        // Build inner border walls: bedrock only up to two below sea level, water above that
        for (int dx = 0; dx < 16; ++dx) {
            int wx = (chunkX << 4) + dx;
            for (int dz = 0; dz < 16; ++dz) {
                int wz = (chunkZ << 4) + dz;
                boolean edgeWall = (wx == 0 || wx == 255 || wz == 0 || wz == 255);
                boolean edgeWater = (wx == 1 || wx == 254 || wz == 1 || wz == 254);
                if (edgeWall) {
                    int bedrockTop = Math.max(0, SEA_LEVEL - 2);
                    for (int y = 0; y <= bedrockTop; ++y) {
                        int index = (dx << 11) | (dz << 7) | y;
                        blocks[index] = (byte)Block.BEDROCK.id;
                    }
                    for (int y = bedrockTop + 1; y < SEA_LEVEL; ++y) {
                        int index = (dx << 11) | (dz << 7) | y;
                        if (blocks[index] == 0) {
                            blocks[index] = (byte)Block.STATIONARY_WATER.id;
                        }
                    }
                } else if (edgeWater) {
                    for (int y = 1; y < SEA_LEVEL; ++y) {
                        int index = (dx << 11) | (dz << 7) | y;
                        if (blocks[index] == 0) {
                            blocks[index] = (byte)Block.STATIONARY_WATER.id;
                        }
                    }
                }
            }
        }

        Chunk chunk = new Chunk(this.worldObj, blocks, chunkX, chunkZ);
        chunk.initLighting();
        return chunk;
    }

    public void populate(IChunkProvider chunkProvider, int chunkX, int chunkZ) {
        // Do not populate outside classic bounds
        if (chunkX < 0 || chunkZ < 0 || chunkX > 15 || chunkZ > 15) {
            return;
        }
        BlockSand.instaFall = true;
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        this.rand.setSeed(this.worldObj.getSeed());
        long l1 = this.rand.nextLong() / 2L * 2L + 1L;
        long l2 = this.rand.nextLong() / 2L * 2L + 1L;
        this.rand.setSeed((long)chunkX * l1 + (long)chunkZ * l2 ^ this.worldObj.getSeed());

        // Ores (approximate to Classic ratios)
        for (int i = 0; i < 20; ++i) { // coal
            int x = baseX + this.rand.nextInt(16);
            int y = this.rand.nextInt(128);
            int z = baseZ + this.rand.nextInt(16);
            (new WorldGenMinable("minecraft:coal_ore", 16)).a(this.worldObj, this.rand, x, y, z);
        }
        for (int i = 0; i < 10; ++i) { // iron
            int x = baseX + this.rand.nextInt(16);
            int y = this.rand.nextInt(64);
            int z = baseZ + this.rand.nextInt(16);
            (new WorldGenMinable("minecraft:iron_ore", 8)).a(this.worldObj, this.rand, x, y, z);
        }
        for (int i = 0; i < 2; ++i) { // gold
            int x = baseX + this.rand.nextInt(16);
            int y = this.rand.nextInt(32);
            int z = baseZ + this.rand.nextInt(16);
            (new WorldGenMinable("minecraft:gold_ore", 8)).a(this.worldObj, this.rand, x, y, z);
        }

        // Trees
        int trees = 2 + this.rand.nextInt(3);
        for (int i = 0; i < trees; ++i) {
            int x = baseX + this.rand.nextInt(16) + 8;
            int z = baseZ + this.rand.nextInt(16) + 8;
            int y = this.worldObj.getHighestBlockYAt(x, z);
            if (y > 0 && y < 128) {
                WorldGenerator gen = (this.rand.nextInt(10) == 0) ? new WorldGenBigTree() : new WorldGenTrees();
                gen.a(this.worldObj, this.rand, x, y, z);
            }
        }

        // Flowers and reeds
        for (int i = 0; i < 2; ++i) {
            int x = baseX + this.rand.nextInt(16) + 8;
            int y = this.rand.nextInt(128);
            int z = baseZ + this.rand.nextInt(16) + 8;
            (new WorldGenFlowers("minecraft:dandelion")).a(this.worldObj, this.rand, x, y, z);
        }
        if (this.rand.nextInt(2) == 0) {
            int x = baseX + this.rand.nextInt(16) + 8;
            int y = this.rand.nextInt(128);
            int z = baseZ + this.rand.nextInt(16) + 8;
            (new WorldGenFlowers("minecraft:poppy")).a(this.worldObj, this.rand, x, y, z);
        }
        for (int i = 0; i < 10; ++i) {
            int x = baseX + this.rand.nextInt(16) + 8;
            int y = this.rand.nextInt(128);
            int z = baseZ + this.rand.nextInt(16) + 8;
            (new WorldGenReed()).a(this.worldObj, this.rand, x, y, z);
        }

        // Liquids inside caves
        for (int i = 0; i < 30; ++i) {
            int x = baseX + this.rand.nextInt(16) + 8;
            int y = this.rand.nextInt(this.rand.nextInt(112) + 8);
            int z = baseZ + this.rand.nextInt(16) + 8;
            (new WorldGenLiquids("minecraft:water")).a(this.worldObj, this.rand, x, y, z);
        }
        for (int i = 0; i < 10; ++i) {
            int x = baseX + this.rand.nextInt(16) + 8;
            int y = this.rand.nextInt(this.rand.nextInt(112) + 8);
            int z = baseZ + this.rand.nextInt(16) + 8;
            (new WorldGenLiquids("minecraft:lava")).a(this.worldObj, this.rand, x, y, z);
        }

        BlockSand.instaFall = false;
    }

    public boolean isChunkLoaded(int x, int z) { return true; }
    public boolean saveChunks(boolean flag, IProgressUpdate progress) { return true; }
    public boolean unloadChunks() { return false; }
    public boolean canSave() { return true; }
    public Chunk getChunkAt(int x, int z) { return getOrCreateChunk(x, z); }
    public void getChunkAt(IChunkProvider ichunkprovider, int x, int z) { this.populate(ichunkprovider, x, z); }
    public String makeString() { return "ClassicLevelSource"; }
    public Chunk prepareChunk(int x, int z) { return getOrCreateChunk(x, z); }

    // --- Classic noise implementations (local, minimal) ---
    private static final class ClassicCombinedNoise {
        private final ClassicOctaveNoise n1;
        private final ClassicOctaveNoise n2;
        ClassicCombinedNoise(ClassicOctaveNoise a, ClassicOctaveNoise b) { this.n1 = a; this.n2 = b; }
        double compute(double x, double z) { return n1.compute(x + n2.compute(x, z), z); }
    }
    private static final class ClassicOctaveNoise {
        private final ClassicPerlinNoise[] perlin;
        private final int octaves;
        ClassicOctaveNoise(Random r, int octaves) {
            this.octaves = octaves;
            this.perlin = new ClassicPerlinNoise[octaves];
            for (int i = 0; i < octaves; ++i) this.perlin[i] = new ClassicPerlinNoise(new Random(r.nextLong()));
        }
        double compute(double x, double z) {
            double sum = 0.0D, scale = 1.0D;
            for (int i = 0; i < this.octaves; ++i) {
                sum += this.perlin[i].compute(x / scale, z / scale) * scale;
                scale *= 2.0D;
            }
            return sum;
        }
    }
    private static final class ClassicPerlinNoise {
        private final int[] noise = new int[512];
        ClassicPerlinNoise(Random rand) {
            for (int i = 0; i < 256; ++i) noise[i] = i;
            for (int i = 0; i < 256; ++i) {
                int j = rand.nextInt(256 - i) + i;
                int t = noise[i]; noise[i] = noise[j]; noise[j] = t; noise[i + 256] = noise[i];
            }
        }
        private static double fade(double t) { return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D); }
        private static double lerp(double a, double b, double t) { return a + t * (b - a); }
        private static double grad(int hash, double x, double y, double z) {
            hash &= 15;
            double u = (hash < 8) ? x : y;
            double v = (hash < 4) ? y : (hash == 12 || hash == 14 ? x : z);
            return (((hash & 1) == 0) ? u : -u) + (((hash & 2) == 0) ? v : -v);
        }
        double compute(double x, double z) {
            double y = 0.0D;
            int X = (int)Math.floor(x) & 255;
            int Y = (int)Math.floor(z) & 255;
            int Z = (int)Math.floor(y) & 255;
            double xf = x - Math.floor(x);
            double yf = y - Math.floor(y);
            double zf = z - Math.floor(z);
            double u = fade(xf), v = fade(zf), w = fade(yf);
            int A = noise[X] + Y; int AA = noise[A] + Z; int AB = noise[A + 1] + Z;
            int B = noise[X + 1] + Y; int BA = noise[B] + Z; int BB = noise[B + 1] + Z;
            return lerp(
                lerp(lerp(grad(noise[AA], xf, zf, yf), grad(noise[BA], xf - 1, zf, yf), u),
                     lerp(grad(noise[AB], xf, zf - 1, yf), grad(noise[BB], xf - 1, zf - 1, yf), u), v),
                lerp(lerp(grad(noise[AA + 1], xf, zf, yf - 1), grad(noise[BA + 1], xf - 1, zf, yf - 1), u),
                     lerp(grad(noise[AB + 1], xf, zf - 1, yf - 1), grad(noise[BB + 1], xf - 1, zf - 1, yf - 1), u), v),
                w);
        }
    }

    private Chunk makeSolidBedrockChunk(int chunkX, int chunkZ) {
        byte[] blocks = new byte[16 * 16 * 128];
        int bedrockTop = Math.max(0, SEA_LEVEL - 3);
        for (int dx = 0; dx < 16; ++dx) {
            for (int dz = 0; dz < 16; ++dz) {
                // Bedrock base up to two below sea level
                for (int y = 0; y <= bedrockTop; ++y) {
                    blocks[(dx << 11) | (dz << 7) | y] = (byte)Block.BEDROCK.id;
                }
                // Still water up to sea level
                for (int y = bedrockTop + 1; y < SEA_LEVEL; ++y) {
                    blocks[(dx << 11) | (dz << 7) | y] = (byte)Block.STATIONARY_WATER.id;
                }
                // Above sea level remains air
            }
        }
        Chunk chunk = new Chunk(this.worldObj, blocks, chunkX, chunkZ);
        chunk.initLighting();
        return chunk;
    }
}


