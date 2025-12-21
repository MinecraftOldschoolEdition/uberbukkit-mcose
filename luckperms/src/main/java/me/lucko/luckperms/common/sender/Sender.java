/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 */

package me.lucko.luckperms.common.sender;

import me.lucko.luckperms.bukkit.LPBukkitPlugin;
import me.lucko.luckperms.common.util.Tristate;

import java.util.UUID;

/**
 * Wrapper interface to represent a CommandSender/CommandSource within the common command implementations.
 */
public interface Sender {

    /** The uuid used by the console sender. */
    UUID CONSOLE_UUID = new UUID(0, 0); // 00000000-0000-0000-0000-000000000000
    /** The name used by the console sender. */
    String CONSOLE_NAME = "Console";

    /**
     * Gets the plugin instance the sender is from.
     *
     * @return the plugin
     */
    LPBukkitPlugin getPlugin();

    /**
     * Gets the sender's username
     *
     * @return a friendly username for the sender
     */
    String getName();

    /**
     * Gets a string representing the senders username, and their current location
     * within the network.
     *
     * @return a friendly identifier for the sender
     */
    default String getNameWithLocation() {
        return getName();
    }

    /**
     * Gets the sender's unique id.
     *
     * <p>See {@link #CONSOLE_UUID} for the console's UUID representation.</p>
     *
     * @return the sender's uuid
     */
    UUID getUniqueId();

    /**
     * Send a message to the Sender.
     *
     * @param message the message to send.
     */
    void sendMessage(String message);

    /**
     * Gets the tristate a permission is set to.
     *
     * @param permission the permission to check for
     * @return a tristate
     */
    Tristate getPermissionValue(String permission);

    /**
     * Check if the Sender has a permission.
     *
     * @param permission the permission to check for
     * @return true if the sender has the permission
     */
    boolean hasPermission(String permission);

    /**
     * Makes the sender perform a command.
     *
     * @param commandLine the command
     */
    void performCommand(String commandLine);

    /**
     * Gets whether this sender is the console
     *
     * @return if the sender is the console
     */
    boolean isConsole();

    /**
     * Gets whether this sender is still valid & receiving messages.
     *
     * @return if this sender is valid
     */
    default boolean isValid() {
        return true;
    }

}

