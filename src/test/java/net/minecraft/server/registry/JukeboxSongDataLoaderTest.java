package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.ItemRecord;
import net.minecraft.server.NBTTagCompound;
import net.minecraft.server.Packet61;
import net.minecraft.server.TileEntityRecordPlayer;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class JukeboxSongDataLoaderTest {
    private static final String[] KEYS = {
            "13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal",
            "strad", "ward", "11", "wait", "aria_math", "dog", "certitudes",
            "tsuki_no_koibumi"
    };
    private static final String[] RECORD_NAMES = {
            "13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal",
            "strad", "ward", "11", "wait", "aria math", "dog", "certitudes",
            "tsuki no koibumi"
    };
    private static final String[] SOUND_PATHS = {
            "streaming/13", "streaming/cat", "streaming/blocks", "streaming/chirp",
            "streaming/far", "streaming/mall", "streaming/mellohi", "streaming/stal",
            "streaming/strad", "streaming/ward", "streaming/11", "streaming/wait",
            "streaming/Aria Math", "streaming/Dog", "streaming/certitudes",
            "streaming/tsuki no koibumi"
    };
    private static final int[] LENGTHS = {
            178, 185, 345, 185, 174, 197, 96, 150, 188, 251, 71, 238, 0, 0, 0, 0
    };

    @BeforeClass
    public static void loadRegistry() {
        assertTrue(Block.STONE != null);
        assertTrue(Item.SIGN != null);
        ItemRegistryBootstrap.initialize();
        JukeboxSongRegistryBootstrap.initialize();
    }

    @Test
    public void dataPreservesAllSixteenLegacyBindingsInExactOrder() {
        ResourceLocation[] actualKeys = JukeboxSongRegistryApi.keys().toArray(
                new ResourceLocation[JukeboxSongRegistryApi.size()]);
        ResourceLocation[] expectedKeys = new ResourceLocation[KEYS.length];
        ItemRecord[] records = legacyRecords();
        for (int i = 0; i < KEYS.length; i++) {
            expectedKeys[i] = new ResourceLocation("minecraft", KEYS[i]);
        }
        assertEquals(Arrays.asList(expectedKeys), Arrays.asList(actualKeys));
        assertFalse("jukebox registry must remain extensible", Registries.JUKEBOX_SONG.isFrozen());

        for (int i = 0; i < KEYS.length; i++) {
            JukeboxSong song = JukeboxSongRegistryApi.get(expectedKeys[i]);
            assertEquals(KEYS[i], song.getName());
            assertEquals(new ResourceLocation("minecraft", "music_disc." + KEYS[i]),
                    song.getSoundEvent());
            assertEquals("jukebox_song.minecraft." + KEYS[i],
                    song.getDescriptionTranslationKey());
            assertEquals(SOUND_PATHS[i], song.getSoundPath());
            assertEquals(LENGTHS[i], song.getLengthInSeconds());
            assertEquals(i < 12 ? i + 1 : 0, song.getComparatorOutput());
            assertEquals(2256 + i, song.getItemId());
            assertEquals(RECORD_NAMES[i], song.getLegacyRecordName());
            assertEquals(2256 + i, records[i].id);
            assertEquals(2000 + i, records[i].id - 256);
            assertEquals(RECORD_NAMES[i], records[i].getRecordName());
        }
    }

    @Test
    public void bothPacketAndFullItemIdRangesResolveToTheSameSongAndToken() {
        for (int i = 0; i < KEYS.length; i++) {
            JukeboxSong full = JukeboxSongRegistryBootstrap.getSongByItemId(2256 + i);
            JukeboxSong packet = JukeboxSongRegistryBootstrap.getSongByItemId(2000 + i);
            assertSame(full, packet);
            assertEquals(RECORD_NAMES[i], full.getLegacyRecordName());
        }
        assertNull(JukeboxSongRegistryBootstrap.getSongByItemId(0));
        assertNull(JukeboxSongRegistryBootstrap.getSongByItemId(1999));
        assertNull(JukeboxSongRegistryBootstrap.getSongByItemId(2272));
    }

    @Test
    public void codecRejectsBindingsThatWouldChangeLegacyPlaybackOrProtocolIdentity() {
        ResourceLocation key = new ResourceLocation("minecraft", "13");
        ItemRecord record = (ItemRecord)Item.GOLD_RECORD;
        String valid = "{\"sound_event\":\"minecraft:music_disc.13\","
                + "\"description\":{\"translate\":\"jukebox_song.minecraft.13\"},"
                + "\"length_in_seconds\":178.0,\"comparator_output\":1,"
                + "\"legacy_record_name\":\"13\",\"legacy_sound_path\":\"streaming/13\"}";
        assertDecodeFails(key, record, 2257, valid);
        assertDecodeFails(key, record, 2256, valid.replace("music_disc.13", "music_disc.cat"));
        assertDecodeFails(key, record, 2256,
                valid.replace("\"13\",\"legacy_sound", "\"cat\",\"legacy_sound"));
        assertDecodeFails(key, record, 2256, valid.replace("178.0", "178.5"));
        assertDecodeFails(key, record, 2256,
                valid.replace("\"comparator_output\":1", "\"comparator_output\":16"));
        assertDecodeFails(key, record, 2256,
                valid.replace("\"description\":{\"translate\":\"jukebox_song.minecraft.13\"}",
                        "\"description\":{\"translate\":\"jukebox_song.minecraft.13\",\"extra\":1}"));
        assertDecodeFails(key, record, 2256, valid.substring(0, valid.length() - 1)
                + ",\"unsupported\":true}");
        assertDecodeFails(key, record, 2256, valid.replace("streaming/13", "records/13"));

        JukeboxSong decoded = JukeboxSongCodec.decode(
                key, JsonParser.parseString(valid).getAsJsonObject(), record, 2256);
        assertEquals("13", decoded.getLegacyRecordName());
        assertEquals(178, decoded.getLengthInSeconds());
    }

    @Test
    public void atomicPublicationRejectsAConflictWithoutPublishingItsValidSuffix() {
        ResourceLocation builtInKey = new ResourceLocation("minecraft", "13");
        ResourceLocation lateKey = new ResourceLocation("test", "must_not_publish");
        Map<ResourceLocation, JukeboxSong> staged =
                new LinkedHashMap<ResourceLocation, JukeboxSong>();
        staged.put(builtInKey, new JukeboxSong("replacement", "streaming/replacement", 1, 2256));
        staged.put(lateKey, new JukeboxSong("late", "streaming/late", 1, 3000));

        assertFalse(JukeboxSongRegistryApi.publishAtomic(staged));
        assertNull(JukeboxSongRegistryApi.get(lateKey));
        assertEquals(KEYS.length, JukeboxSongRegistryApi.size());
    }

    @Test
    public void legacyRecordNbtAndPacket61PayloadsRemainByteCompatible() throws Exception {
        for (int i = 0; i < KEYS.length; i++) {
            TileEntityRecordPlayer original = new TileEntityRecordPlayer();
            original.a = 2256 + i;
            NBTTagCompound saved = new NBTTagCompound();
            original.b(saved);
            TileEntityRecordPlayer restored = new TileEntityRecordPlayer();
            restored.a(saved);
            assertEquals(2256 + i, restored.a);

            Packet61 outgoing = new Packet61(1005, 12, 64, -3, 2000 + i);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            outgoing.a(output);
            output.flush();
            assertEquals(17, bytes.size());

            Packet61 incoming = new Packet61();
            incoming.a(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            assertEquals(1005, incoming.a);
            assertEquals(2000 + i, incoming.b);
            assertEquals(12, incoming.c);
            assertEquals(64, incoming.d);
            assertEquals(-3, incoming.e);
        }
    }

    private static void assertDecodeFails(
            ResourceLocation key,
            ItemRecord record,
            int itemId,
            String json) {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            JukeboxSongCodec.decode(key, object, record, itemId);
            fail("Expected invalid jukebox-song data to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    private static ItemRecord[] legacyRecords() {
        return new ItemRecord[] {
                (ItemRecord)Item.GOLD_RECORD,
                (ItemRecord)Item.GREEN_RECORD,
                (ItemRecord)Item.RECORD_BLOCKS,
                (ItemRecord)Item.RECORD_CHIRP,
                (ItemRecord)Item.RECORD_FAR,
                (ItemRecord)Item.RECORD_MALL,
                (ItemRecord)Item.RECORD_MELLOHI,
                (ItemRecord)Item.RECORD_STAL,
                (ItemRecord)Item.RECORD_STRAD,
                (ItemRecord)Item.RECORD_WARD,
                (ItemRecord)Item.RECORD_11,
                (ItemRecord)Item.RECORD_WAIT,
                (ItemRecord)Item.RECORD_ARIA_MATH,
                (ItemRecord)Item.RECORD_DOG,
                (ItemRecord)Item.RECORD_CERTITUDES,
                (ItemRecord)Item.RECORD_TSUKI_NO_KOIBUMI
        };
    }
}
