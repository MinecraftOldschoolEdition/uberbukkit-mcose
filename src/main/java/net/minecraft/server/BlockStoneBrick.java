package net.minecraft.server;

public class BlockStoneBrick extends Block {

    public BlockStoneBrick(int id) {
        super(id, 54, Material.STONE);
    }

    public int a(int side, int meta) {
        // 0 = normal (terrain idx 54), 1 = mossy (100), 2 = cracked (101)
        switch (meta) {
            case 1:
                return 100;
            case 2:
                return 101;
            default:
                return 54;
        }
    }

    public int a(int side) {
        return this.a(side, 0);
    }

    protected int a_(int meta) {
        return meta;
    }
}


