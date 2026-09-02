package net.minecraft.server.registry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.server.Block;
import net.minecraft.server.Material;
import net.minecraft.server.World;
import net.minecraft.server.WorldChunkManager;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

/** Pins the modern data shape to the exact Beta 1.7.3 snow-only pass. */
public class LegacyFreezeTopLayerFeatureParityTest {
    private static final int BASE_X = -24;
    private static final int BASE_Z = 40;

    @BeforeClass
    public static void initializeFeatureData() {
        assertNotNull(Block.SNOW);
        PlacedFeatureDataBootstrap.initialize();
    }

    @Test
    public void dataUsesTheStrictModernNoConfigAndBiomeOnlyShapes() {
        ConfiguredFeatureDefinition configured =
                ConfiguredFeatureDataBootstrap.get(
                        ConfiguredFeatureDataBootstrap.FREEZE_TOP_LAYER);
        PlacedFeatureDefinition placed = PlacedFeatureDataBootstrap.get(
                PlacedFeatureDataBootstrap.FREEZE_TOP_LAYER);

        assertNotNull(configured);
        assertEquals(ConfiguredFeatureCodec.FREEZE_TOP_LAYER,
                configured.getType());
        assertFalse(configured.hasOreConfiguration());
        assertFalse(configured.hasSpringConfiguration());
        assertFalse(configured.hasLakeConfiguration());
        assertNotNull(placed);
        assertEquals(ConfiguredFeatureDataBootstrap.FREEZE_TOP_LAYER,
                placed.getFeature());
        assertTrue(placed.hasOnlyBiomeFilter());
        assertEquals(0, placed.getCount());
        assertNull(placed.getHeight());
        assertFalse(placed.hasRarityFilter());
        assertFalse(placed.hasBlockPredicateFilter());
        assertFalse(placed.hasLegacyLavaLakeFilter());

        assertConfiguredCodecRejects(
                "{\"type\":\"minecraft:freeze_top_layer\",\"extra\":true}");
        assertPlacedCodecRejects(
                "{\"feature\":\"minecraft:freeze_top_layer\","
                        + "\"placement\":[{\"type\":\"minecraft:biome\"},"
                        + "{\"type\":\"minecraft:in_square\"}]}");
        assertPlacedCodecRejects(
                "{\"feature\":\"minecraft:freeze_top_layer\","
                        + "\"placement\":[{\"type\":\"minecraft:biome\","
                        + "\"extra\":true}]}");
    }

    @Test
    public void executorMatchesTheXMajorBetaOracleAndNeverFreezesWater()
            throws Exception {
        RecordingWorld world = RecordingWorld.create();
        double[] scratch = new double[256];
        Arrays.fill(world.manager.source, 1.0D);
        Arrays.fill(world.topY, 64);
        Arrays.fill(world.air, true);
        Arrays.fill(world.support, Material.EARTH);

        world.manager.source[index(0, 0)] = 0.49D;
        world.manager.source[index(0, 1)] = 0.5D;
        world.manager.source[index(0, 2)] = -10.0D;
        world.topY[index(0, 2)] = 0;
        world.manager.source[index(0, 3)] = -10.0D;
        world.topY[index(0, 3)] = 128;
        world.manager.source[index(0, 4)] = -10.0D;
        world.air[index(0, 4)] = false;
        world.manager.source[index(0, 5)] = -10.0D;
        world.support[index(0, 5)] = Material.WATER;
        world.manager.source[index(0, 6)] = -10.0D;
        world.support[index(0, 6)] = Material.ICE;
        world.manager.source[index(0, 7)] = 0.6D;
        world.topY[index(0, 7)] = 96;

        List<String> expected = betaSnowOracle(world);
        Random untouched = new Random(918273645L);
        Random expectedUntouched = new Random(918273645L);
        double[] returned = LegacyPlacedFeatureExecutor.generateFreezeTopLayer(
                world, BASE_X, BASE_Z, scratch,
                PlacedFeatureDataBootstrap.FREEZE_TOP_LAYER);

        assertSame(scratch, returned);
        assertSame(scratch, world.manager.lastInput);
        assertEquals(1, world.manager.calls);
        assertEquals(BASE_X, world.manager.x);
        assertEquals(BASE_Z, world.manager.z);
        assertEquals(16, world.manager.width);
        assertEquals(16, world.manager.depth);
        assertEquals(expectedUntouched.nextLong(), untouched.nextLong());
        assertEquals(Arrays.asList(
                position(BASE_X, 64, BASE_Z),
                position(BASE_X, 96, BASE_Z + 7)), expected);
        assertEquals(expected, world.writes);
        assertFalse(world.writes.contains(position(BASE_X, 64, BASE_Z + 5)));
        assertEquals(256, world.topQueries.size());
        int query = 0;
        for (int xOffset = 0; xOffset < 16; xOffset++) {
            for (int zOffset = 0; zOffset < 16; zOffset++) {
                assertEquals(position(BASE_X + xOffset, -1,
                        BASE_Z + zOffset), world.topQueries.get(query++));
            }
        }
    }

    @Test
    public void defaultAndSkyProvidersKeepOrderScratchOwnershipAndZeroRng()
            throws Exception {
        Path sourceRoot = findCheckoutRoot().resolve(
                "src/main/java/net/minecraft/server");
        assertProviderRouting(read(sourceRoot.resolve(
                "ChunkProviderGenerate.java")));
        assertProviderRouting(read(sourceRoot.resolve(
                "ChunkProviderSky.java")));
    }

    @Test
    public void authoritativeClientAndServerJsonAreByteIdentical()
            throws Exception {
        Path server = findCheckoutRoot();
        Path developer = server.getParent();
        assertNotNull(developer);
        Path client = developer.resolve("client-source/minecraft/resources");
        Path standalone = developer.resolve("resourcepack");
        Path serverResources = server.resolve("src/main/resources");
        assertCopies(client, serverResources, standalone,
                "worldgen/feature",
                ConfiguredFeatureDataBootstrap.FREEZE_TOP_LAYER);
        assertCopies(client, serverResources, standalone,
                "worldgen/placed_feature",
                PlacedFeatureDataBootstrap.FREEZE_TOP_LAYER);
    }

    private static List<String> betaSnowOracle(RecordingWorld world) {
        List<String> expected = new ArrayList<String>();
        for (int xOffset = 0; xOffset < 16; xOffset++) {
            for (int zOffset = 0; zOffset < 16; zOffset++) {
                int cell = index(xOffset, zOffset);
                int topY = world.topY[cell];
                double adjusted = world.manager.source[cell]
                        - (double)(topY - 64) / 64.0D * 0.3D;
                Material support = world.support[cell];
                if (adjusted < 0.5D && topY > 0 && topY < 128
                        && world.air[cell] && support.isSolid()
                        && support != Material.ICE) {
                    expected.add(position(BASE_X + xOffset, topY,
                            BASE_Z + zOffset));
                }
            }
        }
        return expected;
    }

    private static void assertProviderRouting(String source) {
        int water = source.lastIndexOf(
                "PlacedFeatureDataBootstrap.SPRING_WATER");
        int lava = source.lastIndexOf(
                "PlacedFeatureDataBootstrap.SPRING_LAVA");
        int freeze = source.indexOf(
                "LegacyPlacedFeatureExecutor.generateFreezeTopLayer(", lava);
        int assignment = source.lastIndexOf("this.w =", freeze);
        int fallStart = source.lastIndexOf(
                "BlockSand.instaFall = true;", water);
        int fallStop = source.indexOf(
                "BlockSand.instaFall = false;", freeze);
        assertTrue(fallStart >= 0 && fallStart < water
                && water < lava && lava < assignment
                && assignment <= freeze && freeze < fallStop);

        String call = source.substring(assignment,
                source.indexOf(");", freeze) + 2);
        assertTrue(call.contains("this.w ="));
        assertTrue(call.contains("this.w,"));
        assertTrue(call.contains(
                "PlacedFeatureDataBootstrap.FREEZE_TOP_LAYER"));
        assertFalse(call.contains("this.j"));

        String decorationScope = source.substring(fallStart, fallStop);
        assertEquals(1, occurrences(decorationScope,
                "LegacyPlacedFeatureExecutor.generateFreezeTopLayer("));
        assertFalse(decorationScope.contains("getWorldChunkManager().a("));
        assertFalse(decorationScope.contains("\"minecraft:snow\""));
    }

    private static void assertConfiguredCodecRejects(String json) {
        try {
            ConfiguredFeatureCodec.decode(key("invalid_freeze"),
                    parseObject(json));
            fail("Invalid freeze-top-layer configuration was accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    private static void assertPlacedCodecRejects(String json) {
        try {
            PlacedFeatureCodec.decode(key("invalid_freeze_placed"),
                    parseObject(json));
            fail("Invalid freeze-top-layer placement was accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    private static JsonObject parseObject(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }

    private static void assertCopies(
            Path client,
            Path server,
            Path standalone,
            String registry,
            ResourceLocation key) throws Exception {
        String relative = "data/" + key.getNamespace() + "/" + registry
                + "/" + key.getPath() + ".json";
        byte[] expected = Files.readAllBytes(standalone.resolve(relative));
        assertArrayEquals(relative + " client mirror", expected,
                Files.readAllBytes(client.resolve(relative)));
        assertArrayEquals(relative + " server mirror", expected,
                Files.readAllBytes(server.resolve(relative)));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static int index(int xOffset, int zOffset) {
        return xOffset * 16 + zOffset;
    }

    private static String position(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findCheckoutRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("src/main/resources"))
                    && (Files.isRegularFile(current.resolve("build.gradle"))
                            || Files.isRegularFile(
                                    current.resolve("build.gradle.kts")))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate server checkout root");
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }

    private static final class RecordingManager extends WorldChunkManager {
        private final double[] source = new double[256];
        private double[] lastInput;
        private int calls;
        private int x;
        private int z;
        private int width;
        private int depth;

        public double[] a(
                double[] target, int x, int z, int width, int depth) {
            this.calls++;
            this.lastInput = target;
            this.x = x;
            this.z = z;
            this.width = width;
            this.depth = depth;
            if (target == null || target.length < width * depth) {
                target = new double[width * depth];
            }
            System.arraycopy(this.source, 0, target, 0, width * depth);
            return target;
        }
    }

    private static final class RecordingWorld extends World {
        private RecordingManager manager;
        private int[] topY;
        private boolean[] air;
        private Material[] support;
        private List<String> writes;
        private List<String> topQueries;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null,
                    org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld create() throws Exception {
            RecordingWorld world = (RecordingWorld)unsafe()
                    .allocateInstance(RecordingWorld.class);
            world.manager = new RecordingManager();
            world.topY = new int[256];
            world.air = new boolean[256];
            world.support = new Material[256];
            world.writes = new ArrayList<String>();
            world.topQueries = new ArrayList<String>();
            return world;
        }

        public WorldChunkManager getWorldChunkManager() {
            return this.manager;
        }

        public int e(int x, int z) {
            this.topQueries.add(position(x, -1, z));
            return this.topY[index(x - BASE_X, z - BASE_Z)];
        }

        public boolean isEmpty(int x, int y, int z) {
            return this.air[index(x - BASE_X, z - BASE_Z)];
        }

        public Material getMaterial(int x, int y, int z) {
            return this.support[index(x - BASE_X, z - BASE_Z)];
        }

        public boolean setBlockStateAndData(
                int x, int y, int z, String blockKey) {
            assertEquals("minecraft:snow", blockKey);
            this.writes.add(position(x, y, z));
            return true;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }
}
