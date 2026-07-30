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
}
