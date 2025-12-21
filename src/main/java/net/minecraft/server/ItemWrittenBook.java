package net.minecraft.server;

/**
 * A Written Book - a signed book that can only be read, not edited.
 * Has an author and title stored in NBT.
 */
public class ItemWrittenBook extends Item {
    
    public ItemWrittenBook(int itemId) {
        super(itemId);
        this.c(1); // Max stack size = 1
    }

    /**
     * Called when the player right-clicks with this item.
     * The client handles opening the GUI - server just confirms it's allowed.
     */
    @Override
    public ItemStack a(ItemStack itemstack, World world, EntityHuman entityhuman) {
        return itemstack;
    }
}
