/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.common.storage;

import me.lucko.luckperms.common.model.Group;
import me.lucko.luckperms.common.model.Track;
import me.lucko.luckperms.common.model.User;

import java.util.Set;
import java.util.UUID;

/**
 * Interface for permission data storage.
 */
public interface Storage {
    
    /**
     * Initialize the storage.
     */
    void init();
    
    /**
     * Shutdown the storage.
     */
    void shutdown();
    
    /**
     * Load a user from storage.
     * 
     * @param uuid the user's UUID
     * @param username the user's username (may be null)
     * @return the loaded user, or null if not found
     */
    User loadUser(UUID uuid, String username);
    
    /**
     * Save a user to storage.
     * 
     * @param user the user to save
     */
    void saveUser(User user);
    
    /**
     * Gets all unique user UUIDs that have data stored.
     * Used for the web editor.
     *
     * @return a set of all stored user UUIDs
     */
    Set<UUID> getUniqueUsers();
    
    /**
     * Load a group from storage.
     * 
     * @param name the group name
     * @return the loaded group, or null if not found
     */
    Group loadGroup(String name);
    
    /**
     * Save a group to storage.
     * 
     * @param group the group to save
     */
    void saveGroup(Group group);
    
    /**
     * Delete a group from storage.
     * 
     * @param group the group to delete
     */
    void deleteGroup(Group group);
    
    /**
     * Loads all groups from storage.
     */
    void loadAllGroups();
    
    /**
     * Load a track from storage.
     * 
     * @param name the track name
     * @return the loaded track, or null if not found
     */
    Track loadTrack(String name);
    
    /**
     * Save a track to storage.
     * 
     * @param track the track to save
     */
    void saveTrack(Track track);
    
    /**
     * Delete a track from storage.
     * 
     * @param track the track to delete
     */
    void deleteTrack(Track track);
    
    /**
     * Loads all tracks from storage.
     */
    void loadAllTracks();
}

