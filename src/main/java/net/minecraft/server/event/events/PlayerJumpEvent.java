package net.minecraft.server.event.events;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.World;
import net.minecraft.server.event.CancellableEvent;

public final class PlayerJumpEvent implements CancellableEvent {
    private final EntityPlayer player;
    private final World world;
    private double jumpVelocity;
    private boolean cancelled;

    public PlayerJumpEvent(EntityPlayer player, World world, double jumpVelocity) {
        this.player = player;
        this.world = world;
        this.jumpVelocity = jumpVelocity;
    }

    public EntityPlayer getPlayer() { return player; }
    public World getWorld() { return world; }
    public double getJumpVelocity() { return jumpVelocity; }
    public void setJumpVelocity(double jumpVelocity) { this.jumpVelocity = jumpVelocity; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
