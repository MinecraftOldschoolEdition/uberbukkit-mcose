/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.bukkit;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * LuckPerms loader plugin for Uberbukkit.
 * This is the main entry point for the plugin.
 */
public class LPBukkitLoaderPlugin extends JavaPlugin {
    
    private LPBukkitPlugin plugin;
    
    @Override
    public void onEnable() {
        this.plugin = new LPBukkitPlugin(this);
        
        try {
            this.plugin.enable();
        } catch (Throwable t) {
            getServer().getLogger().log(Level.SEVERE, "[LuckPerms] Exception occurred whilst enabling LuckPerms");
            t.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        if (this.plugin != null) {
            try {
                this.plugin.disable();
            } catch (Throwable t) {
                getServer().getLogger().log(Level.SEVERE, "[LuckPerms] Exception occurred whilst disabling LuckPerms", t);
            }
        }
    }
    
    public LPBukkitPlugin getLuckPermsPlugin() {
        return this.plugin;
    }
    
    public java.util.logging.Logger getPluginLogger() {
        return getServer().getLogger();
    }
}
