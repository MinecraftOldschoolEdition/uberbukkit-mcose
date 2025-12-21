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
 * A custom permission subscription map that integrates with LuckPerms.
 */
public class LuckPermsSubscriptionMap implements Map<String, Map<Permissible, Boolean>> {
    
    private final LPBukkitPlugin plugin;
    private final Map<String, Map<Permissible, Boolean>> backing;
    
    public LuckPermsSubscriptionMap(LPBukkitPlugin plugin, Map<String, Map<Permissible, Boolean>> existingData) {
        this.plugin = plugin;
        this.backing = new ConcurrentHashMap<>();
        
        // Copy existing data
        if (existingData != null) {
            for (Entry<String, Map<Permissible, Boolean>> entry : existingData.entrySet()) {
                Map<Permissible, Boolean> copy = new WeakHashMap<>(entry.getValue());
                this.backing.put(entry.getKey().toLowerCase(), copy);
            }
        }
    }
    
    public LPBukkitPlugin getPlugin() {
        return this.plugin;
    }
    
    /**
     * Detaches this map and returns a plain HashMap copy.
     */
    public Map<String, Map<Permissible, Boolean>> detach() {
        Map<String, Map<Permissible, Boolean>> result = new HashMap<>();
        for (Entry<String, Map<Permissible, Boolean>> entry : this.backing.entrySet()) {
            result.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
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
        return this.backing.containsKey(key instanceof String ? ((String) key).toLowerCase() : key);
    }
    
    @Override
    public boolean containsValue(Object value) {
        return this.backing.containsValue(value);
    }
    
    @Override
    public Map<Permissible, Boolean> get(Object key) {
        String keyLower = key instanceof String ? ((String) key).toLowerCase() : key.toString();
        Map<Permissible, Boolean> result = this.backing.get(keyLower);
        if (result == null) {
            result = new WeakHashMap<>();
            this.backing.put(keyLower, result);
        }
        return result;
    }
    
    @Override
    public Map<Permissible, Boolean> put(String key, Map<Permissible, Boolean> value) {
        return this.backing.put(key.toLowerCase(), value);
    }
    
    @Override
    public Map<Permissible, Boolean> remove(Object key) {
        return this.backing.remove(key instanceof String ? ((String) key).toLowerCase() : key);
    }
    
    @Override
    public void putAll(Map<? extends String, ? extends Map<Permissible, Boolean>> m) {
        for (Entry<? extends String, ? extends Map<Permissible, Boolean>> entry : m.entrySet()) {
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
    public Collection<Map<Permissible, Boolean>> values() {
        return this.backing.values();
    }
    
    @Override
    public Set<Entry<String, Map<Permissible, Boolean>>> entrySet() {
        return this.backing.entrySet();
    }
}

