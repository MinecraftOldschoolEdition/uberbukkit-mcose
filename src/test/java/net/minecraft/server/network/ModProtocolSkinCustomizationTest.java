package net.minecraft.server.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class ModProtocolSkinCustomizationTest {
    @Test
    public void skinCustomizationPayloadCarriesMainHand() {
        ModProtocol.SkinPartsInfo info = ModProtocol.readSkinPartsPayload(
                ModProtocol.createSkinPartsPayload("Alex", 0x55, true));

        assertEquals("Alex", info.username);
        assertEquals(0x55, info.modelPartMask);
        assertTrue(info.leftHanded);
    }

    @Test
    public void legacySkinPartsPayloadDefaultsToRightHand() {
        ModProtocol.SkinPartsInfo info = ModProtocol.readSkinPartsPayload(
                ModProtocol.createSkinPartsPayload("Steve", 0x7F));

        assertEquals("Steve", info.username);
        assertEquals(0x7F, info.modelPartMask);
        assertFalse(info.leftHanded);
    }

    @Test
    public void skinCustomizationPayloadRejectsTrailingData() {
        byte[] valid = ModProtocol.createSkinPartsPayload("Alex", 0x7F, true);
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        ModProtocol.SkinPartsInfo info = ModProtocol.readSkinPartsPayload(trailing);

        assertEquals("", info.username);
        assertFalse(info.leftHanded);
    }
}
