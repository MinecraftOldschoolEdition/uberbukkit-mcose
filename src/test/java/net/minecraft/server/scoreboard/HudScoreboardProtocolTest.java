package net.minecraft.server.scoreboard;

import net.minecraft.server.network.ModProtocol;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HudScoreboardProtocolTest {
    @Test
    public void featureIsAdvertisedAndSnapshotRoundTrips() throws Exception {
        assertTrue((ModProtocol.resolveServerSupportedFeatures() & ModProtocol.FEATURE_HUD_SCOREBOARD) != 0);
        ModernScoreboard board = new ModernScoreboard();
        ModernScoreboard.Objective objective = board.addObjective("points", "dummy", "Points");
        board.setScore("Alice", objective, 42, false);
        List<byte[]> chunks = HudScoreboardProtocol.scoreboardSnapshot(board);
        assertEquals(1, chunks.size());

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(chunks.get(0)));
        assertEquals(HudScoreboardProtocol.VERSION, in.readUnsignedByte());
        assertEquals(HudScoreboardProtocol.SCOREBOARD_SNAPSHOT, in.readUnsignedByte());
        assertEquals(board.getRevision(), in.readInt());
        assertEquals(0, in.readUnsignedShort());
        assertEquals(1, in.readUnsignedShort());
        int total = in.readInt();
        int length = in.readUnsignedShort();
        assertEquals(total, length);
        byte[] compressed = new byte[length];
        in.readFully(compressed);
        ModernScoreboard decoded = HudScoreboardProtocol.decodeCompressedSnapshot(compressed);
        assertEquals(42, decoded.getScore("Alice", "points").value);
    }

    @Test
    public void titlePacketsRetain26_3ActionsAndTimes() throws Exception {
        DataInputStream text = new DataInputStream(new ByteArrayInputStream(
            HudScoreboardProtocol.titleText(HudScoreboardProtocol.SUBTITLE, "Ready")));
        assertEquals(1, text.readUnsignedByte());
        assertEquals(HudScoreboardProtocol.SUBTITLE, text.readUnsignedByte());
        assertEquals("Ready", text.readUTF());

        DataInputStream times = new DataInputStream(new ByteArrayInputStream(
            HudScoreboardProtocol.titleTimes(10, 70, 20)));
        assertEquals(1, times.readUnsignedByte());
        assertEquals(HudScoreboardProtocol.TIMES, times.readUnsignedByte());
        assertEquals(10, times.readInt());
        assertEquals(70, times.readInt());
        assertEquals(20, times.readInt());
    }
}
