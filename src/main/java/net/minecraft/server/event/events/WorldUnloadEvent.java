package net.minecraft.server.event.events;

import net.minecraft.server.World;

public final class WorldUnloadEvent {
    private final World world;

    public WorldUnloadEvent(World world) {
        this.world = world;
    }

    public World getWorld() {
        return world;
    }
}
