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

import org.bukkit.permissions.Permissible;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A custom default permission subscription map.
 */
public class LuckPermsDefaultsMap implements Map<Boolean, Map<Permissible, Boolean>> {
    
    private final LPBukkitPlugin plugin;
    private final Map<Boolean, Map<Permissible, Boolean>> backing;
    
    public LuckPermsDefaultsMap(LPBukkitPlugin plugin, Map<Boolean, Map<Permissible, Boolean>> existingData) {
        this.plugin = plugin;
        this.backing = new ConcurrentHashMap<>();
        
        // Initialize with empty maps for both op states
        this.backing.put(true, new WeakHashMap<>());
        this.backing.put(false, new WeakHashMap<>());
        
        if (existingData != null) {
            for (Entry<Boolean, Map<Permissible, Boolean>> entry : existingData.entrySet()) {
                Map<Permissible, Boolean> target = this.backing.get(entry.getKey());
                if (target != null) {
                    target.putAll(entry.getValue());
                }
            }
        }
    }
    
    public LPBukkitPlugin getPlugin() {
        return this.plugin;
    }
    
    public Map<Boolean, Map<Permissible, Boolean>> detach() {
        Map<Boolean, Map<Permissible, Boolean>> result = new HashMap<>();
        result.put(true, new HashMap<>(this.backing.get(true)));
        result.put(false, new HashMap<>(this.backing.get(false)));
        return result;
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
        return this.backing.containsKey(key);
    }
    
    @Override
    public boolean containsValue(Object value) {
        return this.backing.containsValue(value);
    }
    
    @Override
    public Map<Permissible, Boolean> get(Object key) {
        return this.backing.get(key);
    }
    
    @Override
    public Map<Permissible, Boolean> put(Boolean key, Map<Permissible, Boolean> value) {
        return this.backing.put(key, value);
    }
    
    @Override
    public Map<Permissible, Boolean> remove(Object key) {
        return this.backing.remove(key);
    }
    
    @Override
    public void putAll(Map<? extends Boolean, ? extends Map<Permissible, Boolean>> m) {
        this.backing.putAll(m);
    }
    
    @Override
    public void clear() {
        this.backing.get(true).clear();
        this.backing.get(false).clear();
    }
    
    @Override
    public Set<Boolean> keySet() {
        return this.backing.keySet();
    }
    
    @Override
    public Collection<Map<Permissible, Boolean>> values() {
        return this.backing.values();
    }
    
    @Override
    public Set<Entry<Boolean, Map<Permissible, Boolean>>> entrySet() {
        return this.backing.entrySet();
    }
}

