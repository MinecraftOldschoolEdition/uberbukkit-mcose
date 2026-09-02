package net.minecraft.server.scoreboard;

import net.minecraft.server.PacketLimits;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/** Versioned title and chunked scoreboard payload codec. */
public final class HudScoreboardProtocol {
    public static final String CHANNEL_HUD = "MCOSE|HUD";
    public static final String CHANNEL_SCOREBOARD = "MCOSE|SCORE";
    public static final int VERSION = 1;
    public static final int TITLE = 1;
    public static final int SUBTITLE = 2;
    public static final int ACTION_BAR = 3;
    public static final int CLEAR = 4;
    public static final int RESET = 5;
    public static final int TIMES = 6;
    public static final int SCOREBOARD_SNAPSHOT = 1;
    public static final int SNAPSHOT_CHUNK_BYTES = 30000;
    public static final int MAX_SNAPSHOT_BYTES = 2 * 1024 * 1024;
    public static final int MAX_SNAPSHOT_PARTS = 80;

    private HudScoreboardProtocol() {
    }

    public static byte[] titleText(int action, String text) {
        if (action != TITLE && action != SUBTITLE && action != ACTION_BAR) return new byte[0];
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeByte(action);
            out.writeUTF(text == null ? "" : truncate(text, ModernScoreboard.MAX_TEXT));
            out.close();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            return new byte[0];
        }
    }

    public static byte[] titleClear(boolean resetTimes) {
        return new byte[] {(byte) VERSION, (byte) (resetTimes ? RESET : CLEAR)};
    }

    public static byte[] titleTimes(int fadeIn, int stay, int fadeOut) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(14);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeByte(TIMES);
            out.writeInt(fadeIn);
            out.writeInt(stay);
            out.writeInt(fadeOut);
            out.close();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            return new byte[0];
        }
    }

    public static List<byte[]> scoreboardSnapshot(ModernScoreboard scoreboard) {
        if (scoreboard == null) return Collections.emptyList();
        try {
            ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(compressedBytes);
            DataOutputStream data = new DataOutputStream(gzip);
            scoreboard.write(data);
            data.close();
            byte[] compressed = compressedBytes.toByteArray();
            if (compressed.length <= 0 || compressed.length > MAX_SNAPSHOT_BYTES) return Collections.emptyList();
            int partCount = (compressed.length + SNAPSHOT_CHUNK_BYTES - 1) / SNAPSHOT_CHUNK_BYTES;
            if (partCount <= 0 || partCount > MAX_SNAPSHOT_PARTS) return Collections.emptyList();
            List<byte[]> result = new ArrayList<byte[]>(partCount);
            int revision = scoreboard.getRevision();
            for (int part = 0; part < partCount; ++part) {
                int start = part * SNAPSHOT_CHUNK_BYTES;
                int length = Math.min(SNAPSHOT_CHUNK_BYTES, compressed.length - start);
                ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream(length + 20);
                DataOutputStream payload = new DataOutputStream(payloadBytes);
                payload.writeByte(VERSION);
                payload.writeByte(SCOREBOARD_SNAPSHOT);
                payload.writeInt(revision);
                payload.writeShort(part);
                payload.writeShort(partCount);
                payload.writeInt(compressed.length);
                payload.writeShort(length);
                payload.write(compressed, start, length);
                payload.close();
                byte[] encoded = payloadBytes.toByteArray();
                if (encoded.length > PacketLimits.MAX_CUSTOM_PAYLOAD_BYTES) return Collections.emptyList();
                result.add(encoded);
            }
            return result;
        } catch (IOException failure) {
            return Collections.emptyList();
        }
    }

    /** Used by focused tests to validate the persistence/sync wire body. */
    public static ModernScoreboard decodeCompressedSnapshot(byte[] compressed) throws IOException {
        if (compressed == null || compressed.length == 0 || compressed.length > MAX_SNAPSHOT_BYTES) {
            throw new IOException("Invalid compressed scoreboard length");
        }
        java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(compressed));
        DataInputStream in = new DataInputStream(gzip);
        ModernScoreboard result = ModernScoreboard.read(in);
        if (in.read() != -1) throw new IOException("Trailing scoreboard data");
        in.close();
        return result;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
