package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.server.network.ModProtocol;
import org.junit.Test;

public class EntityTrackerPaintingBlockModelPacketTest {
    @Test
    public void nativePaintingSpawnUsesTheNegotiatedBlockModelChannel() {
        EntityPainting painting = new EntityPainting(null);
        painting.id = 123;
        painting.b = -45;
        painting.c = 72;
        painting.d = 91;
        painting.a = 3;
        painting.e = EnumArt.SKULL_AND_ROSES;

        Packet250CustomPayload packet = EntityTrackerEntry.createNativePaintingVisualPacket(painting);

        assertTrue(EntityTrackerEntry.shouldUseNativeBlockModelSpawn(painting, true));
        assertFalse(EntityTrackerEntry.shouldUseNativeBlockModelSpawn(painting, false));
        assertEquals(ModProtocol.CHANNEL_BLOCK_MODEL_VISUAL, packet.channel);
        assertTrue(packet.data.length > 0);
    }

    @Test
    public void vanillaPaintingSpawnRetainsTheBetaPacket() {
        EntityPainting painting = new EntityPainting(null);
        painting.id = 321;
        painting.b = 8;
        painting.c = 70;
        painting.d = -14;
        painting.a = 1;
        painting.e = EnumArt.KEBAB;

        assertFalse(EntityTrackerEntry.shouldUseNativeBlockModelSpawn(painting, false));
        Packet25EntityPainting packet = new Packet25EntityPainting(painting);
        assertEquals(321, packet.a);
        assertEquals(8, packet.b);
        assertEquals(70, packet.c);
        assertEquals(-14, packet.d);
        assertEquals(1, packet.e);
        assertEquals("Kebab", packet.f);
    }
}
