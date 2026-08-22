package net.minecraft.server;

/**
 * Furnace input keeps food in furnace-sized stacks without changing the
 * world-wide inventory stacking rule for food.
 */
public class SlotFurnaceInput extends Slot {

    public SlotFurnaceInput(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    public int getItemStackLimit(ItemStack itemstack, World world) {
        return itemstack != null && itemstack.getItem() instanceof ItemFood
                ? this.d()
                : super.getItemStackLimit(itemstack, world);
    }
}
