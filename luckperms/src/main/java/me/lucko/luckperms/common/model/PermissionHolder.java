/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.common.model;

import me.lucko.luckperms.common.node.Node;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Base class for permission holders (users and groups).
 */
public abstract class PermissionHolder {
    
    private final List<Node> nodes = new CopyOnWriteArrayList<>();
    
    /**
     * Gets all nodes held by this holder.
     */
    public List<Node> getNodes() {
        return Collections.unmodifiableList(this.nodes);
    }
    
    /**
     * Gets a modifiable view of nodes for internal use.
     */
    protected List<Node> getNodesInternal() {
        return this.nodes;
    }
    
    /**
     * Sets a permission node.
     */
    public void setNode(Node node) {
        // Remove any existing node with the same key
        this.nodes.removeIf(n -> n.getKey().equalsIgnoreCase(node.getKey()));
        this.nodes.add(node);
    }
    
    /**
     * Unsets a permission node.
     */
    public void unsetNode(Node node) {
        this.nodes.removeIf(n -> n.getKey().equalsIgnoreCase(node.getKey()));
    }
    
    /**
     * Clears all nodes.
     */
    public void clearNodes() {
        this.nodes.clear();
    }
    
    /**
     * Gets the holder type.
     */
    public abstract HolderType getType();
    
    /**
     * Gets the plain display name of this holder.
     */
    public abstract String getPlainDisplayName();
    
    /**
     * Gets the identifier of this holder.
     */
    public abstract PermissionHolderIdentifier getIdentifier();
    
    /**
     * Gets all permission nodes (excluding group membership).
     */
    public List<Node> getPermissionNodes() {
        return this.nodes.stream()
                .filter(n -> !n.getKey().startsWith("group."))
                .collect(Collectors.toList());
    }
    
    /**
     * Gets the inherited groups.
     */
    public List<String> getInheritedGroups() {
        return this.nodes.stream()
                .filter(n -> n.getKey().startsWith("group."))
                .map(n -> n.getKey().substring(6))
                .collect(Collectors.toList());
    }
    
    /**
     * Provides a view of the normal data stored in this holder.
     */
    public NodeDataView normalData() {
        return new NodeDataView(this);
    }
    
    /**
     * Inner class to provide a view of node data.
     */
    public static class NodeDataView {
        private final PermissionHolder holder;
        
        NodeDataView(PermissionHolder holder) {
            this.holder = holder;
        }
        
        public List<Node> asList() {
            return this.holder.getNodes();
        }
        
        public void forEach(java.util.function.Consumer<Node> consumer) {
            this.holder.getNodes().forEach(consumer);
        }
    }
}

