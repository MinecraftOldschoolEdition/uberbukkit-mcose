package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyServerPingAndJoinCompatibilityTest {
    @Test
    public void acceptsClassicSimpleExtendedAndPingHostRequests() throws Exception {
        Packet254ServerPing classic = parsePing(new byte[0]);
        assertTrue(classic.valid);
        assertFalse(classic.extended);

        Packet254ServerPing simpleExtended = parsePing(new byte[] {1});
        assertTrue(simpleExtended.valid);
        assertTrue(simpleExtended.extended);
        assertFalse(simpleExtended.pingHost);

        Packet254ServerPing pingHost = parsePing(createPingHostBody(127, "play.example.test", 25570));
        assertTrue(pingHost.valid);
        assertTrue(pingHost.extended);
        assertTrue(pingHost.pingHost);
        assertEquals(127, pingHost.protocolVersion);
        assertEquals("play.example.test", pingHost.host);
        assertEquals(25570, pingHost.port);

        Packet254ServerPing obsoletePingHost = parsePing(createPingHostBody(72, "play.example.test", 25570));
        assertFalse(obsoletePingHost.valid);
    }

    @Test
    public void formatsVersionedAndVanillaBetaStatusResponses() {
        assertEquals(
                "\u00a71\u000014\u0000b1.7.3\u0000Welcome\u00005\u000020",
                NetLoginHandler.createLegacyPingResponse(true, "Welcome", 5, 20));
        assertEquals(
                "Welcome\u00a75\u00a720",
                NetLoginHandler.createLegacyPingResponse(false, "Welcome", 5, 20));
    }

    @Test
    public void vanillaWideHandshakeAndLoginPacketsStillDecode() throws Exception {
        ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
        DataOutputStream handshakeOutput = new DataOutputStream(handshakeBytes);
        Packet.a("Steve", handshakeOutput);
        Packet2Handshake handshake = new Packet2Handshake();
        handshake.a(new DataInputStream(new ByteArrayInputStream(handshakeBytes.toByteArray())));
        assertEquals("Steve", handshake.a);
        assertTrue(handshake.pvn11);

        ByteArrayOutputStream loginBytes = new ByteArrayOutputStream();
        DataOutputStream loginOutput = new DataOutputStream(loginBytes);
        loginOutput.writeInt(14);
        Packet.a("Steve", loginOutput);
        loginOutput.writeLong(0L);
        loginOutput.writeByte(0);
        Packet1Login login = new Packet1Login();
        login.a(new DataInputStream(new ByteArrayInputStream(loginBytes.toByteArray())));
        assertEquals(14, login.a);
        assertEquals("Steve", login.name);
        assertEquals(0L, login.c);
        assertEquals(0, login.d);

        assertTrue(Packet.isAllowedLoginPacketId(1));
        assertTrue(Packet.isAllowedLoginPacketId(2));
        assertTrue(Packet.isAllowedLoginPacketId(254));
    }

    private static Packet254ServerPing parsePing(byte[] body) throws Exception {
        Packet254ServerPing ping = new Packet254ServerPing();
        ping.a(new DataInputStream(new ByteArrayInputStream(body)));
        return ping;
    }

    private static byte[] createPingHostBody(int protocol, String host, int port) throws Exception {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream payloadOutput = new DataOutputStream(payloadBytes);
        payloadOutput.writeByte(protocol);
        Packet.a(host, payloadOutput);
        payloadOutput.writeInt(port);
        payloadOutput.flush();

        byte[] payload = payloadBytes.toByteArray();
        ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
        DataOutputStream bodyOutput = new DataOutputStream(bodyBytes);
        bodyOutput.writeByte(1);
        bodyOutput.writeByte(250);
        Packet.a("MC|PingHost", bodyOutput);
        bodyOutput.writeShort(payload.length);
        bodyOutput.write(payload);
        bodyOutput.flush();
        return bodyBytes.toByteArray();
    }
}
