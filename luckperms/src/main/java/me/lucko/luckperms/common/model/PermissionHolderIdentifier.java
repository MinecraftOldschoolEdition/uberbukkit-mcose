/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 */

package me.lucko.luckperms.common.model;

import java.util.Objects;

public final class PermissionHolderIdentifier {
    public static final String USER_TYPE = "user";
    public static final String GROUP_TYPE = "group";
    
    private final String type;
    private final String name;

    public PermissionHolderIdentifier(HolderType type, String name) {
        this.type = Objects.requireNonNull(type, "type") == HolderType.USER
                ? USER_TYPE
                : GROUP_TYPE;
        this.name = Objects.requireNonNull(name, "name");
    }

    public PermissionHolderIdentifier(String type, String name) {
        this.type = Objects.requireNonNull(type, "type");
        this.name = Objects.requireNonNull(name, "name");
    }

    public String getType() {
        return this.type;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PermissionHolderIdentifier)) return false;
        PermissionHolderIdentifier that = (PermissionHolderIdentifier) o;
        return this.type.equals(that.type) && this.name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.type, this.name);
    }

    @Override
    public String toString() {
        return this.type + '/' + this.name;
    }
}

