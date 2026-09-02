package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.Block;
import net.minecraft.server.CraftingManager;
import net.minecraft.server.CraftingRecipe;
import net.minecraft.server.FurnaceRecipes;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.ShapedRecipes;
import net.minecraft.server.ShapelessRecipes;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class RecipeDataLoaderTest {
    private static final String[] KEYS = {
            "crafting/redstone_block",
            "crafting/coal_block",
            "crafting/redstone_from_block",
            "crafting/coal_from_block",
            "crafting/flint_and_steel",
            "crafting/pumpkin_seeds"
    };
    private static List baselineRecipes;

    @BeforeClass
    public static void loadRegistry() {
        // Match production order and capture every server/PVN/config/plugin
        // recipe that exists before the data-backed compatibility tail.
        assertTrue(Block.STONE != null);
        assertTrue(Item.STICK != null);
        CraftingManager manager = CraftingManager.getInstance();
        if (Registries.RECIPE.get(key(KEYS[0])) == null) {
            baselineRecipes = new ArrayList(manager.b());
        }
        RecipeRegistryBootstrap.initialize();
    }

    @Test
    public void defaultProfileIsFullyDataBackedBeforeExactUnsortedSixEntryTail() {
        List recipes = CraftingManager.getInstance().b();
        int tailStart = recipes.size() - KEYS.length;
        assertTrue(tailStart > 0);
        assertEquals("fallbacks " + describeFallbacks(),
                0, RecipeRegistryBootstrap.javaFallbackRecipeCount());
        assertEquals(180, RecipeRegistryBootstrap.dataRecipeKeys().size());

        if (baselineRecipes != null) {
            assertEquals(baselineRecipes.size(), tailStart);
            for (int i = 0; i < baselineRecipes.size(); i++) {
                CraftingRecipe before = (CraftingRecipe)baselineRecipes.get(i);
                CraftingRecipe after = (CraftingRecipe)recipes.get(i);
                assertNotSame("baseline recipe " + i, before, after);
                assertRecipeEquals("baseline recipe " + i, before, after);
                ResourceLocation key = RecipeRegistryBootstrap
                        .activeCraftingRecipeKeys().get(i);
                CraftingRecipe synchronizedValue =
                        RecipeRegistryBootstrap.dataRecipe(key);
                if (synchronizedValue != null) {
                    assertSame("data identity " + key,
                            synchronizedValue, after);
                }
                assertSame("registry identity " + key,
                        RecipeRegistryApi.get(key), after);
                assertEquals("retired Java identity " + i,
                        null, Registries.RECIPE.getKey(before));
            }
        }

        for (int i = 0; i < KEYS.length; i++) {
            CraftingRecipe registered = RecipeRegistryApi.get(key(KEYS[i]));
            assertSame(registered, recipes.get(tailStart + i));
        }

        CraftingRecipe lateFlint = RecipeRegistryApi.get(
                key("crafting/flint_and_steel"));
        boolean earlierDuplicate = false;
        for (int i = 0; i < tailStart; i++) {
            CraftingRecipe recipe = (CraftingRecipe)recipes.get(i);
            ItemStack output = recipe.b();
            if (output != null && output.id == Item.FLINT_AND_STEEL.id) {
                earlierDuplicate = true;
                assertNotSame(lateFlint, recipe);
                break;
            }
        }
        assertTrue("pre-existing flint-and-steel recipe must remain first",
                earlierDuplicate);
    }

    private static void assertRecipeEquals(
            String message,
            CraftingRecipe expected,
            CraftingRecipe actual) {
        assertEquals(message + " class", expected.getClass(), actual.getClass());
        assertStackEquals(message + " output", expected.b(), actual.b());
        if (expected instanceof ShapedRecipes) {
            ShapedRecipes expectedShaped = (ShapedRecipes)expected;
            ShapedRecipes actualShaped = (ShapedRecipes)actual;
            assertEquals(message + " width",
                    expectedShaped.getRecipeWidth(), actualShaped.getRecipeWidth());
            assertEquals(message + " height",
                    expectedShaped.getRecipeHeight(), actualShaped.getRecipeHeight());
            assertIngredientStacks(message,
                    expectedShaped.getRecipeIngredients(),
                    actualShaped.getRecipeIngredients());
            return;
        }
        ShapelessRecipes expectedShapeless = (ShapelessRecipes)expected;
        ShapelessRecipes actualShapeless = (ShapelessRecipes)actual;
        List<ItemStack> expectedIngredients = expectedShapeless.getRecipeIngredients();
        List<ItemStack> actualIngredients = actualShapeless.getRecipeIngredients();
        assertIngredientStacks(message,
                expectedIngredients.toArray(new ItemStack[expectedIngredients.size()]),
                actualIngredients.toArray(new ItemStack[actualIngredients.size()]));
    }

    private static String describeFallbacks() {
        StringBuilder result = new StringBuilder();
        for (Integer index : RecipeRegistryBootstrap.javaFallbackRecipeIndices()) {
            CraftingRecipe recipe = (CraftingRecipe)baselineRecipes.get(index.intValue());
            ItemStack output = recipe.b();
            result.append(index).append(':')
                    .append(recipe.getClass().getSimpleName()).append(':')
                    .append(output.id).append('x').append(output.count)
                    .append('@').append(output.getData()).append('/').append(recipe.a())
                    .append(' ');
        }
        return result.toString();
    }

    private static void assertIngredientStacks(
            String message,
            ItemStack[] expected,
            ItemStack[] actual) {
        assertEquals(message + " ingredient count", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] == null || actual[i] == null) {
                assertEquals(message + " ingredient " + i, expected[i], actual[i]);
            } else {
                assertEquals(message + " ingredient id " + i,
                        expected[i].id, actual[i].id);
                assertEquals(message + " ingredient metadata " + i,
                        expected[i].getData(), actual[i].getData());
            }
        }
    }

    private static void assertStackEquals(
            String message,
            ItemStack expected,
            ItemStack actual) {
        assertEquals(message + " id", expected.id, actual.id);
        assertEquals(message + " count", expected.count, actual.count);
        assertEquals(message + " metadata", expected.getData(), actual.getData());
    }

    @Test
    public void decodedStructuresPreserveIdsMetadataCountsAndPatternOrder() {
        assertRecipe(KEYS[0], 3, 3, Block.REDSTONE_BLOCK.id, 1, 0,
                repeat(Item.REDSTONE.id, 0, 9));
        assertRecipe(KEYS[1], 3, 3, Block.COAL_BLOCK.id, 1, 0,
                repeat(Item.COAL.id, 0, 9));
        assertRecipe(KEYS[2], 1, 1, Item.REDSTONE.id, 9, 0,
                new int[][] {{Block.REDSTONE_BLOCK.id, -1}});
        assertRecipe(KEYS[3], 1, 1, Item.COAL.id, 9, 0,
                new int[][] {{Block.COAL_BLOCK.id, -1}});
        assertRecipe(KEYS[4], 2, 2, Item.FLINT_AND_STEEL.id, 1, 0,
                new int[][] {
                        {Item.IRON_INGOT.id, 0}, {-1, 0},
                        {-1, 0}, {Item.FLINT.id, 0}
                });
    }

    @Test
    public void allElevenGenericSmeltingMappingsAreDataAuthoritative() {
        List<ResourceLocation> keys = RecipeRegistryBootstrap.dataRecipeKeys();
        assertEquals(180, keys.size());
        assertEquals(11, FurnaceRecipes.getInstance().getBuiltInInputIds().size());
        for (int i = 163; i < 174; i++) {
            ResourceLocation key = keys.get(i);
            CraftingRecipe raw = RecipeRegistryBootstrap.dataRecipe(key);
            assertTrue(key.toString(), raw instanceof SmeltingRecipe);
            SmeltingRecipe recipe = (SmeltingRecipe)raw;
            assertEquals(key.toString(), -1, recipe.getInputMetadata());
            assertSame(key.toString(), recipe, RecipeRegistryApi.get(key));
            assertStackEquals(key.toString(), recipe.getSmeltingResult(),
                    (ItemStack)FurnaceRecipes.getInstance().b().get(
                            Integer.valueOf(recipe.getInputItemId())));
        }
    }

    @Test
    public void craftingResultAndShapedAccessorsAreDefensive() {
        ShapedRecipes recipe = (ShapedRecipes)RecipeRegistryApi.get(
                key("crafting/redstone_from_block"));
        ItemStack declared = recipe.b();
        ItemStack crafted = recipe.b(null);
        assertNotSame(declared, crafted);
        assertEquals(declared.id, crafted.id);
        assertEquals(declared.count, crafted.count);
        assertEquals(declared.getData(), crafted.getData());

        ItemStack[] first = recipe.getRecipeIngredients();
        ItemStack[] second = recipe.getRecipeIngredients();
        assertNotSame(first, second);
        assertNotSame(first[0], second[0]);
        first[0].count = 0;
        first[0] = null;
        assertEquals(1, recipe.getRecipeIngredients()[0].count);
    }

    @Test
    public void codecRejectsMalformedOrAmbiguousShapedData() {
        String valid = "{\"type\":\"minecraft:crafting_shaped\","
                + "\"pattern\":[\"#\"],\"key\":{\"#\":{\"id\":\"minecraft:coal\","
                + "\"legacy_metadata\":0}},\"result\":{\"id\":\"minecraft:coal_block\","
                + "\"count\":1,\"legacy_metadata\":0}}";
        ResourceLocation key = new ResourceLocation("minecraft", "test");
        RecipeCodec.decodeShaped(key, JsonParser.parseString(valid).getAsJsonObject());
        assertDecodeFails(key, valid.replace("[\"#\"]", "[\"#\",\"##\"]"));
        assertDecodeFails(key, valid.replace("\"key\":{",
                "\"key\":{\"X\":{\"id\":\"minecraft:coal\",\"legacy_metadata\":0},"));
        assertDecodeFails(key, valid.replace("minecraft:coal\"", "minecraft:missing\""));
        assertDecodeFails(key, valid.replace("\"count\":1", "\"count\":0"));
        assertDecodeFails(key, valid.substring(0, valid.length() - 1)
                + ",\"unsupported\":true}");
    }

    private static void assertRecipe(
            String path,
            int width,
            int height,
            int outputId,
            int outputCount,
            int outputMetadata,
            int[][] ingredients) {
        ShapedRecipes recipe = (ShapedRecipes)RecipeRegistryApi.get(key(path));
        assertEquals(width, recipe.getRecipeWidth());
        assertEquals(height, recipe.getRecipeHeight());
        ItemStack output = recipe.b();
        assertEquals(outputId, output.id);
        assertEquals(outputCount, output.count);
        assertEquals(outputMetadata, output.getData());

        ItemStack[] actual = recipe.getRecipeIngredients();
        assertEquals(ingredients.length, actual.length);
        for (int i = 0; i < ingredients.length; i++) {
            if (ingredients[i][0] < 0) {
                assertEquals("ingredient " + i, null, actual[i]);
            } else {
                assertEquals("ingredient id " + i, ingredients[i][0], actual[i].id);
                assertEquals("ingredient metadata " + i,
                        ingredients[i][1], actual[i].getData());
            }
        }
    }

    private static int[][] repeat(int itemId, int metadata, int count) {
        int[][] values = new int[count][2];
        for (int i = 0; i < count; i++) {
            values[i][0] = itemId;
            values[i][1] = metadata;
        }
        return values;
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }

    private static void assertDecodeFails(ResourceLocation key, String json) {
        try {
            RecipeCodec.decodeShaped(
                    key, JsonParser.parseString(json).getAsJsonObject());
            fail("Expected invalid recipe data to be rejected: " + json);
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().length() > 0);
        }
    }
}
