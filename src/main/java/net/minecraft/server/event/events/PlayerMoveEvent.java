package net.minecraft.server.event.events;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.World;
import net.minecraft.server.event.CancellableEvent;

public final class PlayerMoveEvent implements CancellableEvent {
    private final EntityPlayer player;
    private final World world;
    private final double startX;
    private final double startY;
    private final double startZ;
    private final boolean flying;
    private float strafe;
    private float forward;
    private boolean cancelled;

    public PlayerMoveEvent(
            EntityPlayer player,
            World world,
            double startX,
            double startY,
            double startZ,
            float strafe,
            float forward,
            boolean flying
    ) {
        this.player = player;
        this.world = world;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.strafe = strafe;
        this.forward = forward;
        this.flying = flying;
    }

    public EntityPlayer getPlayer() { return player; }
    public World getWorld() { return world; }
    public double getStartX() { return startX; }
    public double getStartY() { return startY; }
    public double getStartZ() { return startZ; }
    public boolean isFlying() { return flying; }
    public float getStrafe() { return strafe; }
    public float getForward() { return forward; }
    public void setStrafe(float strafe) { this.strafe = strafe; }
    public void setForward(float forward) { this.forward = forward; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
