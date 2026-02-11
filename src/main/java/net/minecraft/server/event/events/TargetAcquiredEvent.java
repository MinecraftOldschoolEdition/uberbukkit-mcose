package net.minecraft.server.event.events;

import net.minecraft.server.Entity;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.World;
import net.minecraft.server.event.CancellableEvent;

public final class TargetAcquiredEvent implements CancellableEvent {
    private final EntityLiving source;
    private Entity target;
    private final World world;
    private boolean cancelled;

    public TargetAcquiredEvent(EntityLiving source, Entity target, World world) {
        this.source = source;
        this.target = target;
        this.world = world;
    }

    public EntityLiving getSource() { return source; }
    public Entity getTarget() { return target; }
    public void setTarget(Entity target) { this.target = target; }
    public World getWorld() { return world; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
