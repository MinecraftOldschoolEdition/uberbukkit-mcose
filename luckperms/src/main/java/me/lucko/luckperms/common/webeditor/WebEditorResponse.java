/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 */

package me.lucko.luckperms.common.webeditor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.lucko.luckperms.bukkit.LPBukkitPlugin;
import me.lucko.luckperms.common.http.BytebinClient;
import me.lucko.luckperms.common.http.UnsuccessfulRequestException;
import me.lucko.luckperms.common.model.Group;
import me.lucko.luckperms.common.model.PermissionHolder;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.node.Node;
import me.lucko.luckperms.common.node.NodeJsonSerializer;
import me.lucko.luckperms.common.sender.Sender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles responses from the web editor.
 */
public class WebEditorResponse {

    private final JsonObject payload;
    private final LPBukkitPlugin plugin;
    private final Sender sender;

    public WebEditorResponse(JsonObject payload, LPBukkitPlugin plugin, Sender sender) {
        this.payload = payload;
        this.plugin = plugin;
        this.sender = sender;
    }

    /**
     * Downloads and parses a web editor response from bytebin.
     */
    public static WebEditorResponse download(String code, LPBukkitPlugin plugin, Sender sender) {
        BytebinClient bytebin = plugin.getBytebin();
        if (bytebin == null) {
            sender.sendMessage("&cBytebin client is not available.");
            return null;
        }

        JsonElement json;
        try {
            json = bytebin.getJsonContent(code);
        } catch (UnsuccessfulRequestException e) {
            if (e.getResponse().code() == 404) {
                sender.sendMessage("&cThe web editor data has expired or was not found.");
            } else {
                sender.sendMessage("&cHTTP request failed: " + e.getResponse().code() + " - " + e.getResponse().message());
            }
            return null;
        } catch (IOException e) {
            new RuntimeException("Error downloading from bytebin", e).printStackTrace();
            sender.sendMessage("&cAn error occurred while downloading data.");
            return null;
        }

        if (!json.isJsonObject()) {
            sender.sendMessage("&cInvalid response format from web editor.");
            return null;
        }

        return new WebEditorResponse(json.getAsJsonObject(), plugin, sender);
    }

    /**
     * Applies the changes from the web editor response.
     */
    public boolean apply() {
        int applied = 0;

        // The web editor can return changes in different formats:
        // Format 1: { "changes": { "users": [...], "groups": [...] } }
        // Format 2: { "changes": [...] } - array of holder changes directly
        // Format 3: { "users": [...], "groups": [...] } - without wrapper
        
        if (this.payload.has("changes")) {
            JsonElement changesEl = this.payload.get("changes");
            
            if (changesEl.isJsonArray()) {
                // Format 2: changes is an array of holder changes
                JsonArray changesArr = changesEl.getAsJsonArray();
                for (JsonElement changeEl : changesArr) {
                    if (changeEl.isJsonObject()) {
                        JsonObject change = changeEl.getAsJsonObject();
                        String type = change.has("type") ? change.get("type").getAsString() : "unknown";
                        
                        if ("user".equalsIgnoreCase(type)) {
                            if (applyHolderChanges(change, true)) applied++;
                        } else if ("group".equalsIgnoreCase(type)) {
                            if (applyHolderChanges(change, false)) applied++;
                        } else if ("track".equalsIgnoreCase(type)) {
                            if (applyTrackChanges(change)) applied++;
                        } else {
                            // Try to detect by looking at the ID format
                            if (change.has("id")) {
                                String id = change.get("id").getAsString();
                                try {
                                    UUID.fromString(id);
                                    // Valid UUID, treat as user
                                    if (applyHolderChanges(change, true)) applied++;
                                } catch (IllegalArgumentException e) {
                                    // Not a UUID, treat as group
                                    if (applyHolderChanges(change, false)) applied++;
                                }
                            }
                        }
                    }
                }
            } else if (changesEl.isJsonObject()) {
                // Format 1: changes is an object with users/groups/tracks arrays
                JsonObject changes = changesEl.getAsJsonObject();
                applied += processChangesObject(changes);
            }
        } else {
            // Format 3: users/groups/tracks directly in payload
            applied += processChangesObject(this.payload);
        }

        if (applied > 0) {
            this.sender.sendMessage("&aApplied " + applied + " change(s) from the web editor.");
            return true;
        } else {
            this.sender.sendMessage("&eNo changes were applied.");
            return false;
        }
    }
    
    private int processChangesObject(JsonObject changes) {
        int applied = 0;
        
        // Process user changes
        if (changes.has("users")) {
            JsonArray users = changes.getAsJsonArray("users");
            for (JsonElement userEl : users) {
                if (userEl.isJsonObject() && applyHolderChanges(userEl.getAsJsonObject(), true)) {
                    applied++;
                }
            }
        }

        // Process group changes
        if (changes.has("groups")) {
            JsonArray groups = changes.getAsJsonArray("groups");
            for (JsonElement groupEl : groups) {
                if (groupEl.isJsonObject() && applyHolderChanges(groupEl.getAsJsonObject(), false)) {
                    applied++;
                }
            }
        }

        // Process track changes
        if (changes.has("tracks")) {
            JsonArray tracks = changes.getAsJsonArray("tracks");
            for (JsonElement trackEl : tracks) {
                if (trackEl.isJsonObject() && applyTrackChanges(trackEl.getAsJsonObject())) {
                    applied++;
                }
            }
        }
        
        return applied;
    }

    private boolean applyHolderChanges(JsonObject holderJson, boolean isUser) {
        String id = holderJson.get("id").getAsString();
        
        PermissionHolder holder;
        if (isUser) {
            try {
                UUID uuid = UUID.fromString(id);
                holder = this.plugin.getUserManager().getIfLoaded(uuid);
                if (holder == null) {
                    // Try to load the user
                    holder = this.plugin.getStorage().loadUser(uuid, null);
                }
            } catch (IllegalArgumentException e) {
                this.sender.sendMessage("&cInvalid UUID: " + id);
                return false;
            }
        } else {
            holder = this.plugin.getGroupManager().getIfLoaded(id);
            if (holder == null) {
                // Create the group if it doesn't exist
                holder = this.plugin.getGroupManager().getOrMake(id);
            }
        }

        if (holder == null) {
            this.sender.sendMessage("&cCould not find holder: " + id);
            return false;
        }

        // Clear existing nodes and apply new ones
        if (holderJson.has("nodes")) {
            holder.clearNodes();
            JsonArray nodes = holderJson.getAsJsonArray("nodes");
            for (JsonElement nodeEl : nodes) {
                Node node = NodeJsonSerializer.deserializeNode(nodeEl);
                holder.setNode(node);
            }
        }

        // Save the holder
        if (isUser) {
            this.plugin.getStorage().saveUser((User) holder);
        } else {
            this.plugin.getStorage().saveGroup((Group) holder);
        }

        return true;
    }

    private boolean applyTrackChanges(JsonObject trackJson) {
        String id = trackJson.get("id").getAsString();
        
        me.lucko.luckperms.common.model.Track track = this.plugin.getTrackManager().getIfLoaded(id);
        if (track == null) {
            track = this.plugin.getTrackManager().getOrMake(id);
        }

        if (trackJson.has("groups")) {
            List<String> groups = new ArrayList<>();
            JsonArray groupsArr = trackJson.getAsJsonArray("groups");
            for (JsonElement groupEl : groupsArr) {
                groups.add(groupEl.getAsString());
            }
            track.setGroups(groups);
        }

        this.plugin.getStorage().saveTrack(track);
        return true;
    }
}

