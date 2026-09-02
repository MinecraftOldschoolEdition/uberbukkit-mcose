package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import net.minecraft.server.Block;
import net.minecraft.server.MapGenCaves;
import net.minecraft.server.MapGenCavesHell;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class ConfiguredCarverDataTest {
    @BeforeClass
    public static void initializeRegistries() {
        ConfiguredCarverDataBootstrap.initialize();
    }

    @Test
    public void codeTypesAndConfiguredDefinitionsAreSeparateRegistries() {
        assertEquals(2, CarverRegistryApi.size());
        assertNotNull(CarverRegistryApi.get(ConfiguredCarverCodec.CAVE));
        assertNotNull(CarverRegistryApi.get(ConfiguredCarverCodec.NETHER_CAVE));
        assertNull(CarverRegistryApi.get(ConfiguredCarverDataBootstrap.SKY_CAVE));

        assertEquals(3, ConfiguredCarverRegistryApi.size());
        ConfiguredCarverDefinition cave = ConfiguredCarverDataBootstrap.get(
                ConfiguredCarverDataBootstrap.CAVE);
        ConfiguredCarverDefinition nether = ConfiguredCarverDataBootstrap.get(
                ConfiguredCarverDataBootstrap.NETHER_CAVE);
        ConfiguredCarverDefinition sky = ConfiguredCarverDataBootstrap.get(
                ConfiguredCarverDataBootstrap.SKY_CAVE);
        assertSame(cave, Registries.CONFIGURED_CARVER.get(cave.getId()));
        assertSame(nether, Registries.CONFIGURED_CARVER.get(nether.getId()));
        assertEquals(ConfiguredCarverCodec.CAVE, sky.getType());
        assertTrue(cave.create() instanceof MapGenCaves);
        assertTrue(nether.create() instanceof MapGenCavesHell);
        assertEquals(3, cave.getReplaceableBlocks().size());
        assertEquals(2, cave.getAvoidedFluids().size());
        assertTrue(cave.isReplaceable((byte)Block.STONE.id));
        assertTrue(cave.isReplaceable((byte)Block.DIRT.id));
        assertTrue(cave.isReplaceable((byte)Block.GRASS.id));
        assertFalse(cave.isReplaceable((byte)Block.SAND.id));
        assertTrue(cave.isAvoidedFluid((byte)Block.WATER.id));
        assertTrue(cave.isAvoidedFluid((byte)Block.STATIONARY_WATER.id));
        assertFalse(cave.isAvoidedFluid((byte)Block.LAVA.id));
        assertTrue(nether.isReplaceable((byte)Block.NETHERRACK.id));
        assertTrue(nether.isAvoidedFluid((byte)Block.LAVA.id));
        assertTrue(nether.isAvoidedFluid((byte)Block.STATIONARY_LAVA.id));
        assertFalse(nether.isAvoidedFluid((byte)Block.STATIONARY_WATER.id));
    }

    @Test
    public void codecRejectsUnknownFieldsAndNoncanonicalBlockReferences() {
        ResourceLocation key = new ResourceLocation("example", "strict_cave");
        ConfiguredCarverDefinition decoded = ConfiguredCarverCodec.decode(
                key, overworldJson());
        assertEquals(key, decoded.getId());
        assertEquals(new ResourceLocation("minecraft", "grass_block"),
                decoded.getReplaceableBlocks().get(2));

        JsonObject unknown = overworldJson();
        unknown.addProperty("typo", true);
        expectFailure(key, unknown, "unsupported field");

        JsonObject alias = overworldJson();
        alias.getAsJsonArray("replaceable_blocks").set(
                0, new com.google.gson.JsonPrimitive("minecraft:hellrock"));
        expectFailure(key, alias, "canonical registered block");

        JsonObject duplicate = overworldJson();
        duplicate.getAsJsonArray("avoided_fluids").add("minecraft:water");
        expectFailure(key, duplicate, "duplicate");

        JsonObject shortRange = overworldJson();
        shortRange.addProperty("range", 1);
        expectFailure(key, shortRange, "range");

        JsonObject zeroOffset = overworldJson();
        zeroOffset.getAsJsonObject("y").addProperty("offset", 0);
        expectFailure(key, zeroOffset, "offset");

        JsonObject highY = overworldJson();
        highY.getAsJsonObject("y").addProperty("bound", 129);
        expectFailure(key, highY, "height bound");
    }

    @Test
    public void layeredDefinitionsStageWithoutPublishingPartialFailures()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-server-configured-carvers-");
        ResourceLocation custom = new ResourceLocation("example", "deep_cave");
        try {
            JsonObject customJson = overworldJson();
            customJson.addProperty("range", 12);
            write(root, custom, customJson);
            Map<ResourceLocation, ConfiguredCarverDefinition> staged =
                    ConfiguredCarverDataBootstrap.loadForTests(
                            RegistryDataLoader.createLayeredProvider(root.toFile()));
            assertEquals(4, staged.size());
            assertEquals(12, staged.get(custom).getRange());

            Map<ResourceLocation, ConfiguredCarverDefinition> live =
                    ConfiguredCarverDataBootstrap.rawDefinitions();
            ConfiguredCarverDefinition liveCave =
                    ConfiguredCarverDataBootstrap.get(
                            ConfiguredCarverDataBootstrap.CAVE);
            JsonObject invalid = overworldJson();
            invalid.getAsJsonArray("avoided_fluids").set(
                    0, new com.google.gson.JsonPrimitive(
                            "minecraft:not_a_registered_block"));
            write(root, ConfiguredCarverDataBootstrap.CAVE, invalid);
            try {
                ConfiguredCarverDataBootstrap.loadForTests(
                        RegistryDataLoader.createLayeredProvider(root.toFile()));
                fail("Malformed staged carver data must fail atomically");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("minecraft:cave"));
            }
            assertSame(live, ConfiguredCarverDataBootstrap.rawDefinitions());
            assertSame(liveCave, ConfiguredCarverDataBootstrap.get(
                    ConfiguredCarverDataBootstrap.CAVE));
            assertNull(ConfiguredCarverRegistryApi.get(custom));
        } finally {
            deleteTree(root);
        }
    }

    private static void expectFailure(
            ResourceLocation key, JsonObject json, String messagePart) {
        try {
            ConfiguredCarverCodec.decode(key, json);
            fail("Expected configured-carver decoding to fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains(messagePart));
        }
    }

    private static JsonObject overworldJson() {
        return new JsonParser().parse("{"
                + "\"type\":\"minecraft:cave\","
                + "\"range\":8,"
                + "\"count\":{\"type\":\"minecraft:legacy_nested_random\",\"bound\":40},"
                + "\"start_chance\":{\"type\":\"minecraft:legacy_one_in\",\"bound\":15},"
                + "\"y\":{\"type\":\"minecraft:legacy_nested_random\",\"bound\":120,\"offset\":8},"
                + "\"tunnel_thickness_multiplier\":1.0,"
                + "\"horizontal_radius_multiplier\":1.0,"
                + "\"room_vertical_radius_multiplier\":0.5,"
                + "\"vertical_radius_multiplier\":1.0,"
                + "\"floor_level\":-0.7,"
                + "\"maximum_carve_y\":120,"
                + "\"lava_level\":10,"
                + "\"replaceable_blocks\":[\"minecraft:stone\",\"minecraft:dirt\",\"minecraft:grass_block\"],"
                + "\"avoided_fluids\":[\"minecraft:water\",\"minecraft:water_still\"]"
                + "}").getAsJsonObject();
    }

    private static void write(
            Path root, ResourceLocation key, JsonObject json) throws Exception {
        Path file = root.resolve("data").resolve(key.getNamespace())
                .resolve("worldgen").resolve("carver")
                .resolve(key.getPath() + ".json");
        Files.createDirectories(file.getParent());
        Files.write(file, json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
        }
    }
}
