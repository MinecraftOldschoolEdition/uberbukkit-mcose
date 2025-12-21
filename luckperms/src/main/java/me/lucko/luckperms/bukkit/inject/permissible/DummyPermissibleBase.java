/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.bukkit.inject.permissible;

import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Set;

/**
 * A dummy PermissibleBase used when a player is about to quit.
 * Returns false for all permission checks.
 */
public class DummyPermissibleBase extends PermissibleBase {
    
    public static final DummyPermissibleBase INSTANCE = new DummyPermissibleBase();
    
    private DummyPermissibleBase() {
        super(null);
    }
    
    @Override
    public boolean isOp() {
        return false;
    }
    
    @Override
    public void setOp(boolean value) {
        // no-op
    }
    
    @Override
    public boolean isPermissionSet(String name) {
        return false;
    }
    
    @Override
    public boolean isPermissionSet(Permission perm) {
        return false;
    }
    
    @Override
    public boolean hasPermission(String inName) {
        return false;
    }
    
    @Override
    public boolean hasPermission(Permission perm) {
        return false;
    }
    
    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        return null;
    }
    
    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return null;
    }
    
    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
        return null;
    }
    
    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
        return null;
    }
    
    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        // no-op
    }
    
    @Override
    public void recalculatePermissions() {
        // no-op
    }
    
    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return Collections.emptySet();
    }
}

