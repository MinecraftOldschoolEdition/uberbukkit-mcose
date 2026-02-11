package net.minecraft.server.event.events;

import net.minecraft.server.Entity;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.World;
import net.minecraft.server.event.CancellableEvent;

public final class PlayerDamageEvent implements CancellableEvent {
    private final EntityPlayer player;
    private final World world;
    private final Entity directSource;
    private final Entity resolvedSource;
    private final PlayerDamageType damageType;
    private int amount;
    private boolean cancelled;

    public PlayerDamageEvent(
            EntityPlayer player,
            World world,
            Entity directSource,
            Entity resolvedSource,
            PlayerDamageType damageType,
            int amount
    ) {
        this.player = player;
        this.world = world;
        this.directSource = directSource;
        this.resolvedSource = resolvedSource;
        this.damageType = damageType == null ? PlayerDamageType.GENERIC : damageType;
        this.amount = amount;
    }

    public EntityPlayer getPlayer() { return player; }
    public World getWorld() { return world; }
    public Entity getDirectSource() { return directSource; }
    public Entity getResolvedSource() { return resolvedSource; }
    public PlayerDamageType getDamageType() { return damageType; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = Math.max(0, amount); }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
