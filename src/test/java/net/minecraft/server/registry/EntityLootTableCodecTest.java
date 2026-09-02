package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonParser;
import java.util.Arrays;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class EntityLootTableCodecTest {
    private static final ResourceLocation KEY =
            new ResourceLocation("minecraft", "entities/test");

    @BeforeClass
    public static void loadItems() {
        ItemRegistryBootstrap.initialize();
    }

    @Test
    public void decodesStrictEntityPoolsEntriesCountsAndConditions() {
        EntityLootTable table = (EntityLootTable)LootTableCodec.decode(
                KEY,
                JsonParser.parseString("{"
                        + "\"type\":\"minecraft:entity\","
                        + "\"pools\":[{"
                        + "\"condition\":{" + condition(
                                "{\"minecraft:type_specific/cube_mob\":{\"size\":1}}") + "},"
                        + "\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":["
                        + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:feather\","
                        + "\"condition\":{" + condition(
                                "{\"minecraft:flags\":{\"is_on_fire\":true}}") + "},"
                        + "\"modifier\":[{\"type\":\"minecraft:set_count\","
                        + "\"count\":{\"type\":\"minecraft:uniform\",\"min\":0,\"max\":2}}],"
                        + "\"legacy_metadata\":7},"
                        + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:bone\"}]}],"
                        + "\"rolls\":1}],"
                        + "\"random_sequence\":\"minecraft:entities/test\"}")
                        .getAsJsonObject());

        assertEquals(KEY, table.getId());
        assertTrue(table.getPools().isEmpty());
        assertEquals(1, table.getEntityPools().size());
        EntityLootTable.EntityPool pool = table.getEntityPools().get(0);
        assertEquals(Integer.valueOf(1), pool.getCondition().getCubeSize());
        assertTrue(pool.getEntry() instanceof EntityLootTable.AlternativesEntry);
        EntityLootTable.AlternativesEntry alternatives =
                (EntityLootTable.AlternativesEntry)pool.getEntry();
        assertEquals(new ResourceLocation("minecraft", "alternatives"), alternatives.getType());
        assertEquals(2, alternatives.getChildren().size());

        EntityLootTable.ItemEntry first =
                (EntityLootTable.ItemEntry)alternatives.getChildren().get(0);
        assertEquals(new ResourceLocation("minecraft", "item"), first.getType());
        assertEquals(new ResourceLocation("minecraft", "feather"), first.getItemId());
        assertEquals(Boolean.TRUE, first.getCondition().getOnFire());
        assertEquals(new ResourceLocation("minecraft", "uniform"), first.getCount().getType());
        assertEquals(0, first.getCount().getMinimum());
        assertEquals(2, first.getCount().getMaximum());
        assertEquals(7, first.getLegacyMetadata());

        EntityLootTable.ItemEntry second =
                (EntityLootTable.ItemEntry)alternatives.getChildren().get(1);
        assertTrue(second.getCondition().isAlways());
        assertEquals(new ResourceLocation("minecraft", "constant"), second.getCount().getType());
        assertEquals(1, second.getCount().getMinimum());
        assertEquals(1, second.getCount().getMaximum());
    }

    @Test
    public void entityCollectionsAreImmutableSnapshots() {
        EntityLootTable.ItemEntry item = new EntityLootTable.ItemEntry(
                EntityLootTable.EntityCondition.ALWAYS,
                new ResourceLocation("minecraft", "feather"),
                new EntityLootTable.EntityCountProvider(0, 2),
                0);
        EntityLootTable table = new EntityLootTable(KEY).addEntityPool(
                new EntityLootTable.EntityPool(
                        EntityLootTable.EntityCondition.ALWAYS,
                        new EntityLootTable.AlternativesEntry(
                                EntityLootTable.EntityCondition.ALWAYS,
                                Arrays.<EntityLootTable.EntityEntry>asList(item))));
        try {
            table.getEntityPools().clear();
            fail("Expected entity pool view to be immutable");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            ((EntityLootTable.AlternativesEntry)table.getEntityPools().get(0).getEntry())
                    .getChildren().clear();
            fail("Expected alternatives view to be immutable");
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    public void rejectsFieldsThatWouldChangeLegacyRandomnessOrSemantics() {
        assertDecodeFails("{\"type\":\"minecraft:entity\",\"pools\":["
                + validPool() + "]}");
        assertDecodeFails(root(validPool(), "minecraft:entities/wrong"));
        assertDecodeFails(root("{\"name\":\"main\",\"rolls\":1,\"entries\":[" + item() + "]}"));
        assertDecodeFails(root("{\"rolls\":2,\"entries\":[" + item() + "]}"));
        assertDecodeFails(root("{\"rolls\":1,\"entries\":[" + item() + "," + item() + "]}"));
        assertDecodeFails(root("{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\","
                + "\"name\":\"minecraft:feather\",\"weight\":1}]}"));
        assertDecodeFails(root("{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\","
                + "\"name\":\"minecraft:missing_item\"}]}"));
        assertDecodeFails(root("{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\","
                + "\"name\":\"minecraft:feather\",\"conditions\":[]}]}"));
        assertDecodeFails(root("{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\","
                + "\"name\":\"minecraft:feather\",\"modifier\":["
                + "{\"type\":\"minecraft:set_count\",\"count\":1},"
                + "{\"type\":\"minecraft:set_count\",\"count\":1}]}]}"));
        assertDecodeFails(root("{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\","
                + "\"name\":\"minecraft:feather\",\"modifier\":{" 
                + "\"type\":\"minecraft:set_count\",\"count\":-1}}]}"));
        assertDecodeFails(root("{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:alternatives\","
                + "\"children\":[]}]}"));
        assertDecodeFails(root("{\"rolls\":1,\"condition\":{" + condition(
                "{\"minecraft:unsupported\":true}") + "},\"entries\":[" + item() + "]}"));
    }

    @Test
    public void allRequiredEntityFilesLoadAndWolfRemainsOnFallback() {
        LootTables.initialize();
        ResourceLocation[] entityKeys = new ResourceLocation[] {
                LootTables.ZOMBIE,
                LootTables.SKELETON,
                LootTables.CREEPER,
                LootTables.SPIDER,
                LootTables.PIG,
                LootTables.COW,
                LootTables.CHICKEN,
                LootTables.SHEEP,
                LootTables.SQUID,
                LootTables.SLIME,
                LootTables.GHAST,
                LootTables.ZOMBIFIED_PIGLIN,
                LootTables.SNOW_GOLEM
        };
        for (int i = 0; i < entityKeys.length; i++) {
            assertTrue(entityKeys[i].toString(),
                    LootTables.get(entityKeys[i]) instanceof EntityLootTable);
        }
        assertFalse(LootTables.keys().contains(
                new ResourceLocation("minecraft", "entities/wolf")));
    }

    private static String condition(String predicate) {
        return "\"type\":\"minecraft:entity_properties\","
                + "\"entity\":\"this\",\"predicate\":" + predicate;
    }

    private static String item() {
        return "{\"type\":\"minecraft:item\",\"name\":\"minecraft:feather\"}";
    }

    private static String validPool() {
        return "{\"rolls\":1,\"entries\":[" + item() + "]}";
    }

    private static String root(String pool) {
        return root(pool, KEY.toString());
    }

    private static String root(String pool, String randomSequence) {
        return "{\"type\":\"minecraft:entity\",\"pools\":[" + pool + "],"
                + "\"random_sequence\":\"" + randomSequence + "\"}";
    }

    private static void assertDecodeFails(String json) {
        try {
            LootTableCodec.decode(
                    KEY, JsonParser.parseString(json).getAsJsonObject());
            fail("Expected invalid entity loot table data to be rejected: " + json);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }
}
