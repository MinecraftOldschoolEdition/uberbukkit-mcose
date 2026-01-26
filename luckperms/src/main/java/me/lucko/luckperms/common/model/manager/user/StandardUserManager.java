/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.common.model.manager.user;

import me.lucko.luckperms.bukkit.LPBukkitPlugin;
import me.lucko.luckperms.common.model.User;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for User instances.
 */
public class StandardUserManager {
    
    private final LPBukkitPlugin plugin;
    private final Map<UUID, User> users = new ConcurrentHashMap<>();
    private final UserHousekeeper housekeeper;
    
    public StandardUserManager(LPBukkitPlugin plugin) {
        this.plugin = plugin;
        this.housekeeper = new UserHousekeeper(this);
    }
    
    public User getOrMake(UUID uuid, String username) {
        return this.users.computeIfAbsent(uuid, u -> {
            User user = new User(u, username);
            // Set the group manager so inheritance can be resolved
            user.getCachedData().setGroupManager(this.plugin.getGroupManager());
            return user;
        });
    }
    
    public User getIfLoaded(UUID uuid) {
        return this.users.get(uuid);
    }
    
    public User getByUsername(String username) {
        for (User user : this.users.values()) {
            if (user.getUsername().isPresent() && 
                user.getUsername().get().equalsIgnoreCase(username)) {
                return user;
            }
        }
        return null;
    }
    
    public void unload(UUID uuid) {
        this.users.remove(uuid);
    }
    
    public boolean isLoaded(UUID uuid) {
        return this.users.containsKey(uuid);
    }
    
    public Map<UUID, User> getAll() {
        return this.users;
    }
    
    public UserHousekeeper getHouseKeeper() {
        return this.housekeeper;
    }
    
    /**
     * Simple housekeeper that tracks user usage.
     */
    public static class UserHousekeeper {
        private final StandardUserManager manager;
        private final Map<UUID, Long> lastUsage = new ConcurrentHashMap<>();
        
        public UserHousekeeper(StandardUserManager manager) {
            this.manager = manager;
        }
        
        public void registerUsage(UUID uuid) {
            this.lastUsage.put(uuid, System.currentTimeMillis());
        }
        
        public void cleanup() {
            long threshold = System.currentTimeMillis() - (1000 * 60 * 10); // 10 minutes
            for (Map.Entry<UUID, Long> entry : this.lastUsage.entrySet()) {
                if (entry.getValue() < threshold) {
                    this.manager.unload(entry.getKey());
                    this.lastUsage.remove(entry.getKey());
                }
            }
        }
    }
}

