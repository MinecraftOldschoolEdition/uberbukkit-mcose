package net.minecraft.server.registry;

import net.minecraft.server.CraftingRecipe;
import net.minecraft.server.InventoryCrafting;
import net.minecraft.server.ItemStack;

/**
 * Furnace wrapper stored in the generic recipe registry.
 */
public final class SmeltingRecipe implements CraftingRecipe {
    public static final int DEFAULT_COOKING_TIME = 200;
    public static final int MAX_COOKING_TIME = Short.MAX_VALUE;

    private final int inputItemId;
    private final int inputMetadata;
    private final ItemStack output;
    private final int cookingTime;

    public SmeltingRecipe(int inputItemId, ItemStack output) {
        this(inputItemId, -1, output);
    }

    public SmeltingRecipe(int inputItemId, int inputMetadata, ItemStack output) {
        this(inputItemId, inputMetadata, output, DEFAULT_COOKING_TIME);
    }

    public SmeltingRecipe(
            int inputItemId,
            int inputMetadata,
            ItemStack output,
            int cookingTime) {
        if (cookingTime <= 0 || cookingTime > MAX_COOKING_TIME) {
            throw new IllegalArgumentException(
                    "Cooking time must be between 1 and " + MAX_COOKING_TIME);
        }
        this.inputItemId = inputItemId;
        this.inputMetadata = inputMetadata;
        this.output = output;
        this.cookingTime = cookingTime;
    }

    public int getInputItemId() {
        return this.inputItemId;
    }

    public int getInputMetadata() {
        return this.inputMetadata;
    }

    public int getCookingTime() {
        return this.cookingTime;
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
