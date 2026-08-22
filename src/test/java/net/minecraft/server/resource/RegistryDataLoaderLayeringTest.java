package net.minecraft.server.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.minecraft.server.util.ResourceLocation;
import org.junit.Assume;
import org.junit.Test;

public class RegistryDataLoaderLayeringTest {
    private static final ResourceLocation TEST_TAG =
            new ResourceLocation("minecraft", "ordered");

    @Test
    public void highestEntryOverridesVanillaAndMalformedHighestNeverFallsBack() {
        ResourceLocation key = new ResourceLocation("minecraft", "entry");
        List<ResourceLocation> keys = Collections.singletonList(key);
        TestLayeredProvider valid = new TestLayeredProvider(
                "{\"value\":\"vanilla\"}",
                "{\"value\":\"configured\"}");
        Map<ResourceLocation, String> loaded = RegistryDataLoader.loadRequired(
                "test_registry",
                keys,
                valid,
                new RegistryDataLoader.Decoder<String>() {
                    public String decode(ResourceLocation ignored, JsonObject json) {
                        return json.get("value").getAsString();
                    }
                });
        assertEquals("configured", loaded.get(key));

        Map<ResourceLocation, String> fallback = RegistryDataLoader.loadRequired(
                "test_registry",
                keys,
                new TestLayeredProvider("{\"value\":\"vanilla\"}", null),
                new RegistryDataLoader.Decoder<String>() {
                    public String decode(ResourceLocation ignored, JsonObject json) {
                        return json.get("value").getAsString();
                    }
                });
        assertEquals("vanilla", fallback.get(key));

        try {
            RegistryDataLoader.loadRequired(
                    "test_registry",
                    keys,
                    new TestLayeredProvider("{\"value\":\"vanilla\"}", "not-json"),
                    new RegistryDataLoader.Decoder<String>() {
                        public String decode(ResourceLocation ignored, JsonObject json) {
                            return json.get("value").getAsString();
                        }
                    });
            fail("Expected malformed configured data to fail instead of falling back");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("[configured]"));
        }
    }

    @Test
    public void tagStackAppendsDeduplicatesAndHonorsReplaceInPriorityOrder() {
        List<ResourceLocation> appended = RegistryDataLoader.loadRequiredTag(
                "test_registry",
                TEST_TAG,
                new TestLayeredProvider(
                        "{\"values\":[\"minecraft:first\",\"minecraft:second\"]}",
                        "{\"values\":[\"minecraft:second\",\"minecraft:third\"]}"));
        assertEquals(asKeys("first", "second", "third"), appended);

        List<ResourceLocation> replaced = RegistryDataLoader.loadRequiredTag(
                "test_registry",
                TEST_TAG,
                new TestLayeredProvider(
                        "{\"values\":[\"minecraft:first\",\"minecraft:second\"]}",
                        "{\"replace\":true,\"values\":[\"minecraft:third\"]}"));
        assertEquals(asKeys("third"), replaced);

        List<ResourceLocation> duplicateText = RegistryDataLoader.loadRequiredTag(
                "test_registry",
                TEST_TAG,
                new TestLayeredProvider(
                        "{\"values\":[\"minecraft:first\",\"minecraft:first\"]}",
                        null));
        assertEquals(asKeys("first"), duplicateText);

        try {
            RegistryDataLoader.loadRequiredTag(
                    "test_registry",
                    TEST_TAG,
                    new TestLayeredProvider(
                            "{\"values\":[{\"id\":\"minecraft:missing\","
                                    + "\"required\":false}]}",
                            "{\"values\":[{\"id\":\"minecraft:missing\","
                                    + "\"required\":true}]}"),
                    new RegistryDataLoader.TagValueResolver() {
                        public boolean contains(ResourceLocation key) {
                            return false;
                        }
                    });
            fail("Expected higher required duplicate to remain authoritative");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("missing required tag value"));
        }
    }

    @Test
    public void nestedTagsResolveInOrderWithOptionalAndRequiredEntries() {
        MapLayeredProvider provider = new MapLayeredProvider();
        provider.vanilla("ordered", "{\"values\":[\"minecraft:first\","
                + "\"#minecraft:nested\","
                + "{\"id\":\"minecraft:missing_optional\",\"required\":false},"
                + "{\"id\":\"#minecraft:missing_tag\",\"required\":false}]}");
        provider.vanilla("nested", "{\"values\":[\"minecraft:second\"]}");
        provider.configured("nested", "{\"values\":[\"minecraft:second\","
                + "{\"id\":\"minecraft:third\",\"required\":true}]}");

        List<ResourceLocation> resolved = RegistryDataLoader.loadRequiredTag(
                "test_registry",
                TEST_TAG,
                provider,
                new RegistryDataLoader.TagValueResolver() {
                    public boolean contains(ResourceLocation key) {
                        return asKeys("first", "second", "third").contains(key);
                    }
                });
        assertEquals(asKeys("first", "second", "third"), resolved);

        provider.vanilla("ordered", "{\"values\":["
                + "{\"id\":\"minecraft:missing\",\"required\":true}]}");
        try {
            RegistryDataLoader.loadRequiredTag(
                    "test_registry",
                    TEST_TAG,
                    provider,
                    new RegistryDataLoader.TagValueResolver() {
                        public boolean contains(ResourceLocation key) {
                            return false;
                        }
                    });
            fail("Expected a missing required direct value to fail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("missing required tag value"));
        }
    }

    @Test
    public void nestedTagCyclesAndMissingRequiredTagsHaveActionableErrors() {
        MapLayeredProvider cycle = new MapLayeredProvider();
        cycle.vanilla("ordered", "{\"values\":[\"#minecraft:a\"]}");
        cycle.vanilla("a", "{\"values\":[\"#minecraft:b\"]}");
        cycle.vanilla("b", "{\"values\":[\"#minecraft:a\"]}");
        try {
            RegistryDataLoader.loadRequiredTag("test_registry", TEST_TAG, cycle);
            fail("Expected nested tag cycle to fail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("cyclic registry tag reference"));
            assertTrue(expected.getMessage().contains("#minecraft:a"));
        }

        MapLayeredProvider missing = new MapLayeredProvider();
        missing.vanilla("ordered", "{\"values\":[\"#minecraft:absent\"]}");
        try {
            RegistryDataLoader.loadRequiredTag("test_registry", TEST_TAG, missing);
            fail("Expected missing required nested tag to fail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("missing data/minecraft/tags/"
                    + "test_registry/absent.json"));
        }
    }

    @Test
    public void unsafeRelativePathsAndInvalidTagControlsAreRejected() {
        try {
            RegistryDataLoader.toResourcePath(
                    "test_registry", new ResourceLocation("minecraft", "../escape"));
            fail("Expected traversal path to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("invalid relative resource path"));
        }


        String[] invalidIds = new String[]{
                "Minecraft:first", "minecraft:", "minecraft:a:b", "minecraft:../escape"
        };
        for (int i = 0; i < invalidIds.length; i++) {
            try {
                RegistryDataLoader.loadRequiredTag(
                        "test_registry",
                        TEST_TAG,
                        new TestLayeredProvider(
                                "{\"values\":[\"" + invalidIds[i] + "\"]}", null));
                fail("Expected invalid tag id to fail: " + invalidIds[i]);
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("invalid")
                        || expected.getMessage().contains("empty"));
            }
        }

        try {
            RegistryDataLoader.loadRequiredTag(
                    "test_registry",
                    TEST_TAG,
                    new TestLayeredProvider(
                            "{\"values\":[\"minecraft:first\"]}",
                            "{\"replace\":\"yes\",\"values\":[\"minecraft:second\"]}"));
            fail("Expected non-boolean replace to be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("[configured]"));
        }
    }

    @Test
    public void configuredRootRejectsSymlinkEscape() throws Exception {
        Path base = Files.createTempDirectory("mcose-registry-layer-test");
        Path root = Files.createDirectory(base.resolve("root"));
        Path outside = Files.createDirectory(base.resolve("outside"));
        Path outsideFile = outside.resolve("escape.json");
        Path link = root.resolve("link");
        Files.write(outsideFile, Arrays.asList("{}"), StandardCharsets.UTF_8);
        try {
            try {
                Files.createSymbolicLink(link, outside);
            } catch (UnsupportedOperationException failure) {
                Assume.assumeNoException(failure);
            } catch (IOException failure) {
                Assume.assumeNoException(failure);
            } catch (SecurityException failure) {
                Assume.assumeNoException(failure);
            }

            RegistryDataLoader.LayeredResourceProvider provider =
                    RegistryDataLoader.createLayeredProvider(root.toFile());
            try {
                provider.getResource("link/escape.json");
                fail("Expected symlink escape to be rejected");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("escapes configured root"));
            }
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outsideFile);
            Files.deleteIfExists(outside);
            Files.deleteIfExists(root);
            Files.deleteIfExists(base);
        }
    }

    @Test
    public void packagedDiscoveryIsDeterministicAndConfiguredLayerWins()
            throws Exception {
        Path base = Files.createTempDirectory("mcose-registry-jar-test");
        Path jarPath = base.resolve("vanilla.jar");
        Path configured = Files.createDirectory(base.resolve("configured"));
        Path override = configured.resolve(
                "data/zeta/test_registry/a.json");
        Files.createDirectories(override.getParent());
        Files.write(override,
                Collections.singletonList("{\"value\":\"configured\"}"),
                StandardCharsets.UTF_8);
        JarOutputStream jar = new JarOutputStream(
                new FileOutputStream(jarPath.toFile()));
        try {
            putJarEntry(jar, "data/zeta/test_registry/a.json",
                    "{\"value\":\"vanilla-a\"}");
            putJarEntry(jar, "data/alpha/test_registry/z.json",
                    "{\"value\":\"vanilla-z\"}");
        } finally {
            jar.close();
        }

        try {
            RegistryDataLoader.LayeredResourceProvider provider =
                    RegistryDataLoader.createLayeredProvider(
                            configured.toFile(), jarPath.toUri().toURL());
            List<ResourceLocation> keys = RegistryDataLoader.discoverKeys(
                    "test_registry", provider);
            assertEquals(Arrays.asList(
                    new ResourceLocation("zeta", "a"),
                    new ResourceLocation("alpha", "z")), keys);
            Map<ResourceLocation, String> values = RegistryDataLoader.loadAll(
                    "test_registry",
                    provider,
                    new RegistryDataLoader.Decoder<String>() {
                        public String decode(ResourceLocation key, JsonObject json) {
                            return json.get("value").getAsString();
                        }
                    });
            assertEquals("configured", values.get(
                    new ResourceLocation("zeta", "a")));
            assertEquals("vanilla-z", values.get(
                    new ResourceLocation("alpha", "z")));
        } finally {
            Files.deleteIfExists(override);
            Files.deleteIfExists(override.getParent());
            Files.deleteIfExists(override.getParent().getParent());
            Files.deleteIfExists(override.getParent().getParent().getParent());
            Files.deleteIfExists(configured);
            Files.deleteIfExists(jarPath);
            Files.deleteIfExists(base);
        }
    }

    @Test
    public void loadAllTagsSnapshotsEveryLayerBeforeNestedResolution() {
        MapLayeredProvider provider = new MapLayeredProvider();
        provider.vanilla("ordered", "{\"values\":[\"#minecraft:nested\"]}");
        provider.vanilla("nested", "{\"values\":[\"minecraft:first\"]}");
        Map<ResourceLocation, List<ResourceLocation>> loaded =
                RegistryDataLoader.loadAllTags(
                        "test_registry",
                        provider,
                        new RegistryDataLoader.TagValueResolver() {
                            public boolean contains(ResourceLocation key) {
                                return asKeys("first").contains(key);
                            }
                        });
        assertEquals(asKeys("first"), loaded.get(TEST_TAG));
        assertEquals(1, provider.opens("ordered"));
        assertEquals(1, provider.opens("nested"));
    }

    private static void putJarEntry(
            JarOutputStream jar,
            String path,
            String text) throws IOException {
        jar.putNextEntry(new JarEntry(path));
        jar.write(text.getBytes(StandardCharsets.UTF_8));
        jar.closeEntry();
    }

    private static List<ResourceLocation> asKeys(String... paths) {
        ArrayList<ResourceLocation> keys = new ArrayList<ResourceLocation>(paths.length);
        for (int i = 0; i < paths.length; i++) {
            keys.add(new ResourceLocation("minecraft", paths[i]));
        }
        return keys;
    }

    private static final class TestLayeredProvider
            implements RegistryDataLoader.LayeredResourceProvider {
        private final RegistryDataLoader.RegistryResource vanilla;
        private final RegistryDataLoader.RegistryResource configured;

        TestLayeredProvider(String vanillaJson, String configuredJson) {
            this.vanilla = resource("vanilla", vanillaJson);
            this.configured = resource("configured", configuredJson);
        }

        public InputStream open(String path) throws IOException {
            RegistryDataLoader.RegistryResource selected = getResource(path);
            return selected == null ? null : selected.open();
        }

        public RegistryDataLoader.RegistryResource getResource(String path) {
            return configured != null ? configured : vanilla;
        }

        public List<RegistryDataLoader.RegistryResource> getResourceStack(String path) {
            ArrayList<RegistryDataLoader.RegistryResource> result =
                    new ArrayList<RegistryDataLoader.RegistryResource>(2);
            if (vanilla != null) result.add(vanilla);
            if (configured != null) result.add(configured);
            return result;
        }
    }

    private static final class MapLayeredProvider
            implements RegistryDataLoader.LayeredResourceProvider {
        private final Map<String, RegistryDataLoader.RegistryResource> vanilla =
                new java.util.HashMap<String, RegistryDataLoader.RegistryResource>();
        private final Map<String, RegistryDataLoader.RegistryResource> configured =
                new java.util.HashMap<String, RegistryDataLoader.RegistryResource>();
        private final Map<String, Integer> opens =
                new java.util.HashMap<String, Integer>();

        void vanilla(String tagPath, String json) {
            this.vanilla.put(path(tagPath), countingResource(
                    "vanilla", tagPath, json, this.opens));
        }

        void configured(String tagPath, String json) {
            this.configured.put(path(tagPath), countingResource(
                    "configured", tagPath, json, this.opens));
        }

        public InputStream open(String path) throws IOException {
            RegistryDataLoader.RegistryResource selected = getResource(path);
            return selected == null ? null : selected.open();
        }

        public RegistryDataLoader.RegistryResource getResource(String path) {
            RegistryDataLoader.RegistryResource high = this.configured.get(path);
            return high != null ? high : this.vanilla.get(path);
        }

        public List<RegistryDataLoader.RegistryResource> getResourceStack(String path) {
            ArrayList<RegistryDataLoader.RegistryResource> result =
                    new ArrayList<RegistryDataLoader.RegistryResource>(2);
            RegistryDataLoader.RegistryResource low = this.vanilla.get(path);
            RegistryDataLoader.RegistryResource high = this.configured.get(path);
            if (low != null) result.add(low);
            if (high != null) result.add(high);
            return result;
        }

        public List<String> listResources(String resourceRoot) {
            TreeSet<String> paths = new TreeSet<String>();
            paths.addAll(this.vanilla.keySet());
            paths.addAll(this.configured.keySet());
            return new ArrayList<String>(paths);
        }

        int opens(String tagPath) {
            Integer count = this.opens.get(tagPath);
            return count == null ? 0 : count.intValue();
        }

        private static String path(String tagPath) {
            return "data/minecraft/tags/test_registry/" + tagPath + ".json";
        }
    }

    private static RegistryDataLoader.RegistryResource resource(
            final String sourceId,
            final String json) {
        if (json == null) return null;
        return new RegistryDataLoader.RegistryResource() {
            public String sourceId() {
                return sourceId;
            }

            public InputStream open() throws IOException {
                return new ByteArrayInputStream(json.getBytes("UTF-8"));
            }
        };
    }

    private static RegistryDataLoader.RegistryResource countingResource(
            final String sourceId,
            final String key,
            final String json,
            final Map<String, Integer> opens) {
        return new RegistryDataLoader.RegistryResource() {
            public String sourceId() {
                return sourceId;
            }

            public InputStream open() throws IOException {
                Integer count = opens.get(key);
                opens.put(key, Integer.valueOf(count == null ? 1 : count + 1));
                return new ByteArrayInputStream(json.getBytes("UTF-8"));
            }
        };
    }
}
