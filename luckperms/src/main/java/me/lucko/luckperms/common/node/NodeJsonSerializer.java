/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 */

package me.lucko.luckperms.common.node;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.List;

/**
 * Serializes and deserializes nodes to/from JSON.
 */
public final class NodeJsonSerializer {

    private NodeJsonSerializer() {}

    public static JsonArray serializeNodes(List<Node> nodes) {
        JsonArray arr = new JsonArray();
        for (Node node : nodes) {
            arr.add(serializeNode(node));
        }
        return arr;
    }

    public static JsonObject serializeNode(Node node) {
        JsonObject obj = new JsonObject();
        obj.addProperty("key", node.getKey());
        obj.addProperty("value", node.getValue());
        
        if (node.hasExpiry()) {
            obj.addProperty("expiry", node.getExpiry());
        }
        
        // Add context if present
        JsonObject context = new JsonObject();
        if (node.getServer() != null) {
            context.addProperty("server", node.getServer());
        }
        if (node.getWorld() != null) {
            context.addProperty("world", node.getWorld());
        }
        if (context.size() > 0) {
            obj.add("context", context);
        }
        
        return obj;
    }

    public static Node deserializeNode(JsonElement element) {
        if (element.isJsonPrimitive()) {
            // Simple permission string
            String key = element.getAsString();
            boolean value = true;
            if (key.startsWith("-")) {
                key = key.substring(1);
                value = false;
            }
            return Node.builder(key).value(value).build();
        }
        
        JsonObject obj = element.getAsJsonObject();
        String key = obj.get("key").getAsString();
        boolean value = obj.has("value") ? obj.get("value").getAsBoolean() : true;
        
        Node.Builder builder = Node.builder(key).value(value);
        
        if (obj.has("expiry")) {
            builder.expiry(obj.get("expiry").getAsLong());
        }
        
        if (obj.has("context")) {
            JsonObject context = obj.getAsJsonObject("context");
            if (context.has("server")) {
                builder.server(context.get("server").getAsString());
            }
            if (context.has("world")) {
                builder.world(context.get("world").getAsString());
            }
        }
        
        return builder.build();
    }
}

