/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 */

package me.lucko.luckperms.common.webeditor;

import com.google.gson.JsonObject;
import me.lucko.luckperms.bukkit.LPBukkitPlugin;
import me.lucko.luckperms.common.model.Group;
import me.lucko.luckperms.common.model.PermissionHolder;
import me.lucko.luckperms.common.model.PermissionHolderIdentifier;
import me.lucko.luckperms.common.model.Track;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.node.Node;
import me.lucko.luckperms.common.node.NodeJsonSerializer;
import me.lucko.luckperms.common.sender.Sender;
import me.lucko.luckperms.common.util.gson.GsonProvider;
import me.lucko.luckperms.common.util.gson.JArray;
import me.lucko.luckperms.common.util.gson.JObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;

/**
 * Encapsulates a request to the web editor.
 */
public class WebEditorRequest {

    public static final int MAX_USERS = 500;

    /**
     * The encoded json object this payload is made up of
     */
    private final JsonObject payload;

    private final Map<PermissionHolderIdentifier, List<Node>> holders;
    private final Map<String, List<String>> tracks;

    private WebEditorRequest(JsonObject payload, Map<PermissionHolder, List<Node>> holdersMap, Map<Track, List<String>> tracksMap) {
        this.payload = payload;
        this.holders = new HashMap<>();
        for (Map.Entry<PermissionHolder, List<Node>> entry : holdersMap.entrySet()) {
            this.holders.put(entry.getKey().getIdentifier(), entry.getValue());
        }
        this.tracks = new HashMap<>();
        for (Map.Entry<Track, List<String>> entry : tracksMap.entrySet()) {
            this.tracks.put(entry.getKey().getName(), entry.getValue());
        }
    }

    public JsonObject getPayload() {
        return this.payload;
    }

    public byte[] encode() {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(bytesOut), StandardCharsets.UTF_8)) {
            GsonProvider.normal().toJson(this.payload, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bytesOut.toByteArray();
    }

    public Map<PermissionHolderIdentifier, List<Node>> getHolders() {
        return this.holders;
    }

    public Map<String, List<String>> getTracks() {
        return this.tracks;
    }

    /**
     * Generates a web editor request payload.
     *
     * @param holders the holders to edit
     * @param tracks the tracks to edit
     * @param sender the sender who is creating the session
     * @param cmdLabel the command label used by LuckPerms
     * @param plugin the plugin
     * @return a payload
     */
    public static WebEditorRequest generate(List<PermissionHolder> holders, List<Track> tracks, Sender sender, String cmdLabel, LPBukkitPlugin plugin) {
        // form the payload data
        Map<PermissionHolder, List<Node>> holdersMap = new HashMap<>();
        for (PermissionHolder holder : holders) {
            holdersMap.put(holder, new ArrayList<>(holder.getNodes()));
        }

        Map<Track, List<String>> tracksMap = new HashMap<>();
        for (Track track : tracks) {
            tracksMap.put(track, new ArrayList<>(track.getGroups()));
        }

        JsonObject json = createJsonPayload(holdersMap, tracksMap, sender, cmdLabel, plugin).toJson();
        return new WebEditorRequest(json, holdersMap, tracksMap);
    }

    private static JObject createJsonPayload(Map<PermissionHolder, List<Node>> holders, Map<Track, List<String>> tracks, Sender sender, String cmdLabel, LPBukkitPlugin plugin) {
        return new JObject()
                .add("metadata", formMetadata(sender, cmdLabel, "1.0"))
                .add("permissionHolders", new JArray().consume(arr ->
                        holders.forEach((holder, data) ->
                                arr.add(formPermissionHolder(holder, data))
                        )
                ))
                .add("tracks", new JArray().consume(arr ->
                        tracks.forEach((track, data) ->
                                arr.add(formTrack(track, data))
                        )
                ))
                .add("knownPermissions", new JArray())
                .add("potentialContexts", new JObject());
    }

    private static JObject formMetadata(Sender sender, String cmdLabel, String pluginVersion) {
        return new JObject()
                .add("commandAlias", cmdLabel)
                .add("uploader", new JObject()
                        .add("name", sender.getNameWithLocation())
                        .add("uuid", sender.getUniqueId().toString())
                )
                .add("time", System.currentTimeMillis())
                .add("pluginVersion", pluginVersion);
    }

    private static JObject formPermissionHolder(PermissionHolder holder, List<Node> data) {
        return new JObject()
                .add("type", holder.getType().toString())
                .add("id", holder.getIdentifier().getName())
                .add("displayName", holder.getPlainDisplayName())
                .add("nodes", NodeJsonSerializer.serializeNodes(data));
    }

    private static JObject formTrack(Track track, List<String> data) {
        return new JObject()
                .add("type", "track")
                .add("id", track.getName())
                .add("groups", new JArray().addAll(data));
    }

    public static void includeMatchingGroups(List<? super Group> holders, Predicate<? super Group> filter, LPBukkitPlugin plugin) {
        for (Group group : plugin.getGroupManager().getAll().values()) {
            if (filter.test(group)) {
                holders.add(group);
            }
        }
    }

    public static void includeMatchingUsers(List<? super User> holders, boolean includeOffline, LPBukkitPlugin plugin) {
        // First add all currently loaded users
        Map<java.util.UUID, User> loadedUsers = plugin.getUserManager().getAll();
        int count = 0;
        for (User user : loadedUsers.values()) {
            if (count >= MAX_USERS) break;
            holders.add(user);
            count++;
        }
        
        // If we want offline users and haven't hit the limit, load from storage
        if (includeOffline && count < MAX_USERS) {
            java.util.Set<java.util.UUID> allUserUuids = plugin.getStorage().getUniqueUsers();
            for (java.util.UUID uuid : allUserUuids) {
                if (count >= MAX_USERS) break;
                // Skip if already loaded
                if (loadedUsers.containsKey(uuid)) continue;
                
                // Load user from storage
                User user = plugin.getStorage().loadUser(uuid, null);
                if (user != null) {
                    holders.add(user);
                    count++;
                }
            }
        }
    }
}

