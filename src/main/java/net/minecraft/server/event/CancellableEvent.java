package net.minecraft.server.event;

public interface CancellableEvent {
    boolean isCancelled();
    void setCancelled(boolean cancelled);
}
