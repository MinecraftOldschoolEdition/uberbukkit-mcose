package net.minecraft.server.network;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModProtocolDropAllItemsTest {
    @Test
    public void serverAdvertisesDropAllItemsSupport() {
        assertTrue((ModProtocol.resolveServerSupportedFeatures()
                & ModProtocol.FEATURE_DROP_ALL_ITEMS) != 0);
    }
}
