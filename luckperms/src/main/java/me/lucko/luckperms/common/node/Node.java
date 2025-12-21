/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.common.node;

import java.util.Objects;

/**
 * Represents a permission node.
 */
public class Node {
    
    private final String key;
    private final boolean value;
    private final String server;
    private final String world;
    private final long expiry;
    
    private Node(String key, boolean value, String server, String world, long expiry) {
        this.key = Objects.requireNonNull(key, "key").toLowerCase();
        this.value = value;
        this.server = server;
        this.world = world;
        this.expiry = expiry;
    }
    
    public String getKey() {
        return this.key;
    }
    
    public boolean getValue() {
        return this.value;
    }
    
    public String getServer() {
        return this.server;
    }
    
    public String getWorld() {
        return this.world;
    }
    
    public long getExpiry() {
        return this.expiry;
    }
    
    public boolean hasExpiry() {
        return this.expiry != 0;
    }
    
    public boolean hasExpired() {
        return hasExpiry() && System.currentTimeMillis() > this.expiry;
    }
    
    public boolean isNegated() {
        return !this.value;
    }
    
    public boolean isGroupNode() {
        return this.key.startsWith("group.");
    }
    
    public String getGroupName() {
        if (!isGroupNode()) {
            throw new IllegalStateException("Not a group node");
        }
        return this.key.substring(6);
    }
    
    public boolean isPrefix() {
        return this.key.startsWith("prefix.");
    }
    
    public boolean isSuffix() {
        return this.key.startsWith("suffix.");
    }
    
    public boolean isMeta() {
        return this.key.startsWith("meta.");
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return this.value == node.value && 
               this.key.equals(node.key) &&
               Objects.equals(this.server, node.server) &&
               Objects.equals(this.world, node.world);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(this.key, this.value, this.server, this.world);
    }
    
    @Override
    public String toString() {
        return "Node{key=" + this.key + ", value=" + this.value + "}";
    }
    
    // Builder
    public static Builder builder(String key) {
        return new Builder(key);
    }
    
    public static class Builder {
        private final String key;
        private boolean value = true;
        private String server = null;
        private String world = null;
        private long expiry = 0;
        
        public Builder(String key) {
            this.key = key;
        }
        
        public Builder value(boolean value) {
            this.value = value;
            return this;
        }
        
        public Builder negated(boolean negated) {
            this.value = !negated;
            return this;
        }
        
        public Builder server(String server) {
            this.server = server;
            return this;
        }
        
        public Builder world(String world) {
            this.world = world;
            return this;
        }
        
        public Builder expiry(long expiry) {
            this.expiry = expiry;
            return this;
        }
        
        public Node build() {
            return new Node(this.key, this.value, this.server, this.world, this.expiry);
        }
    }
}

