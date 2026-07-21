package net.minecraft.server;

import org.junit.After;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.Rule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PaperBackportSecurityTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void closeRegionCache() {
        RegionFileCache.a();
        RegionFileCache.setBulkConversionMode(false);
    }

    @Test
    public void rejectsNonEmptyEndTagListBeforeAllocation() throws Exception {
        assertListRejected((byte) 0, 1);
    }

    @Test
    public void accountsListElementHeapOverheadBeforeReadingElements() throws Exception {
        assertListRejected((byte) 1, 100000);
    }

    @Test
    public void rejectsMalformedLegacyInventoryArraysAndCounts() throws Exception {
        Packet5EntityEquipment oversized = new Packet5EntityEquipment();
        oversized.pvn = 6;
        expectIOException(new PacketReader() {
            public void read() throws IOException {
                oversized.a(packet5Payload(-1, 37, -1, 0));
            }
        });

        // Start with the block registry, matching the server bootstrap order and
        // ensuring the corresponding ItemBlock entry exists in Item.byId.
        final int stoneId = Block.STONE.id;
        Packet5EntityEquipment negativeCount = new Packet5EntityEquipment();
        negativeCount.pvn = 6;
        expectIOException(new PacketReader() {
            public void read() throws IOException {
                negativeCount.a(packet5Payload(-1, 36, stoneId, -1));
            }
        });

        Packet5EntityEquipment valid = new Packet5EntityEquipment();
        valid.pvn = 6;
        valid.a(packet5Payload(-1, 36, -1, 0));
        assertEquals(36, valid.items.length);
    }

    @Test
    public void rejectsMalformedItemStacksInInteractionPackets() throws Exception {
        final int stoneId = Block.STONE.id;

        final Packet15Place invalidPlaceCount = new Packet15Place();
        invalidPlaceCount.pvn = 8;
        expectIOException(new PacketReader() {
            public void read() throws IOException {
                invalidPlaceCount.a(itemStackPacketPayload(stoneId, 0));
            }
        });

        final Packet102WindowClick invalidClickCount = new Packet102WindowClick();
        invalidClickCount.pvn = 8;
        expectIOException(new PacketReader() {
            public void read() throws IOException {
                invalidClickCount.a(windowClickPayload(stoneId, 0));
            }
        });

        final Packet15Place invalidSentinel = new Packet15Place();
        invalidSentinel.pvn = 8;
        expectIOException(new PacketReader() {
            public void read() throws IOException {
                invalidSentinel.a(itemStackPacketPayload(-2, 0));
            }
        });
    }

    @Test
    public void malformedPacketLogReasonIsConciseAndSingleLine() {
        StringBuilder oversized = new StringBuilder("invalid count 0\r\n");
        for (int i = 0; i < 200; ++i) {
            oversized.append('x');
        }

        String reason = NetworkManager.describeMalformedPacket(new IOException(oversized.toString()));
        assertTrue(reason.startsWith("IOException: invalid count 0"));
        assertTrue(reason.endsWith("..."));
        assertTrue(reason.length() <= 160);
        assertTrue(reason.indexOf('\r') < 0);
        assertTrue(reason.indexOf('\n') < 0);
    }

    @Test
    public void rejectsBooksOverPaperByteBudget() throws Exception {
        NBTTagList pages = new NBTTagList();
        StringBuilder oversizedPage = new StringBuilder();
        for (int i = 0; i < 1024; ++i) {
            oversizedPage.append('\u0800');
        }
        pages.a(new NBTTagString(oversizedPage.toString()));
        pages.a(new NBTTagString(oversizedPage.toString()));

        Method validator = NetServerHandler.class.getDeclaredMethod("isValidBookPages", NBTTagList.class);
        validator.setAccessible(true);
        assertEquals(Boolean.FALSE, validator.invoke(null, pages));
    }

    @Test
    public void repairsPartialRegionHeaderAtEndOfFile() throws Exception {
        File regionPath = this.temporaryFolder.newFile("r.0.0.mcr");
        RandomAccessFile partial = new RandomAccessFile(regionPath, "rw");
        partial.setLength(5000L);
        partial.close();

        RegionFile region = new RegionFile(regionPath);
        region.b();

        assertEquals(8192L, regionPath.length());
        File[] backups = regionPath.getParentFile().listFiles();
        boolean foundBackup = false;
        if (backups != null) {
            for (int i = 0; i < backups.length; ++i) {
                if (backups[i].getName().startsWith(regionPath.getName() + ".malformed-") && backups[i].getName().endsWith(".bak")) {
                    foundBackup = true;
                    break;
                }
            }
        }
        assertTrue(foundBackup);
    }

    @Test
    public void oversizedRegionWriteFailsInsteadOfSilentlySucceeding() throws Exception {
        File regionPath = new File(this.temporaryFolder.getRoot(), "r.1.0.mcr");
        final RegionFile region = new RegionFile(regionPath);
        final byte[] payload = new byte[4096 * 255];
        try {
            region.a(0, 0, payload, payload.length, (byte) 2);
            fail("Expected oversized region write to fail");
        } catch (RegionFile.ChunkTooLargeException expected) {
            assertTrue(expected.sectors >= 256);
        } finally {
            region.b();
        }
    }

    @Test
    public void oversizedSidecarRoundTripsEntityData() throws Exception {
        setRegionCacheSizeForTest();
        RegionFileCache.setBulkConversionMode(true);

        File worldDirectory = this.temporaryFolder.newFolder("world");
        ChunkRegionLoader loader = new ChunkRegionLoader(worldDirectory);
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound level = new NBTTagCompound();
        NBTTagList entities = new NBTTagList();
        NBTTagCompound entity = new NBTTagCompound();
        entity.a("Blob", new byte[65536]);
        entities.a(entity);
        level.a("Entities", entities);
        level.a("TileEntities", new NBTTagList());
        root.a("Level", level);

        Method writeOversized = ChunkRegionLoader.class.getDeclaredMethod(
                "writeOversizedChunk",
                NBTTagCompound.class,
                NBTTagCompound.class,
                Integer.TYPE,
                Integer.TYPE,
                RegionFile.ChunkTooLargeException.class);
        writeOversized.setAccessible(true);
        writeOversized.invoke(loader, root, level, Integer.valueOf(2), Integer.valueOf(3),
                new RegionFile.ChunkTooLargeException(2, 3, 4096 * 255, 256));

        DataInputStream input = RegionFileCache.c(worldDirectory, 2, 3);
        assertNotNull(input);
        NBTTagCompound stored = CompressedStreamTools.a((DataInput) input);
        assertTrue(stored.k("Level").hasKey("UberBukkitOversized"));

        Method mergeOversized = ChunkRegionLoader.class.getDeclaredMethod(
                "mergeOversizedData", NBTTagCompound.class, Integer.TYPE, Integer.TYPE);
        mergeOversized.setAccessible(true);
        mergeOversized.invoke(loader, stored, Integer.valueOf(2), Integer.valueOf(3));

        NBTTagList restoredEntities = stored.k("Level").l("Entities");
        assertEquals(1, restoredEntities.c());
        assertEquals(65536, ((NBTTagCompound) restoredEntities.a(0)).j("Blob").length);
    }

    @Test
    public void failedSerializationDoesNotReplaceLastGoodRegionChunk() throws Exception {
        setRegionCacheSizeForTest();
        RegionFileCache.setBulkConversionMode(true);

        File worldDirectory = this.temporaryFolder.newFolder("serialization-world");
        ChunkRegionLoader loader = new ChunkRegionLoader(worldDirectory);
        Method writeRegionChunk = ChunkRegionLoader.class.getDeclaredMethod(
                "writeRegionChunk", NBTTagCompound.class, Integer.TYPE, Integer.TYPE);
        writeRegionChunk.setAccessible(true);

        NBTTagCompound validRoot = new NBTTagCompound();
        NBTTagCompound validLevel = new NBTTagCompound();
        validLevel.setString("Value", "last-good-copy");
        validRoot.a("Level", validLevel);
        writeRegionChunk.invoke(loader, validRoot, Integer.valueOf(4), Integer.valueOf(5));
        RegionFileCache.flushRegion(worldDirectory, 4, 5);

        NBTTagCompound invalidRoot = new NBTTagCompound();
        NBTTagCompound invalidLevel = new NBTTagCompound();
        StringBuilder oversizedUtf = new StringBuilder();
        for (int i = 0; i < 70000; ++i) {
            oversizedUtf.append('x');
        }
        invalidLevel.setString("Value", oversizedUtf.toString());
        invalidRoot.a("Level", invalidLevel);

        try {
            writeRegionChunk.invoke(loader, invalidRoot, Integer.valueOf(4), Integer.valueOf(5));
            fail("Expected serialization failure");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof IOException);
        }

        DataInputStream input = RegionFileCache.c(worldDirectory, 4, 5);
        assertNotNull(input);
        NBTTagCompound stored;
        try {
            stored = CompressedStreamTools.a((DataInput) input);
        } finally {
            input.close();
        }
        assertEquals("last-good-copy", stored.k("Level").getString("Value"));
    }

    @Test
    public void missingOversizedSidecarIsAChunkLoadFailure() throws Exception {
        setRegionCacheSizeForTest();
        RegionFileCache.setBulkConversionMode(true);

        File worldDirectory = this.temporaryFolder.newFolder("missing-sidecar-world");
        ChunkRegionLoader loader = new ChunkRegionLoader(worldDirectory);
        Method writeRegionChunk = ChunkRegionLoader.class.getDeclaredMethod(
                "writeRegionChunk", NBTTagCompound.class, Integer.TYPE, Integer.TYPE);
        writeRegionChunk.setAccessible(true);

        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound level = new NBTTagCompound();
        level.setString("UberBukkitOversized", "deadbeef-1");
        level.a("Blocks", new byte[32768]);
        root.a("Level", level);
        writeRegionChunk.invoke(loader, root, Integer.valueOf(6), Integer.valueOf(7));

        try {
            loader.a((World) null, 6, 7);
            fail("Expected missing sidecar to abort chunk loading");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Missing oversized chunk sidecar"));
        }
    }

    private static void assertListRejected(byte type, int count) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeByte(type);
        output.writeInt(count);
        output.close();

        final NBTTagList list = new NBTTagList();
        final DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        expectIOException(new PacketReader() {
            public void read() throws IOException {
                list.a(input, NBTReadLimiter.packet());
            }
        });
    }

    private static DataInputStream packet5Payload(int inventoryId, int slots, int firstItemId, int firstCount) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(inventoryId);
        output.writeShort(slots);
        for (int i = 0; i < slots; ++i) {
            if (i == 0 && firstItemId >= 0) {
                output.writeShort(firstItemId);
                output.writeByte(firstCount);
                output.writeShort(0);
            } else {
                output.writeShort(-1);
            }
        }
        output.close();
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static DataInputStream itemStackPacketPayload(int itemId, int count) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0);
        output.writeByte(64);
        output.writeInt(0);
        output.writeByte(1);
        output.writeShort(itemId);
        if (itemId >= 0) {
            output.writeByte(count);
            output.writeShort(0);
        }
        output.close();
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static DataInputStream windowClickPayload(int itemId, int count) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeByte(0);
        output.writeShort(0);
        output.writeByte(0);
        output.writeShort(0);
        output.writeShort(itemId);
        if (itemId >= 0) {
            output.writeByte(count);
            output.writeShort(0);
        }
        output.close();
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static void expectIOException(PacketReader reader) throws Exception {
        try {
            reader.read();
            fail("Expected IOException");
        } catch (IOException expected) {
        }
    }

    private static void setRegionCacheSizeForTest() throws Exception {
        Field cacheSize = RegionFileCache.class.getDeclaredField("configuredCacheSize");
        cacheSize.setAccessible(true);
        cacheSize.setInt(null, 16);
    }

    private interface PacketReader {
        void read() throws IOException;
    }
}
