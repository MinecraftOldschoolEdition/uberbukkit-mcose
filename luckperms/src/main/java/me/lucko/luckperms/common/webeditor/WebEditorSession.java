/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 */

package me.lucko.luckperms.common.webeditor;

import me.lucko.luckperms.bukkit.LPBukkitPlugin;
import me.lucko.luckperms.common.http.AbstractHttpClient;
import me.lucko.luckperms.common.http.BytebinClient;
import me.lucko.luckperms.common.http.UnsuccessfulRequestException;
import me.lucko.luckperms.common.model.PermissionHolder;
import me.lucko.luckperms.common.model.PermissionHolderIdentifier;
import me.lucko.luckperms.common.model.Track;
import me.lucko.luckperms.common.sender.Sender;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Encapsulates a session with the web editor.
 *
 * <p>A session is tied to a specific user, and can comprise of multiple requests to and
 * responses from the web editor.</p>
 */
public class WebEditorSession {

    private static final String WEB_EDITOR_URL = "https://luckperms.net/editor/";

    public static WebEditorSession create(List<PermissionHolder> holders, List<Track> tracks, Sender sender, String cmdLabel, LPBukkitPlugin plugin) {
        WebEditorRequest initialRequest = WebEditorRequest.generate(holders, tracks, sender, cmdLabel, plugin);
        return new WebEditorSession(initialRequest, plugin, sender, cmdLabel);
    }

    private WebEditorRequest initialRequest;

    private final LPBukkitPlugin plugin;
    private final Sender sender;
    private final String cmdLabel;

    private final Set<PermissionHolderIdentifier> holders;
    private final Set<String> tracks;

    public WebEditorSession(WebEditorRequest initialRequest, LPBukkitPlugin plugin, Sender sender, String cmdLabel) {
        this.initialRequest = initialRequest;
        this.plugin = plugin;
        this.sender = sender;
        this.cmdLabel = cmdLabel;

        this.holders = new LinkedHashSet<>(initialRequest.getHolders().keySet());
        this.tracks = new LinkedHashSet<>(initialRequest.getTracks().keySet());
    }

    public String open() {
        return createInitialSession();
    }

    private String createInitialSession() {
        if (this.initialRequest == null) {
            return null;
        }

        WebEditorRequest request = this.initialRequest;
        this.initialRequest = null;

        String id = uploadRequestData(request);
        if (id == null) {
            return null;
        }

        // form a url for the editor
        String url = WEB_EDITOR_URL + id;
        this.sender.sendMessage("&aWeb editor session created!");
        this.sender.sendMessage("&7Click the link below to open the editor:");
        this.sender.sendMessage("&b" + url);

        return id;
    }

    public String getCommandLabel() {
        return this.cmdLabel;
    }

    private String uploadRequestData(WebEditorRequest request) {
        BytebinClient bytebin = this.plugin.getBytebin();
        if (bytebin == null) {
            this.sender.sendMessage("&cBytebin client is not available.");
            return null;
        }

        byte[] requestBuf = request.encode();

        String pasteId;
        try {
            pasteId = bytebin.postContent(requestBuf, AbstractHttpClient.JSON_TYPE, "editor").key();
        } catch (UnsuccessfulRequestException e) {
            this.sender.sendMessage("&cHTTP request failed: " + e.getResponse().code() + " - " + e.getResponse().message());
            return null;
        } catch (IOException e) {
            new RuntimeException("Error uploading data to bytebin", e).printStackTrace();
            this.sender.sendMessage("&cAn error occurred while uploading data.");
            return null;
        }

        // Store the session for later retrieval
        this.plugin.getWebEditorStore().addSession(pasteId, request);
        return pasteId;
    }
}

