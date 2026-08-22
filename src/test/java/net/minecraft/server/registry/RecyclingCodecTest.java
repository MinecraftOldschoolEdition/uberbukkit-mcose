package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.Item;
import net.minecraft.server.RecyclingManager;
import net.minecraft.server.StatisticList;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class RecyclingCodecTest {
    private static final ResourceLocation WOODEN_PICKAXE =
            new ResourceLocation("minecraft", "wooden_pickaxe");

    @BeforeClass
    public static void initializeRegistries() {
        StatisticList.a();
        ItemRegistry.keys();
        BlockRegistry.keys();
    }

    @Test
    public void decodesCanonicalActiveDefinitionAndStrictTombstone() {
        RecyclingCodec.Entry active = RecyclingCodec.decode(
                WOODEN_PICKAXE,
                object(active("craft", "minecraft:oak_planks", 3, 0, "")));
        assertFalse(active.isTombstone());
        assertEquals(WOODEN_PICKAXE, active.getDefinition().getInputKey());
        assertEquals(new ResourceLocation("minecraft", "oak_planks"),
                active.getDefinition().getResultKey());
        assertEquals(3, active.getDefinition().getCount());
        assertFalse(active.getDefinition().isLegacyInert());

        RecyclingCodec.Entry tombstone = RecyclingCodec.decode(
                WOODEN_PICKAXE,
                object("{\"type\":\"mcose:recycling\",\"enabled\":false}"));
        assertTrue(tombstone.isTombstone());
        assertEquals(Item.WOOD_PICKAXE.id, tombstone.getInputItemId());
    }

    @Test
    public void rejectsAliasesUnknownFieldsAndMalformedTombstones() {
        ResourceLocation alias = new ResourceLocation("minecraft", "pickaxe_wood");
        assertSame(Item.WOOD_PICKAXE, ItemRegistry.get(alias));
        expectFailure(alias, active("craft", "minecraft:oak_planks", 3, 0, ""),
                "canonical");
        expectFailure(WOODEN_PICKAXE,
                active("craft", "minecraft:ingot_iron", 3, 0, ""),
                "canonical");
        expectFailure(WOODEN_PICKAXE,
                active("craft", "minecraft:not_real", 3, 0, ""),
                "unknown");
        expectFailure(WOODEN_PICKAXE,
                "{\"type\":\"mcose:recycling\",\"method\":\"craft\","
                        + "\"result\":{\"id\":\"minecraft:oak_planks\","
                        + "\"count\":3,\"legacy_metadata\":0},\"extra\":1}",
                "unsupported field");
        expectFailure(WOODEN_PICKAXE,
                "{\"type\":\"mcose:recycling\",\"enabled\":true}",
                "must be false");
        expectFailure(WOODEN_PICKAXE,
                "{\"type\":\"mcose:recycling\",\"enabled\":false,"
                        + "\"method\":\"craft\"}",
                "unsupported field");
    }

    @Test
    public void rejectsInvalidMethodCountMetadataAndIdentifierSyntax() {
        expectFailure(WOODEN_PICKAXE,
                active("grind", "minecraft:oak_planks", 3, 0, ""),
                "craft");
        expectFailure(WOODEN_PICKAXE,
                active("craft", "minecraft:oak_planks", 0, 0, ""),
                "between 1 and 64");
        expectFailure(WOODEN_PICKAXE,
                active("craft", "minecraft:oak_planks", 65, 0, ""),
                "between 1 and 64");
        expectFailure(WOODEN_PICKAXE,
                active("craft", "minecraft:oak_planks", 3, 1, ""),
                "must be 0");
        expectFailure(WOODEN_PICKAXE,
                active("craft", "Minecraft:oak_planks", 3, 0, ""),
                "invalid namespace");
        expectFailure(WOODEN_PICKAXE,
                active("craft", "minecraft:../oak_planks", 3, 0, ""),
                "invalid relative path");
    }

    @Test
    public void legacyInertMarkerIsStrictAndReservedForBow() {
        ResourceLocation bow = new ResourceLocation("minecraft", "bow");
        RecyclingCodec.Entry inert = RecyclingCodec.decode(
                bow,
                object(active("craft", "minecraft:string", 3, 0,
                        ",\"legacy_inert\":true")));
        assertTrue(inert.getDefinition().isLegacyInert());

        ResourceLocation stick = new ResourceLocation("minecraft", "stick");
        expectFailure(stick,
                active("craft", "minecraft:oak_planks", 1, 0, ""),
                "requires legacy_inert");
        expectFailure(stick,
                active("craft", "minecraft:oak_planks", 1, 0,
                        ",\"legacy_inert\":true"),
                "reserved");
        expectFailure(WOODEN_PICKAXE,
                active("craft", "minecraft:oak_planks", 3, 0,
                        ",\"legacy_inert\":true"),
                "damageable");
        expectFailure(bow,
                active("craft", "minecraft:string", 3, 0,
                        ",\"legacy_inert\":false"),
                "must be true");
        expectFailure(bow,
                active("craft", "minecraft:string", 3, 0,
                        ",\"legacy_inert\":\"true\""),
                "must be true");
    }

    @Test
    public void managerRejectsDuplicateLogicalInputsAndStalePublicationTokens() {
        RecipeRegistryBootstrap.initialize();
        RecyclingManager manager = RecyclingManager.getInstance();
        RecyclingManager.Definition wooden = manager.definitions().get(WOODEN_PICKAXE);
        ResourceLocation duplicateKey = new ResourceLocation("test", "z_duplicate");
        RecyclingManager.Definition duplicate = new RecyclingManager.Definition(
                duplicateKey,
                wooden.getInputItemId(),
                wooden.getResultKey(),
                wooden.getResultItemId(),
                wooden.getCount(),
                wooden.getLegacyMetadata(),
                wooden.getMethod(),
                false);
        LinkedHashMap<ResourceLocation, RecyclingManager.Definition> duplicated =
                new LinkedHashMap<ResourceLocation, RecyclingManager.Definition>();
        duplicated.put(WOODEN_PICKAXE, wooden);
        duplicated.put(duplicateKey, duplicate);
        try {
            manager.prepareDataPublication(duplicated);
            fail("Duplicate legacy recycling input was accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("Duplicate logical"));
        }

        Map<ResourceLocation, RecyclingManager.Definition> current =
                manager.definitions();
        RecyclingManager.PreparedState stale =
                manager.prepareDataPublication(current);
        RecyclingManager.PreparedState winner =
                manager.prepareDataPublication(current);
        manager.publishPreparedState(winner);
        Map<ResourceLocation, RecyclingManager.Definition> published =
                manager.definitions();
        manager.publishPreparedState(stale);
        assertSame("Stale token rolled back recycling state",
                published, manager.definitions());
    }

    private static String active(
            String method,
            String result,
            int count,
            int metadata,
            String extraRootField) {
        return "{\"type\":\"mcose:recycling\",\"method\":\"" + method
                + "\"" + extraRootField + ",\"result\":{\"id\":\""
                + result + "\",\"count\":" + count
                + ",\"legacy_metadata\":" + metadata + "}}";
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static void expectFailure(
            ResourceLocation input,
            String json,
            String expectedMessage) {
        try {
            RecyclingCodec.decode(input, object(json));
            fail("Malformed recycling definition was accepted: " + json);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains(expectedMessage));
        }
    }
}
