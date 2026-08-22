package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public final class JukeboxSongRegistryApi {
    private JukeboxSongRegistryApi() {}

    public static boolean publishAtomic(Map<ResourceLocation, JukeboxSong> staged) {
        return Registries.JUKEBOX_SONG.registerAllAtomic(staged);
    }

    public static boolean register(ResourceLocation key, JukeboxSong value) {
        return RegistryApiSupport.register(Registries.JUKEBOX_SONG, key, value);
    }

    public static JukeboxSong get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.JUKEBOX_SONG, key);
    }

    public static JukeboxSong getByIdentifier(String any) {
        return RegistryApiSupport.getByIdentifier(Registries.JUKEBOX_SONG, any);
    }

    public static ResourceLocation getKey(JukeboxSong value) {
        return RegistryApiSupport.getKey(Registries.JUKEBOX_SONG, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.JUKEBOX_SONG);
    }

    public static Collection<JukeboxSong> values() {
        return RegistryApiSupport.values(Registries.JUKEBOX_SONG);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.JUKEBOX_SONG);
    }

    public static String normalizeInputIdentifier(String any) {
        return RegistryApiSupport.normalizeInputIdentifier(Registries.JUKEBOX_SONG, any);
    }

    public static String canonicalizeIdentifier(String any) {
        return RegistryApiSupport.canonicalizeIdentifier(Registries.JUKEBOX_SONG, any);
    }
}
