package net.minecraft.server.registry;

/**
 * Represents a music disc/jukebox song.
 * Modeled after Fabric API's JukeboxSong registry.
 */
public class JukeboxSong {
    private final String name;
    private final String soundPath;
    private final int lengthInSeconds;
    private final int itemId;
    
    public JukeboxSong(String name, String soundPath, int lengthInSeconds, int itemId) {
        this.name = name;
        this.soundPath = soundPath;
        this.lengthInSeconds = lengthInSeconds;
        this.itemId = itemId;
    }
    
    public String getName() {
        return this.name;
    }
    
    public String getSoundPath() {
        return this.soundPath;
    }
    
    public int getLengthInSeconds() {
        return this.lengthInSeconds;
    }
    
    public int getItemId() {
        return this.itemId;
    }
    
    @Override
    public String toString() {
        return "JukeboxSong{" + name + "}";
    }
}

