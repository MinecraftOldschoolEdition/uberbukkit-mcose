package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.server.network.ModProtocol;
import org.junit.Test;

public class ContainerInputTest {
    @Test
    public void serverAdvertisesAtomicContainerInputs() {
        assertTrue((ModProtocol.resolveServerSupportedFeatures() & ModProtocol.FEATURE_CONTAINER_INPUTS) != 0);
    }

    @Test
    public void legacyPickupAndQuickMoveKeepOriginalButtonEncoding() {
        assertEquals(0, ContainerInput.encodeButton(ContainerInput.PICKUP, 0));
        assertEquals(1, ContainerInput.encodeButton(ContainerInput.QUICK_MOVE, 1));
        assertFalse(ContainerInput.isExtendedButton(1));
        assertEquals(ContainerInput.PICKUP, ContainerInput.decodeInput(0, false));
        assertEquals(ContainerInput.QUICK_MOVE, ContainerInput.decodeInput(0, true));
    }

    @Test
    public void everyExtendedInputRoundTripsWithItsButton() {
        for (int input = ContainerInput.SWAP; input <= ContainerInput.PICKUP_ALL; ++input) {
            for (int button = 0; button <= 15; ++button) {
                int encoded = ContainerInput.encodeButton(input, button);
                assertTrue(ContainerInput.isExtendedButton(encoded));
                assertEquals(input, ContainerInput.decodeInput(encoded, false));
                assertEquals(button, ContainerInput.decodeButton(encoded));
            }
        }
    }

    @Test
    public void quickCraftMasksAndArmorSlotsMatchSnapshotSemantics() {
        for (int type = 0; type <= 2; ++type) {
            for (int header = 0; header <= 2; ++header) {
                int mask = ContainerInput.getQuickCraftMask(header, type);
                assertEquals(type, ContainerInput.getQuickCraftType(mask));
                assertEquals(header, ContainerInput.getQuickCraftHeader(mask));
            }
        }
        assertEquals(3, ArmorEquipHelper.getArmorInventoryIndex(0));
        assertEquals(2, ArmorEquipHelper.getArmorInventoryIndex(1));
        assertEquals(1, ArmorEquipHelper.getArmorInventoryIndex(2));
        assertEquals(0, ArmorEquipHelper.getArmorInventoryIndex(3));
    }

    @Test
    public void signedPacketButtonDecodesExtendedInput() {
        Packet102WindowClick packet = new Packet102WindowClick();
        packet.c = (byte)ContainerInput.encodeButton(ContainerInput.SWAP, 8);
        packet.f = false;

        assertTrue(packet.hasExtendedContainerInput());
        assertEquals(ContainerInput.SWAP, packet.getContainerInput());
        assertEquals(8, packet.getMouseButton());
    }
}
