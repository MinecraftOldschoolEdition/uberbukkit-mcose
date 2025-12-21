/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 */

package me.lucko.luckperms.common.model;

public enum HolderType {
    USER,
    GROUP;
    
    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}

