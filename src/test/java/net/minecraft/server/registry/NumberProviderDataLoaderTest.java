package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.Item;
import net.minecraft.server.item.component.CookingFuel;
import net.minecraft.server.registry.number.NumberProvider;
import net.minecraft.server.registry.number.NumberProviderCodec;
import net.minecraft.server.registry.number.NumberProviders;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class NumberProviderDataLoaderTest {
    @BeforeClass
    public static void initializeRegistry() {
        NumberProviderRegistryBootstrap.initialize();
    }

    @After
    public void restoreBuiltIns() {
        NumberProviderRegistryBootstrap.reload();
    }

    @Test
    public void scalarAndTypedConstantFormsDecodeStrictly() {
        ResourceLocation key = NumberProviders.COOKING_TIME_COAL;
        assertEquals(100.6F, NumberProviderCodec.decode(
                key, JsonParser.parseString("100.6")).getValue(), 0.0F);
        assertEquals(42.0F, NumberProviderCodec.decode(key, JsonParser.parseString(
                "{\"type\":\"minecraft:constant\",\"value\":42}")).getValue(), 0.0F);

        assertDecodeFails("true");
        assertDecodeFails("\"100\"");
        assertDecodeFails("null");
        assertDecodeFails("[]");
        assertDecodeFails("{\"value\":100}");
        assertDecodeFails("{\"type\":\"minecraft:uniform\",\"value\":100}");
        assertDecodeFails("{\"type\":\"minecraft:constant\",\"value\":100,\"extra\":0}");
    }

    @Test
    public void builtInCookingProvidersHaveExactLegacyValuesAndOrder() {
        assertEquals(NumberProviders.keys(),
                new java.util.ArrayList<ResourceLocation>(
                        NumberProviderRegistryApi.keys()));
        assertValue(NumberProviders.COOKING_DEFAULT_SPEED_MULTIPLIER, 1.0F);
        assertValue(NumberProviders.COOKING_TIME_COAL, 1600.0F);
        assertValue(NumberProviders.COOKING_TIME_COAL_BLOCK, 14400.0F);
        assertValue(NumberProviders.COOKING_TIME_DRY_PLANTS, 100.0F);
        assertValue(NumberProviders.COOKING_TIME_LAVA_BUCKET, 20000.0F);
        assertValue(NumberProviders.COOKING_TIME_WOOD_BLOCKS, 300.0F);
        assertValue(NumberProviders.COOKING_TIME_WOOD_ITEMS_EXTRA_SMALL, 100.0F);
    }

    @Test
    public void scalarLoaderDoesNotWeakenExistingObjectOnlyLoader() {
        final byte[] scalar = "1.0".getBytes(StandardCharsets.UTF_8);
        RegistryDataLoader.ResourceProvider provider =
                new RegistryDataLoader.ResourceProvider() {
                    public InputStream open(String resourcePath) {
                        return new ByteArrayInputStream(scalar);
                    }
                };
        ResourceLocation key = new ResourceLocation("test", "scalar");
        Map<ResourceLocation, NumberProvider> values =
                RegistryDataLoader.loadRequiredValues(
                        "number_provider",
                        java.util.Collections.singletonList(key),
                        provider,
                        new RegistryDataLoader.ValueDecoder<NumberProvider>() {
                            public NumberProvider decode(
                                    ResourceLocation entryKey,
                                    com.google.gson.JsonElement json) {
                                return NumberProviderCodec.decode(entryKey, json);
                            }
                        });
        assertEquals(1.0F, values.get(key).getValue(), 0.0F);

        try {
            RegistryDataLoader.loadRequired(
                    "object_registry",
                    java.util.Collections.singletonList(key),
                    provider,
                    new RegistryDataLoader.Decoder<Object>() {
                        public Object decode(
                                ResourceLocation entryKey,
                                com.google.gson.JsonObject json) {
                            return new Object();
                        }
                    });
            fail("Object-only registry loader accepted a scalar root");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("root must be a JSON object"));
        }
    }

    @Test
    public void malformedReloadRollsBackRegistryAndRuntimeGeneration() throws Exception {
        Map<ResourceLocation, NumberProvider> before = snapshot();
        long generation = RegistryRuntime.current().getGeneration();
        Path root = Files.createTempDirectory("mcose-number-provider-malformed-");
        try {
            Path override = root.resolve(
                    "data/minecraft/number_provider/cooking/time_coal.json");
            Files.createDirectories(override.getParent());
            Files.write(override, "true".getBytes(StandardCharsets.UTF_8));
            try {
                NumberProviderRegistryBootstrap.reload(
                        RegistryDataLoader.createLayeredProvider(root.toFile()));
                fail("Malformed number provider was accepted");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("number_provider"));
            }

            assertEquals(generation, RegistryRuntime.current().getGeneration());
            assertEquals(before.keySet(), NumberProviderRegistryApi.keys());
            for (Map.Entry<ResourceLocation, NumberProvider> entry : before.entrySet()) {
                assertSame(entry.getValue(), NumberProviderRegistryApi.get(entry.getKey()));
            }
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void reloadPublishesCustomNamespaceAndRoundsAtComponentUseTime()
            throws Exception {
        ItemRegistryBootstrap.initialize();
        ItemCapabilityRegistryBootstrap.initialize();
        Path root = Files.createTempDirectory("mcose-number-provider-reload-");
        try {
            Path stick = root.resolve(
                    "data/minecraft/number_provider/cooking/time_wood_items_extra_small.json");
            Path custom = root.resolve(
                    "data/example/number_provider/cooking/test.json");
            Files.createDirectories(stick.getParent());
            Files.createDirectories(custom.getParent());
            Files.write(stick, "100.6".getBytes(StandardCharsets.UTF_8));
            Files.write(custom, "7.0".getBytes(StandardCharsets.UTF_8));

            long beforeGeneration = RegistryRuntime.current().getGeneration();
            NumberProviderRegistryBootstrap.reload(
                    RegistryDataLoader.createLayeredProvider(root.toFile()));

            assertTrue(RegistryRuntime.current().getGeneration() > beforeGeneration);
            assertEquals(7.0F, NumberProviderRegistryApi.get(
                    new ResourceLocation("example", "cooking/test")).getValue(), 0.0F);
            CookingFuel stickFuel = ItemCapabilityRegistryApi.getCookingFuel(Item.STICK);
            assertEquals(101, stickFuel.resolveBurnTimeTicks());
            assertEquals(101, ItemCapabilityRegistryApi.getFuelTicks(Item.STICK));
        } finally {
            deleteTree(root);
        }
    }

    private static Map<ResourceLocation, NumberProvider> snapshot() {
        LinkedHashMap<ResourceLocation, NumberProvider> result =
                new LinkedHashMap<ResourceLocation, NumberProvider>();
        for (ResourceLocation key : NumberProviderRegistryApi.keys()) {
            result.put(key, NumberProviderRegistryApi.get(key));
        }
        return result;
    }

    private static void assertValue(ResourceLocation key, float expected) {
        NumberProvider provider = NumberProviderRegistryApi.get(key);
        assertTrue("Missing number provider " + key, provider != null);
        assertEquals(expected, provider.getValue(), 0.0F);
    }

    private static void assertDecodeFails(String json) {
        try {
            NumberProviderCodec.decode(
                    NumberProviders.COOKING_TIME_COAL,
                    JsonParser.parseString(json));
            fail("Expected invalid number provider to be rejected: " + json);
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        java.util.stream.Stream<Path> paths = Files.walk(root);
        try {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {}
            });
        } finally {
            paths.close();
        }
    }
}
