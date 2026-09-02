package net.minecraft.server.registry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.Block;
import net.minecraft.server.FireSpreadRule;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

/** Pins the server data-backed fire table to the exact client legacy oracle. */
public class FireSpreadDataParityTest {
    @BeforeClass
    public static void initializeFireSpreadData() {
        assertNotNull(Block.FIRE);
        assertNotNull(Block.WOOD);
        FireSpreadDataBootstrap.initialize();
    }

    @After
    public void restoreBuiltInGeneration() {
        FireSpreadRegistryApi.removeRuntimeOverride(Block.STONE);
        FireSpreadDataBootstrap.reload(null);
    }

    @Test
    public void builtInGenerationIsTheExactNineDefinitionLegacyOracle() {
        FireSpreadDataBootstrap.reload(null);

        LinkedHashMap<Block, int[]> expected =
                new LinkedHashMap<Block, int[]>();
        expected.put(Block.WOOD, odds(5, 20));
        expected.put(Block.FENCE, odds(5, 20));
        expected.put(Block.WOOD_STAIRS, odds(5, 20));
        expected.put(Block.LOG, odds(5, 5));
        expected.put(Block.LEAVES, odds(30, 60));
        expected.put(Block.BOOKSHELF, odds(30, 20));
        expected.put(Block.TNT, odds(15, 100));
        expected.put(Block.LONG_GRASS, odds(60, 100));
        expected.put(Block.WOOL, odds(30, 60));

        assertEquals(9, FireSpreadRegistryApi.size());
        assertEquals(9, FireSpreadRegistryApi.snapshotRules().size());
        assertEquals(9, FireSpreadDataBootstrap.REQUIRED_BLOCKS.size());
        assertTrue(FireSpreadRegistryApi.isCurrent());
        for (Map.Entry<Block, int[]> entry : expected.entrySet()) {
            ResourceLocation key = BlockRegistry.getKey(entry.getKey());
            assertNotNull(key);
            assertRule(key.toString(), FireSpreadRegistryApi.get(entry.getKey()),
                    entry.getValue()[0], entry.getValue()[1]);
            assertRule(key.toString(),
                    FireSpreadRegistryApi.snapshotRules().get(key),
                    entry.getValue()[0], entry.getValue()[1]);
        }
    }

    @Test
    public void blockWithoutDefinitionUsesSharedZeroRule() {
        FireSpreadDataBootstrap.reload(null);
        assertSame(FireSpreadRule.ZERO, FireSpreadRegistryApi.get(Block.STONE));
        assertRule("stone", FireSpreadRegistryApi.get(Block.STONE), 0, 0);
    }

    @Test
    public void decoderRejectsMissingNegativeFractionalAndUnknownFields()
            throws Exception {
        assertInvalid("{\"ignite_odds\":5}");
        assertInvalid("{\"burn_odds\":20}");
        assertInvalid("{\"ignite_odds\":-1,\"burn_odds\":20}");
        assertInvalid("{\"ignite_odds\":5,\"burn_odds\":-1}");
        assertInvalid("{\"ignite_odds\":5.5,\"burn_odds\":20}");
        assertInvalid("{\"ignite_odds\":5,\"burn_odds\":20,"
                + "\"unsupported\":true}");
    }

    @Test
    public void failedReloadDoesNotReplacePublishedGeneration()
            throws Exception {
        FireSpreadDataBootstrap.reload(null);
        Map<ResourceLocation, FireSpreadRule> before =
                FireSpreadRegistryApi.snapshotRules();
        FireSpreadRule woodBefore = FireSpreadRegistryApi.get(Block.WOOD);
        Path root = Files.createTempDirectory("mcose-fire-spread-atomic-");
        try {
            writeOverride(root,
                    BlockRegistry.getKey(Block.WOOD),
                    "{\"ignite_odds\":5,\"burn_odds\":-1}");
            try {
                FireSpreadDataBootstrap.reload(
                        RegistryDataLoader.createLayeredProvider(root.toFile()));
                fail("Expected invalid fire-spread data to reject the reload");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage(),
                        expected.getMessage().contains("fire_spread")
                                || expected.getMessage().contains("fire spread"));
            }

            assertSame(before, FireSpreadRegistryApi.snapshotRules());
            assertSame(woodBefore, FireSpreadRegistryApi.get(Block.WOOD));
            assertEquals(9, FireSpreadRegistryApi.size());
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void authoritativeClientAndServerDefinitionsAreByteIdentical()
            throws Exception {
        FireSpreadDataBootstrap.reload(null);
        Path developer = developerRoot();
        Path authoritativeRoot = developer.resolve("resourcepack");
        Path clientRoot = developer.resolve("client-source/minecraft/resources");
        Path serverRoot = developer.resolve("uberbukkit-mcose/src/main/resources");

        List<String> expectedFiles = new ArrayList<String>();
        for (ResourceLocation key
                : FireSpreadRegistryApi.snapshotRules().keySet()) {
            String relative = "data/" + key.getNamespace()
                    + "/fire_spread/" + key.getPath() + ".json";
            expectedFiles.add(relative);
            Path authoritative = authoritativeRoot.resolve(relative);
            Path client = clientRoot.resolve(relative);
            Path server = serverRoot.resolve(relative);
            assertTrue(authoritative.toString(), Files.isRegularFile(authoritative));
            assertTrue(client.toString(), Files.isRegularFile(client));
            assertTrue(server.toString(), Files.isRegularFile(server));
            byte[] expected = Files.readAllBytes(authoritative);
            assertArrayEquals(relative + " client", expected,
                    Files.readAllBytes(client));
            assertArrayEquals(relative + " server", expected,
                    Files.readAllBytes(server));
        }
        Collections.sort(expectedFiles);
        assertEquals(expectedFiles, listDefinitionFiles(authoritativeRoot));
        assertEquals(expectedFiles, listDefinitionFiles(clientRoot));
        assertEquals(expectedFiles, listDefinitionFiles(serverRoot));
    }

    @Test
    public void legacyFenceAndStairSwitchSurvivesDataReloadInFreshJvm()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-fire-spread-switch-");
        try {
            Files.write(root.resolve("uberbukkit.yml"),
                    ("mechanics:\n"
                            + "    flammable_fences_stairs: false\n")
                            .getBytes(StandardCharsets.UTF_8));
            File javaExecutable = new File(
                    new File(System.getProperty("java.home"), "bin"),
                    System.getProperty("os.name", "").toLowerCase()
                            .contains("win") ? "java.exe" : "java");
            Process process = new ProcessBuilder(
                    javaExecutable.getAbsolutePath(),
                    "-cp", System.getProperty("java.class.path"),
                    DisabledFenceProbe.class.getName())
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            InputStream output = process.getInputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = output.read(buffer)) >= 0) {
                captured.write(buffer, 0, read);
            }
            int exit = process.waitFor();
            String text = new String(
                    captured.toByteArray(), StandardCharsets.UTF_8);
            assertEquals(text, 0, exit);
            assertTrue(text, text.contains("FIRE_SPREAD_SWITCH_OK"));
        } finally {
            deleteTree(root);
        }
    }

    public static final class DisabledFenceProbe {
        public static void main(String[] args) {
            assertNotNull(Block.FIRE);
            FireSpreadDataBootstrap.initialize();
            assertRule("wood", FireSpreadRegistryApi.get(Block.WOOD), 5, 20);
            assertRule("fence disabled",
                    FireSpreadRegistryApi.get(Block.FENCE), 0, 0);
            assertRule("stairs disabled",
                    FireSpreadRegistryApi.get(Block.WOOD_STAIRS), 0, 0);
            FireSpreadDataBootstrap.reload();
            assertRule("fence remains disabled after reload",
                    FireSpreadRegistryApi.get(Block.FENCE), 0, 0);
            assertRule("stairs remain disabled after reload",
                    FireSpreadRegistryApi.get(Block.WOOD_STAIRS), 0, 0);
            System.out.println("FIRE_SPREAD_SWITCH_OK");
        }
    }

    private static void assertInvalid(String json) throws Exception {
        Path root = Files.createTempDirectory("mcose-fire-spread-invalid-");
        try {
            writeOverride(root, BlockRegistry.getKey(Block.WOOD), json);
            try {
                FireSpreadDataBootstrap.loadForTests(
                        RegistryDataLoader.createLayeredProvider(root.toFile()));
                fail("Expected invalid fire-spread definition to be rejected: "
                        + json);
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage(),
                        expected.getMessage().contains("fire_spread")
                                || expected.getMessage().contains("fire spread"));
            }
        } finally {
            deleteTree(root);
        }
    }

    private static void assertRule(
            String label,
            FireSpreadRule actual,
            int igniteOdds,
            int burnOdds) {
        assertNotNull(label, actual);
        assertEquals(label, igniteOdds, actual.getIgniteOdds());
        assertEquals(label, burnOdds, actual.getBurnOdds());
    }

    private static int[] odds(int igniteOdds, int burnOdds) {
        return new int[]{igniteOdds, burnOdds};
    }

    private static List<String> listDefinitionFiles(Path resourceRoot)
            throws Exception {
        Path data = resourceRoot.resolve("data");
        ArrayList<String> files = new ArrayList<String>();
        java.util.stream.Stream<Path> stream = Files.walk(data);
        try {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> path.toString().replace('\\', '/')
                            .contains("/fire_spread/"))
                    .forEach(path -> files.add(resourceRoot.relativize(path)
                            .toString().replace('\\', '/')));
        } finally {
            stream.close();
        }
        Collections.sort(files);
        return files;
    }

    private static void writeOverride(
            Path root,
            ResourceLocation key,
            String json) throws Exception {
        Path target = root.resolve("data")
                .resolve(key.getNamespace())
                .resolve("fire_spread")
                .resolve(key.getPath() + ".json");
        Files.createDirectories(target.getParent());
        Files.write(target, json.getBytes(StandardCharsets.UTF_8));
    }

    private static Path developerRoot() {
        Path found = findDeveloperRoot(java.nio.file.Paths.get("")
                .toAbsolutePath().normalize());
        if (found != null) return found;

        String inheritedWorkingDirectory = System.getenv("PWD");
        if (inheritedWorkingDirectory != null
                && inheritedWorkingDirectory.length() > 0) {
            found = findDeveloperRoot(java.nio.file.Paths.get(
                    inheritedWorkingDirectory).toAbsolutePath().normalize());
            if (found != null) return found;
        }
        throw new IllegalStateException(
                "Could not locate client, server, and resourcepack checkouts");
    }

    private static Path findDeveloperRoot(Path cursor) {
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("client-source"))
                    && Files.isDirectory(cursor.resolve("resourcepack"))
                    && Files.isDirectory(cursor.resolve("uberbukkit-mcose"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    private static void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        List<Path> paths = new ArrayList<Path>();
        java.util.stream.Stream<Path> stream = Files.walk(root);
        try {
            stream.forEach(paths::add);
        } finally {
            stream.close();
        }
        Collections.sort(paths, Collections.reverseOrder());
        for (Path path : paths) Files.deleteIfExists(path);
    }
}
