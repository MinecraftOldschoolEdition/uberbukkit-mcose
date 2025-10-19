package net.minecraft.server;

public class ItemStoneBrick extends ItemBlock {

    public ItemStoneBrick(int id) {
        super(id);
        this.d(0);
        this.a(true);
    }

    public int filterData(int meta) {
        // preserve variant metadata on placement
        return meta;
    }
}


