package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.server.registry.BlockRegistry;
import net.minecraft.server.registry.BlockRegistryBootstrap;
import net.minecraft.server.registry.ItemRegistryBootstrap;
import net.minecraft.server.registry.LootTables;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

/** Seeded runtime parity coverage for the central data-driven block-drop seam. */
public class BlockDropEngineParityTest {
    private static final int X = 16777217;
    private static final int Y = 70;
    private static final int Z = -16777217;
    private static final long[] SEEDS = new long[] {
            0L, 1L, 2L, 3L, 42L, 99L, 12345L
    };
    private static final long[] MATRIX_SEEDS = new long[] {0L, 1L, 42L};
    private static final float[] MATRIX_CHANCES =
            new float[] {0.0F, 0.5F, 1.0F};

    @BeforeClass
    public static void initializeData() {
        assertNotNull(Block.STONE);
        assertNotNull(Item.SIGN);
        BlockRegistryBootstrap.initialize();
        ItemRegistryBootstrap.initialize();
        LootTables.initialize();
    }

    @Test
    public void ordinaryStoneGravelAndMultiDropKeepLegacyTraceAndPositions() {
        assertDefaultParity(Block.STONE, 0, 0.65F);
        assertDefaultParity(Block.GRAVEL, 0, 0.65F);
        assertDefaultParity(Block.LAPIS_ORE, 0, 0.65F);
    }

    @Test
    public void everyOrdinaryCanonicalBuiltInMatchesLegacyDefaultMatrix() {
        ArrayList<ResourceLocation> keys =
                new ArrayList<ResourceLocation>(BlockRegistry.primaryKeys());
        Collections.sort(keys, new Comparator<ResourceLocation>() {
            public int compare(ResourceLocation left, ResourceLocation right) {
                return left.toString().compareTo(right.toString());
            }
        });
        int testedBlocks = 0;
        ArrayList<String> mismatches = new ArrayList<String>();
        for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++) {
            ResourceLocation key = keys.get(keyIndex);
            Block block = BlockRegistry.get(key);
            assertNotNull("canonical block " + key, block);
            assertTrue("built-in identity " + key,
                    LootTables.isBuiltInBlock(block));
            if (hasSpecialDropOracle(block)) {
                continue;
            }
            testedBlocks++;
            for (int metadata = 0; metadata <= 15; metadata++) {
                for (int chanceIndex = 0;
                        chanceIndex < MATRIX_CHANCES.length;
                        chanceIndex++) {
                    float chance = MATRIX_CHANCES[chanceIndex];
                    for (int seedIndex = 0;
                            seedIndex < MATRIX_SEEDS.length;
                            seedIndex++) {
                        long seed = MATRIX_SEEDS[seedIndex];
                        String caseName = key + " metadata=" + metadata
                                + " chance=" + chance + " seed=" + seed;
                        try {
                            assertParity(
                                    legacyDefault(
                                            block,
                                            metadata,
                                            chance,
                                            seed),
                                    actual(
                                            block,
                                            metadata,
                                            chance,
                                            seed,
                                            null));
                        } catch (AssertionError mismatch) {
                            if (mismatches.size() < 50) {
                                mismatches.add(caseName + ": "
                                        + mismatch.getMessage());
                            }
                        }
                    }
                }
            }
        }
        assertTrue("ordinary built-in coverage", testedBlocks >= 100);
        assertTrue("ordinary block parity mismatches: " + mismatches,
                mismatches.isEmpty());
    }

    @Test
    public void cropsAndBothStemsKeepImmediateLegacyPlantSpawns() {
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            assertParity(
                    legacyCrops(7, 0.65F, seed),
                    actual(Block.CROPS, 7, 0.65F, seed, null));
            assertParity(
                    legacyStem(Block.PUMPKIN_STEM, 7, 0.65F, seed),
                    actual(Block.PUMPKIN_STEM, 7, 0.65F, seed, null));
            assertParity(
                    legacyStem(Block.MELON_STEM, 5, 0.65F, seed),
                    actual(Block.MELON_STEM, 5, 0.65F, seed, null));
        }
    }

    @Test
    public void leavesKeepSaplingThenAppleShortCircuitOrder() {
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            assertParity(
                    legacyLeaves(0, 0.65F, seed),
                    actual(Block.LEAVES, 0, 0.65F, seed, null));
        }

        long appleSeed = -1L;
        Outcome appleExpected = null;
        for (long seed = 0L; seed < 100000L; seed++) {
            Outcome candidate = legacyLeaves(0, 1.0F, seed);
            if (containsItem(candidate, Item.APPLE.id)) {
                appleSeed = seed;
                appleExpected = candidate;
                break;
            }
        }
        assertTrue("a deterministic apple branch seed must exist", appleSeed >= 0L);
        assertNotNull(appleExpected);
        assertParity(
                appleExpected,
                actual(Block.LEAVES, 0, 1.0F, appleSeed, null));
    }

    @Test
    public void bedHeadAndFootKeepDistinctRandomConsumption() {
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            assertParity(
                    legacyBed(0, 0.65F, seed),
                    actual(Block.BED, 0, 0.65F, seed, null));
            assertParity(
                    legacyBed(8, 0.65F, seed),
                    actual(Block.BED, 8, 0.65F, seed, null));
        }
    }

    @Test
    public void movingPistonDelegatesStoredMetadataAtFullChance() {
        TileEntityPiston piston = new TileEntityPiston(
                Block.WOOL.id, 11, 2, true, false);
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            assertParity(
                    legacyDefault(Block.WOOL, 11, 1.0F, seed),
                    actual(Block.PISTON_MOVING, 0, 0.0F, seed, piston));
        }
    }

    @Test
    public void staticWorldReturnsBeforeTableLookupOrRandomWork() {
        RecordingWorld world = RecordingWorld.create(null);
        world.isStatic = true;
        world.random = new Random() {
            private static final long serialVersionUID = 1L;

            protected int next(int bits) {
                throw new AssertionError("static-world block drop consumed RNG");
            }
        };

        Block.GRAVEL.dropNaturally(world, X, Y, Z, 0, 1.0F);

        assertTrue(world.drops.isEmpty());
    }

    @Test
    public void postBootstrapPluginBlockKeepsServerVirtualFallback() {
        int freeId = findFreeBlockId();
        assertTrue("test requires one unoccupied legacy block slot", freeId >= 0);
        VirtualDropBlock pluginBlock = new VirtualDropBlock(freeId);
        try {
            assertFalse(LootTables.isBuiltInBlock(pluginBlock));
            for (int i = 0; i < SEEDS.length; i++) {
                long seed = SEEDS[i];
                assertParity(
                        legacyVirtual(pluginBlock, 6, 0.65F, seed),
                        actual(pluginBlock, 6, 0.65F, seed, null));
            }

            RecordingWorld zeroChanceWorld = RecordingWorld.create(null);
            zeroChanceWorld.random = new ZeroFloatRandom();
            pluginBlock.dropNaturally(zeroChanceWorld, X, Y, Z, 6, 0.0F);
            assertTrue("Bukkit's strict '<' contract rejects 0.0 at zero chance",
                    zeroChanceWorld.drops.isEmpty());
        } finally {
            clearBlockSlot(freeId);
        }
    }

    private static void assertDefaultParity(
            Block block,
            int metadata,
            float chance) {
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            assertParity(
                    legacyDefault(block, metadata, chance, seed),
                    actual(block, metadata, chance, seed, null));
        }
    }

    private static boolean hasSpecialDropOracle(Block block) {
        return block == Block.CROPS
                || block == Block.LEAVES
                || block == Block.PUMPKIN_STEM
                || block == Block.MELON_STEM
                || block == Block.BED
                || block == Block.PISTON_MOVING;
    }

    private static Outcome actual(
            Block block,
            int metadata,
            float chance,
            long seed,
            TileEntity tile) {
        RecordingWorld world = RecordingWorld.create(tile);
        TracingRandom random = new TracingRandom(seed);
        world.random = random;

        block.dropNaturally(world, X, Y, Z, metadata, chance);

        ArrayList<Drop> drops = new ArrayList<Drop>();
        for (int i = 0; i < world.drops.size(); i++) {
            EntityItem entity = world.drops.get(i);
            drops.add(new Drop(
                    entity.itemStack.id,
                    entity.itemStack.count,
                    entity.itemStack.getData(),
                    entity.locX,
                    entity.locY,
                    entity.locZ,
                    entity.pickupDelay));
        }
        String trace = random.trace();
        long following = random.nextLong();
        return new Outcome(trace, following, drops);
    }

    private static Outcome legacyDefault(
            Block block,
            int metadata,
            float chance,
            long seed) {
        TracingRandom random = new TracingRandom(seed);
        ArrayList<Drop> drops = new ArrayList<Drop>();
        legacyDefault(block, metadata, chance, random, drops);
        return finish(random, drops);
    }

    private static void legacyDefault(
            Block block,
            int metadata,
            float chance,
            TracingRandom random,
            List<Drop> drops) {
        int count = block.a(random);
        for (int i = 0; i < count; ++i) {
            if (random.nextFloat() <= chance) {
                int itemId = block.a(metadata, random);
                if (itemId > 0) {
                    spawnDefault(
                            random,
                            drops,
                            itemId,
                            1,
                            block.a_(metadata));
                }
            }
        }
    }

    private static Outcome legacyVirtual(
            Block block,
            int metadata,
            float chance,
            long seed) {
        TracingRandom random = new TracingRandom(seed);
        ArrayList<Drop> drops = new ArrayList<Drop>();
        int count = block.a(random);
        for (int i = 0; i < count; ++i) {
            if (random.nextFloat() < chance) {
                int itemId = block.a(metadata, random);
                if (itemId > 0) {
                    spawnDefault(
                            random,
                            drops,
                            itemId,
                            1,
                            block.a_(metadata));
                }
            }
        }
        return finish(random, drops);
    }

    private static Outcome legacyCrops(
            int metadata,
            float chance,
            long seed) {
        TracingRandom random = new TracingRandom(seed);
        ArrayList<Drop> drops = new ArrayList<Drop>();
        legacyDefault(Block.CROPS, metadata, chance, random, drops);
        for (int i = 0; i < 3; ++i) {
            if (random.nextInt(15) <= metadata) {
                spawnPlant(random, drops, Item.SEEDS.id, 1, 0);
            }
        }
        return finish(random, drops);
    }

    private static Outcome legacyStem(
            Block stem,
            int metadata,
            float chance,
            long seed) {
        TracingRandom random = new TracingRandom(seed);
        ArrayList<Drop> drops = new ArrayList<Drop>();
        legacyDefault(stem, metadata, chance, random, drops);
        int seedItem = stem == Block.PUMPKIN_STEM
                ? Item.PUMPKIN_SEED.id
                : Item.MELON_SEED.id;
        int age = BlockStem.getGrowthAge(metadata);
        for (int i = 0; i < 3; ++i) {
            if (random.nextInt(15) <= age) {
                spawnPlant(random, drops, seedItem, 1, 0);
            }
        }
        return finish(random, drops);
    }

    private static Outcome legacyLeaves(
            int metadata,
            float chance,
            long seed) {
        TracingRandom random = new TracingRandom(seed);
        ArrayList<Drop> drops = new ArrayList<Drop>();
        legacyDefault(Block.LEAVES, metadata, chance, random, drops);
        if ((metadata & 3) == 0
                && random.nextFloat() <= chance
                && random.nextInt(200) == 0) {
            spawnDefault(random, drops, Item.APPLE.id, 1, 0);
        }
        return finish(random, drops);
    }

    private static Outcome legacyBed(
            int metadata,
            float chance,
            long seed) {
        TracingRandom random = new TracingRandom(seed);
        ArrayList<Drop> drops = new ArrayList<Drop>();
        if (!BlockBed.d(metadata)) {
            legacyDefault(Block.BED, metadata, chance, random, drops);
        }
        return finish(random, drops);
    }

    private static Outcome finish(
            TracingRandom random,
            List<Drop> drops) {
        String trace = random.trace();
        long following = random.nextLong();
        return new Outcome(trace, following, drops);
    }

    private static void spawnDefault(
            TracingRandom random,
            List<Drop> drops,
            int itemId,
            int count,
            int metadata) {
        float spread = 0.7F;
        double offsetX = (double)(random.nextFloat() * spread)
                + (double)(1.0F - spread) * 0.5D;
        double offsetY = (double)(random.nextFloat() * spread)
                + (double)(1.0F - spread) * 0.5D;
        double offsetZ = (double)(random.nextFloat() * spread)
                + (double)(1.0F - spread) * 0.5D;
        drops.add(new Drop(
                itemId,
                count,
                metadata,
                (double)X + offsetX,
                (double)Y + offsetY,
                (double)Z + offsetZ,
                10));
    }

    private static void spawnPlant(
            TracingRandom random,
            List<Drop> drops,
            int itemId,
            int count,
            int metadata) {
        float spread = 0.7F;
        float offsetX = random.nextFloat() * spread
                + (1.0F - spread) * 0.5F;
        float offsetY = random.nextFloat() * spread
                + (1.0F - spread) * 0.5F;
        float offsetZ = random.nextFloat() * spread
                + (1.0F - spread) * 0.5F;
        drops.add(new Drop(
                itemId,
                count,
                metadata,
                (double)((float)X + offsetX),
                (double)((float)Y + offsetY),
                (double)((float)Z + offsetZ),
                10));
    }

    private static void assertParity(Outcome expected, Outcome actual) {
        assertEquals("world.random call trace", expected.trace, actual.trace);
        assertEquals(
                "world.random state after drop",
                expected.followingRandomLong,
                actual.followingRandomLong);
        assertEquals("drop count", expected.drops.size(), actual.drops.size());
        for (int i = 0; i < expected.drops.size(); i++) {
            Drop left = expected.drops.get(i);
            Drop right = actual.drops.get(i);
            assertEquals("item " + i, left.itemId, right.itemId);
            assertEquals("count " + i, left.count, right.count);
            assertEquals("metadata " + i, left.metadata, right.metadata);
            assertEquals("x bits " + i,
                    Double.doubleToLongBits(left.x),
                    Double.doubleToLongBits(right.x));
            assertEquals("y bits " + i,
                    Double.doubleToLongBits(left.y),
                    Double.doubleToLongBits(right.y));
            assertEquals("z bits " + i,
                    Double.doubleToLongBits(left.z),
                    Double.doubleToLongBits(right.z));
            assertEquals("pickup delay " + i,
                    left.pickupDelay,
                    right.pickupDelay);
        }
    }

    private static boolean containsItem(Outcome outcome, int itemId) {
        for (int i = 0; i < outcome.drops.size(); i++) {
            if (outcome.drops.get(i).itemId == itemId) return true;
        }
        return false;
    }

    private static int findFreeBlockId() {
        for (int id = Block.byId.length - 1; id > 0; --id) {
            if (Block.byId[id] == null) return id;
        }
        return -1;
    }

    private static void clearBlockSlot(int id) {
        Block.byId[id] = null;
        Block.n[id] = false;
        Block.o[id] = false;
        Block.isTileEntity[id] = false;
        Block.q[id] = 0;
        Block.r[id] = false;
        Block.s[id] = 0;
        Block.t[id] = false;
    }

    private static final class RecordingWorld extends World {
        private TileEntity tile;
        private List<EntityItem> drops;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null,
                    org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld create(TileEntity tile) {
            try {
                RecordingWorld world = (RecordingWorld)unsafe()
                        .allocateInstance(RecordingWorld.class);
                world.tile = tile;
                world.drops = new ArrayList<EntityItem>();
                world.entityList = new ArrayList();
                world.random = new Random(0L);
                return world;
            } catch (Exception exception) {
                throw new AssertionError("Could not create lightweight test world", exception);
            }
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            return this.tile;
        }

        @Override
        public boolean addEntity(Entity entity) {
            if (entity instanceof EntityItem) {
                this.drops.add((EntityItem)entity);
                return true;
            }
            return false;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }

    private static final class TracingRandom extends Random {
        private static final long serialVersionUID = 1L;
        private final StringBuilder calls = new StringBuilder();

        TracingRandom(long seed) {
            super(seed);
        }

        @Override
        public int nextInt(int bound) {
            int value = super.nextInt(bound);
            this.calls.append('I').append(bound).append('=').append(value)
                    .append(';');
            return value;
        }

        @Override
        public float nextFloat() {
            float value = super.nextFloat();
            this.calls.append('F')
                    .append(Float.floatToIntBits(value)).append(';');
            return value;
        }

        String trace() {
            return this.calls.toString();
        }
    }

    private static final class ZeroFloatRandom extends Random {
        private static final long serialVersionUID = 1L;

        @Override
        public int nextInt(int bound) {
            return 0;
        }

        @Override
        public float nextFloat() {
            return 0.0F;
        }
    }

    private static final class Outcome {
        final String trace;
        final long followingRandomLong;
        final List<Drop> drops;

        Outcome(String trace, long followingRandomLong, List<Drop> drops) {
            this.trace = trace;
            this.followingRandomLong = followingRandomLong;
            this.drops = new ArrayList<Drop>(drops);
        }
    }

    private static final class Drop {
        final int itemId;
        final int count;
        final int metadata;
        final double x;
        final double y;
        final double z;
        final int pickupDelay;

        Drop(
                int itemId,
                int count,
                int metadata,
                double x,
                double y,
                double z,
                int pickupDelay) {
            this.itemId = itemId;
            this.count = count;
            this.metadata = metadata;
            this.x = x;
            this.y = y;
            this.z = z;
            this.pickupDelay = pickupDelay;
        }
    }

    private static final class VirtualDropBlock extends Block {
        VirtualDropBlock(int blockId) {
            super(blockId, 0, Material.STONE);
        }

        @Override
        public int a(Random random) {
            return 2 + random.nextInt(2);
        }

        @Override
        public int a(int metadata, Random random) {
            return random.nextInt(3) == 0 ? 0 : Item.STICK.id;
        }

        @Override
        protected int a_(int metadata) {
            return metadata + 4;
        }
    }
}
