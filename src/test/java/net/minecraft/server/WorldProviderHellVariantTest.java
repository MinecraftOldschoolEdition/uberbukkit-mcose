package net.minecraft.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldProviderHellVariantTest {

    @Test
    public void onlySkyTerrainTypeEnablesNetherSkyVariant() {
        assertTrue(WorldProviderHell.isNetherSkyTerrainType(3));

        for (int terrainType = 0; terrainType <= 7; ++terrainType) {
            if (terrainType != 3) {
                assertFalse("terrain type " + terrainType, WorldProviderHell.isNetherSkyTerrainType(terrainType));
            }
        }
    }

    @Test
    public void originatingOverworldTerrainTypeIsAuthoritative() {
        assertEquals(3, WorldProviderHell.resolveNetherVariantTerrainType(0, Integer.valueOf(3), "DEFAULT"));
        assertEquals(0, WorldProviderHell.resolveNetherVariantTerrainType(3, Integer.valueOf(0), "SKY"));
        assertEquals(6, WorldProviderHell.resolveNetherVariantTerrainType(3, Integer.valueOf(6), "SKY"));
    }

    @Test
    public void startupFallbacksPreserveSkyAndClassicVariants() {
        assertEquals(3, WorldProviderHell.resolveNetherVariantTerrainType(0, null, "SKY"));
        assertEquals(6, WorldProviderHell.resolveNetherVariantTerrainType(0, null, "CLASSIC"));
        assertEquals(3, WorldProviderHell.resolveNetherVariantTerrainType(3, null, "DEFAULT"));
        assertEquals(0, WorldProviderHell.resolveNetherVariantTerrainType(0, null, "DEFAULT"));
    }
}
