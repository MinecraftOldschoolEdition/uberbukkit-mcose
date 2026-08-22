package net.minecraft.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Packet70TerrainTypeStateTest {
    @Test
    public void encodesEverySupportedTerrainTypeWithoutCollidingWithLegacyReasons() {
        for (int terrainTypeId = 0; terrainTypeId <= Packet70Bed.TERRAIN_TYPE_STATE_MAX_ID; ++terrainTypeId) {
            assertEquals(
                Packet70Bed.TERRAIN_TYPE_STATE_BASE + terrainTypeId,
                Packet70Bed.terrainTypeStateReason(terrainTypeId)
            );
        }
    }

    @Test
    public void invalidTerrainTypeFallsBackToDefaultSignal() {
        assertEquals(Packet70Bed.TERRAIN_TYPE_STATE_BASE, Packet70Bed.terrainTypeStateReason(-1));
        assertEquals(Packet70Bed.TERRAIN_TYPE_STATE_BASE, Packet70Bed.terrainTypeStateReason(8));
    }
}
