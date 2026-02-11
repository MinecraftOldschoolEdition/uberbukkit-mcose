package net.minecraft.server.event.events;

import net.minecraft.server.Entity;
import net.minecraft.server.World;
import net.minecraft.server.event.CancellableEvent;

public final class EntitySpawnEvent implements CancellableEvent {
    private final Entity entity;
    private final World world;
    private final String spawnReason;
    private boolean cancelled;

    public EntitySpawnEvent(Entity entity, World world, String spawnReason) {
        this.entity = entity;
        this.world = world;
        this.spawnReason = spawnReason;
    }

    public Entity getEntity() { return entity; }
    public World getWorld() { return world; }
    public String getSpawnReason() { return spawnReason; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
