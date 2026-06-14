package net.minecraft.server;

public class ContainerFurnace extends Container {

    private TileEntityFurnace a;
    private int b = 0;
    private int c = 0;
    private int h = 0;

    // Uberbukkit start
    private Boolean canShiftClick = null;
    private EntityHuman entityHuman;

    public boolean canShiftClick() {
        if (this.canShiftClick != null) {
            return this.canShiftClick;
        }

        if (this.entityHuman instanceof EntityPlayer) {
            return this.canShiftClick = ((EntityPlayer) this.entityHuman).netServerHandler.networkManager.pvn >= 12;
        }

        return true; // assume true for NPCs
    }
    // Uberbukkit end

    public ContainerFurnace(InventoryPlayer inventoryplayer, TileEntityFurnace tileentityfurnace) {
        this.entityHuman = inventoryplayer.d; // Uberbukkit
        this.a = tileentityfurnace;
        this.a(new Slot(tileentityfurnace, 0, 56, 17));
        this.a(new Slot(tileentityfurnace, 1, 56, 53));
        this.a(new SlotResult2(inventoryplayer.d, tileentityfurnace, 2, 116, 35));

        int i;

        for (i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.a(new Slot(inventoryplayer, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        for (i = 0; i < 9; ++i) {
            this.a(new Slot(inventoryplayer, i, 8 + i * 18, 142));
        }
    }

    public void a(ICrafting icrafting) {
        super.a(icrafting);
        icrafting.a(this, 0, this.a.cookTime);
        icrafting.a(this, 1, this.a.burnTime);
        icrafting.a(this, 2, this.a.ticksForCurrentFuel);
    }

    public void a() {
        super.a();

        for (int i = 0; i < this.listeners.size(); ++i) {
            ICrafting icrafting = (ICrafting) this.listeners.get(i);

            if (this.b != this.a.cookTime) {
                icrafting.a(this, 0, this.a.cookTime);
            }

            if (this.c != this.a.burnTime) {
                icrafting.a(this, 1, this.a.burnTime);
            }

            if (this.h != this.a.ticksForCurrentFuel) {
                icrafting.a(this, 2, this.a.ticksForCurrentFuel);
            }
        }

        this.b = this.a.cookTime;
        this.c = this.a.burnTime;
        this.h = this.a.ticksForCurrentFuel;
    }

    public boolean b(EntityHuman entityhuman) {
        return this.a.a_(entityhuman);
    }

    public ItemStack a(int i) {
        if (!this.canShiftClick()) { // Uberbukkit
            return super.a(i);
        }

        ItemStack itemstack = null;
        Slot slot = (Slot) this.e.get(i);

        if (slot != null && slot.b()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.cloneItemStack();
            if (i == 2) {
                // Shift-click: move as much as possible from output up to 64
                int moved = 0;
                while (itemstack1 != null && moved < 64) {
                    int before = itemstack1.count;
                    this.a(itemstack1, 3, 39, true);
                    int diff = before - (itemstack1 != null ? itemstack1.count : 0);
                    if (diff <= 0) break;
                    moved += diff;
                    itemstack1 = ((Slot) this.e.get(2)).getItem();
                    if (itemstack1 == null) break;
                }
            } else if (i >= 3 && i < 39) {
                // Shift-clicking from player inventory - try to place in furnace slots
                int beforeCount = itemstack1.count;
                boolean isSmeltable = FurnaceRecipes.getInstance().a(itemstack1.getItem().id) != null ||
                                      RecyclingManager.getInstance().getSmeltRecycleResult(itemstack1) != null;
                
                if (isSmeltable) {
                    // Match the client: smeltable items prefer the input slot even if they can also burn.
                    this.a(itemstack1, 0, 1, false);
                } else if (TileEntityFurnace.isFuel(itemstack1)) {
                    this.a(itemstack1, 1, 2, false);
                } else if (i >= 3 && i < 30) {
                    // Not furnace-related, move between inventory sections
                    this.a(itemstack1, 30, 39, false);
                } else {
                    this.a(itemstack1, 3, 30, false);
                }
                
                // If we moved items to furnace slots, mark dirty to trigger smelting
                if (itemstack1.count < beforeCount) {
                    this.a.update();
                }
            } else {
                this.a(itemstack1, 3, 39, false);
            }

            if (itemstack1.count == 0) {
                slot.c((ItemStack) null);
            } else {
                slot.c();
            }

            if (itemstack1.count == itemstack.count) {
                return null;
            }

            slot.a(itemstack);
        }

        return itemstack;
    }
}
