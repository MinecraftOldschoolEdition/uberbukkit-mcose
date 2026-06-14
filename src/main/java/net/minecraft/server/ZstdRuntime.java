package net.minecraft.server;

import io.airlift.compress.zstd.ZstdCompressor;
import io.airlift.compress.zstd.ZstdDecompressor;
import io.airlift.compress.zstd.ZstdInputStream;
import java.io.InputStream;
import java.util.Arrays;

public final class ZstdRuntime {

    private static final int DEFAULT_LEVEL = 3;
    private static boolean initialized;
    private static boolean available;
    private static boolean unavailableLogged;

    private ZstdRuntime() {
    }

    public static synchronized boolean isAvailable() {
        ensureInitialized();
        return available;
    }

    public static byte[] compressZstd(byte[] input, int length) {
        return compressZstd(input, 0, length, DEFAULT_LEVEL);
    }

    public static byte[] compressZstd(byte[] input, int offset, int length, int level) {
        if (input == null || offset < 0 || length < 0 || offset + length > input.length) {
            return null;
        }
        if (!isAvailable()) {
            return null;
        }

        try {
            ZstdCompressor compressor = new ZstdCompressor();
            int bound = compressor.maxCompressedLength(length);
            if (bound <= 0) {
                return null;
            }

            byte[] out = new byte[bound];
            int written = compressor.compress(input, offset, length, out, 0, out.length);
            if (written < 0 || written > out.length) {
                return null;
            }

            return Arrays.copyOf(out, written);
        } catch (Throwable failure) {
            disableAfterFailure(failure);
            return null;
        }
    }

    public static byte[] decompressZstd(byte[] compressed, int expectedSize) {
        if (compressed == null || expectedSize < 0) {
            return null;
        }
        if (!isAvailable()) {
            return null;
        }

        try {
            byte[] out = new byte[expectedSize];
            ZstdDecompressor decompressor = new ZstdDecompressor();
            int read = decompressor.decompress(compressed, 0, compressed.length, out, 0, out.length);
            if (read != expectedSize) {
                return null;
            }
            return out;
        } catch (Throwable failure) {
            disableAfterFailure(failure);
            return null;
        }
    }

    public static InputStream openZstdInputStream(InputStream input) {
        if (input == null || !isAvailable()) {
            return null;
        }

        try {
            return new ZstdInputStream(input);
        } catch (Throwable failure) {
            disableAfterFailure(failure);
            return null;
        }
    }

    private static synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }

        try {
            new ZstdCompressor();
            new ZstdDecompressor();
            available = true;
        } catch (Throwable failure) {
            available = false;
            logUnavailable(failure.toString());
        }

        initialized = true;
    }

    private static synchronized void disableAfterFailure(Throwable failure) {
        available = false;
        initialized = true;
        logUnavailable(failure.toString());
    }

    private static void logUnavailable(String reason) {
        if (unavailableLogged) {
            return;
        }
        unavailableLogged = true;
        System.out.println("[Compression] zstd disabled, falling back to zlib: " + reason);
    }
}
