package net.minecraft.server;

import java.util.Random;

public class BlockMelon extends Block {

    protected BlockMelon(int i) {
        super(i, Material.PUMPKIN);
        this.textureId = 136;
    }

    public int a(int i, int j) {
        return i != 1 && i != 0 ? 136 : 137;
    }

    public int a(int i) {
        return i != 1 && i != 0 ? 136 : 137;
    }

    public int a(int i, Random random) {
        return Item.MELON_SLICE.id;
    }

    public int a(Random random) {
        return 3 + random.nextInt(5);
    }

    public void postBreak(World world, int i, int j, int k, int l) {
        super.postBreak(world, i, j, k, l);
        this.removeAdjacentStem(world, i - 1, j, k);
        this.removeAdjacentStem(world, i + 1, j, k);
        this.removeAdjacentStem(world, i, j, k - 1);
        this.removeAdjacentStem(world, i, j, k + 1);
    }

    private void removeAdjacentStem(World world, int i, int j, int k) {
        if (world.getTypeId(i, j, k) == Block.MELON_STEM.id) {
            world.setTypeId(i, j, k, 0);
        }
    }
}
