package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;

public class WorldGenBigMushroomStateTest {
    @Test
    public void featureBuildsThe122SixFaceState() {
        BlockStateKey state = WorldGenBigMushroom.mushroomState(
                "red_mushroom_block", true, false, false, true, true, false);

        assertEquals(new ResourceLocation("minecraft", "red_mushroom_block"), state.getBlockKey());
        assertEquals("true", state.getProperty("north"));
        assertEquals("false", state.getProperty("east"));
        assertEquals("false", state.getProperty("south"));
        assertEquals("true", state.getProperty("west"));
        assertEquals("true", state.getProperty("up"));
        assertEquals("false", state.getProperty("down"));
        assertNull(state.getProperty(BlockStateBridge.PROP_LEGACY_META));

        BlockStateBridge.LegacyBlockData projected = BlockStateBridge.toLegacy(state);
        assertEquals(Block.RED_MUSHROOM_CAP.id, projected.blockId);
        assertEquals(1, projected.metadata);
    }
}
