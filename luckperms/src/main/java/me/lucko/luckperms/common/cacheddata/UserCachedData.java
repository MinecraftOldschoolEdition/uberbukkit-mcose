/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.common.cacheddata;

import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.model.manager.group.StandardGroupManager;

/**
 * Holds cached data for a user.
 */
public class UserCachedData {
    
    private final User user;
    private CachedPermissionData permissionData;
    private StandardGroupManager groupManager;
    
    public UserCachedData(User user) {
        this.user = user;
    }
    
    /**
     * Sets the group manager for resolving group inheritance.
     * Must be called before getPermissionData() for inheritance to work.
     */
    public void setGroupManager(StandardGroupManager groupManager) {
        this.groupManager = groupManager;
    }
    
    public CachedPermissionData getPermissionData() {
        if (this.permissionData == null) {
            refresh();
        }
        return this.permissionData;
    }
    
    public void refresh() {
        if (this.groupManager != null) {
            this.permissionData = CachedPermissionData.calculateWithInheritance(this.user, this.groupManager);
        } else {
            // Fallback: no inheritance resolution
            this.permissionData = CachedPermissionData.calculate(this.user);
        }
    }
    
    public void invalidate() {
        this.permissionData = null;
    }
}

