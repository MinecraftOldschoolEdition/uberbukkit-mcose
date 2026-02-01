package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

public class ItemBow extends Item {

    public ItemBow(int i) {
        super(i);
        this.maxStackSize = 1;
    }

    public ItemStack a(ItemStack itemstack, World world, EntityHuman entityhuman) {
        // Check if player has arrows (without consuming) or is in creative mode
        boolean hasArrow = entityhuman.inventory.hasItem(Item.ARROW.id);
        boolean isCreative = entityhuman.gameMode == 1;
        
        if (hasArrow || isCreative) {
            // Only consume arrow if not in creative mode
            if (!isCreative && hasArrow) {
                entityhuman.inventory.b(Item.ARROW.id); // Consume arrow
            }

            world.makeSound(entityhuman, "random.bow", 1.0F, 1.0F / (b.nextFloat() * 0.4F + 0.8F));
            if ((boolean) PoseidonConfig.getInstance().getProperty("world.settings.skeleton-shooting-sound-fix.enabled")) {
                world.a(entityhuman, 1002, MathHelper.floor(entityhuman.locX), MathHelper.floor(entityhuman.locY - (double) entityhuman.height), MathHelper.floor(entityhuman.locZ), 0); // Poseidon - fix player bow sounds (Strultz)
            }

            if (!world.isStatic) {
                EntityArrow entityarrow = new EntityArrow(world, entityhuman);
                world.addEntity(entityarrow);
            }
        }

        return itemstack;
    }
}
