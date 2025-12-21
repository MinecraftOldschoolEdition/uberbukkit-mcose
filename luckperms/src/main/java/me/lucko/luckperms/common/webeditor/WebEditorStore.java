/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 */

package me.lucko.luckperms.common.webeditor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores active web editor sessions.
 */
public class WebEditorStore {

    private final Map<String, WebEditorRequest> sessions = new ConcurrentHashMap<>();

    public void addSession(String id, WebEditorRequest request) {
        this.sessions.put(id, request);
    }

    public WebEditorRequest getSession(String id) {
        return this.sessions.get(id);
    }

    public WebEditorRequest removeSession(String id) {
        return this.sessions.remove(id);
    }

    public boolean hasSession(String id) {
        return this.sessions.containsKey(id);
    }
}

