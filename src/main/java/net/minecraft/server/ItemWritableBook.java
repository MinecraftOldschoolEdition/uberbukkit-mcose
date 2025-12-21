package net.minecraft.server;

/**
 * A Book and Quill - allows the player to write multiple pages.
 * Right-click opens the book editing GUI on the client.
 */
public class ItemWritableBook extends Item {
    
    public ItemWritableBook(int itemId) {
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
