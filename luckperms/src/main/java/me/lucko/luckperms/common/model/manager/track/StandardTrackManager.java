/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.common.model.manager.track;

import me.lucko.luckperms.bukkit.LPBukkitPlugin;
import me.lucko.luckperms.common.model.Track;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for Track instances.
 */
public class StandardTrackManager {
    
    private final LPBukkitPlugin plugin;
    private final Map<String, Track> tracks = new ConcurrentHashMap<>();
    
    public StandardTrackManager(LPBukkitPlugin plugin) {
        this.plugin = plugin;
    }
    
    public Track getOrMake(String name) {
        return this.tracks.computeIfAbsent(name.toLowerCase(), Track::new);
    }
    
    public Track getIfLoaded(String name) {
        return this.tracks.get(name.toLowerCase());
    }
    
    public void unload(String name) {
        this.tracks.remove(name.toLowerCase());
    }
    
    public boolean isLoaded(String name) {
        return this.tracks.containsKey(name.toLowerCase());
    }
    
    public Map<String, Track> getAll() {
        return this.tracks;
    }
}

