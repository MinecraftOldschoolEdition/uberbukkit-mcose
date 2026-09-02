package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.registry.ItemRegistryBootstrap;
import net.minecraft.server.util.ResourceLocation;
import com.google.gson.JsonParser;
import org.junit.BeforeClass;
import org.junit.Test;

public class BlockLootTableCodecTest {
    private static final ResourceLocation KEY =
            new ResourceLocation("minecraft", "blocks/test");

    @BeforeClass
    public static void loadItems() {
        assertTrue(Block.STONE != null);
        assertTrue(Item.SIGN != null);
        ItemRegistryBootstrap.initialize();
    }

    @Test
    public void decodesTheClosedBlockSchemaIntoImmutableData() {
        BlockLootTable table = decode("{"
                + "\"type\":\"minecraft:block\",\"pools\":["
                + "{\"rolls\":0,\"entries\":[{\"type\":\"minecraft:empty\"}]},"
                + "{\"rolls\":{\"type\":\"minecraft:uniform\",\"min\":1,\"max\":3},"
                + "\"conditions\":[{\"condition\":\"minecraft:legacy_metadata\","
                + "\"mask\":7,\"min\":1,\"max\":4}],"
                + "\"apply_drop_chance\":false,\"placement\":\"minecraft:legacy_plant\","
                + "\"entries\":[{\"type\":\"minecraft:item\","
                + "\"name\":\"minecraft:feather\",\"legacy_metadata\":7}]},"
                + "{\"rolls\":{\"type\":\"minecraft:legacy_one_in\",\"chance\":4},"
                + "\"entries\":[{\"type\":\"minecraft:item\","
                + "\"name\":\"minecraft:bone\",\"legacy_metadata\":{"
                + "\"type\":\"minecraft:block_state\",\"mask\":3}}]},"
                + "{\"rolls\":{\"type\":\"minecraft:legacy_shifted_clamped\","
                + "\"bound\":3,\"add\":-1,\"minimum\":0},\"entries\":[{"
                + "\"type\":\"minecraft:alternatives\",\"conditions\":[{"
                + "\"condition\":\"minecraft:legacy_random_integer\",\"bound\":5,"
                + "\"less_or_equal_metadata\":{\"mask\":3,\"maximum\":2}}],"
                + "\"children\":[{\"type\":\"minecraft:empty\",\"conditions\":[{"
                + "\"condition\":\"minecraft:legacy_metadata\",\"equals\":0}]},{"
                + "\"type\":\"minecraft:item\",\"name\":\"minecraft:feather\","
                + "\"legacy_metadata\":{\"type\":\"minecraft:legacy_threshold\","
                + "\"threshold\":3,\"below_or_equal\":1,\"above\":2}}]}]}]}" );

        assertEquals(KEY, table.getId());
        assertNull(table.getAdapter());
        assertTrue(table.getPools().isEmpty());
        assertEquals(4, table.getBlockPools().size());

        BlockLootTable.Pool uniformPool = table.getBlockPools().get(1);
        assertTrue(uniformPool.getRolls() instanceof BlockLootTable.UniformRollProvider);
        BlockLootTable.UniformRollProvider uniform =
                (BlockLootTable.UniformRollProvider)uniformPool.getRolls();
        assertEquals(new ResourceLocation("minecraft", "uniform"), uniform.getType());
        assertEquals(1, uniform.getMinimum());
        assertEquals(3, uniform.getMaximum());
        assertEquals(1, uniformPool.getConditions().size());
        assertTrue(uniformPool.getConditions().get(0)
                instanceof BlockLootTable.LegacyMetadataCondition);
        assertEquals(Integer.valueOf(7), ((BlockLootTable.LegacyMetadataCondition)
                uniformPool.getConditions().get(0)).getMask());
        assertTrue(!uniformPool.appliesDropChance());
        assertSame(BlockLootTable.SpawnMode.LEGACY_PLANT, uniformPool.getPlacement());
        BlockLootTable.ItemEntry constantItem =
                (BlockLootTable.ItemEntry)uniformPool.getEntry();
        assertEquals(new ResourceLocation("minecraft", "feather"), constantItem.getItemId());
        assertTrue(constantItem.getLegacyMetadata()
                instanceof BlockLootTable.ConstantMetadataProvider);

        assertTrue(table.getBlockPools().get(2).getRolls()
                instanceof BlockLootTable.LegacyOneInRollProvider);
        BlockLootTable.ItemEntry blockStateItem =
                (BlockLootTable.ItemEntry)table.getBlockPools().get(2).getEntry();
        assertTrue(blockStateItem.getLegacyMetadata()
                instanceof BlockLootTable.BlockStateMetadataProvider);

        BlockLootTable.Pool shiftedPool = table.getBlockPools().get(3);
        assertTrue(shiftedPool.getRolls()
                instanceof BlockLootTable.LegacyShiftedClampedRollProvider);
        BlockLootTable.AlternativesEntry alternatives =
                (BlockLootTable.AlternativesEntry)shiftedPool.getEntry();
        assertEquals(2, alternatives.getChildren().size());
        assertTrue(alternatives.getConditions().get(0)
                instanceof BlockLootTable.LegacyRandomIntegerCondition);
        BlockLootTable.ItemEntry thresholdItem =
                (BlockLootTable.ItemEntry)alternatives.getChildren().get(1);
        assertTrue(thresholdItem.getLegacyMetadata()
                instanceof BlockLootTable.LegacyThresholdMetadataProvider);

        assertImmutable(table.getBlockPools());
        assertImmutable(uniformPool.getConditions());
        assertImmutable(alternatives.getChildren());
        assertImmutable(table.getPools());
        try {
            table.addPool(null);
            fail("Expected the inherited builder seam to be closed");
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    public void decodesTheMovingPistonAdapterOnlyAsAnEmptyProgram() {
        BlockLootTable table = decode("{\"type\":\"minecraft:block\","
                + "\"adapter\":\"minecraft:moving_piston\",\"pools\":[]}");
        assertSame(BlockLootTable.Adapter.MOVING_PISTON, table.getAdapter());
        assertTrue(table.getBlockPools().isEmpty());
    }

    @Test
    public void generationPreservesBetaRandomAndImmediateSpawnOrder() {
        BlockLootTable table = decode("{\"type\":\"minecraft:block\",\"pools\":[{"
                + "\"conditions\":[{\"condition\":\"minecraft:legacy_random_integer\","
                + "\"bound\":10,\"equals\":4}],"
                + "\"rolls\":{\"type\":\"minecraft:uniform\",\"min\":2,\"max\":3},"
                + "\"placement\":\"minecraft:legacy_plant\",\"entries\":[{"
                + "\"type\":\"minecraft:alternatives\",\"children\":[{"
                + "\"type\":\"minecraft:empty\",\"conditions\":[{"
                + "\"condition\":\"minecraft:legacy_metadata\",\"equals\":99}]},{"
                + "\"type\":\"minecraft:item\",\"name\":\"minecraft:feather\","
                + "\"conditions\":[{\"condition\":\"minecraft:legacy_random_integer\","
                + "\"bound\":5,\"equals\":2}],\"legacy_metadata\":{"
                + "\"type\":\"minecraft:block_state\",\"mask\":15}}]}]}]}");

        final QueueRandom random = new QueueRandom(
                new int[] {4, 0, 2},
                new float[] {0.25F, 0.11F, 0.12F, 0.13F, 0.75F});
        final List<ItemStack> drops = new ArrayList<ItemStack>();
        final List<BlockLootTable.SpawnMode> modes =
                new ArrayList<BlockLootTable.SpawnMode>();
        table.generate(7, 0.25F, random, new BlockLootTable.DropSink() {
            public void emit(ItemStack stack, BlockLootTable.SpawnMode mode) {
                random.trace.add("EMIT");
                drops.add(stack);
                modes.add(mode);
                // Block.dropBlockAsItemWithChance consumes all three offsets
                // before control returns to the table's next roll.
                random.nextFloat();
                random.nextFloat();
                random.nextFloat();
            }
        });

        assertEquals(Arrays.asList(
                "I10=4", "I2=0", "F", "I5=2", "EMIT",
                "F", "F", "F", "F"), random.trace);
        random.assertConsumed();
        assertEquals(1, drops.size());
        assertEquals(7, drops.get(0).getData());
        assertSame(BlockLootTable.SpawnMode.LEGACY_PLANT, modes.get(0));
    }

    @Test
    public void alternativesStopAtAMatchingEmptyEntry() {
        BlockLootTable table = decode("{\"type\":\"minecraft:block\",\"pools\":[{"
                + "\"rolls\":1,\"apply_drop_chance\":false,\"entries\":[{"
                + "\"type\":\"minecraft:alternatives\",\"children\":[{"
                + "\"type\":\"minecraft:empty\",\"conditions\":[{"
                + "\"condition\":\"minecraft:legacy_random_integer\","
                + "\"bound\":2,\"equals\":1}]},{\"type\":\"minecraft:item\","
                + "\"name\":\"minecraft:feather\",\"conditions\":[{"
                + "\"condition\":\"minecraft:legacy_random_integer\","
                + "\"bound\":99,\"equals\":0}],\"legacy_metadata\":0}]}]}]}");
        QueueRandom random = new QueueRandom(new int[] {1}, new float[0]);
        RecordingSink sink = new RecordingSink();

        table.generate(0, Float.NaN, random, sink);

        random.assertConsumed();
        assertEquals(0, sink.stacks.size());
    }

    @Test
    public void chanceAndMetadataAreNotNormalized() {
        BlockLootTable chanceTable = decode("{\"type\":\"minecraft:block\",\"pools\":[{"
                + "\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\","
                + "\"name\":\"minecraft:feather\",\"legacy_metadata\":0}]}]}");
        QueueRandom chanceRandom = new QueueRandom(new int[0], new float[] {0.0F});
        RecordingSink chanceSink = new RecordingSink();
        chanceTable.generate(0, Float.NaN, chanceRandom, chanceSink);
        chanceRandom.assertConsumed();
        assertEquals(0, chanceSink.stacks.size());

        BlockLootTable metadataTable = decode("{\"type\":\"minecraft:block\",\"pools\":[{"
                + "\"rolls\":1,\"apply_drop_chance\":false,\"entries\":[{"
                + "\"type\":\"minecraft:item\",\"name\":\"minecraft:feather\","
                + "\"legacy_metadata\":{\"type\":\"minecraft:block_state\"}}]}]}");
        QueueRandom metadataRandom = new QueueRandom(new int[0], new float[0]);
        RecordingSink metadataSink = new RecordingSink();
        metadataTable.generate(29, -100.0F, metadataRandom, metadataSink);
        metadataRandom.assertConsumed();
        assertEquals(29, metadataSink.stacks.get(0).getData());
    }

    @Test
    public void rollAndMetadataProvidersMatchTheirLegacyExpressions() {
        BlockLootTable table = decode("{\"type\":\"minecraft:block\",\"pools\":["
                + providerPool("{\"type\":\"minecraft:legacy_one_in\",\"chance\":4}", "0") + ","
                + providerPool("{\"type\":\"minecraft:legacy_shifted_clamped\","
                        + "\"bound\":3,\"add\":-1,\"minimum\":0}",
                        "{\"type\":\"minecraft:legacy_threshold\",\"threshold\":2,"
                                + "\"below_or_equal\":5,\"above\":6}") + ","
                + providerPool("{\"type\":\"minecraft:uniform\",\"min\":2,\"max\":3}",
                        "{\"type\":\"minecraft:block_state\",\"mask\":3}")
                + "]}");
        QueueRandom random = new QueueRandom(new int[] {0, 2, 1}, new float[0]);
        RecordingSink sink = new RecordingSink();

        table.generate(6, -1.0F, random, sink);

        random.assertConsumed();
        assertEquals(5, sink.stacks.size());
        assertEquals(0, sink.stacks.get(0).getData());
        assertEquals(6, sink.stacks.get(1).getData());
        assertEquals(2, sink.stacks.get(2).getData());
        assertEquals(2, sink.stacks.get(3).getData());
        assertEquals(2, sink.stacks.get(4).getData());
    }

    @Test
    public void rejectsAnythingOutsideTheClosedSchema() {
        String[] invalid = new String[] {
                "{\"type\":\"minecraft:block\",\"pools\":[],\"random_sequence\":\"x\"}",
                "{\"type\":\"minecraft:block\"}",
                "{\"type\":\"minecraft:block\",\"pools\":{},\"adapter\":\"minecraft:moving_piston\"}",
                "{\"type\":\"minecraft:block\",\"pools\":[],\"adapter\":\"minecraft:other\"}",
                root("{\"rolls\":0,\"entries\":[{\"type\":\"minecraft:empty\"}],\"name\":\"x\"}"),
                root("{\"rolls\":0,\"entries\":[]}"),
                root("{\"rolls\":0,\"entries\":[{\"type\":\"minecraft:empty\"},{\"type\":\"minecraft:empty\"}]}"),
                root("{\"rolls\":0,\"conditions\":{},\"entries\":[{\"type\":\"minecraft:empty\"}]}"),
                root("{\"rolls\":0,\"apply_drop_chance\":\"true\",\"entries\":[{\"type\":\"minecraft:empty\"}]}"),
                root("{\"rolls\":0,\"placement\":\"minecraft:anywhere\",\"entries\":[{\"type\":\"minecraft:empty\"}]}"),
                root("{\"rolls\":1.5,\"entries\":[{\"type\":\"minecraft:empty\"}]}"),
                root("{\"rolls\":{\"type\":\"minecraft:uniform\",\"min\":2,\"max\":1},\"entries\":[{\"type\":\"minecraft:empty\"}]}"),
                root("{\"rolls\":{\"type\":\"minecraft:legacy_one_in\",\"chance\":0},\"entries\":[{\"type\":\"minecraft:empty\"}]}"),
                root("{\"rolls\":{\"type\":\"minecraft:legacy_shifted_clamped\",\"bound\":2,\"add\":1024,\"minimum\":0},\"entries\":[{\"type\":\"minecraft:empty\"}]}"),
                itemRoot("minecraft:feather", ""),
                itemRoot("minecraft:missing_item", ",\"legacy_metadata\":0"),
                itemRoot("minecraft:feather", ",\"legacy_metadata\":0,\"weight\":1"),
                itemRoot("minecraft:feather", ",\"legacy_metadata\":\"zero\""),
                itemRoot("minecraft:feather", ",\"legacy_metadata\":{\"type\":\"minecraft:block_state\",\"mask\":-1}"),
                itemRoot("minecraft:feather", ",\"legacy_metadata\":{\"type\":\"minecraft:legacy_threshold\",\"threshold\":0,\"below_or_equal\":0}"),
                root("{\"rolls\":0,\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[]}]}"),
                conditionRoot("{\"type\":\"minecraft:legacy_metadata\",\"equals\":0}"),
                conditionRoot("{\"condition\":\"minecraft:legacy_metadata\"}"),
                conditionRoot("{\"condition\":\"minecraft:legacy_metadata\",\"equals\":0,\"min\":0}"),
                conditionRoot("{\"condition\":\"minecraft:legacy_metadata\",\"min\":2,\"max\":1}"),
                conditionRoot("{\"condition\":\"minecraft:legacy_random_integer\",\"bound\":0,\"equals\":0}"),
                conditionRoot("{\"condition\":\"minecraft:legacy_random_integer\",\"bound\":2,\"equals\":2}"),
                conditionRoot("{\"condition\":\"minecraft:legacy_random_integer\",\"bound\":2,\"equals\":0,\"less_or_equal_metadata\":{}}"),
                conditionRoot("{\"condition\":\"minecraft:legacy_random_integer\",\"bound\":2,\"less_or_equal_metadata\":{\"extra\":0}}")
        };
        for (int i = 0; i < invalid.length; i++) {
            assertDecodeFails(KEY, invalid[i]);
        }

        assertDecodeFails(
                new ResourceLocation("minecraft", "entities/not_a_block_path"),
                "{\"type\":\"minecraft:block\",\"pools\":[]}");
        assertDecodeFails(KEY, "{\"type\":\"minecraft:block\","
                + "\"adapter\":\"minecraft:moving_piston\",\"pools\":[{"
                + "\"rolls\":0,\"entries\":[{\"type\":\"minecraft:empty\"}]}]}");
    }

    private static String providerPool(String rolls, String metadata) {
        return "{\"rolls\":" + rolls + ",\"apply_drop_chance\":false,"
                + "\"entries\":[{\"type\":\"minecraft:item\","
                + "\"name\":\"minecraft:feather\",\"legacy_metadata\":" + metadata + "}]}";
    }

    private static String root(String pool) {
        return "{\"type\":\"minecraft:block\",\"pools\":[" + pool + "]}";
    }

    private static String itemRoot(String name, String tail) {
        return root("{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\","
                + "\"name\":\"" + name + "\"" + tail + "}]}");
    }

    private static String conditionRoot(String condition) {
        return root("{\"rolls\":0,\"conditions\":[" + condition + "],"
                + "\"entries\":[{\"type\":\"minecraft:empty\"}]}");
    }

    private static BlockLootTable decode(String json) {
        return (BlockLootTable)LootTableCodec.decode(KEY, JsonParser.parseString(json).getAsJsonObject());
    }

    private static void assertDecodeFails(ResourceLocation key, String json) {
        try {
            LootTableCodec.decode(key, JsonParser.parseString(json).getAsJsonObject());
            fail("Expected invalid block loot data to be rejected: " + json);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    private static void assertImmutable(List<?> values) {
        try {
            values.clear();
            fail("Expected immutable list");
        } catch (UnsupportedOperationException expected) {
        }
    }

    private static final class RecordingSink implements BlockLootTable.DropSink {
        final List<ItemStack> stacks = new ArrayList<ItemStack>();
        final List<BlockLootTable.SpawnMode> modes =
                new ArrayList<BlockLootTable.SpawnMode>();

        public void emit(ItemStack stack, BlockLootTable.SpawnMode mode) {
            stacks.add(stack);
            modes.add(mode);
        }
    }

    private static final class QueueRandom extends Random {
        private final int[] integers;
        private final float[] floats;
        private int integerIndex;
        private int floatIndex;
        final List<String> trace = new ArrayList<String>();

        QueueRandom(int[] integers, float[] floats) {
            this.integers = integers;
            this.floats = floats;
        }

        @Override
        public int nextInt(int bound) {
            if (integerIndex >= integers.length) {
                fail("Unexpected nextInt(" + bound + ")");
            }
            int value = integers[integerIndex++];
            if (value < 0 || value >= bound) {
                fail("Queued integer " + value + " is invalid for bound " + bound);
            }
            trace.add("I" + bound + "=" + value);
            return value;
        }

        @Override
        public float nextFloat() {
            if (floatIndex >= floats.length) {
                fail("Unexpected nextFloat()");
            }
            float value = floats[floatIndex++];
            trace.add("F");
            return value;
        }

        void assertConsumed() {
            assertEquals("queued integers", integers.length, integerIndex);
            assertEquals("queued floats", floats.length, floatIndex);
        }
    }
}
