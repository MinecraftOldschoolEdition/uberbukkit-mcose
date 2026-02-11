package net.minecraft.server.event.events;

import net.minecraft.server.Entity;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.World;
import net.minecraft.server.event.CancellableEvent;

public final class InteractEntityEvent implements CancellableEvent {
    private final EntityHuman player;
    private final Entity target;
    private final World world;
    private boolean cancelled;

    public InteractEntityEvent(EntityHuman player, Entity target, World world) {
        this.player = player;
        this.target = target;
        this.world = world;
    }

    public EntityHuman getPlayer() { return player; }
    public Entity getTarget() { return target; }
    public World getWorld() { return world; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
