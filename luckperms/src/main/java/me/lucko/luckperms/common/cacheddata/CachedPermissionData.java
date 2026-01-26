/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.common.cacheddata;

import me.lucko.luckperms.common.model.Group;
import me.lucko.luckperms.common.model.PermissionHolder;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.model.manager.group.StandardGroupManager;
import me.lucko.luckperms.common.node.Node;
import me.lucko.luckperms.common.util.Tristate;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds cached permission data for efficient lookups.
 */
public class CachedPermissionData {
    
    private final Map<String, Boolean> permissions;
    
    private CachedPermissionData(Map<String, Boolean> permissions) {
        this.permissions = new ConcurrentHashMap<>(permissions);
    }
    
    /**
     * Checks a permission.
     * 
     * @param permission the permission to check
     * @return the result
     */
    public Tristate checkPermission(String permission) {
        if (permission == null) {
            return Tristate.UNDEFINED;
        }
        
        permission = permission.toLowerCase();
        
        // Direct check
        Boolean value = this.permissions.get(permission);
        if (value != null) {
            return Tristate.of(value);
        }
        
        // Check wildcards
        // e.g. for "some.permission.node", check "some.permission.*", "some.*", "*"
        String[] parts = permission.split("\\.");
        StringBuilder builder = new StringBuilder();
        
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) {
                builder.append(".");
            }
            builder.append(parts[i]);
            
            // Check wildcard at this level
            Boolean wildcardValue = this.permissions.get(builder.toString() + ".*");
            if (wildcardValue != null) {
                return Tristate.of(wildcardValue);
            }
        }
        
        // Check global wildcard
        Boolean globalWildcard = this.permissions.get("*");
        if (globalWildcard != null) {
            return Tristate.of(globalWildcard);
        }
        
        return Tristate.UNDEFINED;
    }
    
    /**
     * Gets the raw permission map.
     */
    public Map<String, Boolean> getPermissionMap() {
        return Collections.unmodifiableMap(this.permissions);
    }
    
    /**
     * Calculates cached permission data for a holder.
     */
    public static CachedPermissionData calculate(PermissionHolder holder) {
        Map<String, Boolean> permissions = new HashMap<>();
        
        // Add all permission nodes from the holder itself
        for (Node node : holder.getNodes()) {
            if (!node.hasExpired() && !node.isGroupNode()) {
                permissions.put(node.getKey().toLowerCase(), node.getValue());
            }
        }
        
        return new CachedPermissionData(permissions);
    }
    
    /**
     * Calculates cached permission data for a user, resolving group inheritance.
     *
     * @param user the user
     * @param groupManager the group manager to resolve group inheritance
     * @return the cached permission data
     */
    public static CachedPermissionData calculateWithInheritance(User user, StandardGroupManager groupManager) {
        Map<String, Boolean> permissions = new HashMap<>();
        Set<String> visitedGroups = new HashSet<>();
        
        // First, resolve all inherited group permissions (lower priority)
        for (String groupName : user.getInheritedGroups()) {
            resolveGroupPermissions(groupName, permissions, visitedGroups, groupManager);
        }
        
        // Then add user's own permissions (higher priority - overwrites group permissions)
        for (Node node : user.getNodes()) {
            if (!node.hasExpired() && !node.isGroupNode()) {
                permissions.put(node.getKey().toLowerCase(), node.getValue());
            }
        }
        
        return new CachedPermissionData(permissions);
    }
    
    /**
     * Recursively resolves permissions from a group and its parent groups.
     */
    private static void resolveGroupPermissions(String groupName, Map<String, Boolean> permissions, 
                                                  Set<String> visitedGroups, StandardGroupManager groupManager) {
        // Prevent infinite loops from circular inheritance
        if (visitedGroups.contains(groupName.toLowerCase())) {
            return;
        }
        visitedGroups.add(groupName.toLowerCase());
        
        Group group = groupManager.getIfLoaded(groupName);
        if (group == null) {
            return;
        }
        
        // First resolve parent groups (lower priority)
        for (String parentName : group.getInheritedGroups()) {
            resolveGroupPermissions(parentName, permissions, visitedGroups, groupManager);
        }
        
        // Then add this group's permissions (overwrites parent permissions)
        for (Node node : group.getNodes()) {
            if (!node.hasExpired() && !node.isGroupNode()) {
                permissions.put(node.getKey().toLowerCase(), node.getValue());
            }
        }
    }
}

