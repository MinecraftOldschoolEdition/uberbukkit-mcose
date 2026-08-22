package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

/**
 * Represents a music disc/jukebox song.
 * Modeled after Fabric API's JukeboxSong registry.
 */
public class JukeboxSong {
    private final String name;
    private final ResourceLocation soundEvent;
    private final String descriptionTranslationKey;
    private final String soundPath;
    private final int lengthInSeconds;
    private final int comparatorOutput;
    private final int itemId;
    private final String legacyRecordName;

    public JukeboxSong(String name, String soundPath, int lengthInSeconds, int itemId) {
        this(name, null, null, soundPath, lengthInSeconds, 0, itemId, name);
    }

    public JukeboxSong(
            String name,
            ResourceLocation soundEvent,
            String descriptionTranslationKey,
            String soundPath,
            int lengthInSeconds,
            int comparatorOutput,
            int itemId,
            String legacyRecordName) {
        this.name = name;
        this.soundEvent = soundEvent;
        this.descriptionTranslationKey = descriptionTranslationKey;
        this.soundPath = soundPath;
        this.lengthInSeconds = lengthInSeconds;
        this.comparatorOutput = comparatorOutput;
        this.itemId = itemId;
        this.legacyRecordName = legacyRecordName;
    }

    public String getName() {
        return this.name;
    }

    public String getSoundPath() {
        return this.soundPath;
    }

    public ResourceLocation getSoundEvent() {
        return this.soundEvent;
    }

    public String getDescriptionTranslationKey() {
        return this.descriptionTranslationKey;
    }

    public int getLengthInSeconds() {
        return this.lengthInSeconds;
    }

    public int getComparatorOutput() {
        return this.comparatorOutput;
    }

    public int getItemId() {
        return this.itemId;
    }

    public String getLegacyRecordName() {
        return this.legacyRecordName;
    }

    @Override
    public String toString() {
        return "JukeboxSong{" + name + "}";
    }
}
