package net.minecraft.server;

import java.util.Random;

public class BlockStem extends BlockFlower {

    private final Block fruitBlock;

    protected BlockStem(int i, Block block) {
        super(i, 220);
        this.fruitBlock = block;
        this.a(true);
        float f = 2.0F / 16.0F;
        this.a(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, 0.25F, 0.5F + f);
    }

    protected boolean c(int i) {
        return i == Block.SOIL.id;
    }

    public void a(World world, int i, int j, int k, Random random) {
        super.a(world, i, j, k, random);
        if (world.getLightLevel(i, j + 1, k) >= 9) {
            float f = this.n(world, i, j, k);
            if (random.nextInt((int) (100.0F / f)) == 0) {
                int l = world.getData(i, j, k);
                if (l < 7) {
                    ++l;
                    world.setData(i, j, k, l);
                } else {
                    this.trySpawnFruit(world, i, j, k, random, false);
                }
            }
        }
    }

    public void d_(World world, int i, int j, int k) {
        world.setData(i, j, k, 7);
        this.trySpawnFruit(world, i, j, k, world.random, true);
    }

    private void trySpawnFruit(World world, int i, int j, int k, Random random, boolean forceAnyValidDirection) {
        if (this.hasAdjacentFruit(world, i, j, k)) {
            return;
        }

        if (forceAnyValidDirection) {
            int startDirection = random.nextInt(4);

            for (int offset = 0; offset < 4; ++offset) {
                int direction = startDirection + offset & 3;
                if (this.trySpawnFruitAtDirection(world, i, j, k, direction)) {
                    return;
                }
            }
        } else {
            this.trySpawnFruitAtDirection(world, i, j, k, random.nextInt(4));
        }
    }

    private boolean hasAdjacentFruit(World world, int i, int j, int k) {
        return world.getTypeId(i - 1, j, k) == this.fruitBlock.id
            || world.getTypeId(i + 1, j, k) == this.fruitBlock.id
            || world.getTypeId(i, j, k - 1) == this.fruitBlock.id
            || world.getTypeId(i, j, k + 1) == this.fruitBlock.id;
    }

    private boolean trySpawnFruitAtDirection(World world, int i, int j, int k, int direction) {
        int fruitX = i;
        int fruitZ = k;
        if (direction == 0) {
            fruitX = i - 1;
        } else if (direction == 1) {
            ++fruitX;
        } else if (direction == 2) {
            fruitZ = k - 1;
        } else if (direction == 3) {
            ++fruitZ;
        }

        if (world.getTypeId(fruitX, j, fruitZ) != 0) {
            return false;
        }

        int belowBlockId = world.getTypeId(fruitX, j - 1, fruitZ);
        if (belowBlockId != Block.SOIL.id && belowBlockId != Block.GRASS.id && belowBlockId != Block.DIRT.id) {
            return false;
        }

        if (this.fruitBlock == Block.PUMPKIN) {
            // Use metadata 4 so stem-grown pumpkins are plain/faceless.
            world.setTypeIdAndData(fruitX, j, fruitZ, this.fruitBlock.id, 4);
        } else {
            world.setTypeId(fruitX, j, fruitZ, this.fruitBlock.id);
        }
        return true;
    }

    private float n(World world, int i, int j, int k) {
        float f = 1.0F;
        int l = world.getTypeId(i, j, k - 1);
        int i1 = world.getTypeId(i, j, k + 1);
        int j1 = world.getTypeId(i - 1, j, k);
        int k1 = world.getTypeId(i + 1, j, k);
        int l1 = world.getTypeId(i - 1, j, k - 1);
        int i2 = world.getTypeId(i + 1, j, k - 1);
        int j2 = world.getTypeId(i + 1, j, k + 1);
        int k2 = world.getTypeId(i - 1, j, k + 1);
        boolean flag = j1 == this.id || k1 == this.id;
        boolean flag1 = l == this.id || i1 == this.id;
        boolean flag2 = l1 == this.id || i2 == this.id || j2 == this.id || k2 == this.id;

        for (int l2 = i - 1; l2 <= i + 1; ++l2) {
            for (int i3 = k - 1; i3 <= k + 1; ++i3) {
                int j3 = world.getTypeId(l2, j - 1, i3);
                float f1 = 0.0F;
                if (j3 == Block.SOIL.id) {
                    f1 = 1.0F;
                    if (world.getData(l2, j - 1, i3) > 0) {
                        f1 = 3.0F;
                    }
                }

                if (l2 != i || i3 != k) {
                    f1 /= 4.0F;
                }

                f += f1;
            }
        }

        if (flag2 || flag && flag1) {
            f /= 2.0F;
        }

        return f;
    }

    public int a(int i, int j) {
        return this.textureId;
    }

    public void a(IBlockAccess iblockaccess, int i, int j, int k) {
        this.maxY = (double) ((float) (iblockaccess.getData(i, j, k) * 2 + 2) / 16.0F);
        float f = 2.0F / 16.0F;
        this.a(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, (float) this.maxY, 0.5F + f);
    }

    public int c() {
        return 19;
    }

    public void dropNaturally(World world, int i, int j, int k, int l, float f) {
        super.dropNaturally(world, i, j, k, l, f);
        if (!world.isStatic) {
            if (l >= 7) {
                return;
            }

            Item item = null;
            if (this.fruitBlock == Block.PUMPKIN) {
                item = Item.PUMPKIN_SEED;
            }

            if (this.fruitBlock == Block.MELON) {
                item = Item.MELON_SEED;
            }

            if (item == null) {
                return;
            }

            for (int i1 = 0; i1 < 3; ++i1) {
                if (world.random.nextInt(15) <= l) {
                    float f1 = 0.7F;
                    float f2 = world.random.nextFloat() * f1 + (1.0F - f1) * 0.5F;
                    float f3 = world.random.nextFloat() * f1 + (1.0F - f1) * 0.5F;
                    float f4 = world.random.nextFloat() * f1 + (1.0F - f1) * 0.5F;
                    EntityItem entityitem = new EntityItem(world, (double) ((float) i + f2), (double) ((float) j + f3), (double) ((float) k + f4), new ItemStack(item));
                    entityitem.pickupDelay = 10;
                    world.addEntity(entityitem);
                }
            }
        }
    }

    public int a(int i, Random random) {
        return -1;
    }

    public int a(Random random) {
        return 1;
    }
}
