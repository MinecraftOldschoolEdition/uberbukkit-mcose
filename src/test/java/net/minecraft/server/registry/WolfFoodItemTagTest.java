package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class WolfFoodItemTagTest {
    @BeforeClass
    public static void initializeItemsAndWolfFoodTag() {
        assertTrue(Block.STONE != null);
        ItemRegistryBootstrap.initialize();
        ItemTagRegistryBootstrap.initialize();
    }

    @Test
    public void builtInWolfFoodTagHasExactBetaMembership() {
        assertEquals(Arrays.asList(
                        id("porkchop"),
                        id("cooked_porkchop")),
                ItemTagRegistryApi.tagMemberKeys(ItemTags.WOLF_FOOD));
        assertTrue(ItemTags.is(Item.PORK, ItemTags.WOLF_FOOD));
        assertTrue(ItemTags.is(Item.GRILLED_PORK, ItemTags.WOLF_FOOD));
        assertFalse(ItemTags.is(Item.RAW_FISH, ItemTags.WOLF_FOOD));
        assertFalse(ItemTags.is(Item.COOKED_FISH, ItemTags.WOLF_FOOD));
        assertFalse(ItemTags.is(Item.BONE, ItemTags.WOLF_FOOD));
        assertTrue(ItemTagRegistryApi.areTagBindingsCurrent());
        try {
            ItemTagRegistryApi.tagMemberKeys(ItemTags.WOLF_FOOD).clear();
            fail("Expected immutable wolf-food membership");
        } catch (UnsupportedOperationException expected) {}
    }

    @Test
    public void candidateRejectsMissingAliasAndNonFoodValues()
            throws Exception {
        assertRejected("minecraft:not_an_item", "missing required tag value");
        assertRejected("minecraft:raw_porkchop", "missing required tag value");
        assertRejected("minecraft:bone", "not an ItemFood");
    }

    @Test
    public void configuredTagMayAddCanonicalFoodWithoutChangingCode()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-wolf-food-extra-");
        try {
            Path tag = root.resolve(
                    "data/minecraft/tags/item/wolf_food.json");
            Files.createDirectories(tag.getParent());
            Files.write(tag, (
                    "{\"values\":[\"minecraft:porkchop\","
                    + "\"minecraft:cooked_porkchop\","
                    + "\"minecraft:bread\"]}")
                    .getBytes(StandardCharsets.UTF_8));
            RegistryTagBindings<Item> candidate =
                    ItemTagRegistryBootstrap.loadCandidate(
                            RegistryDataLoader.createLayeredProvider(
                                    root.toFile()));
            assertEquals(Arrays.asList(
                            id("porkchop"),
                            id("cooked_porkchop"),
                            id("bread")),
                    candidate.valueKeys(ItemTags.WOLF_FOOD));
        } finally {
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {}
                    });
        }
    }

    @Test
    public void candidateSnapshotsNestedTagBytesBeforeResolution() {
        final String rootPath =
                "data/minecraft/tags/item/wolf_food.json";
        final String nestedPath =
                "data/minecraft/tags/item/a_wolf_food_nested.json";
        final String[] nested = new String[]{
                "{\"values\":[\"minecraft:porkchop\"]}"};
        RegistryDataLoader.LayeredResourceProvider provider =
                new RegistryDataLoader.LayeredResourceProvider() {
                    public InputStream open(String path) throws IOException {
                        List<RegistryDataLoader.RegistryResource> stack =
                                getResourceStack(path);
                        return stack.isEmpty() ? null : stack.get(0).open();
                    }

                    public RegistryDataLoader.RegistryResource getResource(
                            String path) throws IOException {
                        List<RegistryDataLoader.RegistryResource> stack =
                                getResourceStack(path);
                        return stack.isEmpty() ? null : stack.get(0);
                    }

                    public List<RegistryDataLoader.RegistryResource>
                            getResourceStack(final String path) {
                        if (!rootPath.equals(path) && !nestedPath.equals(path)) {
                            return Collections.emptyList();
                        }
                        return Collections.singletonList(
                                new RegistryDataLoader.RegistryResource() {
                                    public String sourceId() {
                                        return "mutating-test";
                                    }

                                    public InputStream open()
                                            throws IOException {
                                        String json;
                                        if (rootPath.equals(path)) {
                                            json = "{\"values\":["
                                                    + "\"#minecraft:"
                                                    + "a_wolf_food_nested\"]}";
                                            nested[0] = "{\"values\":["
                                                    + "\"minecraft:bread\"]}";
                                        } else {
                                            json = nested[0];
                                        }
                                        return new ByteArrayInputStream(
                                                json.getBytes("UTF-8"));
                                    }
                                });
                    }

                    public List<String> listResources(String resourceRoot) {
                        return Arrays.asList(rootPath, nestedPath);
                    }
                };

        RegistryTagBindings<Item> candidate =
                ItemTagRegistryBootstrap.loadCandidate(provider);
        assertEquals(Collections.singletonList(id("porkchop")),
                candidate.valueKeys(ItemTags.WOLF_FOOD));
    }

    private static void assertRejected(String extraValue, String message)
            throws Exception {
        Path root = Files.createTempDirectory("mcose-wolf-food-tag-");
        try {
            Path tag = root.resolve(
                    "data/minecraft/tags/item/wolf_food.json");
            Files.createDirectories(tag.getParent());
            String json = "{\"replace\":true,\"values\":["
                    + "\"minecraft:porkchop\","
                    + "\"minecraft:cooked_porkchop\","
                    + "\"" + extraValue + "\"]}";
            Files.write(tag, json.getBytes(StandardCharsets.UTF_8));
            RegistryDataLoader.LayeredResourceProvider provider =
                    RegistryDataLoader.createLayeredProvider(root.toFile());
            try {
                ItemTagRegistryBootstrap.loadCandidate(provider);
                fail("Expected rejected wolf-food member " + extraValue);
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage(),
                        expected.getMessage().contains(message));
            }
        } finally {
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {}
                    });
        }
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
