package net.minecraft.server.event.events;

import net.minecraft.server.EntityHuman;
import net.minecraft.server.ItemStack;
import net.minecraft.server.World;
import net.minecraft.server.event.CancellableEvent;

public final class UseItemEvent implements CancellableEvent {
    private final EntityHuman player;
    private final World world;
    private final ItemStack stack;
    private boolean cancelled;

    public UseItemEvent(EntityHuman player, World world, ItemStack stack) {
        this.player = player;
        this.world = world;
        this.stack = stack;
    }

    public EntityHuman getPlayer() { return player; }
    public World getWorld() { return world; }
    public ItemStack getStack() { return stack; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
