/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.bukkit;

import me.lucko.luckperms.bukkit.inject.permissible.LuckPermsPermissible;
import me.lucko.luckperms.bukkit.inject.permissible.PermissibleInjector;
import me.lucko.luckperms.bukkit.inject.server.InjectorDefaultsMap;
import me.lucko.luckperms.bukkit.inject.server.InjectorPermissionMap;
import me.lucko.luckperms.bukkit.inject.server.InjectorSubscriptionMap;
import me.lucko.luckperms.bukkit.inject.server.LuckPermsDefaultsMap;
import me.lucko.luckperms.bukkit.inject.server.LuckPermsPermissionMap;
import me.lucko.luckperms.bukkit.inject.server.LuckPermsSubscriptionMap;
import me.lucko.luckperms.bukkit.listeners.UberbukkitConnectionListener;
import me.lucko.luckperms.common.config.LuckPermsConfiguration;
import me.lucko.luckperms.common.http.BytebinClient;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.model.manager.user.StandardUserManager;
import me.lucko.luckperms.common.model.manager.group.StandardGroupManager;
import me.lucko.luckperms.common.model.manager.track.StandardTrackManager;
import me.lucko.luckperms.common.plugin.PluginLogger;
import me.lucko.luckperms.common.storage.Storage;
import me.lucko.luckperms.common.storage.StorageFactory;
import me.lucko.luckperms.common.webeditor.WebEditorStore;

import okhttp3.OkHttpClient;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * LuckPerms implementation for Uberbukkit.
 */
public class LPBukkitPlugin {
    
    private final LPBukkitLoaderPlugin loader;
    private final PluginLogger logger;
    
    private LuckPermsConfiguration configuration;
    private StandardUserManager userManager;
    private StandardGroupManager groupManager;
    private StandardTrackManager trackManager;
    private Storage storage;
    
    private UberbukkitConnectionListener connectionListener;
    private LuckPermsSubscriptionMap subscriptionMap;
    private LuckPermsPermissionMap permissionMap;
    private LuckPermsDefaultsMap defaultPermissionMap;
    
    // HTTP client for web editor
    private OkHttpClient okHttpClient;
    private BytebinClient bytebinClient;
    private WebEditorStore webEditorStore;
    
    public LPBukkitPlugin(LPBukkitLoaderPlugin loader) {
        this.loader = loader;
        this.logger = new BukkitPluginLogger(loader.getPluginLogger());
    }
    
    public void enable() {
        // Log startup
        this.logger.info("Loading LuckPermsOldschool v1.0...");
        
        // Setup configuration
        this.configuration = new LuckPermsConfiguration(this, resolveConfig());
        
        // Setup HTTP client for web editor
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        
        this.bytebinClient = new BytebinClient(
                this.okHttpClient,
                "https://bytebin.lucko.me/",
                "LuckPermsOldschool/1.0"
        );
        
        this.webEditorStore = new WebEditorStore();
        
        // Setup managers
        this.userManager = new StandardUserManager(this);
        this.groupManager = new StandardGroupManager(this);
        this.trackManager = new StandardTrackManager(this);
        
        // Setup storage
        this.storage = StorageFactory.create(this);
        
        // Register listeners
        this.connectionListener = new UberbukkitConnectionListener(this);
        getServer().getPluginManager().registerEvents(this.connectionListener, this.loader);
        
        // Inject our custom permission maps
        try {
            new InjectorSubscriptionMap(this).inject();
            new InjectorPermissionMap(this).inject();
            new InjectorDefaultsMap(this).inject();
        } catch (Exception e) {
            this.logger.warn("Failed to inject permission maps", e);
        }
        
        // Register command
        PluginCommand command = this.loader.getCommand("luckperms");
        if (command == null) {
            // Try with plugin name prefix
            this.logger.warn("Command 'luckperms' not found, trying with prefix...");
            command = this.loader.getCommand("luckpermsoldschool:luckperms");
        }
        if (command != null) {
            LPBukkitCommandExecutor executor = new LPBukkitCommandExecutor(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
            this.logger.info("Command executor registered for: " + command.getName());
        } else {
            this.logger.severe("Failed to register command - 'luckperms' command not found!");
            this.logger.severe("Available commands in plugin.yml may not have been parsed correctly.");
        }
        
        // Load any online users (in case of reload)
        for (Player player : getServer().getOnlinePlayers()) {
            try {
                User user = this.connectionListener.loadUser(player.getUniqueId(), player.getName());
                if (user != null) {
                    LuckPermsPermissible lpPermissible = new LuckPermsPermissible(player, user, this);
                    PermissibleInjector.inject(player, lpPermissible, this.logger);
                }
            } catch (Exception e) {
                this.logger.severe("Exception loading permissions for " + player.getName(), e);
            }
        }
        
        this.logger.info("LuckPerms enabled successfully!");
    }
    
    public void disable() {
        this.logger.info("Disabling LuckPerms...");
        
        // Uninject from all players
        for (Player player : getServer().getOnlinePlayers()) {
            try {
                PermissibleInjector.uninject(player, false);
            } catch (Exception e) {
                this.logger.warn("Exception uninjecting from " + player.getName(), e);
            }
        }
        
        // Uninject permission maps
        try {
            new InjectorSubscriptionMap(this).uninject();
            new InjectorPermissionMap(this).uninject();
            new InjectorDefaultsMap(this).uninject();
        } catch (Exception e) {
            this.logger.warn("Failed to uninject permission maps", e);
        }
        
        // Close storage
        if (this.storage != null) {
            this.storage.shutdown();
        }
        
        this.logger.info("LuckPerms disabled.");
    }
    
    private File resolveConfig() {
        File configFile = new File(getDataDirectory().toFile(), "config.yml");
        if (!configFile.exists()) {
            getDataDirectory().toFile().mkdirs();
            saveResourceManually("config.yml", configFile);
        }
        return configFile;
    }
    
    /**
     * Manually saves a resource from the plugin JAR to a file.
     * Uberbukkit doesn't have saveResource() on JavaPlugin.
     */
    private void saveResourceManually(String resourcePath, File outFile) {
        try (InputStream in = this.loader.getClass().getResourceAsStream("/" + resourcePath)) {
            if (in == null) {
                this.logger.warn("Resource not found: " + resourcePath);
                return;
            }
            
            try (OutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }
        } catch (IOException e) {
            this.logger.warn("Failed to save resource: " + resourcePath, e);
        }
    }
    
    public Path getDataDirectory() {
        return this.loader.getDataFolder().toPath();
    }
    
    public LPBukkitLoaderPlugin getLoader() {
        return this.loader;
    }
    
    public org.bukkit.Server getServer() {
        return this.loader.getServer();
    }
    
    public PluginLogger getLogger() {
        return this.logger;
    }
    
    public LuckPermsConfiguration getConfiguration() {
        return this.configuration;
    }
    
    public StandardUserManager getUserManager() {
        return this.userManager;
    }
    
    public StandardGroupManager getGroupManager() {
        return this.groupManager;
    }
    
    public StandardTrackManager getTrackManager() {
        return this.trackManager;
    }
    
    public Storage getStorage() {
        return this.storage;
    }
    
    public UberbukkitConnectionListener getConnectionListener() {
        return this.connectionListener;
    }
    
    public void setSubscriptionMap(LuckPermsSubscriptionMap subscriptionMap) {
        this.subscriptionMap = subscriptionMap;
    }
    
    public LuckPermsSubscriptionMap getSubscriptionMap() {
        return this.subscriptionMap;
    }
    
    public void setPermissionMap(LuckPermsPermissionMap permissionMap) {
        this.permissionMap = permissionMap;
    }
    
    public LuckPermsPermissionMap getPermissionMap() {
        return this.permissionMap;
    }
    
    public void setDefaultPermissionMap(LuckPermsDefaultsMap defaultPermissionMap) {
        this.defaultPermissionMap = defaultPermissionMap;
    }
    
    public LuckPermsDefaultsMap getDefaultPermissionMap() {
        return this.defaultPermissionMap;
    }
    
    public BytebinClient getBytebin() {
        return this.bytebinClient;
    }
    
    public WebEditorStore getWebEditorStore() {
        return this.webEditorStore;
    }
    
    public OkHttpClient getOkHttpClient() {
        return this.okHttpClient;
    }
    
    /**
     * Simple logger wrapper for Bukkit's logger.
     */
    private static class BukkitPluginLogger implements PluginLogger {
        private final java.util.logging.Logger logger;
        
        BukkitPluginLogger(java.util.logging.Logger logger) {
            this.logger = logger;
        }
        
        @Override
        public void info(String message) {
            this.logger.info("[LuckPerms] " + message);
        }
        
        @Override
        public void warn(String message) {
            this.logger.warning("[LuckPerms] " + message);
        }
        
        @Override
        public void warn(String message, Throwable t) {
            this.logger.log(Level.WARNING, "[LuckPerms] " + message, t);
        }
        
        @Override
        public void severe(String message) {
            this.logger.severe("[LuckPerms] " + message);
        }
        
        @Override
        public void severe(String message, Throwable t) {
            this.logger.log(Level.SEVERE, "[LuckPerms] " + message, t);
        }
    }
}
