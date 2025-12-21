/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.common.storage;

import me.lucko.luckperms.bukkit.LPBukkitPlugin;
import me.lucko.luckperms.common.storage.implementation.yaml.YamlStorage;

/**
 * Factory for creating Storage implementations.
 */
public class StorageFactory {
    
    private StorageFactory() {}
    
    /**
     * Creates a Storage instance based on configuration.
     */
    public static Storage create(LPBukkitPlugin plugin) {
        // For now, default to YAML storage
        // In the future, this could read from config to support MySQL, etc.
        Storage storage = new YamlStorage(plugin);
        storage.init();
        return storage;
    }
}

