package net.minecraft.server.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import org.junit.Test;

public class ModProtocolBlockModelVisualTest {
    @Test
    public void serverAdvertisesAndRequiresNativeBlockModelVisuals() {
        int features = ModProtocol.resolveServerSupportedFeatures();

        assertTrue((features & ModProtocol.FEATURE_BLOCK_MODEL_VISUALS) != 0);
        assertTrue((features & ModProtocol.FEATURE_BLOCK_MODEL_STATES) != 0);
        assertTrue(ModProtocol.hasRequiredBlockModelVisuals(features));
        assertFalse(ModProtocol.hasRequiredBlockModelVisuals(0));
        assertFalse(ModProtocol.hasRequiredBlockModelVisuals(ModProtocol.FEATURE_BLOCK_MODEL_VISUALS));
        assertFalse(ModProtocol.hasRequiredBlockModelVisuals(ModProtocol.FEATURE_BLOCK_MODEL_STATES));
    }

    @Test
    public void paintingVisualPayloadUsesTheNativeModelChannelContract() throws Exception {
        byte[] payload = ModProtocol.createPaintingVisualPayload(
                123,
                -45,
                72,
                91,
                3,
                "SkullAndRoses");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));

        assertEquals(1, in.readUnsignedByte());
        assertEquals(1, in.readUnsignedByte());
        assertEquals(123, in.readInt());
        assertEquals("SkullAndRoses", in.readUTF());
        assertEquals(-45, in.readInt());
        assertEquals(72, in.readInt());
        assertEquals(91, in.readInt());
        assertEquals(3, in.readUnsignedByte());
        assertEquals(0, in.available());
        in.close();
    }
}
