package net.minecraft.server.registry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.Achievement;
import net.minecraft.server.AchievementManager;
import net.minecraft.server.Packet200Statistic;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class LegacyAdvancementDataTest {
    private static final String[] GOLDEN = new String[] {
        "open_inventory|0|-|0|0|book|340|0|false",
        "mine_wood|1|open_inventory|2|0|oak_log|17|0|false",
        "build_workbench|2|mine_wood|4|0|crafting_table|58|0|false",
        "build_pickaxe|3|build_workbench|4|2|wooden_pickaxe|270|0|false",
        "build_furnace|4|build_pickaxe|4|4|lit_furnace|62|0|false",
        "acquire_iron|5|build_furnace|4|6|iron_ingot|265|0|false",
        "build_better_pickaxe|9|build_pickaxe|6|3|stone_pickaxe|274|0|false",
        "build_hoe|6|build_workbench|2|-1|wooden_hoe|290|0|false",
        "make_bread|7|grow_wheat|-2|-2|bread|297|0|false",
        "bake_cake|8|grow_wheat|-1|-3|cake|354|0|true",
        "grow_wheat|36|build_hoe|0|-2|wheat|296|0|false",
        "cook_fish|10|build_furnace|6|4|cooked_cod|350|0|false",
        "cook_bacon|16|build_furnace|6|5|cooked_porkchop|320|0|false",
        "diamonds|17|acquire_iron|2|7|diamond|264|0|false",
        "obsidian|18|diamonds|0|9|obsidian|49|0|false",
        "hot_stuff|19|acquire_iron|4|8|lava_bucket|327|0|false",
        "portal|20|obsidian|-2|10|portal|90|0|true",
        "ghast_hunter|21|portal|-4|11|gunpowder|289|0|true",
        "blazing_hell|22|portal|-2|12|glowstone|89|0|false",
        "build_sword|12|build_workbench|6|-1|wooden_sword|268|0|false",
        "kill_enemy|13|build_sword|8|-2|bone|352|0|false",
        "kill_cow|14|build_sword|8|0|leather|334|0|false",
        "fly_pig|15|kill_enemy|10|-3|saddle|329|0|true",
        "overkill|39|diamonds|0|7|diamond_sword|276|0|true",
        "build_bow|23|build_workbench|4|-2|bow|261|0|false",
        "snipe_skeleton|24|build_bow|4|-4|arrow|262|0|true",
        "full_iron|25|acquire_iron|2|5|iron_chestplate|307|0|false",
        "full_diamond|26|diamonds|1|8|diamond_chestplate|311|0|true",
        "sleep_in_bed|27|shear_sheep|8|7|bed|355|0|false",
        "egg_hunt|37|kill_cow|10|0|egg|344|0|false",
        "shear_sheep|34|acquire_iron|6|7|shears|359|0|false",
        "rainbow_wool|35|shear_sheep|7|8|white_wool|35|14|true",
        "craft_map|28|build_workbench|6|1|map|358|0|false",
        "bookshelf|29|kill_cow|10|1|bookshelf|47|0|false",
        "jukebox|30|diamonds|3|8|jukebox|84|0|false",
        "explosion|31|kill_enemy|10|-1|tnt|46|0|false",
        "piston|32|repeater|2|4|piston|33|0|false",
        "repeater|33|acquire_iron|3|5|repeater|356|0|false",
        "on_a_rail|11|acquire_iron|6|6|rail|66|0|true",
        "boat_travel|38|build_workbench|2|1|boat|333|0|true"
    };
    private static final String[] TRANSLATION_NAMES = new String[] {
        "openInventory", "mineWood", "buildWorkBench", "buildPickaxe",
        "buildFurnace", "acquireIron", "buildBetterPickaxe", "buildHoe",
        "makeBread", "bakeCake", "growWheat", "cookFish", "cookBacon",
        "diamonds", "obsidian", "hotStuff", "portal", "ghastHunter",
        "blazingHell", "buildSword", "killEnemy", "killCow", "flyPig",
        "overkill", "buildBow", "snipeSkeleton", "fullIron", "fullDiamond",
        "sleepInBed", "eggHunt", "shearSheep", "rainbowWool", "craftMap",
        "bookshelf", "jukebox", "explosion", "piston", "repeater",
        "onARail", "boatTravel"
    };

    @BeforeClass
    public static void initialize() {
        BlockRegistryBootstrap.initialize();
        ItemRegistryBootstrap.initialize();
        AchievementRegistryBootstrap.initialize();
    }

    @Test
    public void allFortyDefinitionsOwnTheLiveLegacySemantics() {
        assertEquals(40, LegacyAdvancementDataBootstrap.definitions().size());
        for (int index = 0; index < GOLDEN.length; index++) {
            String row = GOLDEN[index];
            String[] expected = row.split("\\|");
            ResourceLocation key = key(expected[0]);
            LegacyAdvancementDefinition definition =
                    LegacyAdvancementDataBootstrap.get(key);
            Achievement achievement = AchievementRegistryApi.get(key);
            assertTrue("missing definition " + key, definition != null);
            assertTrue("missing achievement " + key, achievement != null);
            assertEquals(5242880 + Integer.parseInt(expected[1]), achievement.e);
            ResourceLocation expectedParent = "-".equals(expected[2])
                    ? null : key(expected[2]);
            assertEquals(expectedParent, definition.getParent());
            assertSame(expectedParent == null ? null : AchievementRegistryApi.get(expectedParent),
                    achievement.c);
            assertEquals(Integer.parseInt(expected[3]), achievement.a);
            assertEquals(Integer.parseInt(expected[4]), achievement.b);
            assertEquals(key(expected[5]), definition.getIcon());
            assertEquals(Integer.parseInt(expected[6]), definition.getIconLegacyId());
            assertEquals(Integer.parseInt(expected[7]), definition.getIconLegacyDamage());
            assertEquals(definition.getIconLegacyId(), achievement.d.id);
            assertEquals(definition.getIconLegacyDamage(), achievement.d.damage);
            assertEquals(Boolean.parseBoolean(expected[8]), achievement.isSpecial());
            assertEquals(achievement.isSpecial(), definition.isSpecial());
            assertEquals("achievement." + TRANSLATION_NAMES[index],
                    definition.getTitleTranslationKey());
            assertEquals("achievement." + TRANSLATION_NAMES[index] + ".desc",
                    definition.getDescriptionTranslationKey());
            assertTrue(achievement.shouldShowToast());
            assertTrue(achievement.shouldAnnounceToChat());
            assertFalse(achievement.isHidden());
            assertEquals(LegacyAdvancementCodec.LEGACY_TRIGGER,
                    definition.getCriteria().get("legacy_trigger"));
            assertEquals(1, definition.getRequirements().size());
            assertEquals("legacy_trigger", definition.getRequirements().get(0).get(0));
            assertSame(achievement,
                    AchievementManager.findAchievementByStatId(achievement.e));
        }
    }

    @Test
    public void rainbowWoolUsesWhiteWoolIdentityWithExactLegacyMetadata() {
        LegacyAdvancementDefinition rainbow =
                LegacyAdvancementDataBootstrap.get(key("rainbow_wool"));
        assertEquals(key("white_wool"), rainbow.getIcon());
        assertEquals(35, rainbow.getIconLegacyId());
        assertEquals(14, rainbow.getIconLegacyDamage());
        assertEquals(35, AchievementRegistryApi.get(key("rainbow_wool")).d.id);
        assertEquals(14, AchievementRegistryApi.get(key("rainbow_wool")).d.damage);
        assertEquals(null, BlockRegistry.get(key("red_wool")));
    }

    @Test
    public void codecRejectsUnsupportedFieldsIconsAndRequirements() {
        JsonObject extra = validDefinition();
        extra.addProperty("rewards", "unsupported");
        assertDecodeFails(extra, "unsupported field");

        JsonObject unknownIcon = validDefinition();
        unknownIcon.getAsJsonObject("display").getAsJsonObject("icon")
                .addProperty("id", "minecraft:not_an_item");
        assertDecodeFails(unknownIcon, "unknown advancement icon");

        JsonObject badRequirements = validDefinition();
        badRequirements.add("requirements", JsonParser.parseString("[[\"missing\"]]"));
        assertDecodeFails(badRequirements, "missing criterion");

        JsonObject emptyCriteria = validDefinition();
        emptyCriteria.add("criteria", new JsonObject());
        assertDecodeFails(emptyCriteria, "cannot be empty");
    }

    @Test
    public void requirementsPermitModernRepeatedCriterionReferences() {
        JsonObject definition = validDefinition();
        definition.add("criteria", JsonParser.parseString("{"
                + "\"first\":{\"trigger\":\"minecraft:legacy_trigger\"},"
                + "\"second\":{\"trigger\":\"minecraft:legacy_trigger\"}}"));
        definition.add("requirements", JsonParser.parseString(
                "[[\"first\",\"second\"],[\"first\"]]"));

        LegacyAdvancementDefinition decoded = LegacyAdvancementCodec.decode(
                new ResourceLocation("test", "repeated_requirements"), definition);
        assertEquals(2, decoded.getRequirements().size());
        assertEquals("first", decoded.getRequirements().get(1).get(0));
    }

    @Test
    public void managerRejectsCriteriaAbsentFromTheDataDefinition() {
        AchievementManager manager = new AchievementManager(null);
        Achievement achievement = AchievementRegistryApi.get(key("open_inventory"));
        assertFalse(manager.grantCriterion(achievement, "not_in_definition"));
        assertFalse(manager.forceGrantCriterion(achievement, "not_in_definition"));
        assertFalse(manager.revokeCriterion(achievement, "not_in_definition"));
    }

    @Test
    public void exactKeySetMissingParentsAndCyclesAreRejected() {
        Map<ResourceLocation, LegacyAdvancementDefinition> base =
                LegacyAdvancementDataBootstrap.definitions();

        LinkedHashMap<ResourceLocation, LegacyAdvancementDefinition> missingKey =
                new LinkedHashMap<ResourceLocation, LegacyAdvancementDefinition>(base);
        missingKey.remove(key("boat_travel"));
        assertGraphFails(missingKey, "exactly");

        LinkedHashMap<ResourceLocation, LegacyAdvancementDefinition> extraKey =
                new LinkedHashMap<ResourceLocation, LegacyAdvancementDefinition>(base);
        extraKey.put(new ResourceLocation("test", "extra"), base.get(key("open_inventory")));
        assertGraphFails(extraKey, "exactly");

        LinkedHashMap<ResourceLocation, LegacyAdvancementDefinition> missingParent =
                new LinkedHashMap<ResourceLocation, LegacyAdvancementDefinition>(base);
        LegacyAdvancementDefinition mine = base.get(key("mine_wood"));
        missingParent.put(key("mine_wood"), copyWithParent(
                mine, new ResourceLocation("minecraft", "missing_parent")));
        assertGraphFails(missingParent, "missing parent");

        LinkedHashMap<ResourceLocation, LegacyAdvancementDefinition> cycle =
                new LinkedHashMap<ResourceLocation, LegacyAdvancementDefinition>(base);
        LegacyAdvancementDefinition root = base.get(key("open_inventory"));
        cycle.put(key("open_inventory"), copyWithParent(root, key("mine_wood")));
        cycle.put(key("mine_wood"), copyWithParent(mine, key("open_inventory")));
        assertGraphFails(cycle, "cycle");
    }

    @Test
    public void legacyPacket200EncodingRemainsIntThenByte() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new Packet200Statistic(5242880, 1).a(new DataOutputStream(bytes));
        assertArrayEquals(new byte[] {0, 80, 0, 0, 1}, bytes.toByteArray());
    }

    @Test
    public void canonicalDefinitionFingerprintIsStableWithinTheRuntime() {
        String first = LegacyAdvancementDataBootstrap.captureDefinitionFingerprint();
        String second = LegacyAdvancementDataBootstrap.captureDefinitionFingerprint();
        assertEquals(first, second);
        assertEquals(64, first.length());
    }

    @Test
    public void allAuthoritativeAndMirroredJsonFilesAreByteIdentical() throws Exception {
        Path developer = developerRoot();
        Path authoritative = developer.resolve(
                "resourcepack/data/minecraft/advancement");
        Path client = developer.resolve(
                "client-source/minecraft/resources/data/minecraft/advancement");
        Path server = developer.resolve(
                "uberbukkit-mcose/src/main/resources/data/minecraft/advancement");
        assertEquals(40L, jsonCount(authoritative));
        assertEquals(40L, jsonCount(client));
        assertEquals(40L, jsonCount(server));
        for (String row : GOLDEN) {
            String file = row.substring(0, row.indexOf('|')) + ".json";
            byte[] expected = Files.readAllBytes(authoritative.resolve(file));
            assertArrayEquals(file + " client mirror", expected,
                    Files.readAllBytes(client.resolve(file)));
            assertArrayEquals(file + " server mirror", expected,
                    Files.readAllBytes(server.resolve(file)));
        }
    }

    @Test
    public void failedConfiguredLoadCannotMutateLiveDefinitionsOrBindings()
            throws Exception {
        ResourceLocation rootKey = key("open_inventory");
        LegacyAdvancementDefinition liveDefinition = Registries.ADVANCEMENT.get(rootKey);
        Achievement liveAchievement = AchievementRegistryApi.get(rootKey);
        int column = liveAchievement.a;
        int iconId = liveAchievement.d.id;
        Path root = Files.createTempDirectory("mcose-server-invalid-advancement-");
        try {
            Path invalid = root.resolve(
                    "data/minecraft/advancement/open_inventory.json");
            Files.createDirectories(invalid.getParent());
            Files.write(invalid, "{\"unsupported\":true}"
                    .getBytes(StandardCharsets.UTF_8));
            try {
                LegacyAdvancementDataBootstrap.loadForTests(
                        RegistryDataLoader.createLayeredProvider(root.toFile()));
                fail("Malformed configured advancement was accepted");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage(),
                        expected.getMessage().contains("advancement"));
            }
            assertSame(liveDefinition, Registries.ADVANCEMENT.get(rootKey));
            assertSame(liveAchievement, AchievementRegistryApi.get(rootKey));
            assertEquals(column, liveAchievement.a);
            assertEquals(iconId, liveAchievement.d.id);
        } finally {
            deleteTree(root);
        }
    }

    private static LegacyAdvancementDefinition copyWithParent(
            LegacyAdvancementDefinition source,
            ResourceLocation parent) {
        return new LegacyAdvancementDefinition(source.getId(), parent, source.getIcon(),
                source.getIconLegacyId(), source.getIconLegacyDamage(),
                source.getTitleTranslationKey(), source.getDescriptionTranslationKey(),
                source.getFrame(), source.shouldShowToast(), source.shouldAnnounceToChat(),
                source.isHidden(), source.getColumn(), source.getRow(),
                source.getCriteria(), source.getRequirements());
    }

    private static JsonObject validDefinition() {
        return JsonParser.parseString("{"
                + "\"display\":{\"icon\":{\"id\":\"minecraft:book\"},"
                + "\"title\":{\"translate\":\"achievement.test\"},"
                + "\"description\":{\"translate\":\"achievement.test.desc\"}},"
                + "\"criteria\":{\"legacy_trigger\":{"
                + "\"trigger\":\"minecraft:legacy_trigger\"}},"
                + "\"requirements\":[[\"legacy_trigger\"]],"
                + "\"legacy_layout\":{\"column\":0,\"row\":0}}")
                .getAsJsonObject();
    }

    private static void assertDecodeFails(JsonObject json, String message) {
        try {
            LegacyAdvancementCodec.decode(new ResourceLocation("test", "entry"), json);
            fail("Expected decode failure: " + json);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }

    private static void assertGraphFails(
            Map<ResourceLocation, LegacyAdvancementDefinition> definitions,
            String message) {
        try {
            LegacyAdvancementDataBootstrap.validateGraphForTests(definitions);
            fail("Expected graph validation failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }

    private static long jsonCount(Path directory) throws IOException {
        java.util.stream.Stream<Path> files = Files.list(directory);
        try {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .count();
        } finally {
            files.close();
        }
    }

    private static Path developerRoot() {
        Path cursor = java.nio.file.Paths.get("").toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("client-source"))
                    && Files.isDirectory(cursor.resolve("resourcepack"))
                    && Files.isDirectory(cursor.resolve("uberbukkit-mcose"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Could not locate paired Developer repositories");
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
