/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.bukkit.inject.permissible;

import me.lucko.luckperms.common.plugin.PluginLogger;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.PermissionAttachment;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Injects a {@link LuckPermsPermissible} into a {@link Player}.
 *
 * This allows LuckPerms to directly intercept permission checks and take over all handling of
 * checks made by plugins.
 */
public final class PermissibleInjector {
    private PermissibleInjector() {}

    /**
     * The field where the permissible is stored on CraftHumanEntity.
     * In Uberbukkit, this is "perm" in org.bukkit.craftbukkit.entity.CraftHumanEntity
     */
    private static final Field HUMAN_ENTITY_PERMISSIBLE_FIELD;

    /**
     * The field where attachments are stored on a permissible base.
     */
    private static final Field PERMISSIBLE_BASE_ATTACHMENTS_FIELD;

    static {
        try {
            // Uberbukkit uses simple package naming (no version string)
            Class<?> craftHumanEntity = Class.forName("org.bukkit.craftbukkit.entity.CraftHumanEntity");
            HUMAN_ENTITY_PERMISSIBLE_FIELD = craftHumanEntity.getDeclaredField("perm");
            HUMAN_ENTITY_PERMISSIBLE_FIELD.setAccessible(true);

            PERMISSIBLE_BASE_ATTACHMENTS_FIELD = PermissibleBase.class.getDeclaredField("attachments");
            PERMISSIBLE_BASE_ATTACHMENTS_FIELD.setAccessible(true);
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Injects a {@link LuckPermsPermissible} into a {@link Player}.
     *
     * @param player the player to inject into
     * @param newPermissible the permissible to inject
     * @param logger the plugin logger
     * @throws Exception propagates any exceptions which were thrown during injection
     */
    public static void inject(Player player, LuckPermsPermissible newPermissible, PluginLogger logger) throws Exception {
        // Get the existing PermissibleBase held by the player
        PermissibleBase oldPermissible = (PermissibleBase) HUMAN_ENTITY_PERMISSIBLE_FIELD.get(player);

        // Check if we've already injected
        if (oldPermissible instanceof LuckPermsPermissible) {
            throw new IllegalStateException("LPPermissible already injected into player " + player.toString());
        }

        // Warn if another plugin has already injected a custom permissible
        Class<? extends PermissibleBase> oldClass = oldPermissible.getClass();
        if (!PermissibleBase.class.equals(oldClass)) {
            logger.warn("Player " + player.getName() + " already has a custom permissible (" + oldClass.getName() + ")!\n" +
                    "This is probably because you have multiple permission plugins installed.\n" +
                    "Please make sure that LuckPerms is the only permission plugin installed on your server!");
        }

        // Move attachments over from the old permissible
        @SuppressWarnings("unchecked")
        List<PermissionAttachment> attachments = (List<PermissionAttachment>) PERMISSIBLE_BASE_ATTACHMENTS_FIELD.get(oldPermissible);
        
        newPermissible.convertAndAddAttachments(attachments);
        attachments.clear();
        
        // Note: clearPermissions() is private in Uberbukkit, so we just recalculate
        oldPermissible.recalculatePermissions();

        // Setup the new permissible
        newPermissible.getActive().set(true);
        newPermissible.setOldPermissible(oldPermissible);

        // Inject the new instance
        HUMAN_ENTITY_PERMISSIBLE_FIELD.set(player, newPermissible);
    }

    /**
     * Uninjects a {@link LuckPermsPermissible} from a {@link Player}.
     *
     * @param player the player to uninject from
     * @param dummy if the replacement permissible should be a dummy
     * @throws Exception propagates any exceptions which were thrown during uninjection
     */
    public static void uninject(Player player, boolean dummy) throws Exception {
        // Get the current permissible
        PermissibleBase permissible = (PermissibleBase) HUMAN_ENTITY_PERMISSIBLE_FIELD.get(player);

        // Only uninject if it's a LuckPerms permissible
        if (permissible instanceof LuckPermsPermissible) {
            LuckPermsPermissible lpPermissible = (LuckPermsPermissible) permissible;

            // Clear our permissions
            lpPermissible.clearLPPermissions();

            // Set to inactive
            lpPermissible.getActive().set(false);

            // Handle the replacement permissible
            if (dummy) {
                // Inject a dummy class for quitting players
                HUMAN_ENTITY_PERMISSIBLE_FIELD.set(player, DummyPermissibleBase.INSTANCE);
            } else {
                // Restore the original permissible
                PermissibleBase newPb = lpPermissible.getOldPermissible();
                if (newPb == null) {
                    newPb = new PermissibleBase(player);
                }
                HUMAN_ENTITY_PERMISSIBLE_FIELD.set(player, newPb);
            }
        }
    }

    /**
     * Gets the LuckPermsPermissible for a player, if injected.
     */
    public static LuckPermsPermissible get(Player player) {
        PermissibleBase permissibleBase;
        try {
            permissibleBase = (PermissibleBase) HUMAN_ENTITY_PERMISSIBLE_FIELD.get(player);
        } catch (IllegalAccessException e) {
            return null;
        }
        if (permissibleBase instanceof LuckPermsPermissible) {
            return (LuckPermsPermissible) permissibleBase;
        }
        return null;
    }

    /**
     * Checks if a player has our permissible injected.
     */
    public static void checkInjected(Player player, PluginLogger logger) {
        PermissibleBase permissibleBase;
        try {
            permissibleBase = (PermissibleBase) HUMAN_ENTITY_PERMISSIBLE_FIELD.get(player);
        } catch (IllegalAccessException e) {
            return;
        }

        if (permissibleBase instanceof LuckPermsPermissible) {
            return; // All good
        }

        Class<? extends PermissibleBase> clazz = permissibleBase.getClass();
        logger.warn("Player " + player.getName() + " has a non-LuckPerms permissible (" + clazz.getName() + ")!\n" +
                "This is probably because you have multiple permission plugins installed.\n" +
                "Please make sure that LuckPerms is the only permission plugin installed!");
    }
}
