package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.Item;
import net.minecraft.server.ItemRecord;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Loads the fixed legacy jukebox-song bindings from modern-shaped data. */
public final class JukeboxSongRegistryBootstrap {
    private static final ResourceLocation LEGACY_ORDER =
            new ResourceLocation("minecraft", "legacy_order");
    private static boolean initialized;

    private JukeboxSongRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;

        final List<ResourceLocation> keys = RegistryDataLoader.loadRequiredTag(
                "jukebox_song", LEGACY_ORDER);
        final ItemRecord[] records = legacyRecords();
        if (keys.size() != records.length) {
            throw new IllegalStateException("Legacy jukebox-song order must contain exactly "
                    + records.length + " entries (was " + keys.size() + ")");
        }

        final Map<ResourceLocation, ItemRecord> recordsByKey =
                new LinkedHashMap<ResourceLocation, ItemRecord>();
        final Map<ResourceLocation, Integer> expectedItemIds =
                new LinkedHashMap<ResourceLocation, Integer>();
        for (int i = 0; i < records.length; i++) {
            recordsByKey.put(keys.get(i), records[i]);
            expectedItemIds.put(keys.get(i), Integer.valueOf(2256 + i));
        }

        Map<ResourceLocation, JukeboxSong> decoded = RegistryDataLoader.loadRequired(
                "jukebox_song",
                keys,
                new RegistryDataLoader.Decoder<JukeboxSong>() {
                    public JukeboxSong decode(ResourceLocation key, JsonObject json) {
                        return JukeboxSongCodec.decode(
                                key,
                                json,
                                recordsByKey.get(key),
                                expectedItemIds.get(key).intValue());
                    }
                });
        if (!JukeboxSongRegistryApi.publishAtomic(decoded)) {
            throw new IllegalStateException(
                    "Built-in jukebox songs could not be published atomically");
        }
        initialized = true;
        System.out.println("[JukeboxSongRegistryBootstrap] Registered "
                + Registries.JUKEBOX_SONG.keys().size() + " jukebox songs");
    }

    private static ItemRecord[] legacyRecords() {
        return new ItemRecord[] {
                requireRecord(Item.GOLD_RECORD, "13"),
                requireRecord(Item.GREEN_RECORD, "cat"),
                requireRecord(Item.RECORD_BLOCKS, "blocks"),
                requireRecord(Item.RECORD_CHIRP, "chirp"),
                requireRecord(Item.RECORD_FAR, "far"),
                requireRecord(Item.RECORD_MALL, "mall"),
                requireRecord(Item.RECORD_MELLOHI, "mellohi"),
                requireRecord(Item.RECORD_STAL, "stal"),
                requireRecord(Item.RECORD_STRAD, "strad"),
                requireRecord(Item.RECORD_WARD, "ward"),
                requireRecord(Item.RECORD_11, "11"),
                requireRecord(Item.RECORD_WAIT, "wait"),
                requireRecord(Item.RECORD_ARIA_MATH, "aria_math"),
                requireRecord(Item.RECORD_DOG, "dog"),
                requireRecord(Item.RECORD_CERTITUDES, "certitudes"),
                requireRecord(Item.RECORD_TSUKI_NO_KOIBUMI, "tsuki_no_koibumi")
        };
    }

    private static ItemRecord requireRecord(Item item, String key) {
        if (!(item instanceof ItemRecord)) {
            throw new IllegalStateException("Jukebox song " + key + " has no legacy ItemRecord");
        }
        return (ItemRecord)item;
    }

    /** Accepts both full item ids and the unshifted Packet61 auxiliary values. */
    public static JukeboxSong getSongByItemId(int itemId) {
        for (JukeboxSong song : JukeboxSongRegistryApi.values()) {
            int registeredItemId = song.getItemId();
            if (registeredItemId == itemId
                    || registeredItemId - 256 == itemId
                    || registeredItemId + 256 == itemId) {
                return song;
            }
        }
        return null;
    }

    /** Keeps the pre-existing late mod-registration surface available. */
    public static void registerCustomDisc(
            String namespace,
            String name,
            String soundPath,
            int lengthInSeconds,
            int itemId) {
        JukeboxSong song = new JukeboxSong(name, soundPath, lengthInSeconds, itemId);
        JukeboxSongRegistryApi.register(new ResourceLocation(namespace, name), song);
    }
}
