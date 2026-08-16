package net.minecraft.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockTrapdoorPlacementTest {
    @Test
    public void sidePlacementUsesClickedFaceAndExactHitHalf() {
        assertEquals(0, BlockTrapdoor.getModernPlacementMetadata(2, false, 0.5D, 90.0F, false));
        assertEquals(1, BlockTrapdoor.getModernPlacementMetadata(3, false, 0.25D, 0.0F, false));
        assertEquals(2, BlockTrapdoor.getModernPlacementMetadata(4, false, 0.25D, 0.0F, false));
        assertEquals(3, BlockTrapdoor.getModernPlacementMetadata(5, false, 0.25D, 0.0F, false));
        assertEquals(8, BlockTrapdoor.getModernPlacementMetadata(2, false, 0.5001D, 90.0F, false));
    }

    @Test
    public void floorAndCeilingPlacementFaceOppositeThePlayer() {
        assertEquals(0, BlockTrapdoor.getModernPlacementMetadata(1, false, 1.0D, 0.0F, false));
        assertEquals(3, BlockTrapdoor.getModernPlacementMetadata(1, false, 1.0D, 90.0F, false));
        assertEquals(1, BlockTrapdoor.getModernPlacementMetadata(1, false, 1.0D, 180.0F, false));
        assertEquals(2, BlockTrapdoor.getModernPlacementMetadata(1, false, 1.0D, 270.0F, false));
        assertEquals(2, BlockTrapdoor.getModernPlacementMetadata(1, false, 1.0D, -90.0F, false));
        assertEquals(8, BlockTrapdoor.getModernPlacementMetadata(0, false, 0.0D, 0.0F, false));
    }

    @Test
    public void replacementAndPowerMatchModernInitialState() {
        assertEquals(9, BlockTrapdoor.getModernPlacementMetadata(3, true, 0.25D, 180.0F, false));
        assertEquals(4, BlockTrapdoor.getModernPlacementMetadata(1, false, 1.0D, 0.0F, true));
    }

    @Test
    public void topHalfUsesCeilingBoundsWhileOpenShapeIgnoresHalf() {
        BlockTrapdoor trapdoor = (BlockTrapdoor) Block.TRAP_DOOR;
        trapdoor.c(8);
        assertEquals(13.0D / 16.0D, trapdoor.minY, 0.0D);
        assertEquals(1.0D, trapdoor.maxY, 0.0D);

        trapdoor.c(12);
        assertEquals(0.0D, trapdoor.minY, 0.0D);
        assertEquals(1.0D, trapdoor.maxY, 0.0D);
        assertTrue(BlockTrapdoor.d(12));
        assertFalse(BlockTrapdoor.d(8));
    }

    @Test
    public void legacyPlacementEntryPointStillRejectsFloorAndCeilingFaces() {
        BlockTrapdoor trapdoor = (BlockTrapdoor) Block.TRAP_DOOR;
        assertFalse(trapdoor.canPlace(null, 0, 0, 0, 0));
        assertFalse(trapdoor.canPlace(null, 0, 0, 0, 1));
    }
}
