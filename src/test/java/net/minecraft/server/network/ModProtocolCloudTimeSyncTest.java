package net.minecraft.server.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModProtocolCloudTimeSyncTest {
    @Test
    public void serverAdvertisesAndEncodesCloudTimeSync() {
        assertTrue((ModProtocol.resolveServerSupportedFeatures() & ModProtocol.FEATURE_CLOUD_TIME_SYNC) != 0);

        long gameTime = 9_876_543_210L;
        byte[] payload = ModProtocol.createCloudTimePayload(gameTime);

        assertEquals(8, payload.length);
        assertEquals(gameTime, ModProtocol.readCloudTimePayload(payload));
    }

    @Test
    public void malformedCloudTimePayloadIsRejected() {
        assertEquals(Long.MIN_VALUE, ModProtocol.readCloudTimePayload(null));
        assertEquals(Long.MIN_VALUE, ModProtocol.readCloudTimePayload(new byte[7]));
        assertEquals(Long.MIN_VALUE, ModProtocol.readCloudTimePayload(new byte[9]));
    }
}
