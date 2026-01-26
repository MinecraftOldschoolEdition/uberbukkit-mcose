/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.common.storage.implementation.yaml;

import me.lucko.luckperms.bukkit.LPBukkitPlugin;
import me.lucko.luckperms.common.model.Group;
import me.lucko.luckperms.common.model.Track;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.node.Node;
import me.lucko.luckperms.common.storage.Storage;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * YAML file-based storage implementation.
 */
public class YamlStorage implements Storage {
    
    private final LPBukkitPlugin plugin;
    private final Path dataDirectory;
    
    private Path usersDirectory;
    private Path groupsDirectory;
    private Path tracksDirectory;
    
    private Yaml yaml;
    
    public YamlStorage(LPBukkitPlugin plugin) {
        this.plugin = plugin;
        this.dataDirectory = plugin.getDataDirectory();
    }
    
    @Override
    public void init() {
        try {
            this.usersDirectory = this.dataDirectory.resolve("users");
            this.groupsDirectory = this.dataDirectory.resolve("groups");
            this.tracksDirectory = this.dataDirectory.resolve("tracks");
            
            Files.createDirectories(this.usersDirectory);
            Files.createDirectories(this.groupsDirectory);
            Files.createDirectories(this.tracksDirectory);
            
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setIndent(2);
            this.yaml = new Yaml(options);
            
            // Create default group if it doesn't exist
            File defaultGroup = this.groupsDirectory.resolve("default.yml").toFile();
            if (!defaultGroup.exists()) {
                Group group = new Group("default");
                saveGroup(group);
            }
            
        } catch (IOException e) {
            this.plugin.getLogger().severe("Failed to initialize YAML storage", e);
        }
    }
    
    @Override
    public void shutdown() {
        // Nothing to do for YAML
    }
    
    @Override
    public User loadUser(UUID uuid, String username) {
        File file = this.usersDirectory.resolve(uuid.toString() + ".yml").toFile();
        
        User user = this.plugin.getUserManager().getOrMake(uuid, username);
        
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = this.yaml.load(reader);
                if (data != null) {
                    // Load username
                    if (data.containsKey("name")) {
                        user.setUsername((String) data.get("name"));
                    }
                    
                    // Load primary group
                    if (data.containsKey("primary-group")) {
                        user.setPrimaryGroup((String) data.get("primary-group"));
                    }
                    
                    // Load permissions
                    loadPermissions(user, data);
                }
            } catch (IOException e) {
                this.plugin.getLogger().severe("Failed to load user " + uuid, e);
                return null;
            }
        }
        
        // Ensure user has default group
        if (user.getInheritedGroups().isEmpty()) {
            user.setNode(Node.builder("group.default").build());
        }
        
        // Refresh cached data
        user.getCachedData().refresh();
        
        return user;
    }
    
    @Override
    public void saveUser(User user) {
        File file = this.usersDirectory.resolve(user.getUniqueId().toString() + ".yml").toFile();
        
        Map<String, Object> data = new HashMap<>();
        
        user.getUsername().ifPresent(name -> data.put("name", name));
        data.put("primary-group", user.getPrimaryGroup());
        
        // Save permissions
        savePermissions(user, data);
        
        try (FileWriter writer = new FileWriter(file)) {
            this.yaml.dump(data, writer);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Failed to save user " + user.getUniqueId(), e);
        }
    }
    
    @Override
    public Set<UUID> getUniqueUsers() {
        Set<UUID> users = new HashSet<>();
        File[] files = this.usersDirectory.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".yml")) {
                    try {
                        String uuidStr = name.substring(0, name.length() - 4);
                        UUID uuid = UUID.fromString(uuidStr);
                        users.add(uuid);
                    } catch (IllegalArgumentException ignored) {
                        // Not a valid UUID filename, skip
                    }
                }
            }
        }
        return users;
    }
    
    @Override
    public Group loadGroup(String name) {
        File file = this.groupsDirectory.resolve(name.toLowerCase() + ".yml").toFile();
        
        if (!file.exists()) {
            return null;
        }
        
        Group group = this.plugin.getGroupManager().getOrMake(name);
        
        try (FileReader reader = new FileReader(file)) {
            Map<String, Object> data = this.yaml.load(reader);
            if (data != null) {
                // Load weight
                if (data.containsKey("weight")) {
                    group.setWeight(((Number) data.get("weight")).intValue());
                }
                
                // Load display name
                if (data.containsKey("display-name")) {
                    group.setDisplayName((String) data.get("display-name"));
                }
                
                // Load permissions
                loadPermissions(group, data);
            }
        } catch (IOException e) {
            this.plugin.getLogger().severe("Failed to load group " + name, e);
            return null;
        }
        
        return group;
    }
    
    @Override
    public void saveGroup(Group group) {
        File file = this.groupsDirectory.resolve(group.getName() + ".yml").toFile();
        
        Map<String, Object> data = new HashMap<>();
        
        // Convert OptionalInt to int for YAML serialization
        data.put("weight", group.getWeight().orElse(0));
        if (group.getDisplayName() != null && !group.getDisplayName().equals(group.getName())) {
            data.put("display-name", group.getDisplayName());
        }
        
        // Save permissions
        savePermissions(group, data);
        
        try (FileWriter writer = new FileWriter(file)) {
            this.yaml.dump(data, writer);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Failed to save group " + group.getName(), e);
        }
    }
    
    @Override
    public void deleteGroup(Group group) {
        File file = this.groupsDirectory.resolve(group.getName() + ".yml").toFile();
        if (file.exists()) {
            file.delete();
        }
        this.plugin.getGroupManager().unload(group.getName());
    }
    
    @Override
    public void loadAllGroups() {
        File[] files = this.groupsDirectory.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".yml")) {
                    String groupName = name.substring(0, name.length() - 4);
                    loadGroup(groupName);
                }
            }
        }
        this.plugin.getLogger().info("Loaded " + this.plugin.getGroupManager().getAll().size() + " groups.");
    }
    
    @Override
    public Track loadTrack(String name) {
        File file = this.tracksDirectory.resolve(name.toLowerCase() + ".yml").toFile();
        
        if (!file.exists()) {
            return null;
        }
        
        Track track = this.plugin.getTrackManager().getOrMake(name);
        
        try (FileReader reader = new FileReader(file)) {
            Map<String, Object> data = this.yaml.load(reader);
            if (data != null && data.containsKey("groups")) {
                @SuppressWarnings("unchecked")
                List<String> groups = (List<String>) data.get("groups");
                track.clearGroups();
                for (String group : groups) {
                    track.appendGroup(group);
                }
            }
        } catch (IOException e) {
            this.plugin.getLogger().severe("Failed to load track " + name, e);
            return null;
        }
        
        return track;
    }
    
    @Override
    public void saveTrack(Track track) {
        File file = this.tracksDirectory.resolve(track.getName() + ".yml").toFile();
        
        Map<String, Object> data = new HashMap<>();
        data.put("groups", new ArrayList<>(track.getGroups()));
        
        try (FileWriter writer = new FileWriter(file)) {
            this.yaml.dump(data, writer);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Failed to save track " + track.getName(), e);
        }
    }
    
    @Override
    public void deleteTrack(Track track) {
        File file = this.tracksDirectory.resolve(track.getName() + ".yml").toFile();
        if (file.exists()) {
            file.delete();
        }
        this.plugin.getTrackManager().unload(track.getName());
    }
    
    @Override
    public void loadAllTracks() {
        File[] files = this.tracksDirectory.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".yml")) {
                    String trackName = name.substring(0, name.length() - 4);
                    loadTrack(trackName);
                }
            }
        }
        this.plugin.getLogger().info("Loaded " + this.plugin.getTrackManager().getAll().size() + " tracks.");
    }
    
    @SuppressWarnings("unchecked")
    private void loadPermissions(me.lucko.luckperms.common.model.PermissionHolder holder, Map<String, Object> data) {
        holder.clearNodes();
        
        if (data.containsKey("permissions")) {
            List<Object> permissions = (List<Object>) data.get("permissions");
            for (Object perm : permissions) {
                if (perm instanceof String) {
                    // Simple permission: "some.permission"
                    String permission = (String) perm;
                    boolean value = true;
                    if (permission.startsWith("-")) {
                        permission = permission.substring(1);
                        value = false;
                    }
                    holder.setNode(Node.builder(permission).value(value).build());
                } else if (perm instanceof Map) {
                    // Complex permission with context
                    Map<String, Object> permMap = (Map<String, Object>) perm;
                    String permission = (String) permMap.get("permission");
                    boolean value = permMap.containsKey("value") ? (Boolean) permMap.get("value") : true;
                    
                    Node.Builder builder = Node.builder(permission).value(value);
                    
                    if (permMap.containsKey("server")) {
                        builder.server((String) permMap.get("server"));
                    }
                    if (permMap.containsKey("world")) {
                        builder.world((String) permMap.get("world"));
                    }
                    if (permMap.containsKey("expiry")) {
                        builder.expiry(((Number) permMap.get("expiry")).longValue());
                    }
                    
                    holder.setNode(builder.build());
                }
            }
        }
        
        // Load inherited groups
        if (data.containsKey("parents")) {
            List<String> parents = (List<String>) data.get("parents");
            for (String parent : parents) {
                holder.setNode(Node.builder("group." + parent.toLowerCase()).build());
            }
        }
    }
    
    private void savePermissions(me.lucko.luckperms.common.model.PermissionHolder holder, Map<String, Object> data) {
        List<Object> permissions = new ArrayList<>();
        List<String> parents = new ArrayList<>();
        
        for (Node node : holder.getNodes()) {
            if (node.isGroupNode()) {
                parents.add(node.getGroupName());
            } else {
                if (node.getServer() == null && node.getWorld() == null && !node.hasExpiry()) {
                    // Simple format
                    String perm = node.getValue() ? node.getKey() : "-" + node.getKey();
                    permissions.add(perm);
                } else {
                    // Complex format
                    Map<String, Object> permMap = new HashMap<>();
                    permMap.put("permission", node.getKey());
                    permMap.put("value", node.getValue());
                    if (node.getServer() != null) {
                        permMap.put("server", node.getServer());
                    }
                    if (node.getWorld() != null) {
                        permMap.put("world", node.getWorld());
                    }
                    if (node.hasExpiry()) {
                        permMap.put("expiry", node.getExpiry());
                    }
                    permissions.add(permMap);
                }
            }
        }
        
        if (!permissions.isEmpty()) {
            data.put("permissions", permissions);
        }
        if (!parents.isEmpty()) {
            data.put("parents", parents);
        }
    }
}

