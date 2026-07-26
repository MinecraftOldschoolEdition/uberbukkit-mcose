package net.minecraft.server;

import net.minecraft.server.registry.PaintingMotiveRegistryApi;
import net.minecraft.server.registry.PaintingMotiveRegistryBootstrap;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PaintingMotiveParityTest {

    @BeforeClass
    public static void initializePaintingMotives() {
        PaintingMotiveRegistryBootstrap.initialize();
    }

    @Test
    public void mattyMatchesTheClientPaintingDefinition() {
        assertEquals("Matty", EnumArt.MATTY.A);
        assertEquals(16, EnumArt.MATTY.B);
        assertEquals(32, EnumArt.MATTY.C);
        assertEquals(0, EnumArt.MATTY.D);
        assertEquals(0, EnumArt.MATTY.E);
        assertTrue(EnumArt.z >= EnumArt.MATTY.A.length());
    }

    @Test
    public void mattyIsAvailableThroughThePaintingMotiveRegistry() {
        assertSame(EnumArt.MATTY,
                PaintingMotiveRegistryApi.get(new ResourceLocation("minecraft", "matty")));
        assertSame(EnumArt.MATTY, PaintingMotiveRegistryApi.getByIdentifier("Matty"));
    }
}
