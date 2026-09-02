package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Random;
import net.minecraft.server.Block;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.IInventory;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class LootTableDataLoaderParityTest {
    private static final long PARITY_SEEDS = 4096L;

    @BeforeClass
    public static void loadRegistries() {
        assertTrue(Block.STONE != null);
        assertTrue(Item.SIGN != null);
        ItemRegistryBootstrap.initialize();
        LootTables.initialize();
    }

    @Test
    public void decodedTablesPreserveKeysPoolAndEntryOrderAndEveryLegacyField() {
        assertTrue(LootTables.keys().containsAll(LootTables.builtInTableKeys()));
        assertTrue(LootTables.size() >= LootTables.builtInTableKeys().size());
        assertTrue("loot registry must remain extensible", !Registries.LOOT_TABLE.isFrozen());

        assertStructureEquals(
                legacySimple(LootTables.DUNGEON),
                LootTables.get(LootTables.DUNGEON));
        assertStructureEquals(
                legacySimple(LootTables.SIMPLE_DUNGEON_LOOT),
                LootTables.get(LootTables.SIMPLE_DUNGEON_LOOT));
        assertStructureEquals(
                legacyMonster(),
                LootTables.get(LootTables.MONSTER_DUNGEON_LOOT));
    }

    @Test
    public void decodedTablesAreSeedForSeedAndRandomStateEquivalentToLegacyBuilders() {
        assertSeedParity(legacySimple(LootTables.DUNGEON), LootTables.get(LootTables.DUNGEON));
        assertSeedParity(
                legacySimple(LootTables.SIMPLE_DUNGEON_LOOT),
                LootTables.get(LootTables.SIMPLE_DUNGEON_LOOT));
        assertSeedParity(legacyMonster(), LootTables.get(LootTables.MONSTER_DUNGEON_LOOT));
    }

    @Test
    public void decodedTablesFillEmptyAndCollisionHeavyInventoriesExactlyLikeLegacyBuilders() {
        assertFillParity(legacySimple(LootTables.DUNGEON), LootTables.get(LootTables.DUNGEON));
        assertFillParity(
                legacySimple(LootTables.SIMPLE_DUNGEON_LOOT),
                LootTables.get(LootTables.SIMPLE_DUNGEON_LOOT));
        assertFillParity(legacyMonster(), LootTables.get(LootTables.MONSTER_DUNGEON_LOOT));
    }

    @Test
    public void serverCocoaBeansAliasMatchesTheLegacyGeneratorLookup() {
        ResourceLocation cocoaBeans = new ResourceLocation("minecraft", "cocoa_beans");
        Item itemRegistryValue = ItemRegistry.get(cocoaBeans);
        assertTrue(itemRegistryValue != null);
        assertSame(itemRegistryValue, Registries.ITEM.get(cocoaBeans));

        ItemStack stack = LootEntry.builder(cocoaBeans).weight(1).metadata(3)
                .build().generateStack(new Random(0L));
        assertTrue(stack != null);
        assertEquals(itemRegistryValue.id, stack.id);
        assertEquals(3, stack.getData());
    }

    @Test
    public void codecRejectsMalformedOrBehaviorChangingDefinitions() {
        ResourceLocation key = new ResourceLocation("minecraft", "chests/test");
        assertDecodeFails(key, "{\"type\":\"minecraft:entity\",\"pools\":[]}");
        assertDecodeFails(key, "{\"type\":\"minecraft:chest\",\"pools\":[]}");
        assertDecodeFails(key, validSingleEntryJson("1.5", "minecraft:saddle", "minecraft:item"));
        assertDecodeFails(key, validSingleEntryJson("1", "minecraft:missing_item", "minecraft:item"));
        assertDecodeFails(key, validSingleEntryJson("1", "minecraft:saddle", "minecraft:unknown"));
        assertDecodeFails(key,
                "{\"type\":\"minecraft:chest\",\"unsupported\":true,\"pools\":["
                        + "{\"name\":\"main\",\"rolls\":1,\"entries\":["
                        + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:saddle\"}]}]}");
        assertDecodeFails(key, "{\"type\":\"minecraft:chest\",\"pools\":[{\"name\":\"main\","
                + "\"rolls\":1,\"bonus_rolls\":1,\"entries\":[{\"type\":\"minecraft:item\","
                + "\"name\":\"minecraft:saddle\"}]}]}");
        assertDecodeFails(key, "{\"type\":\"minecraft:chest\",\"pools\":[{\"name\":\"main\","
                + "\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\","
                + "\"name\":\"minecraft:saddle\",\"conditions\":[]}]}]}");
    }

    private static String validSingleEntryJson(String rolls, String item, String entryType) {
        return "{\"type\":\"minecraft:chest\",\"pools\":[{\"name\":\"main\","
                + "\"rolls\":" + rolls + ",\"entries\":[{\"type\":\"" + entryType
                + "\",\"name\":\"" + item + "\",\"weight\":1}]}]}";
    }

    private static void assertDecodeFails(ResourceLocation key, String json) {
        try {
            LootTableCodec.decode(key, JsonParser.parseString(json).getAsJsonObject());
            fail("Expected invalid loot table data to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    private static void assertStructureEquals(LootTable expected, LootTable actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getPools().size(), actual.getPools().size());
        for (int poolIndex = 0; poolIndex < expected.getPools().size(); poolIndex++) {
            LootPool expectedPool = expected.getPools().get(poolIndex);
            LootPool actualPool = actual.getPools().get(poolIndex);
            assertEquals(expectedPool.getName(), actualPool.getName());
            assertEquals(expectedPool.getMinRolls(), actualPool.getMinRolls());
            assertEquals(expectedPool.getMaxRolls(), actualPool.getMaxRolls());
            assertEquals(expectedPool.getEntries().size(), actualPool.getEntries().size());
            for (int entryIndex = 0; entryIndex < expectedPool.getEntries().size(); entryIndex++) {
                LootEntry expectedEntry = expectedPool.getEntries().get(entryIndex);
                LootEntry actualEntry = actualPool.getEntries().get(entryIndex);
                assertEquals(expectedEntry.getItemId(), actualEntry.getItemId());
                assertEquals(expectedEntry.getWeight(), actualEntry.getWeight());
                assertEquals(expectedEntry.getMinCount(), actualEntry.getMinCount());
                assertEquals(expectedEntry.getMaxCount(), actualEntry.getMaxCount());
                assertEquals(expectedEntry.getMetadata(), actualEntry.getMetadata());
            }
        }
    }

    private static void assertSeedParity(LootTable legacy, LootTable decoded) {
        for (long seed = 0L; seed < PARITY_SEEDS; seed++) {
            TracingRandom legacyRandom = new TracingRandom(seed);
            TracingRandom decodedRandom = new TracingRandom(seed);
            List<ItemStack> expected = legacy.generateLoot(legacyRandom);
            List<ItemStack> actual = decoded.generateLoot(decodedRandom);
            assertEquals("stack count for seed " + seed, expected.size(), actual.size());
            for (int index = 0; index < expected.size(); index++) {
                assertStackEquals(
                        "generated stack " + index + " for seed " + seed,
                        expected.get(index),
                        actual.get(index));
            }
            assertEquals("random calls for seed " + seed,
                    legacyRandom.getTrace(), decodedRandom.getTrace());
            assertEquals("post-generation random state for seed " + seed,
                    legacyRandom.nextLong(), decodedRandom.nextLong());
        }
    }

    private static void assertFillParity(LootTable legacy, LootTable decoded) {
        for (long seed = 0L; seed < PARITY_SEEDS; seed++) {
            assertFillParity(legacy, decoded, seed, false);
            assertFillParity(legacy, decoded, seed, true);
        }
    }

    private static void assertFillParity(
            LootTable legacy,
            LootTable decoded,
            long seed,
            boolean collisionHeavy) {
        TracingRandom legacyRandom = new TracingRandom(seed);
        TracingRandom decodedRandom = new TracingRandom(seed);
        TestInventory expected = new TestInventory(27);
        TestInventory actual = new TestInventory(27);
        if (collisionHeavy) {
            expected.prefill(20);
            actual.prefill(20);
        }

        legacy.fillInventory(expected, legacyRandom);
        decoded.fillInventory(actual, decodedRandom);
        for (int slot = 0; slot < expected.getSize(); slot++) {
            assertStackEquals(
                    (collisionHeavy ? "collision-heavy" : "empty")
                            + " slot " + slot + " for seed " + seed,
                    expected.getItem(slot),
                    actual.getItem(slot));
        }
        assertEquals("fill random calls for seed " + seed,
                legacyRandom.getTrace(), decodedRandom.getTrace());
        assertEquals("post-fill random state for seed " + seed,
                legacyRandom.nextLong(), decodedRandom.nextLong());
    }

    private static void assertStackEquals(String description, ItemStack expected, ItemStack actual) {
        if (expected == null || actual == null) {
            assertSame(description, expected, actual);
            return;
        }
        assertEquals(description + " item", expected.id, actual.id);
        assertEquals(description + " count", expected.count, actual.count);
        assertEquals(description + " metadata", expected.getData(), actual.getData());
    }

    private static final class TracingRandom extends Random {
        private final StringBuilder trace = new StringBuilder();

        TracingRandom(long seed) {
            super(seed);
        }

        @Override
        public int nextInt(int bound) {
            int result = super.nextInt(bound);
            trace.append(bound).append('=').append(result).append(';');
            return result;
        }

        String getTrace() {
            return trace.toString();
        }
    }

    private static final class TestInventory implements IInventory {
        private final ItemStack[] items;

        TestInventory(int size) {
            this.items = new ItemStack[size];
        }

        void prefill(int count) {
            for (int slot = 0; slot < count && slot < items.length; slot++) {
                items[slot] = new ItemStack(Item.STICK, 1, 0);
            }
        }

        public int getSize() {
            return items.length;
        }

        public ItemStack getItem(int slot) {
            return items[slot];
        }

        public ItemStack splitStack(int slot, int count) {
            ItemStack stack = items[slot];
            if (stack == null) return null;
            if (stack.count <= count) {
                items[slot] = null;
                return stack;
            }
            return stack.a(count);
        }

        public void setItem(int slot, ItemStack stack) {
            items[slot] = stack;
        }

        public String getName() {
            return "loot-parity";
        }

        public int getMaxStackSize() {
            return 64;
        }

        public void update() {}

        public boolean a_(EntityHuman human) {
            return true;
        }

        public ItemStack[] getContents() {
            return items;
        }
    }

    private static LootTable legacySimple(ResourceLocation id) {
        return LootTable.builder(id)
                .pool(LootPool.builder("main")
                        .rolls(1, 3)
                        .add(LootEntry.builder("saddle").weight(10))
                        .add(LootEntry.builder("iron_ingot").weight(10).count(1, 4))
                        .add(LootEntry.builder("bread").weight(10))
                        .add(LootEntry.builder("wheat").weight(10).count(1, 4))
                        .add(LootEntry.builder("gunpowder").weight(10).count(1, 4))
                        .add(LootEntry.builder("string").weight(10).count(1, 4))
                        .add(LootEntry.builder("bucket").weight(10))
                        .add(LootEntry.builder("wet_sponge").weight(6).count(1, 2))
                        .add(LootEntry.builder("redstone").weight(5).count(1, 4))
                        .add(LootEntry.builder("cocoa_beans").weight(10).metadata(3))
                        .add(LootEntry.builder("pumpkin_seed").weight(8).count(1, 3))
                        .add(LootEntry.builder("melon_seed").weight(8).count(1, 3))
                        .add(LootEntry.builder("name_tag").weight(4))
                        .build())
                .pool(LootPool.builder("rare")
                        .rolls(0, 1)
                        .add(LootEntry.builder("golden_apple").weight(1))
                        .add(LootEntry.builder("music_disc_13").weight(2))
                        .add(LootEntry.builder("music_disc_cat").weight(2))
                        .add(LootEntry.builder("air").weight(95))
                        .build())
                .pool(LootPool.builder("music_discs")
                        .rolls(0, 1)
                        .add(LootEntry.builder("music_disc_blocks").weight(1))
                        .add(LootEntry.builder("music_disc_chirp").weight(1))
                        .add(LootEntry.builder("music_disc_far").weight(1))
                        .add(LootEntry.builder("music_disc_mall").weight(1))
                        .add(LootEntry.builder("music_disc_mellohi").weight(1))
                        .add(LootEntry.builder("music_disc_stal").weight(1))
                        .add(LootEntry.builder("music_disc_strad").weight(1))
                        .add(LootEntry.builder("music_disc_ward").weight(1))
                        .add(LootEntry.builder("music_disc_11").weight(1))
                        .add(LootEntry.builder("music_disc_wait").weight(1))
                        .add(LootEntry.builder("air").weight(190))
                        .build())
                .build();
    }

    private static LootTable legacyMonster() {
        return LootTable.builder(LootTables.MONSTER_DUNGEON_LOOT)
                .pool(LootPool.builder("main")
                        .rolls(2, 4)
                        .add(LootEntry.builder("saddle").weight(8))
                        .add(LootEntry.builder("iron_ingot").weight(10).count(1, 4))
                        .add(LootEntry.builder("bread").weight(8))
                        .add(LootEntry.builder("wheat").weight(8).count(1, 4))
                        .add(LootEntry.builder("gunpowder").weight(10).count(1, 4))
                        .add(LootEntry.builder("string").weight(10).count(1, 4))
                        .add(LootEntry.builder("bucket").weight(8))
                        .add(LootEntry.builder("wet_sponge").weight(8).count(1, 3))
                        .add(LootEntry.builder("redstone").weight(6).count(1, 4))
                        .add(LootEntry.builder("cocoa_beans").weight(8).metadata(3))
                        .add(LootEntry.builder("pumpkin_seed").weight(8).count(1, 3))
                        .add(LootEntry.builder("melon_seed").weight(8).count(1, 3))
                        .add(LootEntry.builder("name_tag").weight(5))
                        .add(LootEntry.builder("gold_ingot").weight(5).count(1, 3))
                        .add(LootEntry.builder("iron_sword").weight(3))
                        .add(LootEntry.builder("iron_chestplate").weight(2))
                        .build())
                .pool(LootPool.builder("rare")
                        .rolls(0, 2)
                        .add(LootEntry.builder("golden_apple").weight(2))
                        .add(LootEntry.builder("diamond").weight(1).count(1, 2))
                        .add(LootEntry.builder("music_disc_13").weight(3))
                        .add(LootEntry.builder("music_disc_cat").weight(3))
                        .add(LootEntry.builder("air").weight(91))
                        .build())
                .pool(LootPool.builder("music_discs")
                        .rolls(0, 1)
                        .add(LootEntry.builder("music_disc_blocks").weight(2))
                        .add(LootEntry.builder("music_disc_chirp").weight(2))
                        .add(LootEntry.builder("music_disc_far").weight(2))
                        .add(LootEntry.builder("music_disc_mall").weight(2))
                        .add(LootEntry.builder("music_disc_mellohi").weight(2))
                        .add(LootEntry.builder("music_disc_stal").weight(2))
                        .add(LootEntry.builder("music_disc_strad").weight(2))
                        .add(LootEntry.builder("music_disc_ward").weight(2))
                        .add(LootEntry.builder("music_disc_11").weight(2))
                        .add(LootEntry.builder("music_disc_wait").weight(2))
                        .add(LootEntry.builder("air").weight(80))
                        .build())
                .pool(LootPool.builder("rare_discs")
                        .rolls(0, 1)
                        .add(LootEntry.builder("music_disc_aria_math").weight(1))
                        .add(LootEntry.builder("music_disc_dog").weight(1))
                        .add(LootEntry.builder("music_disc_certitudes").weight(1))
                        .add(LootEntry.builder("music_disc_tsuki_no_koibumi").weight(1))
                        .add(LootEntry.builder("air").weight(96))
                        .build())
                .build();
    }
}
