package net.minecraft.server;

public class ItemAxe extends ItemTool {

    private static Block[] bk = new Block[] { Block.WOOD, Block.BOOKSHELF, Block.LOG, Block.CHEST };

    protected ItemAxe(int i, EnumToolMaterial enumtoolmaterial) {
        super(i, 3, enumtoolmaterial, bk);
    }

    public boolean a(ItemStack itemstack, EntityHuman entityhuman, World world, int i, int j, int k, int l) {
        int blockId = world.getTypeId(i, j, k);
        int data = world.getData(i, j, k);
        if (blockId != Block.PUMPKIN.id || data <= 3) {
            return false;
        }

        int carvedData = this.carvedMetadata(entityhuman, l);
        world.setData(i, j, k, carvedData);
        if (entityhuman == null || entityhuman.gameMode != 1) {
            itemstack.damage(1, entityhuman);
        }
        return true;
    }

    private int carvedMetadata(EntityHuman entityhuman, int side) {
        if (side == 2) return 2;
        if (side == 3) return 0;
        if (side == 4) return 1;
        if (side == 5) return 3;
        return entityhuman == null ? 0 : MathHelper.floor((double) (entityhuman.yaw * 4.0F / 360.0F) + 2.5D) & 3;
    }
}
