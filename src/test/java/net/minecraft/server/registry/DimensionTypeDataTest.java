package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.server.WorldProvider;
import net.minecraft.server.WorldProviderHell;
import net.minecraft.server.WorldProviderSky;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class DimensionTypeDataTest {
    @BeforeClass
    public static void initializeDimensionTypes() {
        DimensionTypeRegistryBootstrap.initialize();
    }

    @Test
    public void builtInMetadataAndLegacyProviderBridgeRemainExact() {
        Map<ResourceLocation, DimensionTypeDefinition> raw =
                DimensionTypeDataBootstrap.rawDefinitions();
        assertTrue(raw.keySet().containsAll(DimensionTypeDataBootstrap.REQUIRED_TYPES));

        DimensionTypeDefinition overworld = raw.get(DimensionTypeDataBootstrap.OVERWORLD);
        assertFalse(overworld.hasFixedTime());
        assertTrue(overworld.hasSkylight());
        assertFalse(overworld.hasCeiling());
        assertEquals(1.0D, overworld.getCoordinateScale(), 0.0D);
        assertEquals(0, overworld.getMinY());
        assertEquals(512, overworld.getHeight());
        assertEquals(512, overworld.getLogicalHeight());
        assertEquals(0.05F, overworld.getAmbientLight(), 0.0F);
        assertEquals(new ResourceLocation("minecraft", "uniform"),
                overworld.getMonsterSpawnLightLevel().getType());
        assertEquals(0, overworld.getMonsterSpawnLightLevel().getMinInclusive());
        assertEquals(7, overworld.getMonsterSpawnLightLevel().getMaxInclusive());
        assertEquals(15, overworld.getMonsterSpawnBlockLightLimit());
        assertEquals("overworld", overworld.getSkybox());
        assertEquals("default", overworld.getCardinalLight());

        DimensionTypeDefinition nether = raw.get(DimensionTypeDataBootstrap.THE_NETHER);
        assertTrue(nether.hasFixedTime());
        assertFalse(nether.hasSkylight());
        assertTrue(nether.hasCeiling());
        assertEquals(8.0D, nether.getCoordinateScale(), 0.0D);
        assertEquals("none", nether.getSkybox());
        assertEquals("nether", nether.getCardinalLight());

        DimensionTypeDefinition sky = raw.get(DimensionTypeDataBootstrap.SKY);
        assertFalse("Sky metadata must describe the visible progressing client clock",
                sky.hasFixedTime());
        assertTrue(sky.hasSkylight());
        assertEquals("overworld", sky.getSkybox());

        // This is the old public provider bridge, not a reflective data factory.
        assertSame(WorldProvider.class,
                DimensionTypeRegistryApi.get(DimensionTypeDataBootstrap.OVERWORLD));
        assertSame(WorldProviderHell.class,
                DimensionTypeRegistryApi.get(DimensionTypeDataBootstrap.THE_NETHER));
        assertSame(WorldProviderHell.class,
                DimensionTypeRegistryApi.getByIdentifier("minecraft:nether"));
        assertSame(WorldProviderSky.class,
                DimensionTypeRegistryApi.get(DimensionTypeDataBootstrap.SKY));
        assertEquals(DimensionTypeDataBootstrap.THE_NETHER,
                DimensionTypeRegistryApi.getKeyByDimensionId(-1));
        assertEquals(DimensionTypeDataBootstrap.OVERWORLD,
                DimensionTypeRegistryApi.getKeyByDimensionId(0));
        assertEquals(DimensionTypeDataBootstrap.SKY,
                DimensionTypeRegistryApi.getKeyByDimensionId(1));
        assertEquals(Integer.valueOf(-1),
                DimensionTypeRegistryApi.getDimensionId("minecraft:nether"));
        assertEquals(Integer.valueOf(0),
                DimensionTypeRegistryApi.getDimensionId(WorldProvider.class));
        assertSame(nether,
                DimensionTypeRegistryApi.getDefinitionByIdentifier("minecraft:nether"));
    }

    @Test
    public void numericIdCollisionCannotPartiallyPublishProviderBridge() {
        ResourceLocation rejected = new ResourceLocation("test", "id_collision");
        assertFalse(DimensionTypeRegistryApi.registerDimensionId(
                0, rejected, CollisionProvider.class));
        assertEquals(null, DimensionTypeRegistryApi.get(rejected));
        assertSame(WorldProvider.class, DimensionTypeRegistryApi.getByDimensionId(0));
    }

    @Test
    public void codecUsesExact263VerticalBoundsAndConstructorChecks() {
        JsonObject objectConstant = validDimension();
        objectConstant.add("monster_spawn_light_level",
                JsonParser.parseString("{\"type\":\"minecraft:constant\",\"value\":7}"));
        DimensionTypeDefinition constant = DimensionTypeCodec.decode(
                new ResourceLocation("test", "constant"), objectConstant);
        assertTrue(constant.getMonsterSpawnLightLevel().isConstant());
        assertEquals(7, constant.getMonsterSpawnLightLevel().getMinInclusive());

        JsonObject boundary = validDimension();
        boundary.addProperty("min_y", -2032);
        boundary.addProperty("height", 4064);
        boundary.addProperty("logical_height", 4064);
        DimensionTypeDefinition accepted = DimensionTypeCodec.decode(
                new ResourceLocation("test", "boundary"), boundary);
        assertEquals(-2032, accepted.getMinY());
        assertEquals(4064, accepted.getHeight());

        JsonObject tooTall = validDimension();
        tooTall.addProperty("height", 4080);
        assertDecodeFails(tooTall, "height");

        JsonObject tooLow = validDimension();
        tooLow.addProperty("min_y", -2048);
        assertDecodeFails(tooLow, "min_y");

        JsonObject sumAboveMaximum = validDimension();
        sumAboveMaximum.addProperty("min_y", 16);
        sumAboveMaximum.addProperty("height", 2032);
        sumAboveMaximum.addProperty("logical_height", 128);
        assertDecodeFails(sumAboveMaximum, "min_y + height");

        JsonObject nonSectionHeight = validDimension();
        nonSectionHeight.addProperty("height", 17);
        assertDecodeFails(nonSectionHeight, "multiple of 16");

        JsonObject nonSectionMinimum = validDimension();
        nonSectionMinimum.addProperty("min_y", 1);
        assertDecodeFails(nonSectionMinimum, "multiple of 16");

        JsonObject logicalAboveHeight = validDimension();
        logicalAboveHeight.addProperty("logical_height", 144);
        assertDecodeFails(logicalAboveHeight, "logical_height");
    }

    @Test
    public void customNamespaceIsRetainedAndMalformedDataCannotTouchLiveGeneration()
            throws Exception {
        Map<ResourceLocation, DimensionTypeDefinition> live =
                DimensionTypeDataBootstrap.rawDefinitions();
        DimensionTypeDefinition liveOverworld =
                DimensionTypeRegistryApi.getDefinition(DimensionTypeDataBootstrap.OVERWORLD);
        Path root = Files.createTempDirectory("mcose-dimension-type-custom-");
        try {
            Path custom = root.resolve("data/example/dimension_type/moon.json");
            Files.createDirectories(custom.getParent());
            Files.write(custom, validDimension().toString().getBytes(StandardCharsets.UTF_8));
            Map<ResourceLocation, DimensionTypeDefinition> decoded =
                    DimensionTypeDataBootstrap.decodeForTests(
                            RegistryDataLoader.createLayeredProvider(root.toFile()));
            ResourceLocation customKey = new ResourceLocation("example", "moon");
            assertTrue(decoded.containsKey(customKey));
            assertEquals(customKey, decoded.get(customKey).getId());

            Files.write(custom, "{\"has_skylight\":true}"
                    .getBytes(StandardCharsets.UTF_8));
            try {
                DimensionTypeDataBootstrap.decodeForTests(
                        RegistryDataLoader.createLayeredProvider(root.toFile()));
                fail("Malformed configured dimension type was accepted");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("dimension_type"));
            }
            assertSame(live, DimensionTypeDataBootstrap.rawDefinitions());
            assertSame(liveOverworld,
                    DimensionTypeRegistryApi.getDefinition(
                            DimensionTypeDataBootstrap.OVERWORLD));
        } finally {
            deleteTree(root);
        }
    }

    private static JsonObject validDimension() {
        return JsonParser.parseString("{"
                + "\"has_fixed_time\":false,"
                + "\"has_skylight\":true,"
                + "\"has_ceiling\":false,"
                + "\"has_ender_dragon_fight\":false,"
                + "\"coordinate_scale\":1.0,"
                + "\"min_y\":0,"
                + "\"height\":128,"
                + "\"logical_height\":128,"
                + "\"infiniburn\":\"#minecraft:infiniburn_overworld\","
                + "\"ambient_light\":0.05,"
                + "\"monster_spawn_light_level\":{"
                + "\"type\":\"minecraft:uniform\","
                + "\"min_inclusive\":0,\"max_inclusive\":7},"
                + "\"monster_spawn_block_light_limit\":15,"
                + "\"skybox\":\"overworld\","
                + "\"cardinal_light\":\"default\"}"
        ).getAsJsonObject();
    }

    private static void assertDecodeFails(JsonObject json, String messagePart) {
        try {
            DimensionTypeCodec.decode(
                    new ResourceLocation("test", "invalid"), json);
            fail("Expected invalid dimension type to be rejected: " + json);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(messagePart));
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

    private static final class CollisionProvider {}
}
