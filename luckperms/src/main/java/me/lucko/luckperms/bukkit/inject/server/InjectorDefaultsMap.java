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
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.SimplePluginManager;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;

/**
 * Injects a {@link LuckPermsDefaultsMap} into the {@link PluginManager}.
 */
public class InjectorDefaultsMap {
    private static final Field DEF_SUBS_FIELD;

    static {
        Field defSubsField = null;
        try {
            defSubsField = SimplePluginManager.class.getDeclaredField("defSubs");
            defSubsField.setAccessible(true);
        } catch (Exception e) {
            // ignore
        }
        DEF_SUBS_FIELD = defSubsField;
    }

    private final LPBukkitPlugin plugin;

    public InjectorDefaultsMap(LPBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    public void inject() {
        try {
            LuckPermsDefaultsMap defaultsMap = tryInject();
            if (defaultsMap != null) {
                this.plugin.setDefaultPermissionMap(defaultsMap);
            }
        } catch (Exception e) {
            this.plugin.getLogger().severe("Exception occurred whilst injecting LuckPerms Default Permission map.", e);
        }
    }

    private LuckPermsDefaultsMap tryInject() throws Exception {
        Objects.requireNonNull(DEF_SUBS_FIELD, "DEF_SUBS_FIELD");
        PluginManager pluginManager = this.plugin.getServer().getPluginManager();

        if (!(pluginManager instanceof SimplePluginManager)) {
            this.plugin.getLogger().severe("PluginManager instance is not a 'SimplePluginManager', instead: " + pluginManager.getClass());
            return null;
        }

        Object map = DEF_SUBS_FIELD.get(pluginManager);
        if (map instanceof LuckPermsDefaultsMap) {
            if (((LuckPermsDefaultsMap) map).getPlugin() == this.plugin) {
                return null;
            }
            map = ((LuckPermsDefaultsMap) map).detach();
        }

        @SuppressWarnings("unchecked")
        Map<Boolean, Map<Permissible, Boolean>> castedMap = (Map<Boolean, Map<Permissible, Boolean>>) map;

        LuckPermsDefaultsMap newMap = new LuckPermsDefaultsMap(this.plugin, castedMap);
        DEF_SUBS_FIELD.set(pluginManager, newMap);
        return newMap;
    }

    public void uninject() {
        try {
            Objects.requireNonNull(DEF_SUBS_FIELD, "DEF_SUBS_FIELD");

            PluginManager pluginManager = this.plugin.getServer().getPluginManager();
            if (!(pluginManager instanceof SimplePluginManager)) {
                return;
            }

            Object map = DEF_SUBS_FIELD.get(pluginManager);
            if (map instanceof LuckPermsDefaultsMap) {
                LuckPermsDefaultsMap lpMap = (LuckPermsDefaultsMap) map;
                DEF_SUBS_FIELD.set(pluginManager, lpMap.detach());
            }
        } catch (Exception e) {
            this.plugin.getLogger().severe("Exception occurred whilst uninjecting LuckPerms Default Permission map.", e);
        }
    }
}

