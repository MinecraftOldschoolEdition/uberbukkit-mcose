package net.minecraft.server;

import java.util.Random;
import net.minecraft.server.registry.ConfiguredFeatureDataBootstrap;
import net.minecraft.server.registry.LegacyPlacedFeatureExecutor;

public class ChunkProviderNetherSky implements IChunkProvider {
    private static final int LAVA_SPRING_ATTEMPTS = 3;
    private static final int COMMON_HANGING_GLOWSTONE_ATTEMPTS = 4;
    private static final int COMMON_GLOWSTONE_CLUSTER_STEPS = 450;
    private static final int RARE_GLOWSTONE_CLUSTER_STEPS = 1050;
    private static final double SOUL_SAND_PATCH_THRESHOLD = 0.35D;
    private static final double GRAVEL_PATCH_THRESHOLD = 0.32D;

    private final World world;
    private final Random random;
    private final ChunkProviderSky skyShapeProvider;
    private final NoiseGeneratorOctaves soulSandNoise;
    private final NoiseGeneratorOctaves gravelNoise;
    private final NoiseGeneratorOctaves surfaceDepthNoise;
    private double[] soulSandBuffer = new double[256];
    private double[] gravelBuffer = new double[256];
    private double[] surfaceDepthBuffer = new double[256];

    public ChunkProviderNetherSky(World world, long seed) {
        this.world = world;
        this.random = new Random(seed);
        this.skyShapeProvider = new ChunkProviderSky(world, seed);
        this.soulSandNoise = new NoiseGeneratorOctaves(this.random, 4);
        this.gravelNoise = new NoiseGeneratorOctaves(this.random, 4);
        this.surfaceDepthNoise = new NoiseGeneratorOctaves(this.random, 4);
    }

    public boolean isChunkLoaded(int i, int j) {
        return true;
    }

    public Chunk getChunkAt(int i, int j) {
        return this.getOrCreateChunk(i, j);
    }

    public Chunk getOrCreateChunk(int i, int j) {
        this.random.setSeed((long)i * 341873128712L + (long)j * 132897987541L);
        Chunk chunk = this.skyShapeProvider.getOrCreateChunk(i, j);
        this.remapSkyTerrainToNether(chunk.b);
        this.replaceSurfaceBlocks(i, j, chunk.b);
        chunk.initLighting();
        return chunk;
    }

    private void remapSkyTerrainToNether(byte[] blocks) {
        for (int i = 0; i < blocks.length; ++i) {
            if ((blocks[i] & 255) != 0) {
                blocks[i] = (byte)Block.NETHERRACK.id;
            }
        }
    }

    private void replaceSurfaceBlocks(int chunkX, int chunkZ, byte[] blocks) {
        double noiseScale = 1.0D / 32.0D;
        this.soulSandBuffer = this.soulSandNoise.a(this.soulSandBuffer, (double)(chunkX * 16), (double)(chunkZ * 16), 0.0D, 16, 16, 1, noiseScale, noiseScale, 1.0D);
        this.gravelBuffer = this.gravelNoise.a(this.gravelBuffer, (double)(chunkX * 16), 109.0134D, (double)(chunkZ * 16), 16, 1, 16, noiseScale, 1.0D, noiseScale);
        this.surfaceDepthBuffer = this.surfaceDepthNoise.a(this.surfaceDepthBuffer, (double)(chunkX * 16), (double)(chunkZ * 16), 0.0D, 16, 16, 1, noiseScale * 2.0D, noiseScale * 2.0D, noiseScale * 2.0D);

        for (int localX = 0; localX < 16; ++localX) {
            for (int localZ = 0; localZ < 16; ++localZ) {
                int noiseIndex = localX + localZ * 16;
                boolean soulSandPatch = this.soulSandBuffer[noiseIndex] + this.random.nextDouble() * 0.2D > SOUL_SAND_PATCH_THRESHOLD;
                boolean gravelPatch = this.gravelBuffer[noiseIndex] + this.random.nextDouble() * 0.2D > GRAVEL_PATCH_THRESHOLD;
                int surfaceDepth = (int)(this.surfaceDepthBuffer[noiseIndex] / 3.0D + 3.0D + this.random.nextDouble() * 0.25D);
                int remainingDepth = -1;
                byte topBlock = (byte)Block.NETHERRACK.id;
                byte fillerBlock = (byte)Block.NETHERRACK.id;

                if (gravelPatch) {
                    topBlock = (byte)Block.GRAVEL.id;
                }
                if (soulSandPatch) {
                    topBlock = (byte)Block.SOUL_SAND.id;
                    fillerBlock = (byte)Block.SOUL_SAND.id;
                }

                for (int y = 127; y >= 0; --y) {
                    int blockIndex = (localZ * 16 + localX) * 128 + y;
                    if ((blocks[blockIndex] & 255) == 0) {
                        remainingDepth = -1;
                    } else if ((blocks[blockIndex] & 255) == Block.NETHERRACK.id) {
                        if (remainingDepth == -1) {
                            remainingDepth = surfaceDepth;
                            blocks[blockIndex] = topBlock;
                        } else if (remainingDepth > 0) {
                            --remainingDepth;
                            blocks[blockIndex] = fillerBlock;
                        }
                    }
                }
            }
        }
    }

    public void getChunkAt(IChunkProvider provider, int chunkX, int chunkZ) {
        BlockSand.instaFall = true;
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        this.random.setSeed(this.world.getSeed());
        long xSeed = this.random.nextLong() / 2L * 2L + 1L;
        long zSeed = this.random.nextLong() / 2L * 2L + 1L;
        this.random.setSeed((long)chunkX * xSeed + (long)chunkZ * zSeed ^ this.world.getSeed());

        for (int i = 0; i < LAVA_SPRING_ATTEMPTS; ++i) {
            int x = baseX + this.random.nextInt(16) + 8;
            int z = baseZ + this.random.nextInt(16) + 8;
            int y = this.findInteriorFeatureY(x, z);
            if (y > 0) {
                LegacyPlacedFeatureExecutor.generateSpringAt(
                        this.world, this.random, x, y, z,
                        ConfiguredFeatureDataBootstrap.SPRING_NETHER_OPEN);
            }
        }

        int fireClusters = this.random.nextInt(this.random.nextInt(6) + 1) + 1;
        for (int i = 0; i < fireClusters; ++i) {
            int x = baseX + this.random.nextInt(16) + 8;
            int z = baseZ + this.random.nextInt(16) + 8;
            int y = this.findSurfaceFeatureY(x, z);
            if (y > 0) {
                (new WorldGenFire()).a(this.world, this.random, x, y, z);
            }
        }

        int rareGlowstoneClusters = this.random.nextInt(this.random.nextInt(6) + 1);
        for (int i = 0; i < rareGlowstoneClusters; ++i) {
            int x = baseX + this.random.nextInt(16) + 8;
            int z = baseZ + this.random.nextInt(16) + 8;
            int y = this.findUndersideFeatureY(x, z);
            if (y > 0) {
                this.generateHangingGlowstone(x, y, z, RARE_GLOWSTONE_CLUSTER_STEPS);
            }
        }

        for (int i = 0; i < COMMON_HANGING_GLOWSTONE_ATTEMPTS; ++i) {
            int x = baseX + this.random.nextInt(16) + 8;
            int z = baseZ + this.random.nextInt(16) + 8;
            int y = this.findUndersideFeatureY(x, z);
            if (y > 0) {
                this.generateHangingGlowstone(x, y, z, COMMON_GLOWSTONE_CLUSTER_STEPS);
            }
        }

        this.generateMushroom(baseX, baseZ, "minecraft:brown_mushroom");
        this.generateMushroom(baseX, baseZ, "minecraft:red_mushroom");
        BlockSand.instaFall = false;
    }

    private void generateMushroom(int baseX, int baseZ, String blockId) {
        if (this.random.nextInt(2) != 0) {
            return;
        }

        int x = baseX + this.random.nextInt(16) + 8;
        int z = baseZ + this.random.nextInt(16) + 8;
        int y = this.findSurfaceFeatureY(x, z);
        if (y > 0) {
            (new WorldGenFlowers(blockId)).a(this.world, this.random, x, y, z);
        }
    }

    private int findSurfaceFeatureY(int x, int z) {
        for (int y = 126; y >= 2; --y) {
            if (this.world.isEmpty(x, y, z) && this.isNetherSkyIslandBlock(this.world.getTypeId(x, y - 1, z))) {
                return y;
            }
        }

        return -1;
    }

    private int findInteriorFeatureY(int x, int z) {
        for (int attempt = 0; attempt < 16; ++attempt) {
            int y = this.random.nextInt(96) + 16;
            if ((this.world.isEmpty(x, y, z) || this.world.getTypeId(x, y, z) == Block.NETHERRACK.id)
                    && this.world.getTypeId(x, y + 1, z) == Block.NETHERRACK.id) {
                return y;
            }
        }

        return this.findUndersideFeatureY(x, z);
    }

    private int findUndersideFeatureY(int x, int z) {
        for (int y = 118; y >= 8; --y) {
            if (this.isNetherSkyIslandBlock(this.world.getTypeId(x, y, z)) && this.world.isEmpty(x, y - 1, z)) {
                return y - 1;
            }
        }

        return -1;
    }

    private boolean isNetherSkyIslandBlock(int blockId) {
        return blockId == Block.NETHERRACK.id
                || blockId == Block.SOUL_SAND.id
                || blockId == Block.GRAVEL.id;
    }

    private void generateHangingGlowstone(int x, int y, int z, int clusterSteps) {
        if (!this.world.isEmpty(x, y, z) || !this.isNetherSkyIslandBlock(this.world.getTypeId(x, y + 1, z))) {
            return;
        }

        this.world.setBlockStateAndData(x, y, z, "minecraft:glowstone");
        for (int i = 0; i < clusterSteps; ++i) {
            int glowX = x + this.random.nextInt(8) - this.random.nextInt(8);
            int glowY = y - this.random.nextInt(12);
            int glowZ = z + this.random.nextInt(8) - this.random.nextInt(8);
            if (glowY <= 1 || glowY >= 127 || !this.world.isEmpty(glowX, glowY, glowZ)) {
                continue;
            }

            int adjacentGlowstone = 0;
            if (this.world.getTypeId(glowX - 1, glowY, glowZ) == Block.GLOWSTONE.id) {
                ++adjacentGlowstone;
            }
            if (this.world.getTypeId(glowX + 1, glowY, glowZ) == Block.GLOWSTONE.id) {
                ++adjacentGlowstone;
            }
            if (this.world.getTypeId(glowX, glowY - 1, glowZ) == Block.GLOWSTONE.id) {
                ++adjacentGlowstone;
            }
            if (this.world.getTypeId(glowX, glowY + 1, glowZ) == Block.GLOWSTONE.id) {
                ++adjacentGlowstone;
            }
            if (this.world.getTypeId(glowX, glowY, glowZ - 1) == Block.GLOWSTONE.id) {
                ++adjacentGlowstone;
            }
            if (this.world.getTypeId(glowX, glowY, glowZ + 1) == Block.GLOWSTONE.id) {
                ++adjacentGlowstone;
            }

            if (adjacentGlowstone == 1) {
                this.world.setBlockStateAndData(glowX, glowY, glowZ, "minecraft:glowstone");
            }
        }
    }

    public boolean saveChunks(boolean flag, IProgressUpdate progressUpdate) {
        return true;
    }

    public boolean unloadChunks() {
        return false;
    }

    public boolean canSave() {
        return true;
    }
}
