/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.bukkit.listeners;

import com.projectposeidon.api.PoseidonUUID;
import com.projectposeidon.johnymuffin.ConnectionPause;
import me.lucko.luckperms.bukkit.LPBukkitPlugin;
import me.lucko.luckperms.bukkit.inject.permissible.LuckPermsPermissible;
import me.lucko.luckperms.bukkit.inject.permissible.PermissibleInjector;
import me.lucko.luckperms.common.model.User;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connection listener for Uberbukkit.
 * Uses Poseidon's connection pause system for async data loading.
 */
public class UberbukkitConnectionListener implements Listener {
    
    private final LPBukkitPlugin plugin;
    private final Set<UUID> uniqueConnections = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> deniedLogin = Collections.synchronizedSet(new HashSet<UUID>());
    
    public UberbukkitConnectionListener(LPBukkitPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Load user data for a player.
     * 
     * @param uuid the player's UUID
     * @param username the player's username
     * @return the loaded User, or null if loading failed
     */
    public User loadUser(UUID uuid, String username) {
        // Create or load user from storage
        User user = this.plugin.getStorage().loadUser(uuid, username);
        if (user != null) {
            this.plugin.getUserManager().getHouseKeeper().registerUsage(uuid);
        }
        return user;
    }
    
    @EventHandler(priority = Event.Priority.Low)
    public void onPlayerPreLogin(PlayerPreLoginEvent e) {
        /*
         * Called when the player first attempts a connection with the server.
         * At this point, Poseidon has already resolved the player's UUID.
         * We use Poseidon's connection pause system to hold the connection
         * while we load the player's data asynchronously.
         */
        
        if (e.getResult() != PlayerPreLoginEvent.Result.ALLOWED) {
            // Another plugin has disallowed the login
            this.plugin.getLogger().info("Another plugin has cancelled the connection for " + 
                    e.getName() + ". No permissions data will be loaded.");
            return;
        }
        
        final String name = e.getName();
        
        // Get the UUID using Poseidon's API - this will match what the Player object will have
        // PoseidonUUID.getPlayerGracefulUUID checks online UUID first, falls back to offline
        final UUID uuid = PoseidonUUID.getPlayerGracefulUUID(name);
        
        if (uuid == null) {
            this.plugin.getLogger().warn("Unable to resolve UUID for " + name);
            return;
        }
        
        // Add a connection pause while we load data
        final ConnectionPause pause = e.addConnectionPause(this.plugin.getLoader(), "LuckPerms-DataLoad");
        
        // Load data asynchronously
        this.plugin.getServer().getScheduler().scheduleAsyncDelayedTask(this.plugin.getLoader(), new Runnable() {
            @Override
            public void run() {
                try {
                    // Load the user's data with the correct Poseidon UUID
                    User user = loadUser(uuid, name);
                    
                    if (user != null) {
                        uniqueConnections.add(uuid);
                        plugin.getLogger().info("Loaded permissions data for " + name + " (" + uuid + ")");
                    } else {
                        plugin.getLogger().warn("Failed to load permissions data for " + name);
                        e.cancelPlayerLogin("Unable to load your permissions data. Please try again later.");
                    }
                } catch (Exception ex) {
                    plugin.getLogger().severe("Exception loading data for " + name, ex);
                    e.cancelPlayerLogin("An error occurred loading your permissions data.");
                } finally {
                    // Always remove the connection pause
                    e.removeConnectionPause(pause);
                }
            }
        });
    }
    
    @EventHandler(priority = Event.Priority.Lowest)
    public void onPlayerLogin(PlayerLoginEvent e) {
        /*
         * Called when the player starts logging into the server.
         * At this point, the user's data should be loaded.
         */
        
        final Player player = e.getPlayer();
        final UUID uuid = player.getUniqueId();
        
        if (e.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            // Login was denied
            return;
        }
        
        final User user = this.plugin.getUserManager().getIfLoaded(uuid);
        
        if (user == null) {
            this.deniedLogin.add(uuid);
            
            if (!this.uniqueConnections.contains(uuid)) {
                this.plugin.getLogger().warn("User " + uuid + " - " + player.getName() +
                        " doesn't have data pre-loaded. Denying login.");
            } else {
                this.plugin.getLogger().warn("User " + uuid + " - " + player.getName() +
                        " data was unloaded before login. Denying login.");
            }
            
            e.disallow(PlayerLoginEvent.Result.KICK_OTHER, 
                    "Unable to load your permissions data. Please try again.");
            return;
        }
        
        // Inject our custom permissible
        try {
            LuckPermsPermissible lpPermissible = new LuckPermsPermissible(player, user, this.plugin);
            PermissibleInjector.inject(player, lpPermissible, this.plugin.getLogger());
        } catch (Throwable t) {
            this.plugin.getLogger().warn("Exception setting up permissions for " +
                    player.getUniqueId() + " - " + player.getName() + ". Denying login.", t);
            
            e.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                    "An error occurred setting up your permissions. Please try again.");
        }
    }
    
    @EventHandler(priority = Event.Priority.Monitor)
    public void onPlayerLoginMonitor(PlayerLoginEvent e) {
        // Check if we denied at LOWEST but it was re-allowed
        if (this.deniedLogin.remove(e.getPlayer().getUniqueId())) {
            if (e.getResult() == PlayerLoginEvent.Result.ALLOWED) {
                this.plugin.getLogger().severe("Player connection was re-allowed for " + e.getPlayer().getUniqueId());
                e.disallow(PlayerLoginEvent.Result.KICK_OTHER, "");
            }
        }
    }
    
    @EventHandler(priority = Event.Priority.Monitor)
    public void onPlayerQuit(PlayerQuitEvent e) {
        final Player player = e.getPlayer();
        final UUID uuid = player.getUniqueId();
        
        // Clean up
        this.uniqueConnections.remove(uuid);
        
        // Uninject permissible
        this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin.getLoader(), new Runnable() {
            @Override
            public void run() {
                try {
                    PermissibleInjector.uninject(player, true);
                } catch (Exception ex) {
                    plugin.getLogger().warn("Exception uninjecting permissible from " + 
                            player.getName(), ex);
                }
                
                // Unload user
                User user = plugin.getUserManager().getIfLoaded(uuid);
                if (user != null) {
                    plugin.getUserManager().unload(uuid);
                }
            }
        }, 1L);
    }
    
    public Set<UUID> getUniqueConnections() {
        return this.uniqueConnections;
    }
}
