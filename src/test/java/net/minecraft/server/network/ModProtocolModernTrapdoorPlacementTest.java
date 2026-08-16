package net.minecraft.server.network;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ModProtocolModernTrapdoorPlacementTest {
    @Test
    public void serverAdvertisesModernTrapdoorPlacementSupport() {
        assertTrue((ModProtocol.resolveServerSupportedFeatures()
                & ModProtocol.FEATURE_MODERN_TRAPDOOR_PLACEMENT) != 0);
    }
}
