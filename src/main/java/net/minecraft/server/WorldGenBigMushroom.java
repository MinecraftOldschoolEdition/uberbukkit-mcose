package net.minecraft.server;

import java.util.Random;
import net.minecraft.server.util.ResourceLocation;

/** 1.22-style huge brown/red mushroom feature projected through RegionCore. */
public class WorldGenBigMushroom extends WorldGenerator {
    private static final int BROWN = 0;
    private static final int RED = 1;
    private static final int BROWN_FOLIAGE_RADIUS = 3;
    private static final int RED_FOLIAGE_RADIUS = 2;
    private int type = -1;

    public WorldGenBigMushroom(int type) {
        this.type = type;
    }

    public WorldGenBigMushroom() {}

    public boolean a(World world, Random random, int x, int y, int z) {
        int mushroomType = this.type >= 0 ? this.type : random.nextInt(2);
        int height = this.getTreeHeight(random);
        if (!this.isValidPosition(world, x, y, z, mushroomType, height)) {
            return false;
        }

        if (mushroomType == BROWN) {
            this.makeBrownCap(world, x, y, z, height);
        } else {
            this.makeRedCap(world, x, y, z, height);
        }
        this.placeTrunk(world, x, y, z, height);
        return true;
    }

    private int getTreeHeight(Random random) {
        int height = random.nextInt(3) + 4;
        if (random.nextInt(12) == 0) {
            height *= 2;
        }
        return height;
    }

    private boolean isValidPosition(World world, int x, int y, int z, int mushroomType, int height) {
        if (y < 1 || y + height + 1 > 128) {
            return false;
        }

        int below = world.getTypeId(x, y - 1, z);
        if (below != Block.DIRT.id && below != Block.GRASS.id) {
            return false;
        }

        for (int dy = 0; dy <= height; ++dy) {
            int radius = this.getTreeRadiusForHeight(mushroomType, height, dy);
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    int blockId = world.getTypeId(x + dx, y + dy, z + dz);
                    if (blockId != 0 && blockId != Block.LEAVES.id) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private int getTreeRadiusForHeight(int mushroomType, int height, int dy) {
        if (mushroomType == BROWN) {
            return dy <= 3 ? 0 : BROWN_FOLIAGE_RADIUS;
        }
        return dy <= height && dy >= height - 3 ? RED_FOLIAGE_RADIUS : 0;
    }

    private void makeBrownCap(World world, int x, int y, int z, int height) {
        int radius = BROWN_FOLIAGE_RADIUS;
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                boolean minX = dx == -radius;
                boolean maxX = dx == radius;
                boolean minZ = dz == -radius;
                boolean maxZ = dz == radius;
                boolean xEdge = minX || maxX;
                boolean zEdge = minZ || maxZ;
                if (xEdge && zEdge) {
                    continue;
                }

                boolean west = minX || zEdge && dx == 1 - radius;
                boolean east = maxX || zEdge && dx == radius - 1;
                boolean north = minZ || xEdge && dz == 1 - radius;
                boolean south = maxZ || xEdge && dz == radius - 1;
                this.placeMushroomBlock(world, x + dx, y + height, z + dz,
                        mushroomState("brown_mushroom_block", north, east, south, west, true, false));
            }
        }
    }

    private void makeRedCap(World world, int x, int y, int z, int height) {
        int center = RED_FOLIAGE_RADIUS - 2;
        for (int dy = height - 3; dy <= height; ++dy) {
            int radius = dy < height ? RED_FOLIAGE_RADIUS : RED_FOLIAGE_RADIUS - 1;
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    boolean minX = dx == -radius;
                    boolean maxX = dx == radius;
                    boolean minZ = dz == -radius;
                    boolean maxZ = dz == radius;
                    boolean xEdge = minX || maxX;
                    boolean zEdge = minZ || maxZ;
                    if (dy < height && xEdge == zEdge) {
                        continue;
                    }

                    this.placeMushroomBlock(world, x + dx, y + dy, z + dz,
                            mushroomState("red_mushroom_block",
                                    dz < -center, dx > center, dz > center, dx < -center,
                                    dy >= height - 1, false));
                }
            }
        }
    }

    private void placeTrunk(World world, int x, int y, int z, int height) {
        BlockStateKey stem = mushroomState("mushroom_stem", true, true, true, true, false, false);
        for (int dy = 0; dy < height; ++dy) {
            this.placeMushroomBlock(world, x, y + dy, z, stem);
        }
    }

    private void placeMushroomBlock(World world, int x, int y, int z, BlockStateKey state) {
        int current = world.getTypeId(x, y, z);
        if (current == 0 || current == Block.LEAVES.id) {
            setGeneratedBlock(world, x, y, z, state);
        }
    }

    static BlockStateKey mushroomState(String path, boolean north, boolean east,
                                       boolean south, boolean west, boolean up, boolean down) {
        return new BlockStateKey(new ResourceLocation("minecraft", path))
                .withProperty("down", Boolean.toString(down))
                .withProperty("east", Boolean.toString(east))
                .withProperty("north", Boolean.toString(north))
                .withProperty("south", Boolean.toString(south))
                .withProperty("up", Boolean.toString(up))
                .withProperty("west", Boolean.toString(west));
    }
}
