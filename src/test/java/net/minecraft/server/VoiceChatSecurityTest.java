package net.minecraft.server;

import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VoiceChatSecurityTest {

    @Test
    public void voiceBindInheritsConfiguredGameServerAddress() throws Exception {
        InetSocketAddress loopback = VoiceChatUDPServer.resolveBindAddress(" 127.0.0.1 ", 24454);
        assertEquals(24454, loopback.getPort());
        assertTrue(loopback.getAddress().isLoopbackAddress());
        assertFalse(loopback.getAddress().isAnyLocalAddress());

        InetSocketAddress wildcard = VoiceChatUDPServer.resolveBindAddress("", 24454);
        assertTrue(wildcard.getAddress().isAnyLocalAddress());
    }

    @Test
    public void clientVoiceFramesRequireExactBoundedLengths() {
        assertFalse(VoiceChatUDPServer.isValidClientDatagramFrame(null));
        assertFalse(VoiceChatUDPServer.isValidClientDatagramFrame(new byte[0]));

        assertTrue(VoiceChatUDPServer.isValidClientDatagramFrame(frame(0x01, 33)));
        assertFalse(VoiceChatUDPServer.isValidClientDatagramFrame(frame(0x01, 34)));
        assertTrue(VoiceChatUDPServer.isValidClientDatagramFrame(frame(0x05, 9)));
        assertFalse(VoiceChatUDPServer.isValidClientDatagramFrame(new byte[] { 0x05 }));
        assertTrue(VoiceChatUDPServer.isValidClientDatagramFrame(frame(0x06, 20)));
        assertTrue(VoiceChatUDPServer.isValidClientDatagramFrame(frame(0x07, 10)));

        byte[] emptyMic = new byte[12];
        emptyMic[0] = 0x03;
        assertTrue(VoiceChatUDPServer.isValidClientDatagramFrame(emptyMic));

        byte[] oneByteMic = new byte[13];
        oneByteMic[0] = 0x03;
        oneByteMic[11] = 0x01;
        assertTrue(VoiceChatUDPServer.isValidClientDatagramFrame(oneByteMic));
        byte[] truncatedMic = frame(0x03, 12);
        truncatedMic[11] = 0x01;
        assertFalse(VoiceChatUDPServer.isValidClientDatagramFrame(truncatedMic));

        byte[] oversizedMic = new byte[12];
        oversizedMic[0] = 0x03;
        oversizedMic[10] = 0x04;
        oversizedMic[11] = 0x01;
        assertFalse(VoiceChatUDPServer.isValidClientDatagramFrame(oversizedMic));
        assertFalse(VoiceChatUDPServer.isValidClientDatagramFrame(new byte[] { 0x7f }));
    }

    @Test
    public void emptyMicStopFramesStillConsumePacketRateTokens() {
        VoiceChatUDPServer.VoiceClient client = new VoiceChatUDPServer.VoiceClient(
            UUID.randomUUID(), "speaker", InetAddress.getLoopbackAddress(), 24454, UUID.randomUUID());

        assertFalse(VoiceChatUDPServer.isUdpVoiceRateLimited(client, 1000L, 0, 1, 1024, 1));
        assertTrue(VoiceChatUDPServer.isUdpVoiceRateLimited(client, 1000L, 0, 1, 1024, 1));
        assertFalse(VoiceChatUDPServer.isUdpVoiceRateLimited(client, 2000L, 0, 1, 1024, 1));
    }

    private static byte[] frame(int type, int length) {
        byte[] frame = new byte[length];
        frame[0] = (byte) type;
        return frame;
    }
}
