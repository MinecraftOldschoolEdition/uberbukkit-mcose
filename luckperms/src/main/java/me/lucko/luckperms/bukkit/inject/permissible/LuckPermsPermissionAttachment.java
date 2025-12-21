/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.bukkit.inject.permissible;

import me.lucko.luckperms.common.util.Tristate;

import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A wrapper around PermissionAttachment that integrates with LuckPerms.
 */
public class LuckPermsPermissionAttachment {
    
    private final LuckPermsPermissible permissible;
    private final PermissionAttachment handle;
    private final Map<String, Boolean> permissions = new ConcurrentHashMap<String, Boolean>();
    
    public LuckPermsPermissionAttachment(LuckPermsPermissible permissible, Plugin plugin) {
        this.permissible = permissible;
        // Create a standard PermissionAttachment - we override behavior in the permissible
        this.handle = new PermissionAttachment(plugin, permissible);
    }
    
    public PermissionAttachment getHandle() {
        return this.handle;
    }
    
    public void setPermission(String name, boolean value) {
        this.permissions.put(name.toLowerCase(), value);
    }
    
    public void unsetPermission(String name) {
        this.permissions.remove(name.toLowerCase());
    }
    
    public void addPermissions(Map<String, Boolean> perms) {
        for (Map.Entry<String, Boolean> entry : perms.entrySet()) {
            this.permissions.put(entry.getKey().toLowerCase(), entry.getValue());
        }
    }
    
    public Map<String, Boolean> getPermissions() {
        return Collections.unmodifiableMap(this.permissions);
    }
    
    public Tristate getPermissionValue(String permission) {
        permission = permission.toLowerCase();
        
        Boolean value = this.permissions.get(permission);
        if (value != null) {
            return Tristate.of(value);
        }
        
        return Tristate.UNDEFINED;
    }
    
    public void clearPermissions() {
        this.permissions.clear();
    }
}
