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
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.SimplePluginManager;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;

/**
 * Injects a {@link LuckPermsPermissionMap} into the {@link PluginManager}.
 */
public class InjectorPermissionMap {
    private static final Field PERMISSIONS_FIELD;

    static {
        Field permissionsField = null;
        try {
            permissionsField = SimplePluginManager.class.getDeclaredField("permissions");
            permissionsField.setAccessible(true);
        } catch (Exception e) {
            // ignore
        }
        PERMISSIONS_FIELD = permissionsField;
    }

    private final LPBukkitPlugin plugin;

    public InjectorPermissionMap(LPBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    public void inject() {
        try {
            LuckPermsPermissionMap permissionMap = tryInject();
            if (permissionMap != null) {
                this.plugin.setPermissionMap(permissionMap);
            }
        } catch (Exception e) {
            this.plugin.getLogger().severe("Exception occurred whilst injecting LuckPerms Permission map.", e);
        }
    }

    private LuckPermsPermissionMap tryInject() throws Exception {
        Objects.requireNonNull(PERMISSIONS_FIELD, "PERMISSIONS_FIELD");
        PluginManager pluginManager = this.plugin.getServer().getPluginManager();

        if (!(pluginManager instanceof SimplePluginManager)) {
            this.plugin.getLogger().severe("PluginManager instance is not a 'SimplePluginManager', instead: " + pluginManager.getClass());
            return null;
        }

        Object map = PERMISSIONS_FIELD.get(pluginManager);
        if (map instanceof LuckPermsPermissionMap) {
            if (((LuckPermsPermissionMap) map).getPlugin() == this.plugin) {
                return null;
            }
            map = ((LuckPermsPermissionMap) map).detach();
        }

        @SuppressWarnings("unchecked")
        Map<String, Permission> castedMap = (Map<String, Permission>) map;

        LuckPermsPermissionMap newMap = new LuckPermsPermissionMap(this.plugin, castedMap);
        PERMISSIONS_FIELD.set(pluginManager, newMap);
        return newMap;
    }

    public void uninject() {
        try {
            Objects.requireNonNull(PERMISSIONS_FIELD, "PERMISSIONS_FIELD");

            PluginManager pluginManager = this.plugin.getServer().getPluginManager();
            if (!(pluginManager instanceof SimplePluginManager)) {
                return;
            }

            Object map = PERMISSIONS_FIELD.get(pluginManager);
            if (map instanceof LuckPermsPermissionMap) {
                LuckPermsPermissionMap lpMap = (LuckPermsPermissionMap) map;
                PERMISSIONS_FIELD.set(pluginManager, lpMap.detach());
            }
        } catch (Exception e) {
            this.plugin.getLogger().severe("Exception occurred whilst uninjecting LuckPerms Permission map.", e);
        }
    }
}

