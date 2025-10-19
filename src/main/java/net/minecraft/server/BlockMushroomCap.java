package net.minecraft.server;

public class BlockMushroomCap extends Block {
    private int type; // 0=brown, 1=red

    public BlockMushroomCap(int id, Material material, int textureIndex, int type) {
        super(id, textureIndex, material);
        this.type = type;
    }

    public int a(int side, int meta) {
        // Ported from client: uses base texture minus 16 and minus type for cap sides
        if (meta >= 1 && meta <= 9 && side == 1) return this.textureId - 16 - this.type;
        if (meta >= 1 && meta <= 3 && side == 2) return this.textureId - 16 - this.type;
        if (meta >= 7 && meta <= 9 && side == 3) return this.textureId - 16 - this.type;
        if ((meta == 1 || meta == 4 || meta == 7) && side == 4) return this.textureId - 16 - this.type;
        if ((meta == 3 || meta == 6 || meta == 9) && side == 5) return this.textureId - 16 - this.type;
        if (meta == 14) return this.textureId - 16 - this.type;
        return this.textureId;
    }

    public int a(int side) {
        return this.textureId;
    }
}


