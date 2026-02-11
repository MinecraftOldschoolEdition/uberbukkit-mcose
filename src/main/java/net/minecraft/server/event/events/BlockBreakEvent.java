package net.minecraft.server.event.events;

import net.minecraft.server.EntityHuman;
import net.minecraft.server.World;
import net.minecraft.server.event.CancellableEvent;

public final class BlockBreakEvent implements CancellableEvent {
    private final EntityHuman player;
    private final World world;
    private final int x;
    private final int y;
    private final int z;
    private final int blockId;
    private final int metadata;
    private boolean cancelled;

    public BlockBreakEvent(EntityHuman player, World world, int x, int y, int z, int blockId, int metadata) {
        this.player = player;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockId = blockId;
        this.metadata = metadata;
    }

    public EntityHuman getPlayer() { return player; }
    public World getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public int getBlockId() { return blockId; }
    public int getMetadata() { return metadata; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
