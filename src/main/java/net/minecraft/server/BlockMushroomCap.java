package net.minecraft.server;

import java.util.Random;

public class BlockMushroomCap extends Block {
    private int type; // 0=brown, 1=red

    public BlockMushroomCap(int id, Material material, int textureIndex, int type) {
        super(id, textureIndex, material);
        this.type = type;
    }

    public int a(int side, int meta) {
        int inside = this.textureId;
        int stem = this.textureId - 1;
        int outside = this.textureId - 16 - this.type;

        if (meta == 10) return side == 0 || side == 1 ? inside : stem;
        if (meta == 15) return stem;
        if (meta == 14) return outside;
        if (meta >= 1 && meta <= 9 && side == 1) return outside;
        if (meta >= 1 && meta <= 3 && side == 2) return outside;
        if (meta >= 7 && meta <= 9 && side == 3) return outside;
        if ((meta == 1 || meta == 4 || meta == 7) && side == 4) return outside;
        if ((meta == 3 || meta == 6 || meta == 9) && side == 5) return outside;
        return inside;
    }

    public int a(int side) {
        return this.textureId;
    }

    public int a(Random random) {
        int count = random.nextInt(10) - 7;
        if (count < 0) count = 0;
        return count;
    }

    public int a(int metadata, Random random) {
        return Block.BROWN_MUSHROOM.id + this.type;
    }
}
