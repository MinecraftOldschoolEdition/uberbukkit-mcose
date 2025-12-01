package net.minecraft.server.registry;

import net.minecraft.server.Item;
import net.minecraft.server.util.ResourceLocation;

/**
 * Bootstrap for the Jukebox Song registry.
 * Registers the built-in music discs for Beta 1.7.3.
 */
public final class JukeboxSongRegistryBootstrap {
    private static boolean initialized = false;
    
    private JukeboxSongRegistryBootstrap() {}
    
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        
        // Beta 1.7.3 music discs
        // The disc items are record13 and recordCat (items 2256 and 2257)
        registerDisc("13", "streaming/13", 178, Item.GOLD_RECORD.id);
        registerDisc("cat", "streaming/cat", 185, Item.GREEN_RECORD.id);
        
        System.out.println("[JukeboxSongRegistryBootstrap] Registered " + Registries.JUKEBOX_SONG.keys().size() + " jukebox songs");
    }
    
    private static void registerDisc(String name, String soundPath, int lengthInSeconds, int itemId) {
        JukeboxSong song = new JukeboxSong(name, soundPath, lengthInSeconds, itemId);
        Registries.JUKEBOX_SONG.register(
            new ResourceLocation("minecraft", name),
            song
        );
    }
    
    /**
     * Get a jukebox song by its item ID.
     */
    public static JukeboxSong getSongByItemId(int itemId) {
        for (Object value : Registries.JUKEBOX_SONG.values()) {
            JukeboxSong song = (JukeboxSong) value;
            if (song.getItemId() == itemId) {
                return song;
            }
        }
        return null;
    }
    
    /**
     * Register a custom music disc.
     */
    public static void registerCustomDisc(String namespace, String name, String soundPath, int lengthInSeconds, int itemId) {
        JukeboxSong song = new JukeboxSong(name, soundPath, lengthInSeconds, itemId);
        Registries.JUKEBOX_SONG.register(
            new ResourceLocation(namespace, name),
            song
        );
    }
}

