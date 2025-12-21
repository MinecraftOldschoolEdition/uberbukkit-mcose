/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.bukkit.inject.server;

import me.lucko.luckperms.bukkit.LPBukkitPlugin;

import org.bukkit.permissions.Permission;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A custom permission map that integrates with LuckPerms.
 */
public class LuckPermsPermissionMap implements Map<String, Permission> {
    
    private final LPBukkitPlugin plugin;
    private final Map<String, Permission> backing;
    
    public LuckPermsPermissionMap(LPBukkitPlugin plugin, Map<String, Permission> existingData) {
        this.plugin = plugin;
        this.backing = new ConcurrentHashMap<>();
        
        if (existingData != null) {
            this.backing.putAll(existingData);
        }
    }
    
    public LPBukkitPlugin getPlugin() {
        return this.plugin;
    }
    
    public Map<String, Permission> detach() {
        return new HashMap<>(this.backing);
    }
    
    @Override
    public int size() {
        return this.backing.size();
    }
    
    @Override
    public boolean isEmpty() {
        return this.backing.isEmpty();
    }
    
    @Override
    public boolean containsKey(Object key) {
        return this.backing.containsKey(key instanceof String ? ((String) key).toLowerCase() : key);
    }
    
    @Override
    public boolean containsValue(Object value) {
        return this.backing.containsValue(value);
    }
    
    @Override
    public Permission get(Object key) {
        return this.backing.get(key instanceof String ? ((String) key).toLowerCase() : key);
    }
    
    @Override
    public Permission put(String key, Permission value) {
        return this.backing.put(key.toLowerCase(), value);
    }
    
    @Override
    public Permission remove(Object key) {
        return this.backing.remove(key instanceof String ? ((String) key).toLowerCase() : key);
    }
    
    @Override
    public void putAll(Map<? extends String, ? extends Permission> m) {
        for (Entry<? extends String, ? extends Permission> entry : m.entrySet()) {
            this.backing.put(entry.getKey().toLowerCase(), entry.getValue());
        }
    }
    
    @Override
    public void clear() {
        this.backing.clear();
    }
    
    @Override
    public Set<String> keySet() {
        return this.backing.keySet();
    }
    
    @Override
    public Collection<Permission> values() {
        return this.backing.values();
    }
    
    @Override
    public Set<Entry<String, Permission>> entrySet() {
        return this.backing.entrySet();
    }
}

