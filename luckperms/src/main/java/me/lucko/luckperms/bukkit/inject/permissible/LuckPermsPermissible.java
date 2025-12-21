/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.bukkit.inject.permissible;

import me.lucko.luckperms.bukkit.LPBukkitPlugin;
import me.lucko.luckperms.common.cacheddata.CachedPermissionData;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.util.Tristate;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LuckPerms' custom Permissible implementation.
 * 
 * This class replaces the default PermissibleBase and handles all
 * permission checks using LuckPerms' cached permission data.
 */
public class LuckPermsPermissible extends PermissibleBase {
    
    private final Player player;
    private final User user;
    private final LPBukkitPlugin plugin;
    
    private final AtomicBoolean active = new AtomicBoolean(false);
    private PermissibleBase oldPermissible;
    
    // Attachments added via the Bukkit API
    private final Map<PermissionAttachment, LuckPermsPermissionAttachment> attachments = new ConcurrentHashMap<PermissionAttachment, LuckPermsPermissionAttachment>();
    
    public LuckPermsPermissible(Player player, User user, LPBukkitPlugin plugin) {
        super(player);
        this.player = player;
        this.user = user;
        this.plugin = plugin;
    }
    
    @Override
    public boolean isOp() {
        return this.player.isOp();
    }
    
    @Override
    public void setOp(boolean value) {
        this.player.setOp(value);
    }
    
    @Override
    public boolean isPermissionSet(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Permission name cannot be null");
        }
        
        Tristate result = getPermissionValue(name);
        return result != Tristate.UNDEFINED;
    }
    
    @Override
    public boolean isPermissionSet(Permission perm) {
        if (perm == null) {
            throw new IllegalArgumentException("Permission cannot be null");
        }
        return isPermissionSet(perm.getName());
    }
    
    @Override
    public boolean hasPermission(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Permission name cannot be null");
        }
        
        Tristate result = getPermissionValue(name);
        
        // If undefined, check if there's a permission with a default value
        if (result == Tristate.UNDEFINED) {
            Permission perm = this.plugin.getServer().getPluginManager().getPermission(name);
            if (perm != null) {
                return perm.getDefault().getValue(isOp());
            }
            return false;
        }
        
        return result.asBoolean();
    }
    
    @Override
    public boolean hasPermission(Permission perm) {
        if (perm == null) {
            throw new IllegalArgumentException("Permission cannot be null");
        }
        
        Tristate result = getPermissionValue(perm.getName());
        
        if (result == Tristate.UNDEFINED) {
            return perm.getDefault().getValue(isOp());
        }
        
        return result.asBoolean();
    }
    
    /**
     * Gets the permission value from LuckPerms' cached data.
     */
    private Tristate getPermissionValue(String permission) {
        if (!this.active.get()) {
            return Tristate.UNDEFINED;
        }
        
        // Check attachments first
        for (LuckPermsPermissionAttachment attachment : this.attachments.values()) {
            Tristate value = attachment.getPermissionValue(permission);
            if (value != Tristate.UNDEFINED) {
                return value;
            }
        }
        
        // Then check cached data from LuckPerms
        CachedPermissionData permissionData = this.user.getCachedData().getPermissionData();
        if (permissionData != null) {
            return permissionData.checkPermission(permission);
        }
        
        return Tristate.UNDEFINED;
    }
    
    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        if (name == null) {
            throw new IllegalArgumentException("Permission name cannot be null");
        }
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (!plugin.isEnabled()) {
            throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName() + " is disabled");
        }
        
        PermissionAttachment attachment = addAttachment(plugin);
        attachment.setPermission(name, value);
        return attachment;
    }
    
    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (!plugin.isEnabled()) {
            throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName() + " is disabled");
        }
        
        LuckPermsPermissionAttachment lpAttachment = new LuckPermsPermissionAttachment(this, plugin);
        this.attachments.put(lpAttachment.getHandle(), lpAttachment);
        
        return lpAttachment.getHandle();
    }
    
    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
        if (name == null) {
            throw new IllegalArgumentException("Permission name cannot be null");
        }
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (!plugin.isEnabled()) {
            throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName() + " is disabled");
        }
        
        PermissionAttachment attachment = addAttachment(plugin, ticks);
        if (attachment != null) {
            attachment.setPermission(name, value);
        }
        return attachment;
    }
    
    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (!plugin.isEnabled()) {
            throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName() + " is disabled");
        }
        
        final PermissionAttachment attachment = addAttachment(plugin);
        
        // Schedule removal
        this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                attachment.remove();
            }
        }, ticks);
        
        return attachment;
    }
    
    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        if (attachment == null) {
            throw new IllegalArgumentException("Attachment cannot be null");
        }
        
        LuckPermsPermissionAttachment lpAttachment = this.attachments.remove(attachment);
        
        if (lpAttachment == null) {
            throw new IllegalArgumentException("Given attachment is not part of Permissible object " + this.player);
        }
        
        lpAttachment.clearPermissions();
    }
    
    @Override
    public void recalculatePermissions() {
        // LuckPerms handles permission recalculation internally
        // Just trigger a refresh of cached data
        if (this.user != null) {
            this.user.getCachedData().invalidate();
        }
    }
    
    /**
     * Clears our internal permission state.
     * Named differently to avoid conflict with PermissibleBase.clearPermissions() which is private.
     */
    public void clearLPPermissions() {
        this.attachments.clear();
    }
    
    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        Set<PermissionAttachmentInfo> perms = new HashSet<PermissionAttachmentInfo>();
        
        // Add permissions from LuckPerms
        if (this.user != null && this.user.getCachedData() != null) {
            CachedPermissionData data = this.user.getCachedData().getPermissionData();
            if (data != null) {
                for (Map.Entry<String, Boolean> entry : data.getPermissionMap().entrySet()) {
                    perms.add(new PermissionAttachmentInfo(this, entry.getKey(), null, entry.getValue()));
                }
            }
        }
        
        // Add permissions from attachments
        for (LuckPermsPermissionAttachment attachment : this.attachments.values()) {
            for (Map.Entry<String, Boolean> entry : attachment.getPermissions().entrySet()) {
                perms.add(new PermissionAttachmentInfo(this, entry.getKey(), attachment.getHandle(), entry.getValue()));
            }
        }
        
        return perms;
    }
    
    /**
     * Converts and adds attachments from the old permissible.
     */
    public void convertAndAddAttachments(List<PermissionAttachment> attachments) {
        for (PermissionAttachment attachment : attachments) {
            LuckPermsPermissionAttachment lpAttachment = new LuckPermsPermissionAttachment(this, attachment.getPlugin());
            lpAttachment.addPermissions(attachment.getPermissions());
            this.attachments.put(lpAttachment.getHandle(), lpAttachment);
        }
    }
    
    public AtomicBoolean getActive() {
        return this.active;
    }
    
    public PermissibleBase getOldPermissible() {
        return this.oldPermissible;
    }
    
    public void setOldPermissible(PermissibleBase oldPermissible) {
        this.oldPermissible = oldPermissible;
    }
    
    public Player getPlayer() {
        return this.player;
    }
    
    public User getUser() {
        return this.user;
    }
    
    public LPBukkitPlugin getPlugin() {
        return this.plugin;
    }
}
