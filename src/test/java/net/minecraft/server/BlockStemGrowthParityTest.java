package net.minecraft.server;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;
import sun.misc.Unsafe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockStemGrowthParityTest {
	@Test
	public void regionCoreRoundTripsModernAgeAndAttachedStates() {
		assertStemState(Block.PUMPKIN_STEM.id, 0, "minecraft:pumpkin_stem", "age", "0");
		assertStemState(Block.PUMPKIN_STEM.id, 7, "minecraft:pumpkin_stem", "age", "7");
		assertStemState(Block.MELON_STEM.id, 3, "minecraft:melon_stem", "age", "3");
		assertStemState(Block.PUMPKIN_STEM.id, 8, "minecraft:attached_pumpkin_stem", "facing", "north");
		assertStemState(Block.PUMPKIN_STEM.id, 9, "minecraft:attached_pumpkin_stem", "facing", "east");
		assertStemState(Block.MELON_STEM.id, 10, "minecraft:attached_melon_stem", "facing", "south");
		assertStemState(Block.MELON_STEM.id, 11, "minecraft:attached_melon_stem", "facing", "west");
	}

	@Test
	public void regionCorePaletteStoresAttachedStemByCanonicalName() {
		byte[] blocks = new byte[32768];
		byte[] metadata = new byte[16384];
		Arrays.fill(blocks, (byte)Block.PUMPKIN_STEM.id);
		Arrays.fill(metadata, (byte)0x88);
		NBTTagCompound level = new NBTTagCompound();

		assertTrue(BlockStateCodec.writeStateData(level, blocks, metadata));

		NBTTagCompound state = (NBTTagCompound)level.l(BlockStateCodec.KEY_PALETTE).a(0);
		assertEquals("minecraft:attached_pumpkin_stem", state.getString("Name"));
		assertEquals("north", state.k("Properties").getString("facing"));
		assertFalse(state.k("Properties").hasKey(BlockStateBridge.PROP_LEGACY_META));

		BlockStateCodec.DecodedState decoded = BlockStateCodec.readStateData(level);
		assertEquals(Block.PUMPKIN_STEM.id, decoded.blocks[0] & 255);
		assertEquals(0x88, decoded.metadata[0] & 255);
	}

	@Test
	public void regionCoreUpgradesLegacyStemAndPumpkinStatesToModernNames() {
		NBTTagCompound level = new NBTTagCompound();
		NBTTagList palette = new NBTTagList();
		palette.a(stateTag("minecraft:pumpkin_stem", "legacy_meta", "8"));
		palette.a(stateTag("minecraft:pumpkin_state", "legacy_meta", "4"));
		level.a(BlockStateCodec.KEY_PALETTE, palette);
		level.a(BlockStateCodec.KEY_DATA, new byte[4096]);
		level.a(BlockStateCodec.KEY_BITS, (byte)1);
		level.a(BlockStateCodec.KEY_VERSION, 1);

		assertTrue(BlockStateCodec.canonicalizeStatePalette(level));
		assertEquals(BlockStateCodec.CURRENT_VERSION, level.e(BlockStateCodec.KEY_VERSION));

		NBTTagList converted = level.l(BlockStateCodec.KEY_PALETTE);
		NBTTagCompound attached = (NBTTagCompound)converted.a(0);
		assertEquals("minecraft:attached_pumpkin_stem", attached.getString("Name"));
		assertEquals("north", attached.k("Properties").getString("facing"));
		assertFalse(attached.k("Properties").hasKey(BlockStateBridge.PROP_LEGACY_META));

		NBTTagCompound pumpkin = (NBTTagCompound)converted.a(1);
		assertEquals("minecraft:pumpkin", pumpkin.getString("Name"));
		BlockStateBridge.LegacyBlockData runtime = BlockStateBridge.toLegacy(
			new BlockStateKey(new ResourceLocation(pumpkin.getString("Name")))
		);
		assertEquals(Block.PUMPKIN_PLAIN.id, runtime.blockId);
		assertEquals(0, runtime.metadata);
		assertFalse(BlockStateCodec.canonicalizeStatePalette(level));
	}

	@Test
	public void matureStemGrowsOneFruitThenIsRemovedWithThatFruit() throws Exception {
		TestWorld world = TestWorld.create();
		int x = 4;
		int y = 70;
		int z = 4;
		world.put(x, y - 1, z, Block.SOIL.id, 7);
		world.put(x, y - 1, z - 1, Block.DIRT.id, 0);
		world.setBlockStateAndData(x, y, z, state("minecraft:pumpkin_stem", "age", "7"));

		BlockStem stem = (BlockStem)Block.PUMPKIN_STEM;
		stem.a(world, x, y, z, new FixedRandom(0, 0));

		BlockStateKey fruit = world.getBlockStateKey(x, y, z - 1);
		BlockStateKey attached = world.getBlockStateKey(x, y, z);
		assertEquals("minecraft:pumpkin", fruit.getBlockKey().toString());
		assertEquals(Block.PUMPKIN_PLAIN.id, world.getTypeId(x, y, z - 1));
		assertEquals(0, world.getData(x, y, z - 1));
		assertEquals("minecraft:attached_pumpkin_stem", attached.getBlockKey().toString());
		assertEquals("north", attached.getProperty("facing"));
		assertFalse(stem.canGrowWithBonemeal(world, x, y, z));

		world.setTypeId(x, y, z - 1, 0);
		stem.doPhysics(world, x, y, z, 0);

		assertEquals(0, world.getTypeId(x, y, z));
	}

	@Test
	public void bonemealOnMatureStemsProducesFruitAndAttachesThem() throws Exception {
		TestWorld world = TestWorld.create();
		int y = 70;

		int pumpkinX = 12;
		int pumpkinZ = 12;
		world.put(pumpkinX, y - 1, pumpkinZ, Block.SOIL.id, 7);
		world.put(pumpkinX, y, pumpkinZ - 1, Block.STONE.id, 0);
		world.put(pumpkinX + 1, y, pumpkinZ, Block.STONE.id, 0);
		world.put(pumpkinX, y, pumpkinZ + 1, Block.STONE.id, 0);
		world.put(pumpkinX - 1, y - 1, pumpkinZ, Block.DIRT.id, 0);
		world.setBlockStateAndData(pumpkinX, y, pumpkinZ,
			state("minecraft:pumpkin_stem", "age", "7"));
		world.random = new FixedRandom(0);

		BlockStem pumpkinStem = (BlockStem)Block.PUMPKIN_STEM;
		assertTrue(pumpkinStem.canGrowWithBonemeal(world, pumpkinX, y, pumpkinZ));
		assertTrue(pumpkinStem.d_(world, pumpkinX, y, pumpkinZ));
		assertEquals(Block.PUMPKIN_PLAIN.id, world.getTypeId(pumpkinX - 1, y, pumpkinZ));
		assertEquals("minecraft:attached_pumpkin_stem",
			world.getBlockStateKey(pumpkinX, y, pumpkinZ).getBlockKey().toString());
		assertEquals("west", world.getBlockStateKey(pumpkinX, y, pumpkinZ).getProperty("facing"));
		assertFalse(pumpkinStem.canGrowWithBonemeal(world, pumpkinX, y, pumpkinZ));

		int melonX = 20;
		int melonZ = 20;
		world.put(melonX, y - 1, melonZ, Block.SOIL.id, 7);
		world.put(melonX, y, melonZ - 1, Block.STONE.id, 0);
		world.put(melonX - 1, y, melonZ, Block.STONE.id, 0);
		world.put(melonX, y, melonZ + 1, Block.STONE.id, 0);
		world.put(melonX + 1, y - 1, melonZ, Block.DIRT.id, 0);
		world.setBlockStateAndData(melonX, y, melonZ,
			state("minecraft:melon_stem", "age", "7"));
		world.random = new FixedRandom(2);

		BlockStem melonStem = (BlockStem)Block.MELON_STEM;
		assertTrue(melonStem.canGrowWithBonemeal(world, melonX, y, melonZ));
		assertTrue(melonStem.d_(world, melonX, y, melonZ));
		assertEquals(Block.MELON.id, world.getTypeId(melonX + 1, y, melonZ));
		assertEquals("minecraft:attached_melon_stem",
			world.getBlockStateKey(melonX, y, melonZ).getBlockKey().toString());
		assertEquals("east", world.getBlockStateKey(melonX, y, melonZ).getProperty("facing"));
		assertFalse(melonStem.canGrowWithBonemeal(world, melonX, y, melonZ));
		world.setTypeId(melonX + 1, y, melonZ, 0);
		melonStem.doPhysics(world, melonX, y, melonZ, 0);
		assertEquals(0, world.getTypeId(melonX, y, melonZ));
	}

	@Test
	public void matureStemOnlyTriesTheSingleSelectedDirection() throws Exception {
		TestWorld world = TestWorld.create();
		int x = 8;
		int y = 70;
		int z = 8;
		world.put(x, y - 1, z, Block.SOIL.id, 7);
		world.put(x, y, z - 1, Block.STONE.id, 0);
		world.put(x + 1, y - 1, z, Block.DIRT.id, 0);
		world.setBlockStateAndData(x, y, z, state("minecraft:melon_stem", "age", "7"));

		((BlockStem)Block.MELON_STEM).a(world, x, y, z, new FixedRandom(0, 0));

		assertEquals(Block.STONE.id, world.getTypeId(x, y, z - 1));
		assertEquals(0, world.getTypeId(x + 1, y, z));
		assertEquals("minecraft:melon_stem", world.getBlockStateKey(x, y, z).getBlockKey().toString());
		assertEquals("7", world.getBlockStateKey(x, y, z).getProperty("age"));
	}

	@Test
	public void unrelatedFruitDoesNotBlockGrowthOrDetachTheStem() throws Exception {
		TestWorld world = TestWorld.create();
		int x = 10;
		int y = 70;
		int z = 10;
		world.put(x, y - 1, z, Block.SOIL.id, 7);
		world.put(x + 1, y - 1, z, Block.DIRT.id, 0);
		world.setBlockStateAndData(x - 1, y, z,
			new BlockStateKey(new ResourceLocation("minecraft:pumpkin")));
		world.setBlockStateAndData(x, y, z, state("minecraft:pumpkin_stem", "age", "7"));

		BlockStem stem = (BlockStem)Block.PUMPKIN_STEM;
		stem.a(world, x, y, z, new FixedRandom(0, 1));

		assertEquals(Block.PUMPKIN_PLAIN.id, world.getTypeId(x + 1, y, z));
		assertEquals("east", world.getBlockStateKey(x, y, z).getProperty("facing"));
		world.setTypeId(x - 1, y, z, 0);
		stem.doPhysics(world, x, y, z, 0);
		assertEquals("minecraft:attached_pumpkin_stem",
			world.getBlockStateKey(x, y, z).getBlockKey().toString());
		assertEquals("east", world.getBlockStateKey(x, y, z).getProperty("facing"));
	}

	@Test
	public void bonemealAddsTwoToFiveAgesWithoutForcingFruit() throws Exception {
		TestWorld world = TestWorld.create();
		world.random = new FixedRandom(0);
		int x = 6;
		int y = 70;
		int z = 6;
		world.put(x, y - 1, z, Block.SOIL.id, 7);
		world.setBlockStateAndData(x, y, z, state("minecraft:melon_stem", "age", "1"));

		BlockStem stem = (BlockStem)Block.MELON_STEM;
		assertTrue(stem.d_(world, x, y, z));
		assertEquals("3", world.getBlockStateKey(x, y, z).getProperty("age"));
		assertEquals(0, world.getTypeId(x, y, z - 1));
	}

	@Test
	public void growthChanceUsesSnapshotBound() {
		assertEquals(26, BlockStem.growthChanceBound(1.0F));
		assertEquals(7, BlockStem.growthChanceBound(4.0F));
		assertEquals(3, BlockStem.growthChanceBound(10.0F));
	}

	private static void assertStemState(int blockId, int metadata, String expectedName, String property, String value) {
		BlockStateKey state = BlockStateBridge.fromLegacy(blockId, metadata);
		BlockStateBridge.LegacyBlockData decoded = BlockStateBridge.toLegacy(state);
		assertEquals(expectedName, state.getBlockKey().toString());
		assertEquals(value, state.getProperty(property));
		assertEquals(blockId, decoded.blockId);
		assertEquals(metadata, decoded.metadata);
		assertFalse(decoded.fallbackUsed);
	}

	private static BlockStateKey state(String name, String property, String value) {
		return new BlockStateKey(new ResourceLocation(name)).withProperty(property, value);
	}

	private static NBTTagCompound stateTag(String name, String property, String value) {
		NBTTagCompound state = new NBTTagCompound();
		state.setString("Name", name);
		NBTTagCompound properties = new NBTTagCompound();
		properties.setString(property, value);
		state.a("Properties", properties);
		return state;
	}

	private static final class FixedRandom extends Random {
		private final int[] values;
		private int index;

		FixedRandom(int... values) {
			this.values = values;
		}

		public int nextInt(int bound) {
			int value = this.index < this.values.length ? this.values[this.index++] : 0;
			if (value < 0 || value >= bound) {
				throw new AssertionError("fixed random value " + value + " outside bound " + bound);
			}
			return value;
		}
	}

	private static final class TestWorld extends World {
		private Map<Long, Integer> blocks;
		private Map<Long, Integer> metadata;

		private TestWorld() {
			super(null, "unused", 0L, null, null, org.bukkit.World.Environment.NORMAL);
		}

		static TestWorld create() throws Exception {
			TestWorld world = (TestWorld)unsafe().allocateInstance(TestWorld.class);
			world.blocks = new HashMap<Long, Integer>();
			world.metadata = new HashMap<Long, Integer>();
			world.random = new Random(1L);
			return world;
		}

		void put(int x, int y, int z, int blockId, int data) {
			long key = key(x, y, z);
			if (blockId == 0) {
				this.blocks.remove(Long.valueOf(key));
				this.metadata.remove(Long.valueOf(key));
				return;
			}
			this.blocks.put(Long.valueOf(key), Integer.valueOf(blockId));
			this.metadata.put(Long.valueOf(key), Integer.valueOf(data & 15));
		}

		public int getTypeId(int x, int y, int z) {
			Integer value = this.blocks.get(Long.valueOf(key(x, y, z)));
			return value == null ? 0 : value.intValue();
		}

		public int getData(int x, int y, int z) {
			Integer value = this.metadata.get(Long.valueOf(key(x, y, z)));
			return value == null ? 0 : value.intValue();
		}

		public void setData(int x, int y, int z, int data) {
			if (this.getTypeId(x, y, z) != 0) {
				this.metadata.put(Long.valueOf(key(x, y, z)), Integer.valueOf(data & 15));
			}
		}

		public boolean setTypeId(int x, int y, int z, int blockId) {
			this.put(x, y, z, blockId, 0);
			return true;
		}

		public boolean setTypeIdAndData(int x, int y, int z, int blockId, int data) {
			this.put(x, y, z, blockId, data);
			return true;
		}

		public boolean setBlockStateAndData(int x, int y, int z, BlockStateKey state) {
			BlockStateBridge.LegacyBlockData legacy = BlockStateBridge.toLegacy(state);
			this.put(x, y, z, legacy.blockId, legacy.metadata);
			return !legacy.fallbackUsed;
		}

		public int k(int x, int y, int z) {
			return 15;
		}

		private static long key(int x, int y, int z) {
			return ((long)x & 0x3FFFFFFL) << 38 | ((long)z & 0x3FFFFFFL) << 12 | (long)y & 0xFFFL;
		}
	}

	private static Unsafe unsafe() throws Exception {
		Field field = Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return (Unsafe)field.get(null);
	}
}
