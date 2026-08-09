package net.minecraft.server;

public class ItemSoup extends ItemFood {

    public ItemSoup(int i, int j) {
        super(i, j, false);
    }

    public ItemStack a(ItemStack itemstack, World world, EntityHuman entityhuman) {
        super.a(itemstack, world, entityhuman);
        ItemStack bowl = new ItemStack(Item.BOWL);
        if (itemstack.count <= 0) {
            return bowl;
        }
        if (!entityhuman.inventory.pickup(bowl)) {
            entityhuman.a(bowl, false);
        }
        return itemstack;
    }
}
