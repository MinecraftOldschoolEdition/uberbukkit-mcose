package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class RegionFile {

    private static final byte[] a = new byte[4096];
    private static final byte REGION_CODEC_GZIP = 1;
    private static final byte REGION_CODEC_ZLIB = 2;
    private static final byte REGION_CODEC_ZSTD = 3;
    private static final String WAL_LOG_PREFIX = "[RegionCore WAL]";
    private static final int WAL_LOG_LEVEL = getWalLogLevel();
    private static final boolean WAL_STRICT_SYNC = isStrictWalMode();
    private static final int WAL_SYNC_BATCH = getWalSyncBatch();
    private static boolean walBannerLogged = false;
    private final File b;
    private RandomAccessFile c;
    private RegionFileWAL writeAheadLog;
    private final int[] d = new int[1024];
    private final int[] e = new int[1024];
    private ArrayList f;
    private int g;
    private long h = 0L;
    private int walPendingWrites = 0;
    private int walCommittedWrites = 0;
    private boolean walFirstWriteLogged = false;

    public RegionFile(File file1) {
        this.b = file1;
        this.b("REGION LOAD " + this.b);
        this.g = 0;
        boolean flag = false;

        try {
            if (file1.exists()) {
                this.h = file1.lastModified();
            }

            this.c = new RandomAccessFile(file1, "rw");
            int i;

            long originalLength = this.c.length();
            boolean malformedBackupAttempted = false;
            if (originalLength > 0L && (originalLength < 8192L || (originalLength & 4095L) != 0L)) {
                if (!this.backupMalformedRegionFile(originalLength)) {
                    throw new IOException("Unable to back up malformed region file before repair: " + this.b);
                }
                malformedBackupAttempted = true;
            }

            if (this.c.length() < 8192L) {
                this.padToLength(8192L);
            }

            long remainder = this.c.length() & 4095L;
            if (remainder != 0L) {
                this.padToLength(this.c.length() + (4096L - remainder));
            }

            i = (int) this.c.length() / 4096;
            this.f = new ArrayList(i);

            int j;

            for (j = 0; j < i; ++j) {
                this.f.add(Boolean.valueOf(true));
            }

            this.f.set(0, Boolean.valueOf(false));
            this.f.set(1, Boolean.valueOf(false));
            this.c.seek(0L);

            int k;
            Set invalidHeaderEntries = new HashSet();

            for (j = 0; j < 1024; ++j) {
                k = this.c.readInt();
                this.d[j] = k;
                if (k != 0) {
                    int sector = k >> 8;
                    int sectorCount = k & 255;
                    boolean valid = sector >= 2 && sectorCount > 0 && sector + sectorCount <= this.f.size();

                    if (valid) {
                        for (int l = 0; l < sectorCount; ++l) {
                            if (!((Boolean) this.f.get(sector + l)).booleanValue()) {
                                valid = false;
                                break;
                            }
                        }
                    }

                    if (valid) {
                        for (int l = 0; l < sectorCount; ++l) {
                            this.f.set(sector + l, Boolean.valueOf(false));
                        }
                    } else {
                        this.d[j] = 0;
                        invalidHeaderEntries.add(Integer.valueOf(j));
                    }
                }
            }

            for (j = 0; j < 1024; ++j) {
                k = this.c.readInt();
                this.e[j] = k;
            }

            if (!invalidHeaderEntries.isEmpty()) {
                if (!malformedBackupAttempted) {
                    if (!this.backupMalformedRegionFile(originalLength)) {
                        throw new IOException("Unable to back up corrupt region header before repair: " + this.b);
                    }
                }
                this.clearInvalidHeaderEntries(invalidHeaderEntries);
            }

            flag = true;
        } catch (IOException ioexception) {
            try {
                if (this.c != null) {
                    this.c.close();
                }
            } catch (IOException closeFailure) {
                ioexception.addSuppressed(closeFailure);
            }
            throw new RegionFileInitializationException(this.b, ioexception);
        }

        if (flag) {
            if (RegionFileCache.isBulkConversionMode()) {
                this.logWal(1, "disabled for " + this.b.getName() + " (bulk conversion mode)");
            } else {
                try {
                    this.writeAheadLog = new RegionFileWAL(this.b);
                    this.logWalBanner();
                    this.logWal(1, "enabled for " + this.b.getName() + " (mode=" + (WAL_STRICT_SYNC ? "strict" : "balanced") + ", batch=" + WAL_SYNC_BATCH + ")");
                    this.recoverFromWriteAheadLog();
                } catch (IOException ioexception1) {
                    this.writeAheadLog = null;
                    this.logWal(1, "disabled for " + this.b.getName() + " (init failed: " + ioexception1.getMessage() + ")");
                    ioexception1.printStackTrace();
                }
            }
        }
    }

    private void recoverFromWriteAheadLog() {
        if (this.writeAheadLog != null) {
            try {
                ArrayList arraylist = this.writeAheadLog.readPendingWrites();

                if (!arraylist.isEmpty()) {
                    this.logWal(1, "replaying " + arraylist.size() + " pending WAL writes for " + this.b.getName());

                    for (int i = 0; i < arraylist.size(); ++i) {
                        RegionFileWAL.PendingEntry regionfilewal_pendingentry = (RegionFileWAL.PendingEntry) arraylist.get(i);

                        this.a("WAL", regionfilewal_pendingentry.chunkX, regionfilewal_pendingentry.chunkZ, regionfilewal_pendingentry.length, "replay");
                        this.writeChunk(regionfilewal_pendingentry.chunkX, regionfilewal_pendingentry.chunkZ, regionfilewal_pendingentry.data, regionfilewal_pendingentry.length, regionfilewal_pendingentry.codec);
                    }

                    this.c.getFD().sync();
                    this.writeAheadLog.clear(true);
                    this.walPendingWrites = 0;
                    this.logWal(1, "replay committed for " + this.b.getName());
                }
            } catch (IOException ioexception) {
                this.logWal(1, "recovery failed for " + this.b.getName() + " (" + ioexception.getMessage() + ")");
                ioexception.printStackTrace();
            }
        }
    }

    public synchronized int a() {
        int i = this.g;

        this.g = 0;
        return i;
    }

    private void a(String s) {
    }

    private void b(String s) {
        this.a(s + "\n");
    }

    private void a(String s, int i, int j, String s1) {
        this.a("REGION " + s + " " + this.b.getName() + "[" + i + "," + j + "] = " + s1);
    }

    private void a(String s, int i, int j, int k, String s1) {
        this.a("REGION " + s + " " + this.b.getName() + "[" + i + "," + j + "] " + k + "B = " + s1);
    }

    private void b(String s, int i, int j, String s1) {
        this.a(s, i, j, s1 + "\n");
    }

    public synchronized DataInputStream a(int i, int j) {
        if (this.d(i, j)) {
            this.b("READ", i, j, "out of bounds");
            return null;
        } else {
            try {
                int k = this.e(i, j);

                if (k == 0) {
                    return null;
                } else {
                    int l = k >> 8;
                    int i1 = k & 255;

                    if (l < 2 || i1 <= 0 || l + i1 > this.f.size()) {
                        this.b("READ", i, j, "invalid sector");
                        return null;
                    } else {
                        this.c.seek((long) (l * 4096));
                        int j1 = this.c.readInt();

                        int maxLength = 4096 * i1 - 4;
                        if (j1 <= 0 || j1 > maxLength) {
                            this.b("READ", i, j, "invalid length: " + j1 + " (max " + maxLength + ")");
                            return null;
                        } else {
                            byte b0 = this.c.readByte();
                            byte[] abyte;
                            DataInputStream datainputstream;

                            if (b0 == REGION_CODEC_GZIP) {
                                abyte = new byte[j1 - 1];
                                this.c.readFully(abyte);
                                datainputstream = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(abyte)));
                                return datainputstream;
                            } else if (b0 == REGION_CODEC_ZLIB) {
                                abyte = new byte[j1 - 1];
                                this.c.readFully(abyte);
                                datainputstream = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(abyte)));
                                return datainputstream;
                            } else if (b0 == REGION_CODEC_ZSTD) {
                                abyte = new byte[j1 - 1];
                                this.c.readFully(abyte);
                                InputStream zstdStream = ZstdRuntime.openZstdInputStream(new ByteArrayInputStream(abyte));
                                if (zstdStream == null) {
                                    this.b("READ", i, j, "zstd unavailable");
                                    return null;
                                }
                                return new DataInputStream(zstdStream);
                            } else {
                                this.b("READ", i, j, "unknown version " + b0);
                                return null;
                            }
                        }
                    }
                }
            } catch (IOException ioexception) {
                this.b("READ", i, j, "exception");
                return null;
            }
        }
    }

    public DataOutputStream b(int i, int j) {
        if (this.d(i, j)) {
            return null;
        }

        byte codec = ZstdRuntime.isAvailable() ? REGION_CODEC_ZSTD : REGION_CODEC_ZLIB;
        ChunkBuffer chunkBuffer = new ChunkBuffer(this, i, j, codec);
        if (codec == REGION_CODEC_ZSTD) {
            return new DataOutputStream(chunkBuffer);
        }
        return new DataOutputStream(new DeflaterOutputStream(chunkBuffer));
    }

    public void writeUncompressed(int i, int j, byte[] serialized, int length) throws IOException {
        if (this.d(i, j)) {
            throw new IOException("Chunk coordinates out of region bounds: " + i + "," + j);
        }
        if (serialized == null || length < 0 || length > serialized.length) {
            throw new IOException("Invalid uncompressed chunk payload length: " + length);
        }

        byte codec = ZstdRuntime.isAvailable() ? REGION_CODEC_ZSTD : REGION_CODEC_ZLIB;
        ChunkBuffer.writeUncompressed(this, i, j, serialized, length, codec);
    }

    protected synchronized void a(int i, int j, byte[] abyte, int k) throws IOException {
        this.a(i, j, abyte, k, REGION_CODEC_ZLIB);
    }

    protected synchronized void a(int i, int j, byte[] abyte, int k, byte codec) throws IOException {
        try {
            validateChunkPayload(i, j, abyte, k, codec);

            if (this.writeAheadLog != null) {
                this.logWal(2, "begin " + this.b.getName() + " chunk [" + i + "," + j + "] bytes=" + k);
                if (!this.walFirstWriteLogged) {
                    this.logWal(1, "first write observed for " + this.b.getName());
                    this.walFirstWriteLogged = true;
                }

                this.writeAheadLog.appendPendingWrite(i, j, abyte, k, codec, WAL_STRICT_SYNC);
            }

            this.writeChunk(i, j, abyte, k, codec);
            if (this.writeAheadLog != null) {
                ++this.walPendingWrites;
                ++this.walCommittedWrites;
                if (WAL_STRICT_SYNC || this.walPendingWrites >= WAL_SYNC_BATCH) {
                    this.flushWal("sync");
                } else if (this.walCommittedWrites % 256 == 0) {
                    this.logWal(1, "progress " + this.b.getName() + " committed=" + this.walCommittedWrites + " pending=" + this.walPendingWrites);
                }

                this.logWal(2, "commit " + this.b.getName() + " chunk [" + i + "," + j + "]");
            }
        } catch (IOException ioexception) {
            this.logWal(1, "write failed " + this.b.getName() + " chunk [" + i + "," + j + "] (" + ioexception.getMessage() + ")");
            throw ioexception;
        }
    }

    private void flushWal(String s) throws IOException {
        if (this.writeAheadLog != null && this.walPendingWrites > 0) {
            if (!WAL_STRICT_SYNC) {
                this.writeAheadLog.sync();
            }

            this.c.getFD().sync();
            this.writeAheadLog.clear(!WAL_STRICT_SYNC);
            this.logWal(2, "flush " + this.b.getName() + " reason=" + s + " writes=" + this.walPendingWrites);
            this.walPendingWrites = 0;
        }
    }

    public synchronized void flush() throws IOException {
        if (this.writeAheadLog != null) {
            this.flushWal("save");
        } else {
            this.c.getFD().sync();
        }
    }

    private void writeChunk(int i, int j, byte[] abyte, int k, byte codec) throws IOException {
        validateChunkPayload(i, j, abyte, k, codec);

        int l = this.e(i, j);
        int i1 = l >> 8;
        int j1 = l & 255;
        int k1 = (k + 5) / 4096 + 1;

        if (k1 >= 256) {
            throw new ChunkTooLargeException(i, j, k, k1);
        }

        if (k1 < 256) {
            if (i1 != 0 && j1 == k1) {
                this.a("SAVE", i, j, k, "rewrite");
                this.writeSector(i1, abyte, k, codec);
            } else {
                int l1;

                for (l1 = 0; l1 < j1; ++l1) {
                    this.f.set(i1 + l1, Boolean.valueOf(true));
                }

                l1 = this.f.indexOf(Boolean.valueOf(true));
                int i2 = 0;
                int j2;

                if (l1 != -1) {
                    for (j2 = l1; j2 < this.f.size(); ++j2) {
                        if (i2 != 0) {
                            if (((Boolean) this.f.get(j2)).booleanValue()) {
                                ++i2;
                            } else {
                                i2 = 0;
                            }
                        } else if (((Boolean) this.f.get(j2)).booleanValue()) {
                            l1 = j2;
                            i2 = 1;
                        }

                        if (i2 >= k1) {
                            break;
                        }
                    }
                }

                if (i2 >= k1) {
                    this.a("SAVE", i, j, k, "reuse");
                    i1 = l1;
                    this.a(i, j, l1 << 8 | k1);

                    for (j2 = 0; j2 < k1; ++j2) {
                        this.f.set(i1 + j2, Boolean.valueOf(false));
                    }

                    this.writeSector(i1, abyte, k, codec);
                } else {
                    this.a("SAVE", i, j, k, "grow");
                    this.c.seek(this.c.length());
                    i1 = this.f.size();

                    for (j2 = 0; j2 < k1; ++j2) {
                        this.c.write(a);
                        this.f.add(Boolean.valueOf(false));
                    }

                    this.g += 4096 * k1;
                    this.writeSector(i1, abyte, k, codec);
                    this.a(i, j, i1 << 8 | k1);
                }
            }

            this.b(i, j, (int) (System.currentTimeMillis() / 1000L));
        }
    }

    private static void validateChunkPayload(int chunkX, int chunkZ, byte[] payload, int length, byte codec) throws IOException {
        if (payload == null || length < 0 || length > payload.length) {
            throw new IOException("Invalid chunk payload length: " + length);
        }
        if (codec != REGION_CODEC_GZIP && codec != REGION_CODEC_ZLIB && codec != REGION_CODEC_ZSTD) {
            throw new IOException("Invalid region codec: " + codec);
        }

        int sectors = (int) (((long) length + 5L) / 4096L + 1L);
        if (sectors >= 256) {
            throw new ChunkTooLargeException(chunkX, chunkZ, length, sectors);
        }
    }

    private boolean backupMalformedRegionFile(long originalLength) {
        File backup = new File(this.b.getPath() + ".malformed-" + System.currentTimeMillis() + ".bak");
        try {
            java.nio.file.Files.copy(this.b.toPath(), backup.toPath());
            System.err.println("Backed up malformed region file " + this.b.getName() + " (" + originalLength + " bytes) to " + backup.getName());
            return true;
        } catch (IOException exception) {
            System.err.println("Failed to back up malformed region file " + this.b.getName() + ": " + exception.getMessage());
            return false;
        }
    }

    private void padToLength(long targetLength) throws IOException {
        this.c.seek(this.c.length());
        while (this.c.length() < targetLength) {
            int bytes = (int) Math.min((long) a.length, targetLength - this.c.length());
            this.c.write(a, 0, bytes);
            this.g += bytes;
        }
    }

    private void clearInvalidHeaderEntries(Set invalidHeaderEntries) throws IOException {
        java.util.Iterator iterator = invalidHeaderEntries.iterator();
        while (iterator.hasNext()) {
            int index = ((Integer) iterator.next()).intValue();
            this.c.seek((long) index * 4L);
            this.c.writeInt(0);
            this.c.seek(4096L + (long) index * 4L);
            this.c.writeInt(0);
            this.e[index] = 0;
        }
        this.c.getFD().sync();
        System.err.println("Cleared " + invalidHeaderEntries.size() + " invalid chunk header entries in " + this.b.getName());
    }

    static final class ChunkTooLargeException extends IOException {
        final int chunkX;
        final int chunkZ;
        final int payloadBytes;
        final int sectors;

        ChunkTooLargeException(int chunkX, int chunkZ, int payloadBytes, int sectors) {
            super("Chunk [" + chunkX + "," + chunkZ + "] is too large for the region format (" + payloadBytes + " bytes, " + sectors + " sectors)");
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.payloadBytes = payloadBytes;
            this.sectors = sectors;
        }
    }

    static final class RegionFileInitializationException extends RuntimeException {
        RegionFileInitializationException(File file, IOException cause) {
            super("Unable to initialize region file " + file, cause);
        }
    }

    private void writeSector(int i, byte[] abyte, int j, byte codec) throws IOException {
        this.b(" " + i);
        this.c.seek((long) (i * 4096));
        this.c.writeInt(j + 1);
        this.c.writeByte(codec);
        this.c.write(abyte, 0, j);
    }

    private boolean d(int i, int j) {
        return i < 0 || i >= 32 || j < 0 || j >= 32;
    }

    private int e(int i, int j) {
        return this.d[i + j * 32];
    }

    public boolean c(int i, int j) {
        return this.e(i, j) != 0;
    }

    private void a(int i, int j, int k) throws IOException {
        this.d[i + j * 32] = k;
        this.c.seek((long) ((i + j * 32) * 4));
        this.c.writeInt(k);
    }

    private void b(int i, int j, int k) throws IOException {
        this.e[i + j * 32] = k;
        this.c.seek((long) (4096 + (i + j * 32) * 4));
        this.c.writeInt(k);
    }

    public void b() throws IOException {
        try {
            if (this.writeAheadLog != null) {
                this.flushWal("close");
                this.logWal(1, "close " + this.b.getName());
                this.writeAheadLog.close();
            }
        } finally {
            this.c.close();
        }
    }

    private void logWal(int i, String s) {
        if (WAL_LOG_LEVEL >= i) {
            System.out.println(WAL_LOG_PREFIX + " " + s);
        }
    }

    private synchronized void logWalBanner() {
        if (!walBannerLogged && WAL_LOG_LEVEL > 0) {
            String s = WAL_LOG_LEVEL >= 2 ? "verbose" : "basic";

            System.out.println(WAL_LOG_PREFIX + " active mode=" + (WAL_STRICT_SYNC ? "strict" : "balanced") + ", batch=" + WAL_SYNC_BATCH + ", log=" + s);
            walBannerLogged = true;
        }
    }

    private static boolean isStrictWalMode() {
        String s = getWalProperty("mode", "balanced");

        return "strict".equals(s.toLowerCase(Locale.ROOT));
    }

    private static int getWalSyncBatch() {
        String custom = System.getProperty("regioncore.wal.syncBatch");
        if (custom != null) {
            try {
                int parsed = Integer.parseInt(custom.trim());
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        Integer integer = Integer.getInteger("mcregion.wal.syncBatch");

        return integer != null && integer.intValue() > 0 ? integer.intValue() : 64;
    }

    private static int getWalLogLevel() {
        String s = getWalProperty("log", "off");
        String s1 = s.toLowerCase(Locale.ROOT);

        if (!"0".equals(s1) && !"false".equals(s1) && !"off".equals(s1)) {
            return !"2".equals(s1) && !"verbose".equals(s1) && !"debug".equals(s1) ? 1 : 2;
        } else {
            return 0;
        }
    }

    private static String getWalProperty(String suffix, String fallback) {
        String value = System.getProperty("regioncore.wal." + suffix);
        if (value != null) {
            return value;
        }
        return System.getProperty("mcregion.wal." + suffix, fallback);
    }
}
