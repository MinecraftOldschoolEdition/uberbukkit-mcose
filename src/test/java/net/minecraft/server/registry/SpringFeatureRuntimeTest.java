package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.server.Block;
import net.minecraft.server.BlockStateBridge;
import net.minecraft.server.BlockStateKey;
import net.minecraft.server.Material;
import net.minecraft.server.World;
import net.minecraft.server.WorldGenHellLava;
import net.minecraft.server.WorldGenerator;
import net.minecraft.server.WorldProviderHell;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

/** Focused runtime proof for the provider-specific spring callbacks. */
public class SpringFeatureRuntimeTest {
    @BeforeClass
    public static void initializeFeatures() {
        PlacedFeatureDataBootstrap.initialize();
    }

    @Test
    public void alphaPlacesTheConfiguredFluidWithoutTheNormalImmediateTick()
            throws Exception {
        ConfiguredFeatureDefinition configured =
                ConfiguredFeatureDataBootstrap.get(
                        ConfiguredFeatureDataBootstrap.SPRING_WATER);
        WorldGenerator alpha = LegacyPlacedFeatureExecutor.createSpringGenerator(
                configured, true);

        RecordingWorld normalWorld = RecordingWorld.springPocket();
        RecordingWorld alphaWorld = RecordingWorld.springPocket();
        Random normalRandom = new Random(938475L);
        Random alphaRandom = new Random(938475L);

        assertTrue(LegacyPlacedFeatureExecutor.generateSpringAt(
                normalWorld, normalRandom, 0, 32, 0,
                ConfiguredFeatureDataBootstrap.SPRING_WATER));
        assertTrue(alpha.a(alphaWorld, alphaRandom, 0, 32, 0));

        // The normal Beta callback ticks immediately and stabilizes the source.
        assertEquals(Block.STATIONARY_WATER.id,
                normalWorld.getTypeId(0, 32, 0));
        // Alpha only writes the configured flowing block; it never ticks it here.
        assertEquals(Block.WATER.id, alphaWorld.getTypeId(0, 32, 0));
        assertFalse(normalWorld.a);
        assertFalse(alphaWorld.a);
        assertTrue(normalWorld.blockWrites > alphaWorld.blockWrites);
        assertEquals(normalRandom.nextLong(), alphaRandom.nextLong());
    }

    @Test
    public void netherOpenSpringMatchesWorldGenHellLavaExactly()
            throws Exception {
        RecordingWorld legacyWorld = RecordingWorld.springPocket(
                Block.NETHERRACK.id);
        RecordingWorld dataWorld = RecordingWorld.springPocket(
                Block.NETHERRACK.id);
        Random legacyRandom = new Random(246813579L);
        Random dataRandom = new Random(246813579L);

        assertTrue(new WorldGenHellLava("minecraft:lava").a(
                legacyWorld, legacyRandom, 0, 32, 0));
        assertTrue(LegacyPlacedFeatureExecutor.generateSpringAt(
                dataWorld, dataRandom, 0, 32, 0,
                ConfiguredFeatureDataBootstrap.SPRING_NETHER_OPEN));

        assertEquals(legacyWorld.getTypeId(0, 32, 0),
                dataWorld.getTypeId(0, 32, 0));
        assertEquals(legacyWorld.getData(0, 32, 0),
                dataWorld.getData(0, 32, 0));
        assertEquals(legacyWorld.blockWrites, dataWorld.blockWrites);
        assertEquals(legacyWorld.a, dataWorld.a);
        assertEquals(legacyRandom.nextLong(), dataRandom.nextLong());
    }

    private static final class RecordingWorld extends World {
        private Map<Long, Integer> blocks;
        private Map<Long, Integer> metadata;
        private int blockWrites;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null,
                    org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld springPocket() throws Exception {
            return springPocket(Block.STONE.id);
        }

        static RecordingWorld springPocket(int validBlockId) throws Exception {
            RecordingWorld world = (RecordingWorld)unsafe()
                    .allocateInstance(RecordingWorld.class);
            world.blocks = new HashMap<Long, Integer>();
            world.metadata = new HashMap<Long, Integer>();
            world.random = new Random(1L);
            world.worldProvider = new WorldProviderHell();
            world.worldProvider.d = true;
            world.put(0, 33, 0, validBlockId, 0);
            world.put(0, 31, 0, validBlockId, 0);
            world.put(0, 32, 0, validBlockId, 0);
            world.put(-1, 32, 0, validBlockId, 0);
            world.put(1, 32, 0, 0, 0);
            world.put(0, 32, -1, validBlockId, 0);
            world.put(0, 32, 1, validBlockId, 0);
            return world;
        }

        public int getTypeId(int x, int y, int z) {
            Integer value = this.blocks.get(Long.valueOf(key(x, y, z)));
            return value == null ? Block.STONE.id : value.intValue();
        }

        public int getTypeIdIfLoaded(int x, int y, int z) {
            return this.getTypeId(x, y, z);
        }

        public int getData(int x, int y, int z) {
            Integer value = this.metadata.get(Long.valueOf(key(x, y, z)));
            return value == null ? 0 : value.intValue();
        }

        public int getDataIfLoaded(int x, int y, int z) {
            return this.getData(x, y, z);
        }

        public Material getMaterialIfLoaded(int x, int y, int z) {
            if (y < 0 || y >= 128) return Material.AIR;
            Block block = Block.byId[this.getTypeId(x, y, z)];
            return block == null ? Material.AIR : block.material;
        }

        public boolean isLoaded(int x, int y, int z) {
            return y >= 0 && y < 128;
        }

        public boolean isEmpty(int x, int y, int z) {
            return this.getTypeId(x, y, z) == 0;
        }

        public boolean setBlockStateAndData(
                int x, int y, int z, BlockStateKey state) {
            BlockStateBridge.LegacyBlockData legacy =
                    BlockStateBridge.toLegacy(state);
            this.put(x, y, z, legacy.blockId, legacy.metadata);
            this.blockWrites++;
            return true;
        }

        public boolean setBlockState(
                int x, int y, int z, BlockStateKey state) {
            return this.setBlockStateAndData(x, y, z, state);
        }

        public boolean setRawTypeIdAndData(
                int x, int y, int z, int blockId, int data) {
            this.put(x, y, z, blockId, data);
            this.blockWrites++;
            return true;
        }

        public boolean setTypeIdAndData(
                int x, int y, int z, int blockId, int data) {
            return this.setRawTypeIdAndData(x, y, z, blockId, data);
        }

        public boolean setRawTypeId(int x, int y, int z, int blockId) {
            return this.setRawTypeIdAndData(x, y, z, blockId, 0);
        }

        public boolean setTypeId(int x, int y, int z, int blockId) {
            return this.setRawTypeId(x, y, z, blockId);
        }

        public void setData(int x, int y, int z, int data) {
            this.metadata.put(Long.valueOf(key(x, y, z)),
                    Integer.valueOf(data & 15));
        }

        public boolean setRawData(int x, int y, int z, int data) {
            this.setData(x, y, z, data);
            return true;
        }

        public void b(int minX, int minY, int minZ,
                int maxX, int maxY, int maxZ) {}

        public void notify(int x, int y, int z) {}

        public void applyPhysics(int x, int y, int z, int blockId) {}

        public void c(int x, int y, int z, int blockId, int delay) {}

        public CraftWorld getWorld() { return null; }

        public CraftServer getServer() { return null; }

        private void put(int x, int y, int z, int blockId, int data) {
            long key = key(x, y, z);
            this.blocks.put(Long.valueOf(key), Integer.valueOf(blockId));
            this.metadata.put(Long.valueOf(key), Integer.valueOf(data & 15));
        }

        private static long key(int x, int y, int z) {
            return ((long)x & 0x3FFFFFFL) << 38
                    | ((long)z & 0x3FFFFFFL) << 12
                    | (long)y & 0xFFFL;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }
}
