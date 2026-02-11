package net.minecraft.server.registry;

import net.minecraft.server.CraftingRecipe;
import net.minecraft.server.InventoryCrafting;
import net.minecraft.server.ItemStack;

/**
 * Furnace wrapper stored in the generic recipe registry.
 */
public final class SmeltingRecipe implements CraftingRecipe {
    private final int inputItemId;
    private final ItemStack output;

    public SmeltingRecipe(int inputItemId, ItemStack output) {
        this.inputItemId = inputItemId;
        this.output = output;
    }

    public int getInputItemId() {
        return this.inputItemId;
    }

    public ItemStack getSmeltingResult() {
        return this.output == null ? null : this.output.cloneItemStack();
    }

    public boolean a(InventoryCrafting inventorycrafting) {
        return false;
    }

    public ItemStack b(InventoryCrafting inventorycrafting) {
        return this.output == null ? null : this.output.cloneItemStack();
    }

    public int a() {
        return 1;
    }

    public ItemStack b() {
        return this.output;
    }
}

