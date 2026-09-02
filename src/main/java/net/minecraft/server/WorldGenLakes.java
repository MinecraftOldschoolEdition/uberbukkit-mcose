package net.minecraft.server;

import java.util.Random;

public class WorldGenLakes extends WorldGenerator {

    private int a;
    private BlockStateKey state;
    private BlockStateKey barrierState;
    private int barrierBlockId;

    public WorldGenLakes(int i) {
        this.a = i;
        this.state = stateFromBlockId(i);
        this.setLegacyBarrierState();
    }

    public WorldGenLakes(String blockId) {
        this.state = stateFromIdentifier(blockId).withProperty("variant", "still");
        this.a = BlockStateBridge.toLegacy(this.state).blockId;
        this.setLegacyBarrierState();
    }

    public WorldGenLakes(BlockStateKey fluidState, BlockStateKey barrierState) {
        if (fluidState == null || barrierState == null) {
            throw new IllegalArgumentException(
                    "Lake fluid and barrier states are required");
        }
        this.state = fluidState;
        this.a = BlockStateBridge.toLegacy(fluidState).blockId;
        this.barrierState = barrierState;
        this.barrierBlockId = BlockStateBridge.toLegacy(barrierState).blockId;
    }

    private void setLegacyBarrierState() {
        boolean lava = Block.byId[this.a] != null
                && Block.byId[this.a].material == Material.LAVA;
        this.barrierState = stateFromIdentifier(
                lava ? "minecraft:stone" : "minecraft:air");
        this.barrierBlockId = BlockStateBridge.toLegacy(
                this.barrierState).blockId;
    }

    public boolean a(World world, Random random, int i, int j, int k) {
        i -= 8;

        for (k -= 8; j > 0 && world.isEmpty(i, j, k); --j) {
            ;
        }

        j -= 4;
        boolean[] aboolean = new boolean[2048];
        int l = random.nextInt(4) + 4;

        int i1;

        for (i1 = 0; i1 < l; ++i1) {
            double d0 = random.nextDouble() * 6.0D + 3.0D;
            double d1 = random.nextDouble() * 4.0D + 2.0D;
            double d2 = random.nextDouble() * 6.0D + 3.0D;
            double d3 = random.nextDouble() * (16.0D - d0 - 2.0D) + 1.0D + d0 / 2.0D;
            double d4 = random.nextDouble() * (8.0D - d1 - 4.0D) + 2.0D + d1 / 2.0D;
            double d5 = random.nextDouble() * (16.0D - d2 - 2.0D) + 1.0D + d2 / 2.0D;

            for (int j1 = 1; j1 < 15; ++j1) {
                for (int k1 = 1; k1 < 15; ++k1) {
                    for (int l1 = 1; l1 < 7; ++l1) {
                        double d6 = ((double) j1 - d3) / (d0 / 2.0D);
                        double d7 = ((double) l1 - d4) / (d1 / 2.0D);
                        double d8 = ((double) k1 - d5) / (d2 / 2.0D);
                        double d9 = d6 * d6 + d7 * d7 + d8 * d8;

                        if (d9 < 1.0D) {
                            aboolean[(j1 * 16 + k1) * 8 + l1] = true;
                        }
                    }
                }
            }
        }

        boolean flag;
        int i2;
        int j2;

        for (i1 = 0; i1 < 16; ++i1) {
            for (i2 = 0; i2 < 16; ++i2) {
                for (j2 = 0; j2 < 8; ++j2) {
                    flag = !aboolean[(i1 * 16 + i2) * 8 + j2] && (i1 < 15 && aboolean[((i1 + 1) * 16 + i2) * 8 + j2] || i1 > 0 && aboolean[((i1 - 1) * 16 + i2) * 8 + j2] || i2 < 15 && aboolean[(i1 * 16 + i2 + 1) * 8 + j2] || i2 > 0 && aboolean[(i1 * 16 + (i2 - 1)) * 8 + j2] || j2 < 7 && aboolean[(i1 * 16 + i2) * 8 + j2 + 1] || j2 > 0 && aboolean[(i1 * 16 + i2) * 8 + (j2 - 1)]);
                    if (flag) {
                        Material material = world.getMaterial(i + i1, j + j2, k + i2);

                        if (j2 >= 4 && material.isLiquid()) {
                            return false;
                        }

                        if (j2 < 4 && !material.isBuildable() && world.getTypeId(i + i1, j + j2, k + i2) != this.a) {
                            return false;
                        }
                    }
                }
            }
        }

        for (i1 = 0; i1 < 16; ++i1) {
            for (i2 = 0; i2 < 16; ++i2) {
                for (j2 = 0; j2 < 8; ++j2) {
                    if (aboolean[(i1 * 16 + i2) * 8 + j2]) {
                        if (j2 >= 4) {
                            world.setBlockState(i + i1, j + j2, k + i2, "minecraft:air");
                        } else {
                            setGeneratedBlock(world, i + i1, j + j2, k + i2, this.state);
                        }
                    }
                }
            }
        }

        for (i1 = 0; i1 < 16; ++i1) {
            for (i2 = 0; i2 < 16; ++i2) {
                for (j2 = 4; j2 < 8; ++j2) {
                    if (aboolean[(i1 * 16 + i2) * 8 + j2] && world.getTypeId(i + i1, j + j2 - 1, k + i2) == Block.DIRT.id && world.a(EnumSkyBlock.SKY, i + i1, j + j2, k + i2) > 0) {
                        world.setBlockState(i + i1, j + j2 - 1, k + i2, "minecraft:grass_block");
                    }
                }
            }
        }

        if (this.barrierBlockId != 0) {
            for (i1 = 0; i1 < 16; ++i1) {
                for (i2 = 0; i2 < 16; ++i2) {
                    for (j2 = 0; j2 < 8; ++j2) {
                        flag = !aboolean[(i1 * 16 + i2) * 8 + j2] && (i1 < 15 && aboolean[((i1 + 1) * 16 + i2) * 8 + j2] || i1 > 0 && aboolean[((i1 - 1) * 16 + i2) * 8 + j2] || i2 < 15 && aboolean[(i1 * 16 + i2 + 1) * 8 + j2] || i2 > 0 && aboolean[(i1 * 16 + (i2 - 1)) * 8 + j2] || j2 < 7 && aboolean[(i1 * 16 + i2) * 8 + j2 + 1] || j2 > 0 && aboolean[(i1 * 16 + i2) * 8 + (j2 - 1)]);
                        if (flag && (j2 < 4 || random.nextInt(2) != 0) && world.getMaterial(i + i1, j + j2, k + i2).isBuildable()) {
                            setGeneratedBlock(world, i + i1, j + j2, k + i2,
                                    this.barrierState);
                        }
                    }
                }
            }
        }

        return true;
    }
}
