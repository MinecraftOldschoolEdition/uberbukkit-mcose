package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;

public class HugeMushroomStateParityTest {
    @Test
    public void capAndStemUseThe122DirectionalStateModel() {
        BlockStateKey cap = BlockStateBridge.fromLegacy(Block.RED_MUSHROOM_CAP.id, 1);
        assertEquals(new ResourceLocation("minecraft", "red_mushroom_block"), cap.getBlockKey());
        assertEquals("true", cap.getProperty("north"));
        assertEquals("true", cap.getProperty("west"));
        assertEquals("true", cap.getProperty("up"));
        assertEquals("false", cap.getProperty("down"));
        assertNull(cap.getProperty(BlockStateBridge.PROP_LEGACY_META));
        assertEquals(1, BlockStateBridge.toLegacy(cap).metadata);

        BlockStateKey stem = BlockStateBridge.fromLegacy(Block.RED_MUSHROOM_CAP.id, 10);
        assertEquals(new ResourceLocation("minecraft", "mushroom_stem"), stem.getBlockKey());
        assertEquals("true", stem.getProperty("north"));
        assertEquals("true", stem.getProperty("east"));
        assertEquals("true", stem.getProperty("south"));
        assertEquals("true", stem.getProperty("west"));
        assertEquals("false", stem.getProperty("up"));
        assertEquals("false", stem.getProperty("down"));
        assertNull(stem.getProperty(BlockStateBridge.PROP_LEGACY_META));
        assertEquals(Block.BROWN_MUSHROOM_CAP.id, BlockStateBridge.toLegacy(stem).blockId);
        assertEquals(10, BlockStateBridge.toLegacy(stem).metadata);
    }

    @Test
    public void legacyTextureProjectionMatchesThe122FaceFlags() {
        BlockMushroomCap red = (BlockMushroomCap)Block.RED_MUSHROOM_CAP;
        BlockMushroomCap brown = (BlockMushroomCap)Block.BROWN_MUSHROOM_CAP;

        assertEquals(125, red.a(1, 5));
        assertEquals(126, brown.a(1, 5));
        assertEquals(142, red.a(0, 5));
        assertEquals(141, red.a(2, 10));
        assertEquals(141, red.a(1, 15));
        assertEquals(125, red.a(0, 14));
    }
}
