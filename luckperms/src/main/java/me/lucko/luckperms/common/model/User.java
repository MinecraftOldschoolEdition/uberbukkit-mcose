/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.common.model;

import me.lucko.luckperms.common.cacheddata.UserCachedData;

import java.util.Optional;
import java.util.UUID;

/**
 * Represents a user - a permission holder that represents a player.
 */
public class User extends PermissionHolder {
    
    private final UUID uniqueId;
    private String username;
    private String primaryGroup;
    
    private final UserCachedData cachedData;
    private final PermissionHolderIdentifier identifier;
    
    public User(UUID uniqueId, String username) {
        this.uniqueId = uniqueId;
        this.username = username;
        this.primaryGroup = "default";
        this.cachedData = new UserCachedData(this);
        this.identifier = new PermissionHolderIdentifier(HolderType.USER, uniqueId.toString());
    }
    
    public UUID getUniqueId() {
        return this.uniqueId;
    }
    
    public Optional<String> getUsername() {
        return Optional.ofNullable(this.username);
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPrimaryGroup() {
        return this.primaryGroup;
    }
    
    public void setPrimaryGroup(String primaryGroup) {
        this.primaryGroup = primaryGroup;
    }
    
    public UserCachedData getCachedData() {
        return this.cachedData;
    }
    
    @Override
    public HolderType getType() {
        return HolderType.USER;
    }
    
    @Override
    public String getPlainDisplayName() {
        return this.username != null ? this.username : this.uniqueId.toString();
    }
    
    @Override
    public PermissionHolderIdentifier getIdentifier() {
        return this.identifier;
    }
}

