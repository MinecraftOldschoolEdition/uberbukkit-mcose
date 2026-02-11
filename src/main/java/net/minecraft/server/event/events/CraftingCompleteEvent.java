package net.minecraft.server.event.events;

import net.minecraft.server.EntityHuman;
import net.minecraft.server.ItemStack;
import net.minecraft.server.World;

public final class CraftingCompleteEvent {
    private final EntityHuman player;
    private final World world;
    private final ItemStack output;
    private final int amount;
    private final String recipeId;

    public CraftingCompleteEvent(EntityHuman player, World world, ItemStack output, int amount, String recipeId) {
        this.player = player;
        this.world = world;
        this.output = output;
        this.amount = amount;
        this.recipeId = recipeId;
    }

    public EntityHuman getPlayer() { return player; }
    public World getWorld() { return world; }
    public ItemStack getOutput() { return output; }
    public int getAmount() { return amount; }
    public String getRecipeId() { return recipeId; }
}
