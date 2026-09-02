package net.minecraft.server.registry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

/** Exhaustively locks every value consumed by the block-loot evaluator. */
public class RegistryDataFingerprintBlockLootWriterTest {
    private static final ResourceLocation KEY =
            new ResourceLocation("minecraft", "blocks/fingerprint_probe");
    private static final String COMPLETE_TABLE =
            "{\"type\":\"minecraft:block\",\"pools\":["
                    + "{\"rolls\":{\"type\":\"minecraft:uniform\","
                    + "\"min\":2,\"max\":3},\"conditions\":[{"
                    + "\"condition\":\"minecraft:legacy_metadata\","
                    + "\"mask\":15,\"min\":1,\"max\":7},{"
                    + "\"condition\":\"minecraft:legacy_random_integer\","
                    + "\"bound\":10,\"less_or_equal_metadata\":{"
                    + "\"mask\":7,\"maximum\":5}}],"
                    + "\"apply_drop_chance\":true,"
                    + "\"placement\":\"minecraft:legacy_plant\","
                    + "\"entries\":[{\"type\":\"minecraft:alternatives\","
                    + "\"conditions\":[{\"condition\":"
                    + "\"minecraft:legacy_metadata\",\"equals\":3}],"
                    + "\"children\":[{\"type\":\"minecraft:empty\","
                    + "\"conditions\":[{\"condition\":"
                    + "\"minecraft:legacy_random_integer\","
                    + "\"bound\":2,\"equals\":0}]},{"
                    + "\"type\":\"minecraft:item\","
                    + "\"name\":\"minecraft:feather\","
                    + "\"conditions\":[{\"condition\":"
                    + "\"minecraft:legacy_metadata\",\"max\":15}],"
                    + "\"legacy_metadata\":{\"type\":"
                    + "\"minecraft:legacy_threshold\",\"threshold\":4,"
                    + "\"below_or_equal\":1,\"above\":2}},{"
                    + "\"type\":\"minecraft:item\","
                    + "\"name\":\"minecraft:bone\","
                    + "\"legacy_metadata\":{\"type\":"
                    + "\"minecraft:block_state\",\"mask\":3}}]}]},"
                    + "{\"rolls\":{\"type\":\"minecraft:legacy_one_in\","
                    + "\"chance\":4},\"apply_drop_chance\":false,"
                    + "\"entries\":[{\"type\":\"minecraft:item\","
                    + "\"name\":\"minecraft:porkchop\","
                    + "\"legacy_metadata\":6}]},"
                    + "{\"rolls\":{\"type\":"
                    + "\"minecraft:legacy_shifted_clamped\","
                    + "\"bound\":3,\"add\":-1,\"minimum\":0},"
                    + "\"entries\":[{\"type\":\"minecraft:empty\"}]},"
                    + "{\"rolls\":1,\"entries\":[{"
                    + "\"type\":\"minecraft:item\","
                    + "\"name\":\"minecraft:string\","
                    + "\"legacy_metadata\":0}]}]}";

    @BeforeClass
    public static void initializeItems() {
        assertTrue(Block.STONE != null);
        assertTrue(Item.STICK != null);
        ItemRegistryBootstrap.initialize();
    }

    @Test
    public void everyBlockLootScalarChangesSemanticBytes() {
        byte[] baseline = semanticBytes(COMPLETE_TABLE);
        Map<String, String> variants = new LinkedHashMap<String, String>();
        variants.put("uniform minimum", replaceOnce(
                COMPLETE_TABLE, "\"min\":2,\"max\":3", "\"min\":1,\"max\":3"));
        variants.put("uniform maximum", replaceOnce(
                COMPLETE_TABLE, "\"min\":2,\"max\":3", "\"min\":2,\"max\":4"));
        variants.put("metadata mask", replaceOnce(
                COMPLETE_TABLE, "\"mask\":15,\"min\":1", "\"mask\":14,\"min\":1"));
        variants.put("metadata minimum", replaceOnce(
                COMPLETE_TABLE, "\"mask\":15,\"min\":1,\"max\":7",
                "\"mask\":15,\"min\":2,\"max\":7"));
        variants.put("metadata maximum", replaceOnce(
                COMPLETE_TABLE, "\"mask\":15,\"min\":1,\"max\":7",
                "\"mask\":15,\"min\":1,\"max\":8"));
        variants.put("random bound", replaceOnce(
                COMPLETE_TABLE, "\"bound\":10,\"less_or_equal_metadata\"",
                "\"bound\":11,\"less_or_equal_metadata\""));
        variants.put("random metadata mask", replaceOnce(
                COMPLETE_TABLE, "\"mask\":7,\"maximum\":5",
                "\"mask\":6,\"maximum\":5"));
        variants.put("random metadata maximum", replaceOnce(
                COMPLETE_TABLE, "\"mask\":7,\"maximum\":5",
                "\"mask\":7,\"maximum\":4"));
        variants.put("drop chance application", replaceOnce(
                COMPLETE_TABLE, "\"apply_drop_chance\":true,\"placement\"",
                "\"apply_drop_chance\":false,\"placement\""));
        variants.put("placement", replaceOnce(
                COMPLETE_TABLE, "\"placement\":\"minecraft:legacy_plant\"",
                "\"placement\":\"minecraft:block\""));
        variants.put("metadata equals", replaceOnce(
                COMPLETE_TABLE, "\"minecraft:legacy_metadata\",\"equals\":3",
                "\"minecraft:legacy_metadata\",\"equals\":4"));
        variants.put("entry random bound", replaceOnce(
                COMPLETE_TABLE, "\"bound\":2,\"equals\":0",
                "\"bound\":3,\"equals\":0"));
        variants.put("entry random equals", replaceOnce(
                COMPLETE_TABLE, "\"bound\":2,\"equals\":0",
                "\"bound\":2,\"equals\":1"));
        variants.put("one-sided metadata maximum", replaceOnce(
                COMPLETE_TABLE, "\"minecraft:legacy_metadata\",\"max\":15",
                "\"minecraft:legacy_metadata\",\"max\":14"));
        variants.put("item name", replaceOnce(
                COMPLETE_TABLE, "\"name\":\"minecraft:feather\"",
                "\"name\":\"minecraft:string\""));
        variants.put("threshold", replaceOnce(
                COMPLETE_TABLE, "\"threshold\":4,\"below_or_equal\":1",
                "\"threshold\":5,\"below_or_equal\":1"));
        variants.put("threshold below result", replaceOnce(
                COMPLETE_TABLE, "\"below_or_equal\":1,\"above\":2",
                "\"below_or_equal\":0,\"above\":2"));
        variants.put("threshold above result", replaceOnce(
                COMPLETE_TABLE, "\"below_or_equal\":1,\"above\":2",
                "\"below_or_equal\":1,\"above\":3"));
        variants.put("block-state mask", replaceOnce(
                COMPLETE_TABLE, "\"minecraft:block_state\",\"mask\":3",
                "\"minecraft:block_state\",\"mask\":2"));
        variants.put("one-in chance", replaceOnce(
                COMPLETE_TABLE, "\"chance\":4", "\"chance\":5"));
        variants.put("constant metadata", replaceOnce(
                COMPLETE_TABLE, "\"legacy_metadata\":6", "\"legacy_metadata\":5"));
        variants.put("shifted bound", replaceOnce(
                COMPLETE_TABLE, "\"bound\":3,\"add\":-1,\"minimum\":0",
                "\"bound\":4,\"add\":-1,\"minimum\":0"));
        variants.put("shifted add", replaceOnce(
                COMPLETE_TABLE, "\"bound\":3,\"add\":-1,\"minimum\":0",
                "\"bound\":3,\"add\":0,\"minimum\":0"));
        variants.put("shifted minimum", replaceOnce(
                COMPLETE_TABLE, "\"bound\":3,\"add\":-1,\"minimum\":0",
                "\"bound\":3,\"add\":-1,\"minimum\":1"));
        variants.put("constant rolls", replaceOnce(
                COMPLETE_TABLE, "{\"rolls\":1,\"entries\":[{",
                "{\"rolls\":2,\"entries\":[{"));
        variants.put("second constant metadata", replaceOnce(
                COMPLETE_TABLE, "\"name\":\"minecraft:string\","
                        + "\"legacy_metadata\":0",
                "\"name\":\"minecraft:string\","
                        + "\"legacy_metadata\":1"));

        for (Map.Entry<String, String> variant : variants.entrySet()) {
            assertFalse(variant.getKey(), Arrays.equals(
                    baseline, semanticBytes(variant.getValue())));
        }
    }

    @Test
    public void listOrderAndSpecialAdapterAreSemantic() {
        JsonObject table = parseObject(COMPLETE_TABLE);
        byte[] baseline = semanticBytes(table);

        JsonObject poolOrder = table.deepCopy();
        swap(poolOrder.getAsJsonArray("pools"), 0, 1);
        assertFalse("pool order", Arrays.equals(
                baseline, semanticBytes(poolOrder)));

        JsonObject conditionOrder = table.deepCopy();
        swap(conditionOrder.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("conditions"), 0, 1);
        assertFalse("condition order", Arrays.equals(
                baseline, semanticBytes(conditionOrder)));

        JsonObject childOrder = table.deepCopy();
        swap(childOrder.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject()
                .getAsJsonArray("children"), 0, 1);
        assertFalse("alternative child order", Arrays.equals(
                baseline, semanticBytes(childOrder)));

        byte[] ordinaryEmpty = semanticBytes(
                "{\"type\":\"minecraft:block\",\"pools\":[]}");
        byte[] movingPiston = semanticBytes(
                "{\"type\":\"minecraft:block\","
                        + "\"adapter\":\"minecraft:moving_piston\","
                        + "\"pools\":[]}");
        assertFalse("adapter", Arrays.equals(ordinaryEmpty, movingPiston));
    }

    @Test
    public void formattingAndExplicitDefaultsNormalizeToTheSameBytes() {
        String explicit = "{\"type\":\"minecraft:block\",\"pools\":[{"
                + "\"rolls\":1,\"apply_drop_chance\":true,"
                + "\"placement\":\"minecraft:block\",\"entries\":[{"
                + "\"type\":\"minecraft:item\","
                + "\"name\":\"minecraft:feather\","
                + "\"conditions\":[],\"legacy_metadata\":0}]}]}";
        String defaults = "{\n  \"pools\": [{\"entries\": [{"
                + "\"legacy_metadata\": 0, \"name\": \"minecraft:feather\","
                + "\"type\": \"minecraft:item\"}], \"rolls\": 1}],"
                + "\n  \"type\": \"minecraft:block\"\n}";
        assertArrayEquals(semanticBytes(explicit), semanticBytes(defaults));
    }

    private static byte[] semanticBytes(String json) {
        return semanticBytes(parseObject(json));
    }

    private static byte[] semanticBytes(JsonObject json) {
        BlockLootTable table = (BlockLootTable)LootTableCodec.decode(KEY, json);
        return RegistryDataFingerprint.blockLootSemanticBytesForTesting(table);
    }

    private static JsonObject parseObject(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static String replaceOnce(String value, String before, String after) {
        int index = value.indexOf(before);
        assertTrue("Missing mutation token: " + before, index >= 0);
        assertTrue("Mutation token is not unique: " + before,
                value.indexOf(before, index + before.length()) < 0);
        return value.substring(0, index) + after
                + value.substring(index + before.length());
    }

    private static void swap(JsonArray values, int first, int second) {
        JsonElement left = values.get(first);
        JsonElement right = values.get(second);
        values.set(first, right);
        values.set(second, left);
    }
}
