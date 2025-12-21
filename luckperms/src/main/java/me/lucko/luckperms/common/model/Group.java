/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.common.model;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Represents a group - a named permission holder.
 */
public class Group extends PermissionHolder {
    
    private final String name;
    private final PermissionHolderIdentifier identifier;
    private int weight;
    private String displayName;
    
    public Group(String name) {
        this.name = name.toLowerCase();
        this.identifier = new PermissionHolderIdentifier(HolderType.GROUP, this.name);
        this.weight = 0;
    }
    
    public String getName() {
        return this.name;
    }
    
    public OptionalInt getWeight() {
        return this.weight != 0 ? OptionalInt.of(this.weight) : OptionalInt.empty();
    }
    
    public void setWeight(int weight) {
        this.weight = weight;
    }
    
    public String getDisplayName() {
        return this.displayName != null ? this.displayName : this.name;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    @Override
    public HolderType getType() {
        return HolderType.GROUP;
    }
    
    @Override
    public String getPlainDisplayName() {
        return getDisplayName();
    }
    
    @Override
    public PermissionHolderIdentifier getIdentifier() {
        return this.identifier;
    }
}

