package net.minecraft.server;

import net.minecraft.server.event.EventBus;
import net.minecraft.server.event.events.CraftingCompleteEvent;
import net.minecraft.server.registry.ItemCapabilityRegistryApi;

public class SlotResult extends Slot {

    private final IInventory d;
    private EntityHuman e;

    public SlotResult(EntityHuman entityhuman, IInventory iinventory, IInventory iinventory1, int i, int j, int k) {
        super(iinventory1, i, j, k);
        this.e = entityhuman;
        this.d = iinventory;
    }

    public boolean isAllowed(ItemStack itemstack) {
        return false;
    }

    public void a(ItemStack itemstack) {
        itemstack.b(this.e.world, this.e);
        EventBus.global().publish(new CraftingCompleteEvent(this.e, this.e.world, itemstack, itemstack == null ? 0 : itemstack.count, null));
        
        // === CORE PROGRESSION ===
        if (itemstack.id == Block.WORKBENCH.id) {
            this.e.a(AchievementList.buildWorkBench, 1);
        } else if (itemstack.id == Item.WOOD_PICKAXE.id) {
            this.e.a(AchievementList.buildPickaxe, 1);
        } else if (itemstack.id == Block.FURNACE.id) {
            this.e.a(AchievementList.buildFurnace, 1);
        } else if (itemstack.id == Item.STONE_PICKAXE.id) {
            this.e.a(AchievementList.buildBetterPickaxe, 1);
        }
        
        // === FARMING ===
        else if (itemstack.id == Item.WOOD_HOE.id) {
            this.e.a(AchievementList.buildHoe, 1);
        } else if (itemstack.id == Item.BREAD.id) {
            this.e.a(AchievementList.makeBread, 1);
        } else if (itemstack.id == Item.CAKE.id) {
            this.e.a(AchievementList.bakeCake, 1);
        }
        
        // === COMBAT ===
        else if (itemstack.id == Item.WOOD_SWORD.id) {
            this.e.a(AchievementList.buildSword, 1);
        } else if (itemstack.id == Item.BOW.id) {
            this.e.a(AchievementList.buildBow, 1);
        }
        
        // === EXPLORATION & BUILDING ===
        else if (itemstack.id == Item.MAP.id) {
            this.e.a(AchievementList.craftMap, 1);
        } else if (itemstack.id == Item.BOAT.id) {
            this.e.a(AchievementList.boatTravel, 1);
        } else if (itemstack.id == Block.BOOKSHELF.id) {
            this.e.a(AchievementList.bookshelf, 1);
        } else if (itemstack.id == Block.JUKEBOX.id) {
            this.e.a(AchievementList.jukebox, 1);
        }
        
        // === REDSTONE & MECHANICS ===
        else if (itemstack.id == Block.PISTON.id || itemstack.id == Block.PISTON_STICKY.id) {
            this.e.a(AchievementList.piston, 1);
        } else if (itemstack.id == Item.DIODE.id) {
            this.e.a(AchievementList.repeater, 1);
        }

        for (int i = 0; i < this.d.getSize(); ++i) {
            ItemStack itemstack1 = this.d.getItem(i);

            if (itemstack1 != null) {
                this.d.splitStack(i, 1);
                ItemStack remainder = ItemCapabilityRegistryApi
                        .createCraftingRemainder(itemstack1.getItem());
                if (remainder != null) {
                    this.d.setItem(i, remainder);
                }
            }
        }
    }
}
