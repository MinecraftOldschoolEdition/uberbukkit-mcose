package net.minecraft.server.event;

public interface EventListener<E> {
    void handle(E event);
}
