package net.minecraft.server.Classic;

import net.minecraft.server.*;
import java.util.Random;
import net.minecraft.server.registry.LegacyPlacedFeatureExecutor;
import net.minecraft.server.registry.PlacedFeatureDataBootstrap;

/**
 * Nether chunk provider for Classic worlds.
 * Generates a 256x256 Classic-style terrain with netherrack and soul sand,
 * surrounded by a lava sea at the borders.
 */
public class ChunkProviderHellClassic implements IChunkProvider {
    private final World worldObj;
    private final Random rand;

    // Classic-style noise (same as overworld Classic)
    private final ClassicCombinedNoise heightNoiseA;
    private final ClassicCombinedNoise heightNoiseB;
    private final ClassicOctaveNoise beachNoise;
    private final ClassicOctaveNoise growNoiseA;
    private final ClassicCombinedNoise erodeNoiseA;
    private final ClassicCombinedNoise erodeNoiseB;
    private final ClassicOctaveNoise soilNoise;

    private static final int LAVA_LEVEL = 32;   // Lava sea level (similar to Classic water level)
    private static final int CLASSIC_HEIGHT = 64; // Classic height band used for terrain
    private static final int NETHER_FLOOR = 32;   // Typical Nether floor height

    public ChunkProviderHellClassic(World world, long seed) {
        this.worldObj = world;
        this.rand = new Random(seed);
        // Use same noise seeds as Classic overworld for consistency
        this.heightNoiseA = new ClassicCombinedNoise(new ClassicOctaveNoise(new Random(seed ^ 0x1A2B3C4DL), 8),
                                                     new ClassicOctaveNoise(new Random(seed ^ 0x5D6E7F10L), 8));
        this.heightNoiseB = new ClassicCombinedNoise(new ClassicOctaveNoise(new Random(seed ^ 0x9ABCDEEFL), 8),
                                                     new ClassicOctaveNoise(new Random(seed ^ 0x13579BDFL), 8));
        this.beachNoise   = new ClassicOctaveNoise(new Random(seed ^ 0x2468ACE0L), 6);
        this.growNoiseA   = new ClassicOctaveNoise(new Random(seed ^ 0x0F0F0F0FL), 8);
        this.erodeNoiseA  = new ClassicCombinedNoise(new ClassicOctaveNoise(new Random(seed ^ 0xCAFEBABEL), 8),
                                                     new ClassicOctaveNoise(new Random(seed ^ 0xDEADBEEFL), 8));
        this.erodeNoiseB  = new ClassicCombinedNoise(new ClassicOctaveNoise(new Random(seed ^ 0x0BADF00DL), 8),
                                                     new ClassicOctaveNoise(new Random(seed ^ 0x600DCA11L), 8));
        this.soilNoise    = new ClassicOctaveNoise(new Random(seed ^ 0xFACEFEEDL), 8);
    }

    public Chunk getOrCreateChunk(int chunkX, int chunkZ) {
        this.rand.setSeed((long)chunkX * 341873128712L + (long)chunkZ * 132897987541L);
        // Outside classic bounds (0..15 chunks each axis → 256x256): solid lava/bedrock border
        // But within 12 chunks of the area (chunks -12 to 27), we still generate lava waterfalls
        if (chunkX < 0 || chunkZ < 0 || chunkX > 15 || chunkZ > 15) {
            // For chunks within 12 chunks of the 256x256 area, generate with waterfalls
            // Chunks from -12 to 27 are within 12 chunks of the 0-15 area
            if (chunkX >= -12 && chunkX <= 27 && chunkZ >= -12 && chunkZ <= 27) {
                return makeLavaBorderChunk(chunkX, chunkZ, true); // Generate with waterfalls
            } else {
                return makeLavaBorderChunk(chunkX, chunkZ, false); // No waterfalls far from area
            }
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

        // Simple erosion pass
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

        // --- Soiling (place netherrack, bedrock at bottom) ---
        for (int dx = 0; dx < 16; ++dx) {
            int wx = (chunkX << 4) + dx;
            for (int dz = 0; dz < 16; ++dz) {
                int wz = (chunkZ << 4) + dz;
                int idxH = dx + dz * 16;
                int base = heights[idxH] + NETHER_FLOOR; // top of netherrack band
                int extra = (int)(this.soilNoise.compute(wx, wz) / 24.0D) - 4;
                int stoneTop = base + extra; // top of netherrack band
                if (stoneTop > CLASSIC_HEIGHT - 2) stoneTop = CLASSIC_HEIGHT - 2;
                if (stoneTop < 1) stoneTop = 1;
                if (base < 1) base = 1;
                topSolidY[idxH] = (stoneTop > base ? stoneTop : base);

                for (int y = 0; y < CLASSIC_HEIGHT; ++y) {
                    int index = (dx << 11) | (dz << 7) | y;
                    int id = 0;
                    if (y <= base) id = Block.NETHERRACK.id;
                    if (y <= stoneTop) id = Block.NETHERRACK.id;
                    // Base layer is bedrock
                    if (y == 0) id = Block.BEDROCK.id;
                    blocks[index] = (byte)id;
                }
            }
        }

        // Lava fill: below lava level, fill air with still lava
        for (int dx = 0; dx < 16; ++dx) {
            for (int dz = 0; dz < 16; ++dz) {
                for (int y = 1; y < LAVA_LEVEL; ++y) {
                    int index = (dx << 11) | (dz << 7) | y;
                    if (blocks[index] == 0) {
                        blocks[index] = (byte)Block.STATIONARY_LAVA.id;
                    }
                }
            }
        }

        // Surface pass: add soul sand patches (similar to sand/gravel in overworld)
        for (int dx = 0; dx < 16; ++dx) {
            int wx = (chunkX << 4) + dx;
            for (int dz = 0; dz < 16; ++dz) {
                int wz = (chunkZ << 4) + dz;
                boolean soulSand = growNoiseA.compute(wx, wz) > 8.0D;   // Similar to beach noise

                int idxCol = dx + dz * 16;
                int topY = topSolidY[idxCol];
                if (topY <= 0) continue;
                int topIndex = (dx << 11) | (dz << 7) | topY;

                boolean underLava = topY < LAVA_LEVEL - 1;
                int surfId = Block.NETHERRACK.id;
                if (underLava || (topY <= CLASSIC_HEIGHT / 2 - 1 && soulSand)) {
                    surfId = Block.SOUL_SAND.id; // Soul sand
                }

                // Set the surface block
                blocks[topIndex] = (byte)surfId;
            }
        }

        // Build border walls: bedrock base, lava sea above
        for (int dx = 0; dx < 16; ++dx) {
            int wx = (chunkX << 4) + dx;
            for (int dz = 0; dz < 16; ++dz) {
                int wz = (chunkZ << 4) + dz;
                boolean edgeWall = (wx == 0 || wx == 255 || wz == 0 || wz == 255);
                boolean edgeLava = (wx == 1 || wx == 254 || wz == 1 || wz == 254);
                if (edgeWall) {
                    // Bedrock wall up to lava level - 2
                    int bedrockTop = Math.max(0, LAVA_LEVEL - 2);
                    for (int y = 0; y <= bedrockTop; ++y) {
                        int index = (dx << 11) | (dz << 7) | y;
                        blocks[index] = (byte)Block.BEDROCK.id;
                    }
                    // Lava above bedrock
                    for (int y = bedrockTop + 1; y < LAVA_LEVEL; ++y) {
                        int index = (dx << 11) | (dz << 7) | y;
                        if (blocks[index] == 0) {
                            blocks[index] = (byte)Block.STATIONARY_LAVA.id;
                        }
                    }
                } else if (edgeLava) {
                    // Lava sea border
                    for (int y = 1; y < LAVA_LEVEL; ++y) {
                        int index = (dx << 11) | (dz << 7) | y;
                        if (blocks[index] == 0) {
                            blocks[index] = (byte)Block.STATIONARY_LAVA.id;
                        }
                    }
                }
            }
        }

        // Build full ceiling across entire 256x256 area: bedrock at top, netherrack layer below
        // Then add small lava fountains flowing downward
        // This runs AFTER border walls so the ceiling covers everything, including borders
        for (int dx = 0; dx < 16; ++dx) {
            int wx = (chunkX << 4) + dx;
            for (int dz = 0; dz < 16; ++dz) {
                int wz = (chunkZ << 4) + dz;
                
                // Bedrock ceiling at Y=127 (top of world) - covers entire 256x256 area
                int bedrockIndex = (dx << 11) | (dz << 7) | 127;
                blocks[bedrockIndex] = (byte)Block.BEDROCK.id;
                
                // Netherrack layer at Y=126 (thin ceiling layer) - covers entire 256x256 area
                int netherrackIndex = (dx << 11) | (dz << 7) | 126;
                blocks[netherrackIndex] = (byte)Block.NETHERRACK.id;
                
                // Small 1-block wide lava waterfalls: use extremely tight noise range for very sparse placement
                // Create small waterfalls that flow down 2-4 blocks from the ceiling
                double fountainNoise = this.beachNoise.compute(wx * 0.5, wz * 0.5);
                // Extremely narrow noise range for very rare single-block sources (~0.05% of blocks)
                if (fountainNoise > 6.48D && fountainNoise < 6.52D) { // Extremely rare single-block waterfalls
                    // Place single lava source block in the netherrack ceiling
                    blocks[netherrackIndex] = (byte)Block.STATIONARY_LAVA.id;
                    
                    // Flow lava downward in a single vertical column (1 block wide)
                    // Small waterfall: 2-4 blocks tall
                    int fountainHeight = 2 + (int)(Math.abs(fountainNoise - 6.5) * 10.0) % 3; // 2-4 blocks
                    for (int drop = 1; drop <= fountainHeight && (126 - drop) >= 0; ++drop) {
                        int dropIndex = (dx << 11) | (dz << 7) | (126 - drop);
                        // Only place lava if it's air - keep it small and contained
                        if (blocks[dropIndex] == 0) {
                            blocks[dropIndex] = (byte)Block.LAVA.id; // Flowing lava (single column)
                        } else {
                            break; // Stop if we hit solid block
                        }
                    }
                }
            }
        }

        Chunk chunk = new Chunk(this.worldObj, blocks, chunkX, chunkZ);
        chunk.initLighting();
        
        // Calculate block light from lava (lava emits light level 15)
        // This is critical for Nether since there's no skylight
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        boolean hasLava = false;
        int minY = 128, maxY = 0;
        
        // First pass: set light values for all lava blocks and propagate light to nearby blocks
        for (int dx = 0; dx < 16; ++dx) {
            for (int dz = 0; dz < 16; ++dz) {
                for (int y = 0; y < 128; ++y) {
                    int blockId = blocks[(dx << 11) | (dz << 7) | y] & 255;
                    if (blockId == Block.STATIONARY_LAVA.id || blockId == Block.LAVA.id) {
                        // Set block light to full brightness for lava
                        chunk.a(EnumSkyBlock.BLOCK, dx, y, dz, 15);
                        hasLava = true;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                        
                        // Manually propagate light to nearby blocks (within chunk bounds)
                        // This ensures netherrack, soul sand, etc. near lava are properly lit
                        for (int ox = -1; ox <= 1; ++ox) {
                            for (int oy = -1; oy <= 1; ++oy) {
                                for (int oz = -1; oz <= 1; ++oz) {
                                    if (ox == 0 && oy == 0 && oz == 0) continue; // Skip the lava block itself
                                    int nx = dx + ox;
                                    int ny = y + oy;
                                    int nz = dz + oz;
                                    
                                    // Only process if within chunk bounds
                                    if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16 && ny >= 0 && ny < 128) {
                                        int neighborBlockId = blocks[(nx << 11) | (nz << 7) | ny] & 255;
                                        // Only set light for non-lava blocks (netherrack, soul sand, air, etc.)
                                        if (neighborBlockId != Block.STATIONARY_LAVA.id && neighborBlockId != Block.LAVA.id) {
                                            // Calculate light level based on distance (lava emits 15, decreases by opacity)
                                            int distance = Math.abs(ox) + Math.abs(oy) + Math.abs(oz);
                                            int lightLevel = Math.max(0, 15 - distance - Block.q[neighborBlockId]);
                                            int currentLight = chunk.a(EnumSkyBlock.BLOCK, nx, ny, nz);
                                            // Set light if it's brighter than current
                                            if (lightLevel > currentLight) {
                                                chunk.a(EnumSkyBlock.BLOCK, nx, ny, nz, lightLevel);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Schedule lighting updates for the entire chunk region to ensure light propagates
        // Expand the update area slightly to ensure proper light propagation to nearby blocks
        // This ensures netherrack, soul sand, and other blocks near lava are properly lit
        if (hasLava) {
            // Expand update area to include surrounding blocks for proper light propagation
            int expandedMinY = Math.max(0, minY - 2);
            int expandedMaxY = Math.min(127, maxY + 15); // Lava light travels up to 15 blocks
            // Schedule updates for current chunk with expanded Y range to ensure all blocks get lit
            this.worldObj.a(EnumSkyBlock.BLOCK, baseX, expandedMinY, baseZ, baseX + 15, expandedMaxY, baseZ + 15);
        } else {
            // Even if no lava in this chunk, schedule a lighting update to ensure ambient light works
            // In Nether, we need at least some lighting calculation
            this.worldObj.a(EnumSkyBlock.BLOCK, baseX, 0, baseZ, baseX + 15, 127, baseZ + 15);
        }
        
        return chunk;
    }

    public void populate(IChunkProvider chunkProvider, int chunkX, int chunkZ) {
        // Do not populate outside classic bounds
        if (chunkX < 0 || chunkZ < 0 || chunkX > 15 || chunkZ > 15) {
            return;
        }
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        this.rand.setSeed(this.worldObj.getSeed());
        long l1 = this.rand.nextLong() / 2L * 2L + 1L;
        long l2 = this.rand.nextLong() / 2L * 2L + 1L;
        this.rand.setSeed((long)chunkX * l1 + (long)chunkZ * l2 ^ this.worldObj.getSeed());

        // Lava pools
        LegacyPlacedFeatureExecutor.generate(
                this.worldObj, this.rand, baseX + 8, baseZ + 8,
                PlacedFeatureDataBootstrap.SPRING_LAVA_CLASSIC_HELL);
    }

    public boolean isChunkLoaded(int x, int z) { return true; }
    public boolean saveChunks(boolean flag, IProgressUpdate progress) { return true; }
    public boolean unloadChunks() { return false; }
    public boolean canSave() { return true; }
    public String makeString() { return "ClassicHellLevelSource"; }
    public Chunk getChunkAt(int x, int z) { return getOrCreateChunk(x, z); }
    public void getChunkAt(IChunkProvider ichunkprovider, int x, int z) { this.populate(ichunkprovider, x, z); }
    public Chunk prepareChunk(int x, int z) { return getOrCreateChunk(x, z); }

    private Chunk makeLavaBorderChunk(int chunkX, int chunkZ, boolean withWaterfalls) {
        byte[] blocks = new byte[16 * 16 * 128];
        int bedrockTop = Math.max(0, LAVA_LEVEL - 3);
        for (int dx = 0; dx < 16; ++dx) {
            for (int dz = 0; dz < 16; ++dz) {
                int wx = (chunkX << 4) + dx;
                int wz = (chunkZ << 4) + dz;
                
                // Bedrock base up to lava level - 3
                for (int y = 0; y <= bedrockTop; ++y) {
                    blocks[(dx << 11) | (dz << 7) | y] = (byte)Block.BEDROCK.id;
                }
                // Lava sea up to lava level
                for (int y = bedrockTop + 1; y < LAVA_LEVEL; ++y) {
                    blocks[(dx << 11) | (dz << 7) | y] = (byte)Block.STATIONARY_LAVA.id;
                }
                // Bedrock ceiling at top, netherrack layer below (mini-Nether ceiling)
                // This ensures even border chunks have the ceiling structure
                blocks[(dx << 11) | (dz << 7) | 127] = (byte)Block.BEDROCK.id;
                blocks[(dx << 11) | (dz << 7) | 126] = (byte)Block.NETHERRACK.id;
                
                // Pre-place/calculate small 1-block wide lava waterfalls for chunks within 12 chunks of 256x256 area
                if (withWaterfalls) {
                    // Use same extremely tight noise pattern as main area for very sparse, small waterfalls
                    double fountainNoise = this.beachNoise.compute(wx * 0.5, wz * 0.5);
                    // Extremely narrow range for very rare single-block waterfalls (~0.05% of blocks)
                    if (fountainNoise > 6.48D && fountainNoise < 6.52D) {
                        // Place single lava source block in the netherrack ceiling
                        blocks[(dx << 11) | (dz << 7) | 126] = (byte)Block.STATIONARY_LAVA.id;
                        
                        // Flow lava downward in a single vertical column (1 block wide, 2-4 blocks tall)
                        int fountainHeight = 2 + (int)(Math.abs(fountainNoise - 6.5) * 10.0) % 3;
                        for (int drop = 1; drop <= fountainHeight && (126 - drop) >= 0; ++drop) {
                            int dropIndex = (dx << 11) | (dz << 7) | (126 - drop);
                            // Only place lava if it's air - keep waterfalls small and contained
                            if (blocks[dropIndex] == 0) {
                                blocks[dropIndex] = (byte)Block.LAVA.id; // Single column of flowing lava
                            } else {
                                break; // Stop if we hit solid block
                            }
                        }
                    }
                }
            }
        }
        Chunk chunk = new Chunk(this.worldObj, blocks, chunkX, chunkZ);
        chunk.initLighting();
        
        // Calculate block light from lava for border chunks too
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        boolean hasLava = false;
        int minY = 128, maxY = 0;
        
        for (int dx = 0; dx < 16; ++dx) {
            for (int dz = 0; dz < 16; ++dz) {
                for (int y = 0; y < 128; ++y) {
                    int blockId = blocks[(dx << 11) | (dz << 7) | y] & 255;
                    if (blockId == Block.STATIONARY_LAVA.id || blockId == Block.LAVA.id) {
                        chunk.a(EnumSkyBlock.BLOCK, dx, y, dz, 15);
                        hasLava = true;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                        
                        // Manually propagate light to nearby blocks (within chunk bounds)
                        // This ensures netherrack, soul sand, etc. near lava are properly lit
                        for (int ox = -1; ox <= 1; ++ox) {
                            for (int oy = -1; oy <= 1; ++oy) {
                                for (int oz = -1; oz <= 1; ++oz) {
                                    if (ox == 0 && oy == 0 && oz == 0) continue;
                                    int nx = dx + ox;
                                    int ny = y + oy;
                                    int nz = dz + oz;
                                    
                                    if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16 && ny >= 0 && ny < 128) {
                                        int neighborBlockId = blocks[(nx << 11) | (nz << 7) | ny] & 255;
                                        if (neighborBlockId != Block.STATIONARY_LAVA.id && neighborBlockId != Block.LAVA.id) {
                                            int distance = Math.abs(ox) + Math.abs(oy) + Math.abs(oz);
                                            int lightLevel = Math.max(0, 15 - distance - Block.q[neighborBlockId]);
                                            int currentLight = chunk.a(EnumSkyBlock.BLOCK, nx, ny, nz);
                                            if (lightLevel > currentLight) {
                                                chunk.a(EnumSkyBlock.BLOCK, nx, ny, nz, lightLevel);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Schedule lighting updates with expanded area for proper light propagation
        if (hasLava) {
            int expandedMinY = Math.max(0, minY - 2);
            int expandedMaxY = Math.min(127, maxY + 15);
            this.worldObj.a(EnumSkyBlock.BLOCK, baseX, expandedMinY, baseZ, baseX + 15, expandedMaxY, baseZ + 15);
        } else {
            // Even border chunks need lighting updates
            this.worldObj.a(EnumSkyBlock.BLOCK, baseX, 0, baseZ, baseX + 15, 127, baseZ + 15);
        }
        
        return chunk;
    }

    // --- Classic noise implementations (reuse from ChunkProviderClassic) ---
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
}
