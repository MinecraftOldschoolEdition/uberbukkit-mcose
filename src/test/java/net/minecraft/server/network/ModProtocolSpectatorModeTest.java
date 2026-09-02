package net.minecraft.server.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModProtocolSpectatorModeTest {
    @Test
    public void serverAdvertisesSpectatorSupport() {
        assertTrue((ModProtocol.resolveServerSupportedFeatures() & ModProtocol.FEATURE_SPECTATOR_MODE) != 0);
    }

    @Test
    public void spectatorTargetPayloadRoundTripsEntityIds() {
        byte[] payload = ModProtocol.createSpectatorTargetPayload(123456);

        assertEquals(4, payload.length);
        assertEquals(123456, ModProtocol.readSpectatorTargetPayload(payload));
        assertEquals(-1, ModProtocol.readSpectatorTargetPayload(ModProtocol.createSpectatorTargetPayload(-1)));
    }

    @Test
    public void malformedSpectatorTargetPayloadIsRejected() {
        assertEquals(Integer.MIN_VALUE, ModProtocol.readSpectatorTargetPayload(null));
        assertEquals(Integer.MIN_VALUE, ModProtocol.readSpectatorTargetPayload(new byte[3]));
        assertEquals(Integer.MIN_VALUE, ModProtocol.readSpectatorTargetPayload(new byte[5]));
    }

    @Test
    public void spectatorTeleportPayloadRoundTripsLegacyPlayerNames() {
        byte[] payload = ModProtocol.createSpectatorTeleportPayload("Camera_Target1");

        assertEquals("Camera_Target1", ModProtocol.readSpectatorTeleportPayload(payload));
    }

    @Test
    public void malformedSpectatorTeleportPayloadIsRejected() {
        assertEquals(0, ModProtocol.createSpectatorTeleportPayload("bad name").length);
        assertEquals(0, ModProtocol.createSpectatorTeleportPayload("seventeen_chars_x").length);
        assertEquals(null, ModProtocol.readSpectatorTeleportPayload(null));
        assertEquals(null, ModProtocol.readSpectatorTeleportPayload(new byte[] {0, 1, 'x', 0}));
    }
}
