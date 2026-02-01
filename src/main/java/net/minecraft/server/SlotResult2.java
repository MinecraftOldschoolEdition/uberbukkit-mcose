package net.minecraft.server;

public class SlotResult2 extends Slot {

    private EntityHuman d;

    public SlotResult2(EntityHuman entityhuman, IInventory iinventory, int i, int j, int k) {
        super(iinventory, i, j, k);
        this.d = entityhuman;
    }

    public boolean isAllowed(ItemStack itemstack) {
        return false;
    }

    public void a(ItemStack itemstack) {
        itemstack.b(this.d.world, this.d);
        
        // Core progression - Acquire Hardware
        if (itemstack.id == Item.IRON_INGOT.id) {
            this.d.a(AchievementList.acquireIron, 1);
        }
        
        // Cooking achievements
        if (itemstack.id == Item.COOKED_FISH.id) {
            this.d.a(AchievementList.cookFish, 1);
        }
        
        if (itemstack.id == Item.GRILLED_PORK.id) {
            this.d.a(AchievementList.cookBacon, 1);
        }
        
        // Diamonds! (smelting diamond ore)
        if (itemstack.id == Item.DIAMOND.id) {
            this.d.a(AchievementList.diamonds, 1);
        }

        super.a(itemstack);
    }
}
