package net.minecraft.server.event.events;

import net.minecraft.server.World;

public final class WorldLoadEvent {
    private final World world;

    public WorldLoadEvent(World world) {
        this.world = world;
    }

    public World getWorld() {
        return world;
    }
}
