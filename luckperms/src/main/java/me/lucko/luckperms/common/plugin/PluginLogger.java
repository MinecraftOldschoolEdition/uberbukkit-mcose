/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 */

package me.lucko.luckperms.common.plugin;

/**
 * Represents a logger instance.
 */
public interface PluginLogger {
    
    void info(String message);
    
    void warn(String message);
    
    void warn(String message, Throwable t);
    
    void severe(String message);
    
    void severe(String message, Throwable t);
}

