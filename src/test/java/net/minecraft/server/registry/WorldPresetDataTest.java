package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.server.Alpha.AlphaChunkProvider;
import net.minecraft.server.ChunkProviderFlat;
import net.minecraft.server.ChunkProviderGenerate;
import net.minecraft.server.ChunkProviderHell;
import net.minecraft.server.ChunkProviderNetherSky;
import net.minecraft.server.ChunkProviderSky;
import net.minecraft.server.Classic.ChunkProviderClassic;
import net.minecraft.server.Classic.ChunkProviderHellClassic;
import net.minecraft.server.Infdev.InfdevChunkProvider;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class WorldPresetDataTest {
    @BeforeClass
    public static void initializeWorldPresets() {
        WorldPresetDataBootstrap.initialize();
    }

    @Test
    public void everyLegacyTerrainAndDimensionRoutesToItsExactGeneratorKey() {
        assertRoute(0, WorldPresetGeneratorRouting.DEFAULT,
                WorldPresetGeneratorRouting.NETHER);
        assertRoute(1, WorldPresetGeneratorRouting.ALPHA,
                WorldPresetGeneratorRouting.NETHER);
        assertRoute(5, WorldPresetGeneratorRouting.ALPHA,
                WorldPresetGeneratorRouting.NETHER);
        assertRoute(2, WorldPresetGeneratorRouting.FLAT,
                WorldPresetGeneratorRouting.NETHER);
        assertRoute(3, WorldPresetGeneratorRouting.SKY_GENERATOR,
                WorldPresetGeneratorRouting.NETHER_SKY);
        assertRoute(6, WorldPresetGeneratorRouting.CLASSIC,
                WorldPresetGeneratorRouting.CLASSIC_NETHER);
        assertRoute(7, WorldPresetGeneratorRouting.INFDEV,
                WorldPresetGeneratorRouting.NETHER);

        // Unsupported legacy terrain ids retain the old default behavior.
        assertRoute(-99, WorldPresetGeneratorRouting.DEFAULT,
                WorldPresetGeneratorRouting.NETHER);
        assertRoute(4, WorldPresetGeneratorRouting.DEFAULT,
                WorldPresetGeneratorRouting.NETHER);
        assertRoute(999, WorldPresetGeneratorRouting.DEFAULT,
                WorldPresetGeneratorRouting.NETHER);
    }

    @Test
    public void rawPresetGraphRetainsUnboundModernNamesAndSkyDimensionType() {
        Map<ResourceLocation, WorldPresetDefinition> raw =
                WorldPresetDataBootstrap.rawPresets();
        assertTrue(raw.keySet().containsAll(WorldPresetDataBootstrap.REQUIRED_PRESETS));
        assertFalse(WorldPresetDataBootstrap.terrainBindings().containsValue(
                WorldPresetDataBootstrap.DEFAULT_1_1));
        assertFalse(WorldPresetDataBootstrap.terrainBindings().containsValue(
                WorldPresetDataBootstrap.LARGE_BIOMES));

        WorldPresetDefinition sky = raw.get(WorldPresetDataBootstrap.SKY);
        assertEquals(DimensionTypeDataBootstrap.SKY,
                sky.getDimension(WorldPresetGeneratorRouting.OVERWORLD)
                        .getDimensionType());
        assertEquals(WorldPresetGeneratorRouting.SKY_GENERATOR,
                sky.getDimension(WorldPresetGeneratorRouting.OVERWORLD)
                        .getGeneratorType());
        assertEquals(DimensionTypeDataBootstrap.THE_NETHER,
                sky.getDimension(WorldPresetGeneratorRouting.THE_NETHER)
                        .getDimensionType());
        assertEquals(WorldPresetGeneratorRouting.NETHER_SKY,
                sky.getDimension(WorldPresetGeneratorRouting.THE_NETHER)
                        .getGeneratorType());
    }

    @Test
    public void chunkGeneratorRegistryIsCompleteExplicitAndCanonical() {
        assertGenerator(WorldPresetGeneratorRouting.DEFAULT, ChunkProviderGenerate.class);
        assertGenerator(WorldPresetGeneratorRouting.NETHER, ChunkProviderHell.class);
        assertGenerator(WorldPresetGeneratorRouting.NETHER_SKY,
                ChunkProviderNetherSky.class);
        assertGenerator(WorldPresetGeneratorRouting.FLAT, ChunkProviderFlat.class);
        assertGenerator(WorldPresetGeneratorRouting.SKY_GENERATOR, ChunkProviderSky.class);
        assertGenerator(WorldPresetGeneratorRouting.CLASSIC, ChunkProviderClassic.class);
        assertGenerator(WorldPresetGeneratorRouting.CLASSIC_NETHER,
                ChunkProviderHellClassic.class);
        assertGenerator(WorldPresetGeneratorRouting.ALPHA, AlphaChunkProvider.class);
        assertGenerator(WorldPresetGeneratorRouting.INFDEV, InfdevChunkProvider.class);
        assertEquals(9, ChunkGeneratorTypeRegistryApi.size());
    }

    @Test
    public void customNamespaceIsRetainedAndUnknownGeneratorFailsBeforePublication()
            throws Exception {
        Map<ResourceLocation, WorldPresetDefinition> live =
                WorldPresetDataBootstrap.rawPresets();
        WorldPresetDefinition liveDefault =
                WorldPresetRegistryApi.get(WorldPresetDataBootstrap.DEFAULT);
        Path root = Files.createTempDirectory("mcose-world-preset-custom-");
        try {
            Path custom = root.resolve(
                    "data/example/worldgen/world_preset/moon.json");
            Files.createDirectories(custom.getParent());
            Files.write(custom, customPreset("minecraft:default")
                    .getBytes(StandardCharsets.UTF_8));
            Map<ResourceLocation, WorldPresetDefinition> decoded =
                    WorldPresetDataBootstrap.decodeForTests(
                            RegistryDataLoader.createLayeredProvider(root.toFile()));
            ResourceLocation customKey = new ResourceLocation("example", "moon");
            assertTrue(decoded.containsKey(customKey));
            assertEquals(customKey, decoded.get(customKey).getId());

            Files.write(custom, customPreset("example:reflective_generator")
                    .getBytes(StandardCharsets.UTF_8));
            try {
                WorldPresetDataBootstrap.decodeForTests(
                        RegistryDataLoader.createLayeredProvider(root.toFile()));
                fail("Unknown configured generator was accepted");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("worldgen/world_preset"));
            }
            assertSame(live, WorldPresetDataBootstrap.rawPresets());
            assertSame(liveDefault,
                    WorldPresetRegistryApi.get(WorldPresetDataBootstrap.DEFAULT));

            Files.write(custom, customPresetWithType(
                    "minecraft:nether", "minecraft:default")
                    .getBytes(StandardCharsets.UTF_8));
            try {
                WorldPresetDataBootstrap.decodeForTests(
                        RegistryDataLoader.createLayeredProvider(root.toFile()));
                fail("Non-canonical dimension type alias was accepted in preset data");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("canonical"));
            }
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void typedDimensionAndWorldPresetRegistriesAreInRuntimeWorldView() {
        RegistryBootstrap.initialize();
        RegistryAccess world = RegistryRuntime.current().worldAccess();
        RegistryView<DimensionTypeDefinition> dimensions = world.lookup(
                new ResourceLocation("minecraft", "dimension_type"));
        RegistryView<WorldPresetDefinition> presets = world.lookup(
                new ResourceLocation("minecraft", "worldgen/world_preset"));
        assertTrue(dimensions != null);
        assertTrue(presets != null);
        assertSame(DimensionTypeDataBootstrap.rawDefinitions().get(
                        DimensionTypeDataBootstrap.OVERWORLD),
                dimensions.get(DimensionTypeDataBootstrap.OVERWORLD));
        assertSame(WorldPresetDataBootstrap.rawPresets().get(
                        WorldPresetDataBootstrap.DEFAULT),
                presets.get(WorldPresetDataBootstrap.DEFAULT));
    }

    private static void assertRoute(
            int terrainType,
            ResourceLocation overworldGenerator,
            ResourceLocation netherGenerator) {
        assertEquals(overworldGenerator, WorldPresetGeneratorRouting.generatorKey(
                terrainType, WorldPresetGeneratorRouting.OVERWORLD));
        assertEquals(netherGenerator, WorldPresetGeneratorRouting.generatorKey(
                terrainType, WorldPresetGeneratorRouting.THE_NETHER));
        assertEquals(WorldPresetGeneratorRouting.SKY_GENERATOR,
                WorldPresetGeneratorRouting.generatorKey(
                        terrainType, WorldPresetGeneratorRouting.SKY));
    }

    private static void assertGenerator(ResourceLocation key, Class<?> expected) {
        assertSame(expected, ChunkGeneratorTypeRegistryApi.get(key));
        assertEquals(key, ChunkGeneratorTypeRegistryApi.getKey(expected));
    }

    private static String customPreset(String generator) {
        return customPresetWithType("minecraft:overworld", generator);
    }

    private static String customPresetWithType(String type, String generator) {
        return "{\"dimensions\":{\"minecraft:overworld\":{"
                + "\"type\":\"" + type + "\","
                + "\"generator\":{\"type\":\"" + generator + "\"}}}}";
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
