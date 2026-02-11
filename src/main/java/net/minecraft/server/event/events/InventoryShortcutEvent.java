package net.minecraft.server.event.events;

import net.minecraft.server.Container;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.Slot;
import net.minecraft.server.event.CancellableEvent;

/**
 * Fired when an inventory shortcut gesture is observed on the server.
 *
 * Note: keyboard shortcut intent is not transmitted explicitly by legacy clients,
 * so this event is inferred from window-click packet patterns.
 */
public final class InventoryShortcutEvent implements CancellableEvent {
    public enum Action {
        HOTBAR_SWAP,
        DROP_SINGLE,
        DROP_STACK,
        RECIPE_REFILL
    }

    private final EntityHuman player;
    private final Container container;
    private final Slot hoveredSlot;
    private final Action action;
    private final int keyCode;
    private final int hotbarIndex;
    private final boolean controlDown;
    private final boolean shiftDown;
    private boolean cancelled;

    public InventoryShortcutEvent(
        EntityHuman player,
        Container container,
        Slot hoveredSlot,
        Action action,
        int keyCode,
        int hotbarIndex,
        boolean controlDown,
        boolean shiftDown
    ) {
        this.player = player;
        this.container = container;
        this.hoveredSlot = hoveredSlot;
        this.action = action;
        this.keyCode = keyCode;
        this.hotbarIndex = hotbarIndex;
        this.controlDown = controlDown;
        this.shiftDown = shiftDown;
    }

    public EntityHuman getPlayer() {
        return player;
    }

    public Container getContainer() {
        return container;
    }

    public Slot getHoveredSlot() {
        return hoveredSlot;
    }

    public Action getAction() {
        return action;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public int getHotbarIndex() {
        return hotbarIndex;
    }

    public boolean isControlDown() {
        return controlDown;
    }

    public boolean isShiftDown() {
        return shiftDown;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
