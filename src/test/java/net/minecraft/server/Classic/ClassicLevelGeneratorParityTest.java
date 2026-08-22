package net.minecraft.server.Classic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class ClassicLevelGeneratorParityTest {
	@Test
	public void completeFiniteLevelKeepsTheC030GenerationContract() {
		byte[] blocks = new ClassicLevelGenerator(123456789L).generate();

		assertEquals(256 * 256 * 64, blocks.length);
		assertEquals(-28431879, Arrays.hashCode(blocks));
		for(int value : blocks) {
			assertNotEquals("c0.30 does not generate bedrock inside the finite level", 7, value & 255);
		}
		for(int z = 0; z < 256; ++z) {
			for(int x = 0; x < 256; ++x) {
				assertEquals("the c0.30 bottom layer is flowing lava", 10, blocks[(z * 256) + x] & 255);
			}
		}

		assertTrue("caves should contain air", count(blocks, 0) > 0);
		assertTrue("boundary water should be generated", count(blocks, 9) > 0);
		assertTrue("lava pockets should be generated", count(blocks, 11) > 0);
		assertTrue("gold should be generated", count(blocks, 14) > 0);
		assertTrue("iron should be generated", count(blocks, 15) > 0);
		assertTrue("coal should be generated", count(blocks, 16) > 0);
		assertTrue("trees should be generated", count(blocks, 17) > 0 && count(blocks, 18) > 0);
		assertTrue("both flowers should be generated", count(blocks, 37) > 0 && count(blocks, 38) > 0);
		assertTrue("both mushrooms should be generated", count(blocks, 39) > 0 && count(blocks, 40) > 0);
	}

	@Test
	public void chunkCopiesAreSlicesOfTheFinishedFiniteLevel() {
		ClassicLevelGenerator generator = new ClassicLevelGenerator(123456789L);
		byte[] level = generator.generate();
		byte[] chunk = new byte[16 * 16 * 128];
		generator.copyChunk(15, 15, chunk);

		for(int x = 0; x < 16; ++x) {
			for(int z = 0; z < 16; ++z) {
				for(int y = 0; y < 64; ++y) {
					int worldIndex = (y * 256 + 240 + z) * 256 + 240 + x;
					assertEquals(level[worldIndex], chunk[x << 11 | z << 7 | y]);
				}
				assertEquals(0, chunk[x << 11 | z << 7 | 64]);
				assertEquals(0, chunk[x << 11 | z << 7 | 127]);
			}
		}
	}

	private static int count(byte[] blocks, int blockId) {
		int count = 0;
		for(int value : blocks) {
			if((value & 255) == blockId) {
				++count;
			}
		}
		return count;
	}
}
