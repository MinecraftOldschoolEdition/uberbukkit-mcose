/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.common.config;

import me.lucko.luckperms.bukkit.LPBukkitPlugin;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration handler for LuckPerms.
 */
public class LuckPermsConfiguration {
    
    private final LPBukkitPlugin plugin;
    private final File configFile;
    private Map<String, Object> config;
    
    public LuckPermsConfiguration(LPBukkitPlugin plugin, File configFile) {
        this.plugin = plugin;
        this.configFile = configFile;
        this.config = new HashMap<>();
        reload();
    }
    
    public void reload() {
        if (this.configFile.exists()) {
            try (FileReader reader = new FileReader(this.configFile)) {
                Yaml yaml = new Yaml();
                Map<String, Object> loaded = yaml.load(reader);
                if (loaded != null) {
                    this.config = loaded;
                }
            } catch (IOException e) {
                this.plugin.getLogger().severe("Failed to load config", e);
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        String[] parts = key.split("\\.");
        Object current = this.config;
        
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return defaultValue;
            }
            
            if (current == null) {
                return defaultValue;
            }
        }
        
        try {
            return (T) current;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
    
    public String getString(String key, String defaultValue) {
        return get(key, defaultValue);
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        return get(key, defaultValue);
    }
    
    public int getInt(String key, int defaultValue) {
        Object value = get(key, null);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    public boolean isOpsEnabled() {
        return getBoolean("enable-ops", true);
    }
    
    public boolean isAutoOp() {
        return getBoolean("auto-op", false);
    }
    
    public boolean isDebugLogins() {
        return getBoolean("debug-logins", false);
    }
    
    public String getStorageMethod() {
        return getString("storage-method", "yaml");
    }
}

