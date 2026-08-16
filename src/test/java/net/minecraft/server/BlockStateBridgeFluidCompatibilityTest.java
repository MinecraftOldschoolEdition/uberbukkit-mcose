package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class BlockStateBridgeFluidCompatibilityTest {
	@Test
	public void everyWaterAndLavaStateRoundTripsWithoutFallback() {
		assertFluid(Block.WATER.id, "water", "flowing");
		assertFluid(Block.STATIONARY_WATER.id, "water", "still");
		assertFluid(Block.LAVA.id, "lava", "flowing");
		assertFluid(Block.STATIONARY_LAVA.id, "lava", "still");
	}

	private static void assertFluid(int blockId, String fluid, String variant) {
		for(int metadata = 0; metadata < 16; ++metadata) {
			BlockStateKey state = BlockStateBridge.fromLegacy(blockId, metadata);
			assertEquals(fluid, state.getProperty("fluid"));
			assertEquals(variant, state.getProperty("variant"));
			assertEquals(Integer.toString(metadata), state.getProperty("level"));
			assertEquals(Boolean.toString((metadata & 8) != 0), state.getProperty("falling"));

			BlockStateBridge.LegacyBlockData decoded = BlockStateBridge.toLegacy(state);
			assertEquals("block id for " + state, blockId, decoded.blockId);
			assertEquals("metadata for " + state, metadata, decoded.metadata);
			assertFalse("fallback for " + state, decoded.fallbackUsed);
		}
	}
}
