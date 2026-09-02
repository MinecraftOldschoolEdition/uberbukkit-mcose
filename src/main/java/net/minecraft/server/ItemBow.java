package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

public class ItemBow extends Item {

    public ItemBow(int i) {
        super(i);
        this.maxStackSize = 1;
    }

    public ItemStack a(ItemStack itemstack, World world, EntityHuman entityhuman) {
        boolean adventureCombat = world != null && world.worldData != null && world.worldData.getAdventureCombat();
        if (adventureCombat && entityhuman instanceof EntityPlayer) {
            ((EntityPlayer) entityhuman).startBowCharge();
            return itemstack;
        }
        boolean isCreative = entityhuman != null && entityhuman.gameMode == 1;
        boolean canFire = isCreative || entityhuman.inventory.hasItem(Item.ARROW.id);

        if (canFire) {
            if (!isCreative) {
                entityhuman.inventory.b(Item.ARROW.id);
            }

            if (entityhuman instanceof EntityPlayer) {
                ((EntityPlayer) entityhuman).triggerBowPose();
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

    public boolean releaseChargedBow(ItemStack itemstack, World world, EntityPlayer player, int chargeTicks) {
        float power = AdventureCombatRules.getBowPowerForTime(chargeTicks);
        if (power < 0.1F) {
            return false;
        }
        boolean isCreative = player != null && player.gameMode == 1;
        if (!isCreative && !player.inventory.b(Item.ARROW.id)) {
            return false;
        }

        world.makeSound(player, "random.bow", 1.0F, 1.0F / (b.nextFloat() * 0.4F + 1.2F) + power * 0.5F);
        if ((boolean) PoseidonConfig.getInstance().getProperty("world.settings.skeleton-shooting-sound-fix.enabled")) {
            world.a(player, 1002, MathHelper.floor(player.locX), MathHelper.floor(player.locY - (double) player.height), MathHelper.floor(player.locZ), 0);
        }

        if (!world.isStatic) {
            EntityArrow arrow = new EntityArrow(world, player, power * 3.0F, 1.0F);
            arrow.setAdventureCombatArrow(true);
            arrow.setCritical(power == 1.0F);
            world.addEntity(arrow);
            if (arrow.isCritical()) {
                world.a(arrow, (byte) 18);
            }
        }
        return true;
    }

    public static float getPowerForTime(int chargeTicks) {
		return AdventureCombatRules.getBowPowerForTime(chargeTicks);
    }
}
