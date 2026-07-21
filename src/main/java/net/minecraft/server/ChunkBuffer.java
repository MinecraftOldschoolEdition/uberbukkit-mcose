package net.minecraft.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

class ChunkBuffer extends ByteArrayOutputStream {

    private static final AtomicLong regionWriteZstdTotal = new AtomicLong(0L);
    private static final AtomicLong regionWriteZlibTotal = new AtomicLong(0L);
    private static final AtomicLong regionWriteZstdFallbackTotal = new AtomicLong(0L);
    private static final AtomicLong regionWriteZstdNanosTotal = new AtomicLong(0L);
    private static final AtomicLong regionWriteZlibNanosTotal = new AtomicLong(0L);

    private int b;
    private int c;
    private byte codec;

    final RegionFile a;

    public ChunkBuffer(RegionFile regionfile, int i, int j, byte codec) {
        super(8096);
        this.a = regionfile;
        this.b = i;
        this.c = j;
        this.codec = codec;
    }

    public void close() throws IOException {
        byte[] payload = this.buf;
        int payloadLength = this.count;
        byte codecToWrite = this.codec;

        if (this.codec == 3) {
            long zstdStart = System.nanoTime();
            byte[] compressed = ZstdRuntime.compressZstd(this.buf, 0, this.count, 3);
            if (compressed != null) {
                long zstdDuration = System.nanoTime() - zstdStart;
                if (zstdDuration > 0L) {
                    regionWriteZstdNanosTotal.addAndGet(zstdDuration);
                }
                regionWriteZstdTotal.incrementAndGet();
                payload = compressed;
                payloadLength = compressed.length;
                codecToWrite = 3;
            } else {
                regionWriteZstdFallbackTotal.incrementAndGet();
                long zlibStart = System.nanoTime();
                byte[] fallback = compressDeflate(this.buf, this.count);
                if (fallback != null) {
                    long zlibDuration = System.nanoTime() - zlibStart;
                    if (zlibDuration > 0L) {
                        regionWriteZlibNanosTotal.addAndGet(zlibDuration);
                    }
                    regionWriteZlibTotal.incrementAndGet();
                    payload = fallback;
                    payloadLength = fallback.length;
                    codecToWrite = 2;
                }
            }
        } else if (this.codec == 2) {
            regionWriteZlibTotal.incrementAndGet();
        }

        this.a.a(this.b, this.c, payload, payloadLength, codecToWrite);
    }

    static void writeUncompressed(RegionFile regionFile, int chunkX, int chunkZ, byte[] source, int length, byte preferredCodec) throws IOException {
        byte[] payload;
        byte codecToWrite;

        if (preferredCodec == 3) {
            long zstdStart = System.nanoTime();
            payload = ZstdRuntime.compressZstd(source, 0, length, 3);
            if (payload != null) {
                long duration = System.nanoTime() - zstdStart;
                if (duration > 0L) {
                    regionWriteZstdNanosTotal.addAndGet(duration);
                }
                regionWriteZstdTotal.incrementAndGet();
                codecToWrite = 3;
            } else {
                regionWriteZstdFallbackTotal.incrementAndGet();
                long zlibStart = System.nanoTime();
                payload = compressDeflate(source, length);
                long duration = System.nanoTime() - zlibStart;
                if (duration > 0L) {
                    regionWriteZlibNanosTotal.addAndGet(duration);
                }
                regionWriteZlibTotal.incrementAndGet();
                codecToWrite = 2;
            }
        } else {
            long zlibStart = System.nanoTime();
            payload = compressDeflate(source, length);
            long duration = System.nanoTime() - zlibStart;
            if (duration > 0L) {
                regionWriteZlibNanosTotal.addAndGet(duration);
            }
            regionWriteZlibTotal.incrementAndGet();
            codecToWrite = 2;
        }

        regionFile.a(chunkX, chunkZ, payload, payload.length, codecToWrite);
    }

    public static long getRegionWriteZstdTotal() {
        return regionWriteZstdTotal.get();
    }

    public static long getRegionWriteZlibTotal() {
        return regionWriteZlibTotal.get();
    }

    public static long getRegionWriteZstdFallbackTotal() {
        return regionWriteZstdFallbackTotal.get();
    }

    public static long getRegionWriteZstdNanosTotal() {
        return regionWriteZstdNanosTotal.get();
    }

    public static long getRegionWriteZlibNanosTotal() {
        return regionWriteZlibNanosTotal.get();
    }

    private static byte[] compressDeflate(byte[] source, int length) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(Math.min(length + 128, 1048576));
        Deflater deflater = new Deflater();
        DeflaterOutputStream output = new DeflaterOutputStream(compressed, deflater);
        try {
            output.write(source, 0, length);
            output.finish();
            return compressed.toByteArray();
        } finally {
            try {
                output.close();
            } finally {
                deflater.end();
            }
        }
    }
}
