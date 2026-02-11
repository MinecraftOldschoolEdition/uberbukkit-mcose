package net.minecraft.server.registry;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.event.EventBus;
import net.minecraft.server.event.EventListener;
import net.minecraft.server.event.EventPriority;
import net.minecraft.server.event.events.AttackEntityEvent;
import net.minecraft.server.event.events.BlockBreakEvent;
import net.minecraft.server.event.events.BlockPlaceEvent;
import net.minecraft.server.event.events.InteractEntityEvent;
import net.minecraft.server.event.events.InventoryShortcutEvent;
import net.minecraft.server.event.events.PlayerDamageEvent;
import net.minecraft.server.event.events.PlayerDealDamageEvent;
import net.minecraft.server.event.events.PlayerFlightToggleEvent;
import net.minecraft.server.event.events.PlayerJumpEvent;
import net.minecraft.server.event.events.PlayerMoveEvent;
import net.minecraft.server.event.events.UseItemEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Public API for player capability flags and player-action hooks.
 */
public final class PlayerCapabilityRegistryApi {
    private static final Map<EntityPlayer, CapabilityState> STATES = new WeakHashMap<EntityPlayer, CapabilityState>();

    private static final class CapabilityState {
        private Boolean allowFlying;
        private Boolean flying;
        private Boolean invulnerable;
        private Boolean instabuild;
    }

    private PlayerCapabilityRegistryApi() {}

    public static boolean canFly(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        CapabilityState state = state(player, false);
        if (state != null && state.allowFlying != null) {
            return state.allowFlying.booleanValue();
        }
        return player.gameMode == 1;
    }

    public static boolean isFlying(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        CapabilityState state = state(player, false);
        if (state != null && state.flying != null) {
            return state.flying.booleanValue();
        }
        return canFly(player) && !player.onGround;
    }

    public static boolean isInvulnerable(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        CapabilityState state = state(player, false);
        if (state != null && state.invulnerable != null) {
            return state.invulnerable.booleanValue();
        }
        return player.gameMode == 1;
    }

    public static boolean canInstabuild(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        CapabilityState state = state(player, false);
        if (state != null && state.instabuild != null) {
            return state.instabuild.booleanValue();
        }
        return player.gameMode == 1;
    }

    public static void setAllowFlying(EntityPlayer player, boolean allowFlying) {
        if (player == null) {
            return;
        }

        boolean previousFlying = isFlying(player);
        CapabilityState state = state(player, true);
        state.allowFlying = Boolean.valueOf(allowFlying);

        if (!allowFlying) {
            state.flying = Boolean.FALSE;
        }

        boolean currentFlying = isFlying(player);
        if (previousFlying != currentFlying) {
            currentFlying = applyFlightToggle(player, previousFlying, currentFlying, "allow_flight");
            state.flying = Boolean.valueOf(currentFlying);
        }
    }

    public static void setFlying(EntityPlayer player, boolean flying) {
        if (player == null) {
            return;
        }

        boolean previousFlying = isFlying(player);
        if (flying && !canFly(player)) {
            return;
        }

        boolean next = applyFlightToggle(player, previousFlying, flying, "api");
        state(player, true).flying = Boolean.valueOf(next);
    }

    public static void setInvulnerable(EntityPlayer player, boolean invulnerable) {
        if (player == null) {
            return;
        }
        state(player, true).invulnerable = Boolean.valueOf(invulnerable);
    }

    public static void setInstabuild(EntityPlayer player, boolean instabuild) {
        if (player == null) {
            return;
        }
        state(player, true).instabuild = Boolean.valueOf(instabuild);
    }

    public static boolean canAffectBlocks(EntityPlayer player) {
        return player != null && player.T();
    }

    public static boolean canAffectEntities(EntityPlayer player) {
        return player != null && player.T();
    }

    public static void handleGroundStateUpdate(EntityPlayer player, boolean onGround) {
        if (player == null) {
            return;
        }

        boolean previousFlying = isFlying(player);
        boolean requestedFlying = canFly(player) && !onGround;
        if (previousFlying == requestedFlying) {
            return;
        }

        boolean resolved = applyFlightToggle(player, previousFlying, requestedFlying, onGround ? "ground_touch" : "movement");
        state(player, true).flying = Boolean.valueOf(resolved);
    }

    public static void handleGameModeUpdate(EntityPlayer player, int previousGameMode, int newGameMode) {
        if (player == null) {
            return;
        }

        CapabilityState state = state(player, true);
        boolean previousCanFly = state.allowFlying != null ? state.allowFlying.booleanValue() : previousGameMode == 1;
        boolean previousFlying = state.flying != null ? state.flying.booleanValue() : (previousCanFly && !player.onGround);
        boolean currentCanFly = state.allowFlying != null ? state.allowFlying.booleanValue() : newGameMode == 1;

        if (!currentCanFly && previousFlying) {
            boolean resolved = applyFlightToggle(player, true, false, "gamemode");
            state.flying = Boolean.valueOf(resolved && currentCanFly);
        }
    }

    public static void onMove(EventPriority priority, EventListener<PlayerMoveEvent> listener) {
        EventBus.global().subscribe(PlayerMoveEvent.class, priority, listener);
    }

    public static void onJump(EventPriority priority, EventListener<PlayerJumpEvent> listener) {
        EventBus.global().subscribe(PlayerJumpEvent.class, priority, listener);
    }

    public static void onFlightToggle(EventPriority priority, EventListener<PlayerFlightToggleEvent> listener) {
        EventBus.global().subscribe(PlayerFlightToggleEvent.class, priority, listener);
    }

    public static void onDamageTaken(EventPriority priority, EventListener<PlayerDamageEvent> listener) {
        EventBus.global().subscribe(PlayerDamageEvent.class, priority, listener);
    }

    public static void onDamageDealt(EventPriority priority, EventListener<PlayerDealDamageEvent> listener) {
        EventBus.global().subscribe(PlayerDealDamageEvent.class, priority, listener);
    }

    public static void onBlockBreak(EventPriority priority, EventListener<BlockBreakEvent> listener) {
        EventBus.global().subscribe(BlockBreakEvent.class, priority, listener);
    }

    public static void onBlockPlace(EventPriority priority, EventListener<BlockPlaceEvent> listener) {
        EventBus.global().subscribe(BlockPlaceEvent.class, priority, listener);
    }

    public static void onUseItem(EventPriority priority, EventListener<UseItemEvent> listener) {
        EventBus.global().subscribe(UseItemEvent.class, priority, listener);
    }

    public static void onAttackEntity(EventPriority priority, EventListener<AttackEntityEvent> listener) {
        EventBus.global().subscribe(AttackEntityEvent.class, priority, listener);
    }

    public static void onInteractEntity(EventPriority priority, EventListener<InteractEntityEvent> listener) {
        EventBus.global().subscribe(InteractEntityEvent.class, priority, listener);
    }

    public static void onInventoryShortcut(EventPriority priority, EventListener<InventoryShortcutEvent> listener) {
        EventBus.global().subscribe(InventoryShortcutEvent.class, priority, listener);
    }

    private static boolean applyFlightToggle(EntityPlayer player, boolean previousFlying, boolean requestedFlying, String reason) {
        PlayerFlightToggleEvent event = new PlayerFlightToggleEvent(player, player.world, previousFlying, requestedFlying, reason);
        EventBus.global().publish(event);
        if (event.isCancelled()) {
            return previousFlying;
        }
        return event.getNewFlying();
    }

    private static CapabilityState state(EntityPlayer player, boolean create) {
        CapabilityState state = STATES.get(player);
        if (state == null && create) {
            state = new CapabilityState();
            STATES.put(player, state);
        }
        return state;
    }
}
