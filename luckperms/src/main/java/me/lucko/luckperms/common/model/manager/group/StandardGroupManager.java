/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.common.model.manager.group;

import me.lucko.luckperms.bukkit.LPBukkitPlugin;
import me.lucko.luckperms.common.model.Group;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for Group instances.
 */
public class StandardGroupManager {
    
    private final LPBukkitPlugin plugin;
    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    
    public StandardGroupManager(LPBukkitPlugin plugin) {
        this.plugin = plugin;
        
        // Create default group
        groups.put("default", new Group("default"));
    }
    
    public Group getOrMake(String name) {
        return this.groups.computeIfAbsent(name.toLowerCase(), Group::new);
    }
    
    public Group getIfLoaded(String name) {
        return this.groups.get(name.toLowerCase());
    }
    
    public void unload(String name) {
        this.groups.remove(name.toLowerCase());
    }
    
    public boolean isLoaded(String name) {
        return this.groups.containsKey(name.toLowerCase());
    }
    
    public Map<String, Group> getAll() {
        return this.groups;
    }
}

