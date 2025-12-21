/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.common.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a track - an ordered list of groups for promotion/demotion.
 */
public class Track {
    
    private final String name;
    private final List<String> groups;
    
    public Track(String name) {
        this.name = name.toLowerCase();
        this.groups = new ArrayList<>();
    }
    
    public String getName() {
        return this.name;
    }
    
    public List<String> getGroups() {
        return Collections.unmodifiableList(this.groups);
    }
    
    public void appendGroup(String group) {
        this.groups.add(group.toLowerCase());
    }
    
    public void insertGroup(String group, int position) {
        this.groups.add(position, group.toLowerCase());
    }
    
    public void removeGroup(String group) {
        this.groups.remove(group.toLowerCase());
    }
    
    public void clearGroups() {
        this.groups.clear();
    }
    
    public void setGroups(List<String> groups) {
        this.groups.clear();
        for (String group : groups) {
            this.groups.add(group.toLowerCase());
        }
    }
    
    public int getSize() {
        return this.groups.size();
    }
    
    public String getNext(String current) {
        int index = this.groups.indexOf(current.toLowerCase());
        if (index == -1 || index == this.groups.size() - 1) {
            return null;
        }
        return this.groups.get(index + 1);
    }
    
    public String getPrevious(String current) {
        int index = this.groups.indexOf(current.toLowerCase());
        if (index <= 0) {
            return null;
        }
        return this.groups.get(index - 1);
    }
    
    public boolean containsGroup(String group) {
        return this.groups.contains(group.toLowerCase());
    }
}

