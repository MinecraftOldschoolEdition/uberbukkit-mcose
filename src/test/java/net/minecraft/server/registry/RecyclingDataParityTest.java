package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.Container;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.InventoryCrafting;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.RecyclingManager;
import net.minecraft.server.StatisticList;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class RecyclingDataParityTest {
    @BeforeClass
    public static void initializeData() {
        StatisticList.a();
        RecipeRegistryBootstrap.initialize();
    }

    @Test
    public void builtInDataPinsExactLegacyMappingAndMethodCounts() {
        Map<String, String> expected = expectedMapping();
        Map<ResourceLocation, RecyclingManager.Definition> actual =
                RecyclingManager.getInstance().definitions();
        assertEquals(45, expected.size());
        assertEquals(expected.size(), actual.size());

        int craft = 0;
        int smelt = 0;
        int inert = 0;
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            ResourceLocation key = new ResourceLocation("minecraft", entry.getKey());
            RecyclingManager.Definition definition = actual.get(key);
            assertTrue("missing " + key, definition != null);
            assertEquals(entry.getValue(), semanticValue(definition));
            assertEquals(key, definition.getInputKey());
            Item input = ItemRegistry.get(key);
            assertTrue("unknown input " + key, input != null);
            assertSame(input, ItemRegistry.getByLegacyId(definition.getInputItemId()));
            assertEquals(key, ItemRegistry.getKey(input));
            if (definition.getMethod() == RecyclingManager.Method.CRAFT) craft++;
            if (definition.getMethod() == RecyclingManager.Method.SMELT) smelt++;
            if (definition.isLegacyInert()) inert++;
            assertEquals(0, definition.getLegacyMetadata());
        }
        assertEquals(11, craft);
        assertEquals(34, smelt);
        assertEquals(1, inert);
        assertTrue(actual.get(new ResourceLocation("minecraft", "bow")).isLegacyInert());
    }

    @Test
    public void discoveredCorpusAndPublishedSnapshotAreDeterministicAndImmutable() {
        List<ResourceLocation> discovered =
                RegistryDataLoader.discoverKeys("recycling");
        assertEquals(45, discovered.size());
        assertEquals(expectedResourceKeys(), discovered);

        Map<ResourceLocation, RecyclingManager.Definition> definitions =
                RecyclingManager.getInstance().definitions();
        assertEquals(discovered, new ArrayList<ResourceLocation>(definitions.keySet()));
        try {
            definitions.clear();
            fail("Published recycling definitions were mutable");
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    public void legacyInertBowRemainsRegisteredButCannotProduceMaterials() {
        RecyclingManager manager = RecyclingManager.getInstance();
        assertTrue(manager.canCraftRecycle(Item.BOW.id));

        InventoryCrafting inventory = inventory(1, 1);
        inventory.setItem(0, new ItemStack(Item.BOW, 7, 0));
        assertNull(manager.getRecycleResult(inventory));
        assertFalse(new ItemStack(Item.BOW).d());
    }

    private static InventoryCrafting inventory(int width, int height) {
        return new InventoryCrafting(new Container() {
            public boolean b(EntityHuman player) {
                return true;
            }
        }, width, height);
    }

    private static List<ResourceLocation> expectedResourceKeys() {
        ArrayList<ResourceLocation> keys = new ArrayList<ResourceLocation>();
        for (String path : expectedMapping().keySet()) {
            keys.add(new ResourceLocation("minecraft", path));
        }
        Collections.sort(keys, new Comparator<ResourceLocation>() {
            public int compare(ResourceLocation left, ResourceLocation right) {
                int path = left.getPath().compareTo(right.getPath());
                return path != 0 ? path
                        : left.getNamespace().compareTo(right.getNamespace());
            }
        });
        return keys;
    }

    private static String semanticValue(RecyclingManager.Definition definition) {
        return definition.getMethod().name().toLowerCase()
                + "|" + definition.getResultKey()
                + "|" + definition.getCount()
                + "|" + definition.getLegacyMetadata()
                + "|" + definition.isLegacyInert();
    }

    private static Map<String, String> expectedMapping() {
        LinkedHashMap<String, String> expected = new LinkedHashMap<String, String>();
        add(expected, "wooden_pickaxe", "craft", "oak_planks", 3, false);
        add(expected, "stone_pickaxe", "smelt", "cobblestone", 3, false);
        add(expected, "iron_pickaxe", "smelt", "iron_ingot", 3, false);
        add(expected, "diamond_pickaxe", "smelt", "diamond", 3, false);
        add(expected, "golden_pickaxe", "smelt", "gold_ingot", 3, false);
        add(expected, "wooden_axe", "craft", "oak_planks", 3, false);
        add(expected, "stone_axe", "smelt", "cobblestone", 3, false);
        add(expected, "iron_axe", "smelt", "iron_ingot", 3, false);
        add(expected, "diamond_axe", "smelt", "diamond", 3, false);
        add(expected, "golden_axe", "smelt", "gold_ingot", 3, false);
        add(expected, "wooden_shovel", "craft", "oak_planks", 1, false);
        add(expected, "stone_shovel", "smelt", "cobblestone", 1, false);
        add(expected, "iron_shovel", "smelt", "iron_ingot", 1, false);
        add(expected, "diamond_shovel", "smelt", "diamond", 1, false);
        add(expected, "golden_shovel", "smelt", "gold_ingot", 1, false);
        add(expected, "wooden_hoe", "craft", "oak_planks", 2, false);
        add(expected, "stone_hoe", "smelt", "cobblestone", 2, false);
        add(expected, "iron_hoe", "smelt", "iron_ingot", 2, false);
        add(expected, "diamond_hoe", "smelt", "diamond", 2, false);
        add(expected, "golden_hoe", "smelt", "gold_ingot", 2, false);
        add(expected, "wooden_sword", "craft", "oak_planks", 2, false);
        add(expected, "stone_sword", "smelt", "cobblestone", 2, false);
        add(expected, "iron_sword", "smelt", "iron_ingot", 2, false);
        add(expected, "diamond_sword", "smelt", "diamond", 2, false);
        add(expected, "golden_sword", "smelt", "gold_ingot", 2, false);
        add(expected, "bow", "craft", "string", 3, true);
        add(expected, "leather_helmet", "craft", "leather", 5, false);
        add(expected, "leather_chestplate", "craft", "leather", 8, false);
        add(expected, "leather_leggings", "craft", "leather", 7, false);
        add(expected, "leather_boots", "craft", "leather", 4, false);
        add(expected, "iron_helmet", "smelt", "iron_ingot", 5, false);
        add(expected, "iron_chestplate", "smelt", "iron_ingot", 8, false);
        add(expected, "iron_leggings", "smelt", "iron_ingot", 7, false);
        add(expected, "iron_boots", "smelt", "iron_ingot", 4, false);
        add(expected, "diamond_helmet", "smelt", "diamond", 5, false);
        add(expected, "diamond_chestplate", "smelt", "diamond", 8, false);
        add(expected, "diamond_leggings", "smelt", "diamond", 7, false);
        add(expected, "diamond_boots", "smelt", "diamond", 4, false);
        add(expected, "golden_helmet", "smelt", "gold_ingot", 5, false);
        add(expected, "golden_chestplate", "smelt", "gold_ingot", 8, false);
        add(expected, "golden_leggings", "smelt", "gold_ingot", 7, false);
        add(expected, "golden_boots", "smelt", "gold_ingot", 4, false);
        add(expected, "shears", "smelt", "iron_ingot", 2, false);
        add(expected, "flint_and_steel", "smelt", "iron_ingot", 1, false);
        add(expected, "fishing_rod", "craft", "string", 2, false);
        return expected;
    }

    private static void add(
            Map<String, String> target,
            String input,
            String method,
            String result,
            int count,
            boolean inert) {
        target.put(input, method + "|minecraft:" + result
                + "|" + count + "|0|" + inert);
    }
}
