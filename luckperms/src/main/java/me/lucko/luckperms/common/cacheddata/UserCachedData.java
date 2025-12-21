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

/**
 * Holds cached data for a user.
 */
public class UserCachedData {
    
    private final User user;
    private CachedPermissionData permissionData;
    
    public UserCachedData(User user) {
        this.user = user;
        refresh();
    }
    
    public CachedPermissionData getPermissionData() {
        if (this.permissionData == null) {
            refresh();
        }
        return this.permissionData;
    }
    
    public void refresh() {
        this.permissionData = CachedPermissionData.calculate(this.user);
    }
    
    public void invalidate() {
        this.permissionData = null;
    }
}

