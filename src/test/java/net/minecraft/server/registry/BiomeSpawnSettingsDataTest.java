package net.minecraft.server.registry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.server.BiomeBase;
import net.minecraft.server.BiomeMeta;
import net.minecraft.server.Block;
import net.minecraft.server.EntityChicken;
import net.minecraft.server.EntityCow;
import net.minecraft.server.EntityCreeper;
import net.minecraft.server.EntityGhast;
import net.minecraft.server.EntityPig;
import net.minecraft.server.EntityPigZombie;
import net.minecraft.server.EntitySheep;
import net.minecraft.server.EntitySkeleton;
import net.minecraft.server.EntitySlime;
import net.minecraft.server.EntitySpider;
import net.minecraft.server.EntitySquid;
import net.minecraft.server.EntityWolf;
import net.minecraft.server.EntityZombie;
import net.minecraft.server.EnumCreatureType;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;
import uk.betacraft.uberbukkit.UberbukkitConfig;

public class BiomeSpawnSettingsDataTest {
    @BeforeClass
    public static void initializeData() {
        assertTrue(Block.STONE != null);
        EntityTypeRegistryBootstrap.initialize();
        BiomeRegistryBootstrap.initialize();
        BiomeSpawnSettingsBootstrap.initialize();
    }

    @Test
    public void rawBuiltInMatrixPreservesEveryLegacyEntryOrderWeightAndCount() {
        Map<ResourceLocation, BiomeSpawnSettings> raw =
                BiomeSpawnSettingsBootstrap.rawTables();
        assertTrue(raw.keySet().containsAll(BiomeSpawnSettingsBootstrap.REQUIRED_BIOMES));

        String[] ordinary = new String[] {
                "rainforest", "swampland", "seasonal_forest", "forest",
                "savanna", "shrubland", "taiga", "desert", "plains",
                "ice_desert", "tundra"
        };
        for (int i = 0; i < ordinary.length; i++) {
            BiomeSpawnSettings settings = require(raw, ordinary[i]);
            assertEntries(settings.getSpawns(BiomeSpawnSettings.MONSTER),
                    spawn(EntitySpider.class, "spider", 4, 10),
                    spawn(EntityZombie.class, "zombie", 4, 10),
                    spawn(EntitySkeleton.class, "skeleton", 4, 10),
                    spawn(EntityCreeper.class, "creeper", 4, 10),
                    spawn(EntitySlime.class, "slime", 4, 10));
            if ("forest".equals(ordinary[i]) || "taiga".equals(ordinary[i])) {
                assertEntries(settings.getSpawns(BiomeSpawnSettings.CREATURE),
                        spawn(EntitySheep.class, "sheep", 4, 12),
                        spawn(EntityPig.class, "pig", 4, 10),
                        spawn(EntityChicken.class, "chicken", 4, 10),
                        spawn(EntityCow.class, "cow", 4, 8),
                        spawn(EntityWolf.class, "wolf", 8, 2));
            } else {
                assertEntries(settings.getSpawns(BiomeSpawnSettings.CREATURE),
                        spawn(EntitySheep.class, "sheep", 4, 12),
                        spawn(EntityPig.class, "pig", 4, 10),
                        spawn(EntityChicken.class, "chicken", 4, 10),
                        spawn(EntityCow.class, "cow", 4, 8));
            }
            assertEntries(settings.getSpawns(BiomeSpawnSettings.WATER_CREATURE),
                    spawn(EntitySquid.class, "squid", 4, 10));
        }

        BiomeSpawnSettings hell = require(raw, "hell");
        assertEntries(hell.getSpawns(BiomeSpawnSettings.MONSTER),
                spawn(EntityGhast.class, "ghast", 1, 10),
                spawn(EntityPigZombie.class, "zombified_piglin", 4, 10));
        assertTrue(hell.getSpawns(BiomeSpawnSettings.CREATURE).isEmpty());
        assertTrue(hell.getSpawns(BiomeSpawnSettings.WATER_CREATURE).isEmpty());

        BiomeSpawnSettings sky = require(raw, "sky");
        assertTrue(sky.getSpawns(BiomeSpawnSettings.MONSTER).isEmpty());
        assertEntries(sky.getSpawns(BiomeSpawnSettings.CREATURE),
                spawn(EntityChicken.class, "chicken", 4, 10));
        assertTrue(sky.getSpawns(BiomeSpawnSettings.WATER_CREATURE).isEmpty());
    }

    @Test
    public void strictCodecRejectsUnsupportedShapesTypesCategoriesAndNumbers() {
        ResourceLocation key = new ResourceLocation("test", "strict");
        BiomeSpawnSettings duplicate = BiomeSpawnSettingsCodec.decode(
                key, JsonParser.parseString(projection(
                        entry("minecraft:spider", 4, 10)
                                + "," + entry("minecraft:spider", 4, 1),
                        "", "")).getAsJsonObject());
        assertEquals(2, duplicate.getSpawns(BiomeSpawnSettings.MONSTER).size());

        assertDecodeFails("{}");
        assertDecodeFails(projection(entry("minecraft:spider", 4, 10), "", "")
                .replace("{\"attributes\":", "{\"extra\":0,\"attributes\":"));
        assertDecodeFails(projection(entry("minecraft:spider", 4, 10), "", "")
                .replace("\"overlay\"", "\"replace\""));
        assertDecodeFails(projection(entry("minecraft:spider", 4, 10), "", "")
                .replace("\"spawn_costs\":{}", "\"spawn_costs\":{\"x\":1}"));
        assertDecodeFails(projection(entry("minecraft:spider", 4, 10), "", "")
                .replace(",\"water_creature\":[]", ""));
        assertDecodeFails(projection(entry("minecraft:spider", 4, 10), "", "")
                .replace("\"water_creature\":[]", "\"ambient\":[]"));
        assertDecodeFails(projection(entry("minecraft:cow", 4, 10), "", ""));
        assertDecodeFails(projection("", entry("minecraft:squid", 4, 10), ""));
        assertDecodeFails(projection("", "", entry("minecraft:spider", 4, 10)));
        assertDecodeFails(projection(entry("minecraft:pigzombie", 4, 10), "", ""));
        assertDecodeFails(projection(entry("minecraft:not_registered", 4, 10), "", ""));
        assertDecodeFails(projection(rawEntry("minecraft:spider", "0", "10"), "", ""));
        assertDecodeFails(projection(rawEntry("minecraft:spider", "1.5", "10"), "", ""));
        assertDecodeFails(projection(rawEntry("minecraft:spider", "2147483648", "10"), "", ""));
        assertDecodeFails(projection(rawEntry("minecraft:spider",
                "{\"type\":\"minecraft:constant\",\"value\":4}", "10"), "", ""));
        assertDecodeFails(projection(rawEntry("minecraft:spider", "4", "-1"), "", ""));
        assertDecodeFails(projection(rawEntry("minecraft:spider", "4", "2147483648"), "", ""));
        assertDecodeFails(projection(
                rawEntry("minecraft:spider", "4", "2147483647") + ","
                        + rawEntry("minecraft:zombie", "4", "1"), "", ""));
        assertDecodeFails(projection(entry("minecraft:spider", 4, 10)
                .replace("}", ",\"extra\":0}"), "", ""));
    }

    @Test
    public void startupConfigMatrixFiltersOnlySquidSlimeAndWolfFromEffectiveView() {
        BiomeSpawnSettings forest = require(
                BiomeSpawnSettingsBootstrap.rawTables(), "forest");
        int rawMonsterCount = forest.getSpawns(BiomeSpawnSettings.MONSTER).size();
        int rawCreatureCount = forest.getSpawns(BiomeSpawnSettings.CREATURE).size();
        int rawWaterCount = forest.getSpawns(BiomeSpawnSettings.WATER_CREATURE).size();

        for (int mask = 0; mask < 8; mask++) {
            boolean squids = (mask & 1) != 0;
            boolean slimes = (mask & 2) != 0;
            boolean wolves = (mask & 4) != 0;
            Map<String, List<BiomeMeta>> effective =
                    BiomeSpawnSettingsBootstrap.deriveEffectiveForTests(
                            forest, squids, slimes, wolves);
            assertEffectiveClasses(effective.get(BiomeSpawnSettings.MONSTER),
                    slimes, EntitySlime.class);
            assertEffectiveClasses(effective.get(BiomeSpawnSettings.CREATURE),
                    wolves, EntityWolf.class);
            assertEffectiveClasses(effective.get(BiomeSpawnSettings.WATER_CREATURE),
                    squids, EntitySquid.class);
            assertEquals(rawMonsterCount, forest.getSpawns(BiomeSpawnSettings.MONSTER).size());
            assertEquals(rawCreatureCount, forest.getSpawns(BiomeSpawnSettings.CREATURE).size());
            assertEquals(rawWaterCount, forest.getSpawns(BiomeSpawnSettings.WATER_CREATURE).size());
        }
    }

    @Test
    public void publishedEffectiveViewIsImmutableAndConfigChangesRequireRestart() {
        List before = BiomeBase.FOREST.a(EnumCreatureType.MONSTER);
        UberbukkitConfig config = UberbukkitConfig.getInstance();
        Object previous = config.getProperty("mechanics.spawn_slimes");
        try {
            config.setProperty("mechanics.spawn_slimes", Boolean.valueOf(
                    !config.getBoolean("mechanics.spawn_slimes", true)));
            assertSame(before, BiomeBase.FOREST.a(EnumCreatureType.MONSTER));
            try {
                before.add(new BiomeMeta(EntityZombie.class, 1));
                fail("Bound effective spawn list was mutable");
            } catch (UnsupportedOperationException expected) {
            }
        } finally {
            config.setProperty("mechanics.spawn_slimes", previous);
        }
    }

    @Test
    public void aliasesSharePrimaryTablesAndNoAliasResourcesAreRequired() {
        assertSame(BiomeBase.PLAINS,
                BiomeRegistryApi.get(new ResourceLocation("minecraft", "alpha_plains")));
        assertSame(BiomeBase.PLAINS,
                BiomeRegistryApi.get(new ResourceLocation("minecraft", "flat_plains")));
        assertSame(BiomeBase.PLAINS,
                BiomeRegistryApi.get(new ResourceLocation("minecraft", "classic_plains")));
        assertSame(BiomeBase.TAIGA,
                BiomeRegistryApi.get(new ResourceLocation("minecraft", "alpha_taiga")));
        assertEquals(new ResourceLocation("minecraft", "plains"),
                BiomeRegistryApi.getKey(BiomeBase.PLAINS));
        assertEquals(new ResourceLocation("minecraft", "taiga"),
                BiomeRegistryApi.getKey(BiomeBase.TAIGA));
        Map<ResourceLocation, ResourceLocation> bindings =
                BiomeSpawnSettingsBootstrap.primaryBindings();
        assertEquals(new ResourceLocation("minecraft", "plains"),
                bindings.get(new ResourceLocation("minecraft", "plains")));
        assertEquals(new ResourceLocation("minecraft", "taiga"),
                bindings.get(new ResourceLocation("minecraft", "taiga")));
        assertFalse(bindings.containsKey(new ResourceLocation("minecraft", "alpha_plains")));
        assertFalse(bindings.containsKey(new ResourceLocation("minecraft", "alpha_taiga")));
    }

    @Test
    public void customNamespaceRawTableIsRetainedEvenWithoutRegisteredBiome()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-biome-spawn-custom-");
        try {
            Path custom = root.resolve("data/example/worldgen/biome/moon.json");
            Files.createDirectories(custom.getParent());
            Files.write(custom, projection(
                    entry("minecraft:spider", 4, 10), "", "")
                    .getBytes(StandardCharsets.UTF_8));
            Map<ResourceLocation, BiomeSpawnSettings> decoded =
                    BiomeSpawnSettingsBootstrap.decodeForTests(
                            RegistryDataLoader.createLayeredProvider(root.toFile()));
            ResourceLocation customKey = new ResourceLocation("example", "moon");
            assertTrue(decoded.containsKey(customKey));
            assertEquals(customKey, decoded.get(customKey).getId());
            assertEquals(1, decoded.get(customKey)
                    .getSpawns(BiomeSpawnSettings.MONSTER).size());
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void serverAndStandaloneBiomeJsonAreByteIdentical() throws Exception {
        Path checkout = findCheckoutRoot();
        Path standalone = checkout.getParent().resolve("resourcepack");
        for (int i = 0; i < BiomeSpawnSettingsBootstrap.REQUIRED_BIOMES.size(); i++) {
            String path = BiomeSpawnSettingsBootstrap.REQUIRED_BIOMES.get(i).getPath();
            Path server = checkout.resolve(
                    "src/main/resources/data/minecraft/worldgen/biome/" + path + ".json");
            Path pack = standalone.resolve(
                    "data/minecraft/worldgen/biome/" + path + ".json");
            assertTrue("Missing server biome data " + server, Files.isRegularFile(server));
            assertTrue("Missing standalone biome data " + pack, Files.isRegularFile(pack));
            assertArrayEquals(path, Files.readAllBytes(pack), Files.readAllBytes(server));
        }
    }

    private static BiomeSpawnSettings require(
            Map<ResourceLocation, BiomeSpawnSettings> raw, String path) {
        BiomeSpawnSettings settings = raw.get(new ResourceLocation("minecraft", path));
        assertTrue("Missing biome spawn table " + path, settings != null);
        return settings;
    }

    private static ExpectedSpawn spawn(
            Class<?> entityClass, String path, int count, int weight) {
        return new ExpectedSpawn(entityClass,
                new ResourceLocation("minecraft", path), count, weight);
    }

    private static void assertEntries(
            List<BiomeSpawnSettings.SpawnEntry> actual, ExpectedSpawn... expected) {
        assertEquals(expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            BiomeSpawnSettings.SpawnEntry entry = actual.get(i);
            assertEquals(expected[i].entityKey, entry.getEntityType());
            assertSame(expected[i].entityClass, entry.getEntityClass());
            assertEquals(expected[i].count, entry.getCount());
            assertEquals(expected[i].weight, entry.getWeight());
        }
    }

    private static void assertEffectiveClasses(
            List<BiomeMeta> entries, boolean specialPresent, Class<?> special) {
        boolean found = false;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).a == special) found = true;
        }
        assertEquals(specialPresent, found);
    }

    private static String projection(String monster, String creature, String water) {
        return "{\"attributes\":{\"minecraft:gameplay/natural_mob_spawns\":{"
                + "\"modifier\":\"overlay\",\"argument\":{\"spawns_by_category\":{"
                + "\"monster\":[" + monster + "],"
                + "\"creature\":[" + creature + "],"
                + "\"water_creature\":[" + water + "]},\"spawn_costs\":{}}}}}";
    }

    private static String entry(String type, int count, int weight) {
        return rawEntry(type, Integer.toString(count), Integer.toString(weight));
    }

    private static String rawEntry(String type, String count, String weight) {
        return "{\"type\":\"" + type + "\",\"count\":" + count
                + ",\"weight\":" + weight + "}";
    }

    private static void assertDecodeFails(String json) {
        try {
            BiomeSpawnSettingsCodec.decode(
                    new ResourceLocation("test", "invalid"),
                    JsonParser.parseString(json).getAsJsonObject());
            fail("Expected invalid biome spawn data to be rejected: " + json);
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }

    private static Path findCheckoutRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve("build.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) throw new AssertionError("Could not locate server checkout");
        return current;
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

    private static final class ExpectedSpawn {
        private final Class<?> entityClass;
        private final ResourceLocation entityKey;
        private final int count;
        private final int weight;

        private ExpectedSpawn(
                Class<?> entityClass, ResourceLocation entityKey, int count, int weight) {
            this.entityClass = entityClass;
            this.entityKey = entityKey;
            this.count = count;
            this.weight = weight;
        }
    }
}
