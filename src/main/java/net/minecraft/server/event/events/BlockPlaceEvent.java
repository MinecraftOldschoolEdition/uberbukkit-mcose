package net.minecraft.server.event.events;

import net.minecraft.server.EntityHuman;
import net.minecraft.server.ItemStack;
import net.minecraft.server.World;
import net.minecraft.server.event.CancellableEvent;

public final class BlockPlaceEvent implements CancellableEvent {
    private final EntityHuman player;
    private final World world;
    private final int x;
    private final int y;
    private final int z;
    private final int face;
    private final ItemStack stack;
    private boolean cancelled;

    public BlockPlaceEvent(EntityHuman player, World world, int x, int y, int z, int face, ItemStack stack) {
        this.player = player;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.face = face;
        this.stack = stack;
    }

    public EntityHuman getPlayer() { return player; }
    public World getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public int getFace() { return face; }
    public ItemStack getStack() { return stack; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
