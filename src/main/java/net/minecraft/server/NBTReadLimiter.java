package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;

public final class NBTReadLimiter {

    public static final long PACKET_QUOTA_BYTES = 2L * 1024L * 1024L;
    public static final long WORLD_QUOTA_BYTES = 100L * 1024L * 1024L;
    public static final int DEFAULT_MAX_DEPTH = 512;

    private final long quota;
    private final int maxDepth;
    private final boolean unlimited;
    private long used;
    private int depth;

    private NBTReadLimiter(long quota, int maxDepth, boolean unlimited) {
        this.quota = quota;
        this.maxDepth = maxDepth;
        this.unlimited = unlimited;
    }

    public static NBTReadLimiter packet() {
        return new NBTReadLimiter(PACKET_QUOTA_BYTES, DEFAULT_MAX_DEPTH, false);
    }

    public static NBTReadLimiter world() {
        return new NBTReadLimiter(WORLD_QUOTA_BYTES, DEFAULT_MAX_DEPTH, false);
    }

    public static NBTReadLimiter unlimited() {
        return new NBTReadLimiter(Long.MAX_VALUE, Integer.MAX_VALUE, true);
    }

    public void account(long bytes) throws IOException {
        if (bytes < 0L) {
            throw new IOException("NBT size is negative");
        }
        if (this.unlimited) {
            return;
        }
        this.used += bytes;
        if (this.used > this.quota) {
            throw new IOException("NBT data exceeded " + this.quota + " byte quota");
        }
    }

    public void enterTag() throws IOException {
        ++this.depth;
        if (!this.unlimited && this.depth > this.maxDepth) {
            throw new IOException("NBT data exceeded " + this.maxDepth + " tag depth");
        }
    }

    public void exitTag() {
        if (this.depth > 0) {
            --this.depth;
        }
    }

    public String readUTF(DataInput input, String fieldName) throws IOException {
        int length = input.readUnsignedShort();
        this.account(2L + (long) length);

        byte[] encoded = new byte[length + 2];
        encoded[0] = (byte) (length >>> 8);
        encoded[1] = (byte) length;
        input.readFully(encoded, 2, length);

        try {
            return new DataInputStream(new ByteArrayInputStream(encoded)).readUTF();
        } catch (IOException exception) {
            throw new IOException("Invalid NBT UTF string for " + fieldName, exception);
        }
    }
}
