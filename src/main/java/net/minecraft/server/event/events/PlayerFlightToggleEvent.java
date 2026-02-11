package net.minecraft.server.event.events;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.World;
import net.minecraft.server.event.CancellableEvent;

public final class PlayerFlightToggleEvent implements CancellableEvent {
    private final EntityPlayer player;
    private final World world;
    private final boolean previousFlying;
    private boolean newFlying;
    private final String reason;
    private boolean cancelled;

    public PlayerFlightToggleEvent(EntityPlayer player, World world, boolean previousFlying, boolean newFlying, String reason) {
        this.player = player;
        this.world = world;
        this.previousFlying = previousFlying;
        this.newFlying = newFlying;
        this.reason = reason;
    }

    public EntityPlayer getPlayer() { return player; }
    public World getWorld() { return world; }
    public boolean getPreviousFlying() { return previousFlying; }
    public boolean getNewFlying() { return newFlying; }
    public void setNewFlying(boolean newFlying) { this.newFlying = newFlying; }
    public String getReason() { return reason; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
