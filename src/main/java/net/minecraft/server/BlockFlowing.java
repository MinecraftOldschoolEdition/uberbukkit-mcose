package net.minecraft.server;

import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockFromToEvent;

import java.util.Random;

// CraftBukkit start
// CraftBukkit end

public class BlockFlowing extends BlockFluids {

    int a = 0;
    boolean[] b = new boolean[4];
    int[] c = new int[4];

    protected BlockFlowing(int i, Material material) {
        super(i, material);
    }

    private void i(World world, int i, int j, int k) {
        int l = CriticalBlockStateAccess.getFluidMetadata(world, i, j, k);

        CriticalBlockStateAccess.setLegacyState(world, i, j, k, this.id + 1, l, false);
        world.b(i, j, k, i, j, k);
        world.notify(i, j, k);
    }

    protected int g(World world, int i, int j, int k) {
        Material material = this.getMaterialIfLoaded(world, i, j, k);

        return material != this.material ? -1 : this.getFluidMetadataIfLoaded(world, i, j, k);
    }

    public void a(World world, int i, int j, int k, Random random) {
        // CraftBukkit start
        org.bukkit.World bworld = world.getWorld();
        org.bukkit.Server server = world.getServer();
        org.bukkit.block.Block source = bworld == null ? null : bworld.getBlockAt(i, j, k);
        // CraftBukkit end

        int l = this.g(world, i, j, k);
        byte b0 = 1;

        if (this.material == Material.LAVA && !world.worldProvider.d) {
            b0 = 2;
        }

        boolean flag = true;
        int i1;

        if (l > 0) {
            byte b1 = -100;

            this.a = 0;
            int j1 = this.f(world, i - 1, j, k, b1);

            j1 = this.f(world, i + 1, j, k, j1);
            j1 = this.f(world, i, j, k - 1, j1);
            j1 = this.f(world, i, j, k + 1, j1);
            i1 = j1 + b0;
            if (i1 >= 8 || j1 < 0) {
                i1 = -1;
            }

            if (this.g(world, i, j + 1, k) >= 0) {
                int k1 = this.g(world, i, j + 1, k);

                if (k1 >= 8) {
                    i1 = k1;
                } else {
                    i1 = k1 + 8;
                }
            }

            if (this.a >= 2 && this.material == Material.WATER) {
                Material belowMaterial = this.getMaterialIfLoaded(world, i, j - 1, k);

                if (belowMaterial != null && belowMaterial.isBuildable()) {
                    i1 = 0;
                } else if (belowMaterial == this.material && this.getFluidMetadataIfLoaded(world, i, j, k) == 0) {
                    i1 = 0;
                }
            }

            if (this.material == Material.LAVA && l < 8 && i1 < 8 && i1 > l && random.nextInt(4) != 0) {
                i1 = l;
                flag = false;
            }

            if (i1 != l) {
                l = i1;
                if (i1 < 0) {
                    world.setTypeId(i, j, k, 0);
                } else {
                    CriticalBlockStateAccess.setMetadata(world, i, j, k, i1, true);
                    world.c(i, j, k, this.id, this.c());
                    world.applyPhysics(i, j, k, this.id);
                }
            } else if (flag) {
                this.i(world, i, j, k);
            }
        } else {
            this.i(world, i, j, k);
        }

        if (this.l(world, i, j - 1, k)) {
            // CraftBukkit start - send "down" to the server
            BlockFromToEvent event = new BlockFromToEvent(source, BlockFace.DOWN);
            if (server != null) {
                server.getPluginManager().callEvent(event);
            }

            if (!event.isCancelled()) {
                if (l >= 8) {
                    CriticalBlockStateAccess.setLegacyState(world, i, j - 1, k, this.id, l, true);
                } else {
                    CriticalBlockStateAccess.setLegacyState(world, i, j - 1, k, this.id, l + 8, true);
                }
            }
            // CraftBukkit end
        } else if (l >= 0 && (l == 0 || this.k(world, i, j - 1, k))) {
            boolean[] aboolean = this.j(world, i, j, k);

            i1 = l + b0;
            if (l >= 8) {
                i1 = 1;
            }

            if (i1 >= 8) {
                return;
            }

            // CraftBukkit start - all four cardinal directions. Do not change the order!
            BlockFace[] faces = new BlockFace[] { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST };
            int index = 0;

            for (BlockFace currentFace : faces) {
                if (aboolean[index]) {
                    BlockFromToEvent event = new BlockFromToEvent(source, currentFace);

                    if (server != null) {
                        server.getPluginManager().callEvent(event);
                    }

                    if (!event.isCancelled()) {
                        this.flow(world, i + currentFace.getModX(), j, k + currentFace.getModZ(), i1);
                    }
                }
                index++;
            }
            // CraftBukkit end
        }
    }

    private void flow(World world, int i, int j, int k, int l) {
        if (this.l(world, i, j, k)) {
            int i1 = this.getTypeIdIfLoaded(world, i, j, k);

            if (i1 > 0) {
                if (this.material == Material.LAVA) {
                    this.h(world, i, j, k);
                } else {
                    Block.byId[i1].g(world, i, j, k, this.getDataIfLoaded(world, i, j, k));
                }
            }

            CriticalBlockStateAccess.setLegacyState(world, i, j, k, this.id, l, true);
        }
    }

    private int b(World world, int i, int j, int k, int l, int i1) {
        int j1 = 1000;

        for (int k1 = 0; k1 < 4; ++k1) {
            if ((k1 != 0 || i1 != 1) && (k1 != 1 || i1 != 0) && (k1 != 2 || i1 != 3) && (k1 != 3 || i1 != 2)) {
                int l1 = i;
                int i2 = k;

                if (k1 == 0) {
                    l1 = i - 1;
                }

                if (k1 == 1) {
                    ++l1;
                }

                if (k1 == 2) {
                    i2 = k - 1;
                }

                if (k1 == 3) {
                    ++i2;
                }

                Material material = this.getMaterialIfLoaded(world, l1, j, i2);

                if (!this.k(world, l1, j, i2) && (material != this.material || this.getFluidMetadataIfLoaded(world, l1, j, i2) != 0)) {
                    if (!this.k(world, l1, j - 1, i2)) {
                        return l;
                    }

                    if (l < 4) {
                        int j2 = this.b(world, l1, j, i2, l + 1, k1);

                        if (j2 < j1) {
                            j1 = j2;
                        }
                    }
                }
            }
        }

        return j1;
    }

    private boolean[] j(World world, int i, int j, int k) {
        int l;
        int i1;

        for (l = 0; l < 4; ++l) {
            this.c[l] = 1000;
            i1 = i;
            int j1 = k;

            if (l == 0) {
                i1 = i - 1;
            }

            if (l == 1) {
                ++i1;
            }

            if (l == 2) {
                j1 = k - 1;
            }

            if (l == 3) {
                ++j1;
            }

            Material material = this.getMaterialIfLoaded(world, i1, j, j1);

            if (!this.k(world, i1, j, j1) && (material != this.material || this.getFluidMetadataIfLoaded(world, i1, j, j1) != 0)) {
                if (!this.k(world, i1, j - 1, j1)) {
                    this.c[l] = 0;
                } else {
                    this.c[l] = this.b(world, i1, j, j1, 1, l);
                }
            }
        }

        l = this.c[0];

        for (i1 = 1; i1 < 4; ++i1) {
            if (this.c[i1] < l) {
                l = this.c[i1];
            }
        }

        for (i1 = 0; i1 < 4; ++i1) {
            this.b[i1] = this.c[i1] == l;
        }

        return this.b;
    }

    private boolean k(World world, int i, int j, int k) {
        if (!this.canProbeWithoutLoading(world, i, j, k)) {
            return true;
        }

        int l = this.getTypeIdIfLoaded(world, i, j, k);

        if (l != Block.WOODEN_DOOR.id && l != Block.IRON_DOOR_BLOCK.id && l != Block.SIGN_POST.id && l != Block.LADDER.id && l != Block.SUGAR_CANE_BLOCK.id) {
            if (l == 0) {
                return false;
            } else {
                Material material = Block.byId[l].material;

                return material.isSolid();
            }
        } else {
            return true;
        }
    }

    protected int f(World world, int i, int j, int k, int l) {
        int i1 = this.g(world, i, j, k);

        if (i1 < 0) {
            return l;
        } else {
            if (i1 == 0) {
                ++this.a;
            }

            if (i1 >= 8) {
                i1 = 0;
            }

            return l >= 0 && i1 >= l ? l : i1;
        }
    }

    private boolean l(World world, int i, int j, int k) {
        Material material = this.getMaterialIfLoaded(world, i, j, k);

        if (material == null) {
            return false;
        }

        return material == this.material ? false : (material == Material.LAVA ? false : !this.k(world, i, j, k));
    }

    private Material getMaterialIfLoaded(World world, int i, int j, int k) {
        if (j < 0 || j >= 128) {
            return Material.AIR;
        }

        return world.getMaterialIfLoaded(i, j, k);
    }

    private int getTypeIdIfLoaded(World world, int i, int j, int k) {
        if (j < 0 || j >= 128) {
            return 0;
        }

        return world.isLoaded(i, j, k) ? world.getTypeIdIfLoaded(i, j, k) : -1;
    }

    private int getDataIfLoaded(World world, int i, int j, int k) {
        if (j < 0 || j >= 128) {
            return 0;
        }

        return world.isLoaded(i, j, k) ? world.getDataIfLoaded(i, j, k) : 0;
    }

    private int getFluidMetadataIfLoaded(World world, int i, int j, int k) {
        if (j < 0 || j >= 128 || !world.isLoaded(i, j, k)) {
            return -1;
        }

        return CriticalBlockStateAccess.getFluidMetadata(world, i, j, k);
    }

    private boolean canProbeWithoutLoading(World world, int i, int j, int k) {
        return j < 0 || j >= 128 || world.isLoaded(i, j, k);
    }

    public void c(World world, int i, int j, int k) {
        super.c(world, i, j, k);
        if (world.getTypeId(i, j, k) == this.id) {
            world.c(i, j, k, this.id, this.c());
        }
    }
}
