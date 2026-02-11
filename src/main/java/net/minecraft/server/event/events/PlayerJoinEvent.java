package net.minecraft.server.event.events;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.World;

public final class PlayerJoinEvent {
    private final EntityPlayer player;
    private final World world;

    public PlayerJoinEvent(EntityPlayer player, World world) {
        this.player = player;
        this.world = world;
    }

    public EntityPlayer getPlayer() { return player; }
    public World getWorld() { return world; }
}
