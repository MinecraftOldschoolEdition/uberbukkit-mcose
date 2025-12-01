package org.bukkit.entity;

/**
 * Represents a map that has been placed on a wall or surface.
 */
public interface MapHanging extends Entity {
    
    /**
     * Get the map ID of this hanging map.
     * @return The map ID
     */
    int getMapId();
    
    /**
     * Set the map ID of this hanging map.
     * @param mapId The map ID to set
     */
    void setMapId(int mapId);
}

