package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.registry.PaintingVariantRegistryApi;
import net.minecraft.server.registry.PaintingVariantRegistryBootstrap;
import net.minecraft.server.registry.SimpleRegistry;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class PaintingVariantDataLoaderTest {
    @BeforeClass
    public static void initializeBlocksBeforeItems() {
        assertTrue(Block.STONE != null);
        assertTrue(Item.SIGN != null);
    }

    @Test
    public void builtInDataPreservesLegacyOrderAndPublishesOptionalModernData() {
        PaintingVariantRegistryBootstrap.initialize();

        EnumArt[] legacy = EnumArt.values();
        Collection<PaintingVariant> registered = PaintingVariantRegistryApi.values();
        List<PaintingVariant> placeable = PaintingVariantRegistryApi.placeableValues();
        List<PaintingVariant> optional = PaintingVariantRegistryApi.optionalValues();
        assertEquals(26, placeable.size());
        assertEquals(17, optional.size());
        assertEquals(43, registered.size());
        assertTrue(PaintingVariantRegistryApi.isFrozen());

        int index = 0;
        for (PaintingVariant variant : placeable) {
            EnumArt art = legacy[index++];
            assertSame(art, variant.getLegacyArt());
            assertTrue(variant.hasLegacyBridge());
            assertEquals(art.A, variant.getLegacyTitle());
            assertEquals(art.B, variant.getPixelWidth());
            assertEquals(art.C, variant.getPixelHeight());

            ResourceLocation key = PaintingVariantRegistryBootstrap.keyFor(art);
            assertEquals(key, PaintingVariantRegistryApi.getKey(variant));
            assertEquals(key, variant.getAssetId());
        }
        assertEquals(legacy.length, index);

        ArrayList<PaintingVariant> ordered =
                new ArrayList<PaintingVariant>(registered);
        assertEquals(placeable, ordered.subList(0, placeable.size()));
        assertEquals(optional, ordered.subList(placeable.size(), ordered.size()));

        LinkedHashMap<ResourceLocation, int[]> expected = expectedOptionalVariants();
        index = 0;
        for (Map.Entry<ResourceLocation, int[]> entry : expected.entrySet()) {
            PaintingVariant variant = optional.get(index++);
            assertSame(variant, PaintingVariantRegistryApi.get(entry.getKey()));
            assertEquals(entry.getKey(), PaintingVariantRegistryApi.getKey(variant));
            assertEquals(entry.getKey(), variant.getAssetId());
            assertEquals(entry.getValue()[0], variant.getWidth());
            assertEquals(entry.getValue()[1], variant.getHeight());
            assertTrue(!variant.hasLegacyBridge());
            assertEquals(null, variant.getLegacyArt());
            assertEquals(null, variant.getLegacyTitle());
        }
        assertEquals(expected.size(), index);
        assertImmutable(placeable);
        assertImmutable(optional);
        assertEquals("42ab2947baf8ec630782a94e48286d46d3de9b3ae0699dfebc75cddc0c53b211",
                PaintingVariantRegistryApi.fingerprint());
    }

    @Test
    public void modernOptionalCodecDoesNotCreateALegacySaveOrWireBridge() {
        ResourceLocation key = new ResourceLocation("minecraft", "backyard");
        PaintingVariant variant = PaintingVariant.decode(key, json(
                "{\"asset_id\":\"minecraft:backyard\",\"width\":3,\"height\":4,"
                        + "\"title\":{\"translate\":\"painting.minecraft.backyard.title\"},"
                        + "\"author\":{\"translate\":\"painting.minecraft.backyard.author\"}}"),
                null);
        assertEquals(3, variant.getWidth());
        assertEquals(4, variant.getHeight());
        assertEquals(key, variant.getAssetId());
        assertTrue(!variant.hasLegacyBridge());
        assertEquals(null, variant.getLegacyArt());
        assertEquals(null, variant.getLegacyTitle());
    }

    @Test
    public void exactLegacyTitlesRemainTheSaveAndWireLookupBoundary() {
        assertSame(EnumArt.SKULL_AND_ROSES,
                PaintingVariantRegistryApi.getByLegacyTitle("SkullAndRoses").getLegacyArt());
        assertSame(EnumArt.MATTY,
                PaintingVariantRegistryApi.getByLegacyTitle("Matty").getLegacyArt());
        assertEquals(null, PaintingVariantRegistryApi.getByLegacyTitle("Backyard"));
        assertEquals(null, PaintingVariantRegistryApi.getByLegacyTitle("skullandroses"));
        assertEquals(null, PaintingVariantRegistryApi.getByLegacyTitle("MissingMotive"));
    }

    @Test
    public void motiveNbtRoundTripAndUnknownFallbackStayLegacyCompatible() {
        EntityPainting original = new EntityPainting(null);
        original.e = EnumArt.SKULL_AND_ROSES;
        original.a = 2;
        original.b = 5;
        original.c = 70;
        original.d = -3;
        NBTTagCompound saved = new NBTTagCompound();
        original.b(saved);
        assertEquals("SkullAndRoses", saved.getString("Motive"));

        EntityPainting restored = new EntityPainting(null);
        restored.a(saved);
        assertSame(EnumArt.SKULL_AND_ROSES, restored.e);
        assertEquals(32, restored.getPaintingVariant().getPixelWidth());
        assertEquals(32, restored.getPaintingVariant().getPixelHeight());

        saved.setString("Motive", "MissingMotive");
        restored.a(saved);
        assertSame(EnumArt.KEBAB, restored.e);
    }

    @Test
    public void codecRejectsDataThatWouldChangeLegacyGameplay() {
        ResourceLocation key = new ResourceLocation("minecraft", "kebab");
        assertDecodeFails(key,
                json("{\"width\":2,\"height\":1,\"asset_id\":\"minecraft:kebab\",\"legacy_title\":\"Kebab\"}"));
        assertDecodeFails(key,
                json("{\"width\":1,\"height\":1,\"asset_id\":\"minecraft:bomb\",\"legacy_title\":\"Kebab\"}"));
        assertDecodeFails(key,
                json("{\"width\":1,\"height\":1,\"asset_id\":\"minecraft:kebab\",\"legacy_title\":\"Bomb\"}"));
        assertDecodeFails(key,
                json("{\"width\":1.5,\"height\":1,\"asset_id\":\"minecraft:kebab\",\"legacy_title\":\"Kebab\"}"));
        assertDecodeFails(key,
                json("{\"width\":\"1\",\"height\":1,\"asset_id\":\"minecraft:kebab\",\"legacy_title\":\"Kebab\"}"));
        assertDecodeFails(key,
                json("{\"width\":1,\"height\":1,\"asset_id\":7,\"legacy_title\":\"Kebab\"}"));
    }

    @Test
    public void failedBatchNeverPublishesItsValidPrefix() {
        final List<ResourceLocation> keys = new ArrayList<ResourceLocation>();
        keys.add(new ResourceLocation("minecraft", "first"));
        keys.add(new ResourceLocation("minecraft", "second"));
        final SimpleRegistry<String> published = new SimpleRegistry<String>();

        try {
            Map<ResourceLocation, String> staged = RegistryDataLoader.loadRequired(
                    "test_registry",
                    keys,
                    new RegistryDataLoader.ResourceProvider() {
                        public InputStream open(String path) throws IOException {
                            String data = path.endsWith("first.json")
                                    ? "{\"value\":\"ok\"}"
                                    : "not-json";
                            return new ByteArrayInputStream(data.getBytes("UTF-8"));
                        }
                    },
                    new RegistryDataLoader.Decoder<String>() {
                        public String decode(ResourceLocation key, JsonObject object) {
                            return object.get("value").getAsString();
                        }
                    });
            assertTrue(published.registerAllAtomic(staged));
            fail("Expected malformed second entry to reject the staged batch");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("minecraft:second"));
        }

        assertTrue(published.keys().isEmpty());
    }

    @Test
    public void loaderRejectsTrailingJsonContent() {
        final List<ResourceLocation> keys = new ArrayList<ResourceLocation>();
        keys.add(new ResourceLocation("minecraft", "trailing"));

        try {
            RegistryDataLoader.loadRequired(
                    "test_registry",
                    keys,
                    new RegistryDataLoader.ResourceProvider() {
                        public InputStream open(String path) throws IOException {
                            return new ByteArrayInputStream(
                                    "{\"value\":\"ok\"} trailing".getBytes("UTF-8"));
                        }
                    },
                    new RegistryDataLoader.Decoder<String>() {
                        public String decode(ResourceLocation key, JsonObject object) {
                            return object.get("value").getAsString();
                        }
                    });
            fail("Expected trailing content to reject the staged batch");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("minecraft:trailing"));
        }
    }

    @Test
    public void registryBatchConflictDoesNotPublishEarlierEntries() {
        SimpleRegistry<String> registry = new SimpleRegistry<String>();
        ResourceLocation occupied = new ResourceLocation("minecraft", "occupied");
        registry.register(occupied, "old");

        LinkedHashMap<ResourceLocation, String> batch =
                new LinkedHashMap<ResourceLocation, String>();
        ResourceLocation first = new ResourceLocation("minecraft", "first");
        batch.put(first, "first-value");
        batch.put(occupied, "replacement");

        assertTrue(!registry.registerAllAtomic(batch));
        assertEquals(null, registry.get(first));
        assertEquals("old", registry.get(occupied));
    }

    @Test
    public void registryBatchRejectsOneIdentityUnderTwoKeys() {
        SimpleRegistry<Object> registry = new SimpleRegistry<Object>();
        Object shared = new Object();
        LinkedHashMap<ResourceLocation, Object> batch =
                new LinkedHashMap<ResourceLocation, Object>();
        ResourceLocation first = new ResourceLocation("minecraft", "first");
        ResourceLocation second = new ResourceLocation("minecraft", "second");
        batch.put(first, shared);
        batch.put(second, shared);

        assertTrue(!registry.registerAllAtomic(batch));
        assertEquals(null, registry.get(first));
        assertEquals(null, registry.get(second));
        assertEquals(null, registry.getKey(shared));
    }

    @Test
    public void registryReplacementPublishesOneImmutableSnapshot() {
        SimpleRegistry<String> registry = new SimpleRegistry<String>();
        ResourceLocation oldKey = new ResourceLocation("minecraft", "old");
        String oldValue = new String("old-value");
        registry.register(oldKey, oldValue);
        Collection<String> oldValues = registry.values();

        ResourceLocation first = new ResourceLocation("minecraft", "first");
        ResourceLocation second = new ResourceLocation("minecraft", "second");
        String firstValue = new String("first-value");
        String secondValue = new String("second-value");
        LinkedHashMap<ResourceLocation, String> replacement =
                new LinkedHashMap<ResourceLocation, String>();
        replacement.put(first, firstValue);
        replacement.put(second, secondValue);

        assertTrue(registry.canReplaceAllAtomic(replacement));
        assertSame(oldValue, registry.get(oldKey));
        assertTrue(registry.replaceAllAtomic(replacement));
        assertEquals(1, oldValues.size());
        assertTrue(oldValues.contains(oldValue));
        assertEquals(null, registry.get(oldKey));
        assertEquals(null, registry.getKey(oldValue));
        assertSame(firstValue, registry.get(first));
        assertSame(first, registry.getKey(firstValue));
        assertEquals(2, registry.values().size());

        LinkedHashMap<ResourceLocation, String> invalid =
                new LinkedHashMap<ResourceLocation, String>();
        invalid.put(new ResourceLocation("minecraft", "alias_one"), firstValue);
        invalid.put(new ResourceLocation("minecraft", "alias_two"), firstValue);
        assertTrue(!registry.canReplaceAllAtomic(invalid));
        assertTrue(!registry.replaceAllAtomic(invalid));
        assertSame(firstValue, registry.get(first));
        assertSame(secondValue, registry.get(second));
    }

    @Test(expected = IllegalStateException.class)
    public void frozenRegistryRejectsAtomicReplacement() {
        SimpleRegistry<String> registry = new SimpleRegistry<String>();
        registry.freeze();
        registry.replaceAllAtomic(new LinkedHashMap<ResourceLocation, String>());
    }

    @Test(expected = IllegalStateException.class)
    public void frozenRegistryRejectsLateMutation() {
        SimpleRegistry<String> registry = new SimpleRegistry<String>();
        registry.register(new ResourceLocation("minecraft", "value"), "value");
        registry.freeze();
        registry.register(new ResourceLocation("minecraft", "late"), "late");
    }

    private static JsonObject json(String data) {
        return JsonParser.parseString(data).getAsJsonObject();
    }

    private static void assertDecodeFails(ResourceLocation key, JsonObject json) {
        try {
            PaintingVariant.decode(key, json, EnumArt.KEBAB);
            fail("Expected behavior-changing painting data to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    private static LinkedHashMap<ResourceLocation, int[]> expectedOptionalVariants() {
        LinkedHashMap<ResourceLocation, int[]> expected =
                new LinkedHashMap<ResourceLocation, int[]>();
        optional(expected, "backyard", 3, 4);
        optional(expected, "bouquet", 3, 3);
        optional(expected, "cavebird", 3, 3);
        optional(expected, "changing", 4, 2);
        optional(expected, "cotan", 3, 3);
        optional(expected, "endboss", 3, 3);
        optional(expected, "fern", 3, 3);
        optional(expected, "finding", 4, 2);
        optional(expected, "lowmist", 4, 2);
        optional(expected, "meditative", 1, 1);
        optional(expected, "orb", 4, 4);
        optional(expected, "owlemons", 3, 3);
        optional(expected, "passage", 4, 2);
        optional(expected, "pond", 3, 4);
        optional(expected, "prairie_ride", 1, 2);
        optional(expected, "sunflowers", 3, 3);
        optional(expected, "tides", 3, 3);
        return expected;
    }

    private static void optional(
            Map<ResourceLocation, int[]> expected,
            String path,
            int width,
            int height) {
        expected.put(new ResourceLocation("minecraft", path),
                new int[] { width, height });
    }

    private static void assertImmutable(List<PaintingVariant> variants) {
        try {
            variants.add(variants.get(0));
            fail("Painting compatibility collection is mutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }
}
