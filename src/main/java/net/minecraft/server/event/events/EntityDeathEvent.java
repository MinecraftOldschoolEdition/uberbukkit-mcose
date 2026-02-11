package net.minecraft.server.event.events;

import net.minecraft.server.Entity;
import net.minecraft.server.World;

public final class EntityDeathEvent {
    private final Entity entity;
    private final Entity killer;
    private final World world;

    public EntityDeathEvent(Entity entity, Entity killer, World world) {
        this.entity = entity;
        this.killer = killer;
        this.world = world;
    }

    public Entity getEntity() { return entity; }
    public Entity getKiller() { return killer; }
    public World getWorld() { return world; }
}
