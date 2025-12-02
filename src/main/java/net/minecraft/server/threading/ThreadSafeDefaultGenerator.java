package net.minecraft.server.threading;

import net.minecraft.server.*;

import java.util.Random;

/**
 * Thread-safe terrain generator for DEFAULT world type.
 * Creates all noise generators per-call to avoid shared state.
 * Does NOT access World or any shared mutable state.
 */
public class ThreadSafeDefaultGenerator implements IThreadSafeGenerator {
    
    @Override
    public String getGeneratorName() {
        return "ThreadSafeDefault";
    }
    
    @Override
    public ChunkGenerationData generateChunkData(long seed, int chunkX, int chunkZ) {
        // Create thread-local Random seeded deterministically
        Random random = new Random(seed);
        
        // Create thread-local noise generators
        NoiseGeneratorOctaves noiseGen1 = new NoiseGeneratorOctaves(random, 16);
        NoiseGeneratorOctaves noiseGen2 = new NoiseGeneratorOctaves(random, 16);
        NoiseGeneratorOctaves noiseGen3 = new NoiseGeneratorOctaves(random, 8);
        NoiseGeneratorOctaves noiseGen4 = new NoiseGeneratorOctaves(random, 4);
        NoiseGeneratorOctaves noiseGen5 = new NoiseGeneratorOctaves(random, 4);
        NoiseGeneratorOctaves noiseGenA = new NoiseGeneratorOctaves(random, 10);
        NoiseGeneratorOctaves noiseGenB = new NoiseGeneratorOctaves(random, 16);
        
        // Per-chunk random seeded from chunk coordinates
        long chunkSeed = (long) chunkX * 341873128712L + (long) chunkZ * 132897987541L + seed;
        Random chunkRandom = new Random(chunkSeed);
        
        // Allocate block data
        byte[] blocks = new byte[32768];
        byte[] metadata = new byte[16384];
        byte[] heightMap = new byte[256];
        
        // Generate base terrain shape
        generateTerrainShape(chunkX, chunkZ, blocks, noiseGen1, noiseGen2, noiseGen3, noiseGenA, noiseGenB);
        
        // Apply surface (grass, sand, etc.) - simplified version without biome access
        applySurface(chunkX, chunkZ, blocks, chunkRandom, noiseGen4, noiseGen5);
        
        // Calculate heightmap
        calculateHeightmap(blocks, heightMap);
        
        // Create biome data (simplified - all zeros, biomes will be determined by WorldChunkManager on main thread)
        byte[] biomes = new byte[256];
        // Biome IDs are not used in the same way in Beta 1.7.3 - leave as zeros
        
        // Skylight will be calculated on main thread
        byte[] skyLight = new byte[16384];
        byte[] blockLight = new byte[16384];
        
        return new ChunkGenerationData(
            chunkX, chunkZ,
            blocks, metadata,
            skyLight, blockLight,
            heightMap, biomes,
            false // Not populated yet
        );
    }
    
    private void generateTerrainShape(int chunkX, int chunkZ, byte[] blocks,
                                       NoiseGeneratorOctaves n1, NoiseGeneratorOctaves n2,
                                       NoiseGeneratorOctaves n3, NoiseGeneratorOctaves nA,
                                       NoiseGeneratorOctaves nB) {
        byte seaLevel = 64;
        int b0 = 4;
        byte b2 = 17;
        int l = b0 + 1;
        
        // Generate density values
        double[] densityMap = generateDensityMap(chunkX * b0, 0, chunkZ * b0, 
                                                  l, b2, l, n1, n2, n3, nA, nB);
        
        // Interpolate and fill blocks
        for (int i1 = 0; i1 < b0; ++i1) {
            for (int j1 = 0; j1 < b0; ++j1) {
                for (int k1 = 0; k1 < 16; ++k1) {
                    double d0 = 0.125D;
                    double d1 = densityMap[((i1 + 0) * l + j1 + 0) * b2 + k1 + 0];
                    double d2 = densityMap[((i1 + 0) * l + j1 + 1) * b2 + k1 + 0];
                    double d3 = densityMap[((i1 + 1) * l + j1 + 0) * b2 + k1 + 0];
                    double d4 = densityMap[((i1 + 1) * l + j1 + 1) * b2 + k1 + 0];
                    double d5 = (densityMap[((i1 + 0) * l + j1 + 0) * b2 + k1 + 1] - d1) * d0;
                    double d6 = (densityMap[((i1 + 0) * l + j1 + 1) * b2 + k1 + 1] - d2) * d0;
                    double d7 = (densityMap[((i1 + 1) * l + j1 + 0) * b2 + k1 + 1] - d3) * d0;
                    double d8 = (densityMap[((i1 + 1) * l + j1 + 1) * b2 + k1 + 1] - d4) * d0;

                    for (int l1 = 0; l1 < 8; ++l1) {
                        double d9 = 0.25D;
                        double d10 = d1;
                        double d11 = d2;
                        double d12 = (d3 - d1) * d9;
                        double d13 = (d4 - d2) * d9;

                        for (int i2 = 0; i2 < 4; ++i2) {
                            int j2 = i2 + i1 * 4 << 11 | 0 + j1 * 4 << 7 | k1 * 8 + l1;
                            short short1 = 128;
                            double d14 = 0.25D;
                            double d15 = d10;
                            double d16 = (d11 - d10) * d14;

                            for (int k2 = 0; k2 < 4; ++k2) {
                                int blockId = 0;

                                if (k1 * 8 + l1 < seaLevel) {
                                    blockId = Block.STATIONARY_WATER.id;
                                }

                                if (d15 > 0.0D) {
                                    blockId = Block.STONE.id;
                                }

                                blocks[j2] = (byte) blockId;
                                j2 += short1;
                                d15 += d16;
                            }

                            d10 += d12;
                            d11 += d13;
                        }

                        d1 += d5;
                        d2 += d6;
                        d3 += d7;
                        d4 += d8;
                    }
                }
            }
        }
    }
    
    private double[] generateDensityMap(int x, int y, int z, int sizeX, int sizeY, int sizeZ,
                                         NoiseGeneratorOctaves n1, NoiseGeneratorOctaves n2,
                                         NoiseGeneratorOctaves n3, NoiseGeneratorOctaves nA,
                                         NoiseGeneratorOctaves nB) {
        double[] result = new double[sizeX * sizeY * sizeZ];
        
        double scaleXZ = 684.412D;
        double scaleY = 684.412D;
        
        double[] noise1 = n1.a(null, x, y, z, sizeX, sizeY, sizeZ, scaleXZ, scaleY, scaleXZ);
        double[] noise2 = n2.a(null, x, y, z, sizeX, sizeY, sizeZ, scaleXZ, scaleY, scaleXZ);
        double[] noise3 = n3.a(null, x, y, z, sizeX, sizeY, sizeZ, scaleXZ / 80.0D, scaleY / 160.0D, scaleXZ / 80.0D);
        double[] noiseA = nA.a(null, x, z, sizeX, sizeZ, 1.121D, 1.121D, 0.5D);
        double[] noiseB = nB.a(null, x, z, sizeX, sizeZ, 200.0D, 200.0D, 0.5D);
        
        int idx = 0;
        int idx2 = 0;
        
        for (int ix = 0; ix < sizeX; ++ix) {
            for (int iz = 0; iz < sizeZ; ++iz) {
                double depthVal = (noiseA[idx2] + 256.0D) / 512.0D;
                if (depthVal > 1.0D) depthVal = 1.0D;
                
                double scaleVal = noiseB[idx2] / 8000.0D;
                if (scaleVal < 0.0D) scaleVal = -scaleVal * 0.3D;
                scaleVal = scaleVal * 3.0D - 2.0D;
                if (scaleVal < 0.0D) {
                    scaleVal /= 2.0D;
                    if (scaleVal < -1.0D) scaleVal = -1.0D;
                    scaleVal /= 1.4D;
                    scaleVal /= 2.0D;
                } else {
                    if (scaleVal > 1.0D) scaleVal = 1.0D;
                    scaleVal /= 8.0D;
                }
                
                ++idx2;
                
                for (int iy = 0; iy < sizeY; ++iy) {
                    double baseVal = 8.0D;
                    double yOffset = ((double) iy - baseVal) * 12.0D / depthVal;
                    if (yOffset < 0.0D) yOffset *= 4.0D;
                    
                    double n1Val = noise1[idx] / 512.0D;
                    double n2Val = noise2[idx] / 512.0D;
                    double n3Val = (noise3[idx] / 10.0D + 1.0D) / 2.0D;
                    
                    double density;
                    if (n3Val < 0.0D) {
                        density = n1Val;
                    } else if (n3Val > 1.0D) {
                        density = n2Val;
                    } else {
                        density = n1Val + (n2Val - n1Val) * n3Val;
                    }
                    
                    density -= yOffset;
                    
                    if (iy > sizeY - 4) {
                        double fadeVal = (double) ((float) (iy - (sizeY - 4)) / 3.0F);
                        density = density * (1.0D - fadeVal) + -10.0D * fadeVal;
                    }
                    
                    result[idx] = density;
                    ++idx;
                }
            }
        }
        
        return result;
    }
    
    private void applySurface(int chunkX, int chunkZ, byte[] blocks, Random random,
                               NoiseGeneratorOctaves n4, NoiseGeneratorOctaves n5) {
        byte seaLevel = 64;
        double d0 = 0.03125D;
        
        double[] sandNoise = n4.a(null, (double) (chunkX * 16), (double) (chunkZ * 16), 0.0D, 16, 16, 1, d0, d0, 1.0D);
        double[] gravelNoise = n4.a(null, (double) (chunkX * 16), 109.0134D, (double) (chunkZ * 16), 16, 1, 16, d0, 1.0D, d0);
        double[] depthNoise = n5.a(null, (double) (chunkX * 16), (double) (chunkZ * 16), 0.0D, 16, 16, 1, d0 * 2.0D, d0 * 2.0D, d0 * 2.0D);

        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                boolean useSand = sandNoise[x + z * 16] + random.nextDouble() * 0.2D > 0.0D;
                boolean useGravel = gravelNoise[x + z * 16] + random.nextDouble() * 0.2D > 3.0D;
                int depth = (int) (depthNoise[x + z * 16] / 3.0D + 3.0D + random.nextDouble() * 0.25D);
                int surfaceDepth = -1;
                
                byte topBlock = (byte) Block.GRASS.id;
                byte fillerBlock = (byte) Block.DIRT.id;

                for (int y = 127; y >= 0; --y) {
                    int idx = (z * 16 + x) * 128 + y;

                    if (y <= 0 + random.nextInt(5)) {
                        blocks[idx] = (byte) Block.BEDROCK.id;
                    } else {
                        byte block = blocks[idx];

                        if (block == 0) {
                            surfaceDepth = -1;
                        } else if (block == Block.STONE.id) {
                            if (surfaceDepth == -1) {
                                if (depth <= 0) {
                                    topBlock = 0;
                                    fillerBlock = (byte) Block.STONE.id;
                                } else if (y >= seaLevel - 4 && y <= seaLevel + 1) {
                                    topBlock = (byte) Block.GRASS.id;
                                    fillerBlock = (byte) Block.DIRT.id;
                                    if (useGravel) {
                                        topBlock = 0;
                                        fillerBlock = (byte) Block.GRAVEL.id;
                                    }
                                    if (useSand) {
                                        topBlock = (byte) Block.SAND.id;
                                        fillerBlock = (byte) Block.SAND.id;
                                    }
                                }

                                if (y < seaLevel && topBlock == 0) {
                                    topBlock = (byte) Block.STATIONARY_WATER.id;
                                }

                                surfaceDepth = depth;
                                if (y >= seaLevel - 1) {
                                    blocks[idx] = topBlock;
                                } else {
                                    blocks[idx] = fillerBlock;
                                }
                            } else if (surfaceDepth > 0) {
                                --surfaceDepth;
                                blocks[idx] = fillerBlock;

                                if (surfaceDepth == 0 && fillerBlock == Block.SAND.id) {
                                    surfaceDepth = random.nextInt(4);
                                    fillerBlock = (byte) Block.SANDSTONE.id;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void calculateHeightmap(byte[] blocks, byte[] heightMap) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int height = 0;
                for (int y = 127; y >= 0; y--) {
                    int idx = (z * 16 + x) * 128 + y;
                    if (blocks[idx] != 0) {
                        height = y + 1;
                        break;
                    }
                }
                heightMap[x + z * 16] = (byte) height;
            }
        }
    }
}

