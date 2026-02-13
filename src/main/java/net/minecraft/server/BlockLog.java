package net.minecraft.server;

import java.util.Random;

import net.minecraft.server.registry.BlockCapabilityRegistryApi;

public class BlockLog extends Block {

    protected BlockLog(int i) {
        super(i, Material.WOOD);
        this.textureId = 20;
    }

    public int a(Random random) {
        return 1;
    }

    public int a(int i, Random random) {
        return Block.LOG.id;
    }

    public void a(World world, EntityHuman entityhuman, int i, int j, int k, int l) {
        super.a(world, entityhuman, i, j, k, l);
    }

    public void remove(World world, int i, int j, int k) {
        byte b0 = 4;
        int l = b0 + 1;

        if (world.a(i - l, j - l, k - l, i + l, j + l, k + l)) {
            for (int i1 = -b0; i1 <= b0; ++i1) {
                for (int j1 = -b0; j1 <= b0; ++j1) {
                    for (int k1 = -b0; k1 <= b0; ++k1) {
                        int l1 = world.getTypeId(i + i1, j + j1, k + k1);

                        Block leafBlock = l1 > 0 && l1 < Block.byId.length ? Block.byId[l1] : null;
                        if (BlockCapabilityRegistryApi.isDecayEnabled(leafBlock)) {
                            int leafX = i + i1;
                            int leafY = j + j1;
                            int leafZ = k + k1;
                            int i2 = world.getData(i + i1, j + j1, k + k1);

                            if ((i2 & 8) == 0) {
                                Chunk leafChunk = world.getChunkAtWorldCoords(leafX, leafZ);
                                leafChunk.b(leafX & 15, leafY, leafZ & 15, i2 | 8);
                            }
                        }
                    }
                }
            }
        }
    }

    public int a(int i, int j) {
        return i == 1 ? 21 : (i == 0 ? 21 : (j == 1 ? 116 : (j == 2 ? 117 : 20)));
    }

    protected int a_(int i) {
        return i;
    }
}
