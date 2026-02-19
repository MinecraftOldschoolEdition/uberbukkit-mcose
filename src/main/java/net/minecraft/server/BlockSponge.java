package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

public class BlockSponge extends Block {
    private final boolean absorbOnPlace;
    private static final int ABSORB_RADIUS = 2;
    private static final int SEARCH_DIAMETER = ABSORB_RADIUS * 2 + 1;
    private static final int SEARCH_VOLUME = SEARCH_DIAMETER * SEARCH_DIAMETER * SEARCH_DIAMETER;
    private static final int[] DX = new int[]{1, -1, 0, 0, 0, 0};
    private static final int[] DY = new int[]{0, 0, 1, -1, 0, 0};
    private static final int[] DZ = new int[]{0, 0, 0, 0, 1, -1};

    protected BlockSponge(int i) {
        this(i, true);
    }

    protected BlockSponge(int i, boolean absorbOnPlace) {
        super(i, Material.SPONGE);
        this.absorbOnPlace = absorbOnPlace;
        this.textureId = absorbOnPlace ? 48 : 170;
    }

    public void c(World world, int i, int j, int k) {
        if (world.isStatic) {
            return;
        }

        if (!this.absorbOnPlace) {
            return;
        }

        if (this.absorbWater(world, i, j, k) > 0) {
            world.setTypeId(i, j, k, Block.WET_SPONGE.id);
        }
    }

    public void remove(World world, int i, int j, int k) {
        byte radius = 2;

        if (PoseidonConfig.getInstance().getConfigBoolean("fix.optimize-sponges.enabled", true)) {
            this.optimizedRemove(world, i, j, k, radius);
            return;
        }

        for (int x = i - radius; x <= i + radius; ++x) {
            for (int y = j - radius; y <= j + radius; ++y) {
                for (int z = k - radius; z <= k + radius; ++z) {
                    world.applyPhysics(x, y, z, world.getTypeId(x, y, z));
                }
            }
        }
    }

    private void optimizedRemove(World world, int i, int j, int k, byte radius) {
        for (int x = i - radius; x <= i + radius; ++x) {
            for (int y = j - radius; y <= j + radius; ++y) {
                if (y > 127 || y < 0) continue;

                for (int z = k - radius; z <= k + radius; ++z) {
                    int type = world.getTypeId(x, y, z);
                    if ((type != Block.WATER.id && type != Block.STATIONARY_WATER.id)) continue;

                    world.applyPhysics(x, y, z, type);
                }
            }
        }
    }

    private int absorbWater(World world, int i, int j, int k) {
        int minX = i - ABSORB_RADIUS;
        int maxX = i + ABSORB_RADIUS;
        int minY = j - ABSORB_RADIUS;
        int maxY = j + ABSORB_RADIUS;
        int minZ = k - ABSORB_RADIUS;
        int maxZ = k + ABSORB_RADIUS;
        boolean[][][] visited = new boolean[SEARCH_DIAMETER][SEARCH_DIAMETER][SEARCH_DIAMETER];
        int[] queueX = new int[SEARCH_VOLUME];
        int[] queueY = new int[SEARCH_VOLUME];
        int[] queueZ = new int[SEARCH_VOLUME];
        int head = 0;
        int tail = 0;
        int absorbed = 0;

        queueX[tail] = i;
        queueY[tail] = j;
        queueZ[tail] = k;
        ++tail;
        visited[ABSORB_RADIUS][ABSORB_RADIUS][ABSORB_RADIUS] = true;

        while (head < tail) {
            int cx = queueX[head];
            int cy = queueY[head];
            int cz = queueZ[head];
            ++head;

            for (int side = 0; side < 6; ++side) {
                int nx = cx + DX[side];
                int ny = cy + DY[side];
                int nz = cz + DZ[side];
                if (nx < minX || nx > maxX || ny < minY || ny > maxY || nz < minZ || nz > maxZ || ny < 0 || ny >= 128) {
                    continue;
                }

                int lx = nx - minX;
                int ly = ny - minY;
                int lz = nz - minZ;
                if (visited[lx][ly][lz]) {
                    continue;
                }

                Material material = world.getMaterial(nx, ny, nz);
                if (material == Material.WATER) {
                    world.setTypeId(nx, ny, nz, 0);
                    ++absorbed;
                    visited[lx][ly][lz] = true;
                    queueX[tail] = nx;
                    queueY[tail] = ny;
                    queueZ[tail] = nz;
                    ++tail;
                }
            }
        }

        return absorbed;
    }
}
