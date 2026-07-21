package net.minecraft.server;

import me.devcody.uberbukkit.math.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class Container {

    public List d = new ArrayList();
    public List e = new ArrayList();
    public int windowId = 0;
    private short a = 0;
    protected List listeners = new ArrayList();
    private Set b = new HashSet();
    private Vec3i position = null;
    private int quickCraftStatus;
    private int quickCraftType;
    private Set quickCraftSlots = new java.util.LinkedHashSet();

    public Container() {
    }

    protected void a(Slot slot) {
        slot.a = this.e.size();
        this.e.add(slot);
        this.d.add(null);
    }

    public void a(ICrafting icrafting) {
        if (this.listeners.contains(icrafting)) {
            throw new IllegalArgumentException("Listener already listening");
        } else {
            this.listeners.add(icrafting);
            icrafting.a(this, this.b());
            this.a();
        }
    }

    public List b() {
        ArrayList arraylist = new ArrayList();

        for (int i = 0; i < this.e.size(); ++i) {
            arraylist.add(((Slot) this.e.get(i)).getItem());
        }

        return arraylist;
    }

    public void a() {
        for (int i = 0; i < this.e.size(); ++i) {
            ItemStack itemstack = ((Slot) this.e.get(i)).getItem();
            ItemStack itemstack1 = (ItemStack) this.d.get(i);

            if (!ItemStack.equals(itemstack1, itemstack)) {
                itemstack1 = itemstack == null ? null : itemstack.cloneItemStack();
                this.d.set(i, itemstack1);

                for (int j = 0; j < this.listeners.size(); ++j) {
                    ((ICrafting) this.listeners.get(j)).a(this, i, itemstack1);
                }
            }
        }
    }

    public Slot a(IInventory iinventory, int i) {
        for (int j = 0; j < this.e.size(); ++j) {
            Slot slot = (Slot) this.e.get(j);

            if (slot.a(iinventory, i)) {
                return slot;
            }
        }

        return null;
    }

    public Slot b(int i) {
        return (Slot) this.e.get(i);
    }

    public ItemStack a(int i) {
        Slot slot = (Slot) this.e.get(i);

        return slot != null ? slot.getItem() : null;
    }

    public ItemStack a(int i, int j, boolean flag, EntityHuman entityhuman) {
        ItemStack itemstack = null;

        if (j == 0 || j == 1) {
            InventoryPlayer inventoryplayer = entityhuman.inventory;

            if (i == -999) {
                if (inventoryplayer.j() != null) {
                    if (j == 0) {
                        entityhuman.b(inventoryplayer.j());
                        inventoryplayer.b((ItemStack) null);
                    }

                    if (j == 1) {
                        entityhuman.b(inventoryplayer.j().a(1));
                        if (inventoryplayer.j().count == 0) {
                            inventoryplayer.b((ItemStack) null);
                        }
                    }
                }
            } else {
                int k;

                if (flag) {
                    if (i < 0 || i >= this.e.size() || !((Slot) this.e.get(i)).canTakeStack()) {
                        return null;
                    }
                    ItemStack itemstack1 = this.a(i);

                    if (itemstack1 != null) {
                        int l = itemstack1.count;

                        itemstack = itemstack1.cloneItemStack();
                        Slot slot = (Slot) this.e.get(i);

                        if (slot != null && slot.getItem() != null) {
                            k = slot.getItem().count;
                            if (k < l) {
                                this.a(i, j, flag, entityhuman);
                            }
                        }
                    }
                } else {
                    Slot slot1 = (Slot) this.e.get(i);

                    if (slot1 != null) {
                        slot1.c();
                        ItemStack itemstack2 = slot1.getItem();
                        ItemStack itemstack3 = inventoryplayer.j();

                        if (itemstack2 != null) {
                            itemstack = itemstack2.cloneItemStack();
                        }

                        if (itemstack2 == null) {
                            if (itemstack3 != null && slot1.isAllowed(itemstack3)) {
                                k = j == 0 ? itemstack3.count : 1;
                                if (k > slot1.d()) {
                                    k = slot1.d();
                                }

                                slot1.c(itemstack3.a(k));
                                if (itemstack3.count == 0) {
                                    inventoryplayer.b((ItemStack) null);
                                }
                            }
                        } else if (itemstack3 == null && slot1.canTakeStack()) {
                            k = j == 0 ? itemstack2.count : (itemstack2.count + 1) / 2;
                            ItemStack itemstack4 = slot1.a(k);

                            inventoryplayer.b(itemstack4);
                            if (itemstack2.count == 0) {
                                slot1.c((ItemStack) null);
                            }

                            slot1.a(inventoryplayer.j());
                        } else if (itemstack3 != null && slot1.isAllowed(itemstack3)) {
                            if (itemstack2.id == itemstack3.id && (!itemstack2.usesData() || itemstack2.getData() == itemstack3.getData())) {
                                k = j == 0 ? itemstack3.count : 1;
                                if (k > slot1.d() - itemstack2.count) {
                                    k = slot1.d() - itemstack2.count;
                                }

                                if (k > itemstack3.getMaxStackSize() - itemstack2.count) {
                                    k = itemstack3.getMaxStackSize() - itemstack2.count;
                                }

                                itemstack3.a(k);
                                if (itemstack3.count == 0) {
                                    inventoryplayer.b((ItemStack) null);
                                }

                                itemstack2.count += k;
                            } else if (itemstack3.count <= slot1.d() && slot1.canTakeStack()) {
                                slot1.c(itemstack3);
                                inventoryplayer.b(itemstack2);
                            }
                        } else if (itemstack3 != null && slot1.canTakeStack() && itemstack2.id == itemstack3.id && itemstack3.getMaxStackSize() > 1 && (!itemstack2.usesData() || itemstack2.getData() == itemstack3.getData())) {
                            k = itemstack2.count;
                            if (k > 0 && k + itemstack3.count <= itemstack3.getMaxStackSize()) {
                                itemstack3.count += k;
                                itemstack2.a(k);
                                if (itemstack2.count == 0) {
                                    slot1.c((ItemStack) null);
                                }

                                slot1.a(inventoryplayer.j());
                            }
                        }
                    }
                }
            }
        }

        return itemstack;
    }

    /**
     * Handles an explicit snapshot-style container input. The original boolean
     * entry point remains authoritative for pickup and quick move so all
     * existing container overrides continue to participate.
     */
    public ItemStack a(int slotIndex, int button, int containerInput, EntityHuman player) {
        if (!ContainerInput.isValid(containerInput) || player == null || player.inventory == null) {
            return null;
        }
        if (containerInput != ContainerInput.QUICK_CRAFT) {
            this.resetQuickCraft();
        }
        switch (containerInput) {
            case ContainerInput.PICKUP:
                return this.a(slotIndex, button, false, player);
            case ContainerInput.QUICK_MOVE:
                return this.a(slotIndex, button, true, player);
            case ContainerInput.SWAP:
                return this.swapWithHotbar(slotIndex, button, player);
            case ContainerInput.CLONE:
                return this.cloneToCursor(slotIndex, player);
            case ContainerInput.THROW:
                return this.throwFromSlot(slotIndex, button, player);
            case ContainerInput.QUICK_CRAFT:
                return this.quickCraft(slotIndex, button, player);
            case ContainerInput.PICKUP_ALL:
                return this.pickupAll(slotIndex, button, player);
            default:
                return null;
        }
    }

    private ItemStack swapWithHotbar(int slotIndex, int hotbarIndex, EntityHuman player) {
        if (slotIndex < 0 || slotIndex >= this.e.size() || hotbarIndex < 0 || hotbarIndex >= 9
                || player.inventory.j() != null) {
            return null;
        }
        Slot target = (Slot) this.e.get(slotIndex);
        ItemStack targetStack = target == null ? null : target.getItem();
        ItemStack result = targetStack == null ? null : targetStack.cloneItemStack();
        if (target == null || target.inventory == player.inventory && target.index == hotbarIndex) {
            return result;
        }

        ItemStack hotbarStack = player.inventory.getItem(hotbarIndex);
        if (hotbarStack == null) {
            if (targetStack != null && target.canTakeStack()) {
                player.inventory.setItem(hotbarIndex, targetStack);
                target.c((ItemStack) null);
                target.a(targetStack);
            }
        } else if (targetStack == null) {
            if (target.isAllowed(hotbarStack)) {
                int amount = Math.min(hotbarStack.count, target.d());
                ItemStack placed = hotbarStack.a(amount);
                target.c(placed);
                if (hotbarStack.count == 0) {
                    player.inventory.setItem(hotbarIndex, (ItemStack) null);
                }
            }
        } else if (target.canTakeStack() && target.isAllowed(hotbarStack)) {
            int limit = Math.min(target.d(), hotbarStack.getMaxStackSize());
            if (hotbarStack.count <= limit) {
                player.inventory.setItem(hotbarIndex, targetStack);
                target.c(hotbarStack);
                target.a(targetStack);
            } else {
                ItemStack placed = hotbarStack.a(limit);
                target.c(placed);
                target.a(targetStack);
                player.inventory.pickup(targetStack);
                if (targetStack.count > 0) {
                    player.b(targetStack);
                }
            }
        }
        return result;
    }

    private ItemStack cloneToCursor(int slotIndex, EntityHuman player) {
        if (player.gameMode != 1 || player.inventory.j() != null
                || slotIndex < 0 || slotIndex >= this.e.size()) {
            return null;
        }
        Slot slot = (Slot) this.e.get(slotIndex);
        if (slot == null || !slot.b()) {
            return null;
        }
        ItemStack result = slot.getItem().cloneItemStack();
        ItemStack carried = result.cloneItemStack();
        carried.count = carried.getMaxStackSize();
        player.inventory.b(carried);
        return result;
    }

    private ItemStack throwFromSlot(int slotIndex, int button, EntityHuman player) {
        if (player.inventory.j() != null || slotIndex < 0 || slotIndex >= this.e.size()
                || (button != 0 && button != 1)) {
            return null;
        }
        Slot slot = (Slot) this.e.get(slotIndex);
        if (slot == null || !slot.b() || !slot.canTakeStack()) {
            return null;
        }
        ItemStack result = slot.getItem().cloneItemStack();
        for (int iteration = 0; iteration < 128 && slot.b(); ++iteration) {
            int amount = button == 0 ? 1 : slot.getItem().count;
            ItemStack dropped = slot.a(amount);
            if (dropped == null) {
                break;
            }
            slot.a(dropped);
            player.b(dropped);
            if (button == 0 || !slot.b() || !this.canStacksMerge(slot.getItem(), dropped)) {
                break;
            }
        }
        return result;
    }

    private ItemStack quickCraft(int slotIndex, int mask, EntityHuman player) {
        int previousStatus = this.quickCraftStatus;
        this.quickCraftStatus = ContainerInput.getQuickCraftHeader(mask);
        if ((previousStatus != 1 || this.quickCraftStatus != 2) && previousStatus != this.quickCraftStatus) {
            this.resetQuickCraft();
            return null;
        }
        ItemStack carried = player.inventory.j();
        if (carried == null) {
            this.resetQuickCraft();
            return null;
        }
        if (this.quickCraftStatus == 0) {
            this.quickCraftType = ContainerInput.getQuickCraftType(mask);
            if (this.quickCraftType == 0 || this.quickCraftType == 1
                    || this.quickCraftType == 2 && player.gameMode == 1) {
                this.quickCraftStatus = 1;
                this.quickCraftSlots.clear();
            } else {
                this.resetQuickCraft();
            }
            return null;
        }
        if (this.quickCraftStatus == 1) {
            if (slotIndex >= 0 && slotIndex < this.e.size()) {
                Slot slot = (Slot) this.e.get(slotIndex);
                if (this.canQuickCraftInto(slot, carried)
                        && (this.quickCraftType == 2 || carried.count > this.quickCraftSlots.size())) {
                    this.quickCraftSlots.add(slot);
                }
            }
            return null;
        }
        if (this.quickCraftStatus == 2) {
            this.finishQuickCraft(player, carried);
        }
        this.resetQuickCraft();
        return null;
    }

    private void finishQuickCraft(EntityHuman player, ItemStack carried) {
        if (this.quickCraftSlots.isEmpty()) {
            return;
        }
        if (this.quickCraftType == 2 && this.quickCraftSlots.size() == 1) {
            return;
        }
        ItemStack source = carried.cloneItemStack();
        int remaining = carried.count;
        int slotCount = this.quickCraftSlots.size();
        for (Object value : this.quickCraftSlots) {
            Slot slot = (Slot) value;
            if (!this.canQuickCraftInto(slot, carried)) {
                continue;
            }
            ItemStack existing = slot.getItem();
            int existingCount = existing == null ? 0 : existing.count;
            int perSlot = this.quickCraftType == 0 ? source.count / slotCount
                    : this.quickCraftType == 1 ? 1 : source.getMaxStackSize();
            int max = Math.min(source.getMaxStackSize(), slot.d());
            int newCount = Math.min(existingCount + perSlot, max);
            int added = newCount - existingCount;
            if (added <= 0 || this.quickCraftType != 2 && added > remaining) {
                continue;
            }
            ItemStack placed = source.cloneItemStack();
            placed.count = newCount;
            slot.c(placed);
            remaining -= added;
        }
        if (remaining <= 0) {
            player.inventory.b((ItemStack) null);
        } else {
            carried.count = remaining;
        }
    }

    private boolean canQuickCraftInto(Slot slot, ItemStack carried) {
        if (slot == null || carried == null || !slot.isAllowed(carried)) {
            return false;
        }
        ItemStack existing = slot.getItem();
        return existing == null || this.canStacksMerge(existing, carried)
                && existing.count < Math.min(existing.getMaxStackSize(), slot.d());
    }

    private ItemStack pickupAll(int slotIndex, int button, EntityHuman player) {
        ItemStack carried = player.inventory.j();
        if (carried == null || (button != 0 && button != 1)
                || slotIndex < 0 || slotIndex >= this.e.size()) {
            return null;
        }
        Slot clickedSlot = (Slot) this.e.get(slotIndex);
        ItemStack result = clickedSlot.getItem();
        result = result == null ? null : result.cloneItemStack();
        if (clickedSlot.b() && clickedSlot.canTakeStack()) {
            return result;
        }
        int start = button == 0 ? 0 : this.e.size() - 1;
        int step = button == 0 ? 1 : -1;
        for (int pass = 0; pass < 2 && carried.count < carried.getMaxStackSize(); ++pass) {
            for (int i = start; i >= 0 && i < this.e.size() && carried.count < carried.getMaxStackSize(); i += step) {
                Slot slot = (Slot) this.e.get(i);
                ItemStack stack = slot == null ? null : slot.getItem();
                if (stack == null || !this.canStacksMerge(stack, carried) || !slot.canTakeStack()
                        || !this.canTakeItemForPickAll(slot)) {
                    continue;
                }
                if (pass == 0 && stack.count == stack.getMaxStackSize()) {
                    continue;
                }
                int amount = Math.min(stack.count, carried.getMaxStackSize() - carried.count);
                ItemStack removed = slot.a(amount);
                if (removed != null) {
                    carried.count += removed.count;
                    slot.a(removed);
                }
            }
        }
        return result;
    }

    protected boolean canTakeItemForPickAll(Slot slot) {
        // Crafting results are excluded by InventoryMenu/CraftingMenu in the
        // snapshot; furnace results deliberately remain gatherable.
        return !(slot instanceof SlotResult);
    }

    private boolean canStacksMerge(ItemStack first, ItemStack second) {
        return first != null && second != null && first.id == second.id
                && (!first.usesData() || first.getData() == second.getData());
    }

    private void resetQuickCraft() {
        this.quickCraftStatus = 0;
        this.quickCraftSlots.clear();
    }

    public void a(EntityHuman entityhuman) {
        InventoryPlayer inventoryplayer = entityhuman.inventory;

        if (inventoryplayer.j() != null) {
            entityhuman.b(inventoryplayer.j());
            inventoryplayer.b((ItemStack) null);
        }
    }

    public void a(IInventory iinventory) {
        this.a();
    }

    public boolean c(EntityHuman entityhuman) {
        return !this.b.contains(entityhuman);
    }

    public void a(EntityHuman entityhuman, boolean flag) {
        if (flag) {
            this.b.remove(entityhuman);
        } else {
            this.b.add(entityhuman);
        }
    }

    public abstract boolean b(EntityHuman entityhuman);

    protected void a(ItemStack itemstack, int i, int j, boolean flag) {
        int k = i;

        if (flag) {
            k = j - 1;
        }

        Slot slot;
        ItemStack itemstack1;

        if (itemstack.isStackable()) {
            while (itemstack.count > 0 && (!flag && k < j || flag && k >= i)) {
                slot = (Slot) this.e.get(k);
                itemstack1 = slot.getItem();
                if (itemstack1 != null && itemstack1.id == itemstack.id && (!itemstack.usesData() || itemstack.getData() == itemstack1.getData())) {
                    int l = itemstack1.count + itemstack.count;

                    if (l <= itemstack.getMaxStackSize()) {
                        itemstack.count = 0;
                        itemstack1.count = l;
                        slot.c();
                    } else if (itemstack1.count < itemstack.getMaxStackSize()) {
                        itemstack.count -= itemstack.getMaxStackSize() - itemstack1.count;
                        itemstack1.count = itemstack.getMaxStackSize();
                        slot.c();
                    }
                }

                if (flag) {
                    --k;
                } else {
                    ++k;
                }
            }
        }

        if (itemstack.count > 0) {
            if (flag) {
                k = j - 1;
            } else {
                k = i;
            }

            while (!flag && k < j || flag && k >= i) {
                slot = (Slot) this.e.get(k);
                itemstack1 = slot.getItem();
                if (itemstack1 == null) {
                    slot.c(itemstack.cloneItemStack());
                    slot.c();
                    itemstack.count = 0;
                    break;
                }

                if (flag) {
                    --k;
                } else {
                    ++k;
                }
            }
        }
    }

    public boolean isPositioned() {
        return (this.position != null);
    }

    public void setPosition(Vec3i position) {
        this.position = position;
    }

    @Nullable
    public Vec3i getPosition() {
        return position;
    }
}
