package net.minecraft.server;

public class ItemShears extends Item {

    public ItemShears(int i) {
        super(i);
        this.c(1);
        this.d(238);
    }

    public boolean a(ItemStack itemstack, EntityHuman entityhuman, World world, int i, int j, int k, int l) {
        int blockId = world.getTypeId(i, j, k);
        int data = world.getData(i, j, k);
        if (blockId == Block.PUMPKIN_PLAIN.id) {
            boolean carved = world.setTypeIdAndData(i, j, k, Block.CARVED_PUMPKIN.id,
                    this.carvedMetadata(entityhuman, l));
            if (carved && (entityhuman == null || entityhuman.gameMode != 1)) {
                itemstack.damage(1, entityhuman);
            }
            return carved;
        }
        if (blockId == Block.PUMPKIN.id && data > 3) {
            world.setData(i, j, k, this.carvedMetadata(entityhuman, l));
            if (entityhuman == null || entityhuman.gameMode != 1) {
                itemstack.damage(1, entityhuman);
            }
            return true;
        }
        return false;
    }

    private int carvedMetadata(EntityHuman entityhuman, int side) {
        if (side == 2) return 2;
        if (side == 3) return 0;
        if (side == 4) return 1;
        if (side == 5) return 3;
        return entityhuman == null ? 0 : MathHelper.floor((double) (entityhuman.yaw * 4.0F / 360.0F) + 2.5D) & 3;
    }

    public boolean a(ItemStack itemstack, int i, int j, int k, int l, EntityLiving entityliving) {
        if (i == Block.LEAVES.id || i == Block.WEB.id) {
            itemstack.damage(1, entityliving);
        }

        return super.a(itemstack, i, j, k, l, entityliving);
    }

    public boolean a(Block block) {
        return block.id == Block.WEB.id;
    }

    public float a(ItemStack itemstack, Block block) {
        return block.id != Block.WEB.id && block.id != Block.LEAVES.id ? (block.id == Block.WOOL.id ? 5.0F : super.a(itemstack, block)) : 15.0F;
    }
}
