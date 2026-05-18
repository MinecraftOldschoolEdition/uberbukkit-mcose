package net.minecraft.server;

import java.util.Random;

public class WorldGenHellLava extends WorldGenerator {

    private int a;
    private BlockStateKey state;

    public WorldGenHellLava(int i) {
        this.a = i;
        this.state = stateFromBlockId(i);
    }

    public WorldGenHellLava(String blockId) {
        this.state = stateFromIdentifier(blockId);
        this.a = BlockStateBridge.toLegacy(this.state).blockId;
    }

    public boolean a(World world, Random random, int i, int j, int k) {
        if (world.getTypeId(i, j + 1, k) != Block.NETHERRACK.id) {
            return false;
        } else if (world.getTypeId(i, j, k) != 0 && world.getTypeId(i, j, k) != Block.NETHERRACK.id) {
            return false;
        } else {
            int l = 0;

            if (world.getTypeId(i - 1, j, k) == Block.NETHERRACK.id) {
                ++l;
            }

            if (world.getTypeId(i + 1, j, k) == Block.NETHERRACK.id) {
                ++l;
            }

            if (world.getTypeId(i, j, k - 1) == Block.NETHERRACK.id) {
                ++l;
            }

            if (world.getTypeId(i, j, k + 1) == Block.NETHERRACK.id) {
                ++l;
            }

            if (world.getTypeId(i, j - 1, k) == Block.NETHERRACK.id) {
                ++l;
            }

            int i1 = 0;

            if (world.isEmpty(i - 1, j, k)) {
                ++i1;
            }

            if (world.isEmpty(i + 1, j, k)) {
                ++i1;
            }

            if (world.isEmpty(i, j, k - 1)) {
                ++i1;
            }

            if (world.isEmpty(i, j, k + 1)) {
                ++i1;
            }

            if (world.isEmpty(i, j - 1, k)) {
                ++i1;
            }

            if (l == 4 && i1 == 1) {
                setGeneratedBlockAndData(world, i, j, k, this.state);
                world.a = true;
                Block.byId[this.a].a(world, i, j, k, random);
                world.a = false;
            }

            return true;
        }
    }
}
