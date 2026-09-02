package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import net.minecraft.server.BiomeBase;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;

public class RegistryDataFingerprintConfiguredDataTest {
    private static final String ZOMBIE_LOOT_COMPACT =
            "{\"type\":\"minecraft:entity\",\"pools\":[{\"entries\":[{"
                    + "\"type\":\"minecraft:item\",\"modifier\":[{"
                    + "\"type\":\"minecraft:set_count\",\"count\":{"
                    + "\"type\":\"minecraft:uniform\",\"min\":0,\"max\":2}}],"
                    + "\"name\":\"minecraft:feather\"}],\"rolls\":1}],"
                    + "\"random_sequence\":\"minecraft:entities/zombie\"}";
    private static final String BIOME_SPAWN_COMPACT =
            "{\"attributes\":{\"minecraft:gameplay/natural_mob_spawns\":{"
                    + "\"modifier\":\"overlay\",\"argument\":{"
                    + "\"spawns_by_category\":{"
                    + "\"monster\":[{\"type\":\"minecraft:zombie\","
                    + "\"count\":4,\"weight\":100}],"
                    + "\"creature\":[],\"water_creature\":[]},"
                    + "\"spawn_costs\":{}}}}}";
    private static final String BIOME_SPAWN_REORDERED =
            "{\n  \"attributes\": {"
                    + "\"minecraft:gameplay/natural_mob_spawns\": {"
                    + "\"argument\": {\"spawn_costs\": {},"
                    + "\"spawns_by_category\": {\"water_creature\": [],"
                    + "\"creature\": [],\"monster\": [{"
                    + "\"weight\": 100, \"type\": \"minecraft:zombie\","
                    + "\"count\": 4}]}}, \"modifier\": \"overlay\"}}}";
    private static final String ADVANCEMENT_COMPACT =
            "{\"display\":{\"icon\":{\"id\":\"minecraft:book\"},"
                    + "\"title\":{\"translate\":\"achievement.openInventory\"},"
                    + "\"description\":{\"translate\":"
                    + "\"achievement.openInventory.desc\"},\"frame\":\"task\","
                    + "\"show_toast\":true,\"announce_to_chat\":true,"
                    + "\"hidden\":false},\"criteria\":{\"legacy_trigger\":{"
                    + "\"trigger\":\"minecraft:legacy_trigger\"}},"
                    + "\"requirements\":[[\"legacy_trigger\"]],"
                    + "\"legacy_layout\":{\"column\":0,\"row\":0}}";
    private static final String ADVANCEMENT_REORDERED =
            "{\n  \"legacy_layout\": {\"row\": 0, \"column\": 0},"
                    + "\n  \"requirements\": [[\"legacy_trigger\"]],"
                    + "\n  \"criteria\": {\"legacy_trigger\": {"
                    + "\"trigger\": \"minecraft:legacy_trigger\"}},"
                    + "\n  \"display\": {\"hidden\": false,"
                    + "\"announce_to_chat\": true, \"show_toast\": true,"
                    + "\"frame\": \"task\", \"description\": {"
                    + "\"translate\": \"achievement.openInventory.desc\"},"
                    + "\"title\": {\"translate\": "
                    + "\"achievement.openInventory\"},"
                    + "\"icon\": {\"id\": \"minecraft:book\"}}}";
    private static final String DIMENSION_TYPE_COMPACT =
            "{\"has_fixed_time\":false,\"has_skylight\":true,"
                    + "\"has_ceiling\":false,\"has_ender_dragon_fight\":false,"
                    + "\"coordinate_scale\":1.0,\"min_y\":0,\"height\":128,"
                    + "\"logical_height\":128,"
                    + "\"infiniburn\":\"#minecraft:infiniburn_overworld\","
                    + "\"ambient_light\":0.05,"
                    + "\"monster_spawn_light_level\":{"
                    + "\"type\":\"minecraft:uniform\","
                    + "\"min_inclusive\":0,\"max_inclusive\":7},"
                    + "\"monster_spawn_block_light_limit\":15,"
                    + "\"skybox\":\"overworld\","
                    + "\"cardinal_light\":\"default\"}";
    private static final String DIMENSION_TYPE_REORDERED =
            "{\n  \"cardinal_light\": \"default\","
                    + "\"monster_spawn_block_light_limit\": 15,"
                    + "\"logical_height\": 128, \"height\": 128,"
                    + "\"min_y\": 0, \"ambient_light\": 0.05,"
                    + "\"monster_spawn_light_level\": {"
                    + "\"max_inclusive\": 7, \"min_inclusive\": 0,"
                    + "\"type\": \"minecraft:uniform\"},"
                    + "\"infiniburn\": \"#minecraft:infiniburn_overworld\","
                    + "\"coordinate_scale\": 1, \"has_ceiling\": false,"
                    + "\"has_skylight\": true,"
                    + "\"has_ender_dragon_fight\": false,"
                    + "\"skybox\": \"overworld\","
                    + "\"has_fixed_time\": false}";
    private static final String WORLD_PRESET_COMPACT =
            "{\"dimensions\":{"
                    + "\"minecraft:overworld\":{\"type\":\"minecraft:overworld\","
                    + "\"generator\":{\"type\":\"minecraft:default\"}},"
                    + "\"minecraft:the_nether\":{\"type\":\"minecraft:the_nether\","
                    + "\"generator\":{\"type\":\"minecraft:nether\"}},"
                    + "\"minecraft:sky\":{\"type\":\"minecraft:sky\","
                    + "\"generator\":{\"type\":\"minecraft:sky\"}}}}";
    private static final String WORLD_PRESET_REORDERED =
            "{\n  \"dimensions\": {"
                    + "\"minecraft:sky\": {\"generator\": {"
                    + "\"type\": \"minecraft:sky\"},"
                    + "\"type\": \"minecraft:sky\"},"
                    + "\"minecraft:the_nether\": {\"generator\": {"
                    + "\"type\": \"minecraft:nether\"},"
                    + "\"type\": \"minecraft:the_nether\"},"
                    + "\"minecraft:overworld\": {\"generator\": {"
                    + "\"type\": \"minecraft:default\"},"
                    + "\"type\": \"minecraft:overworld\"}}}";
    private static final String CONFIGURED_ORE_COMPACT =
            "{\"type\":\"minecraft:ore\","
                    + "\"discard_chance_on_air_exposure\":0.0,"
                    + "\"size\":16,\"targets\":[{"
                    + "\"state\":\"minecraft:coal_ore\",\"target\":{"
                    + "\"predicate_type\":\"minecraft:block_match\","
                    + "\"block\":\"minecraft:stone\"}}]}";
    private static final String CONFIGURED_ORE_REORDERED =
            "{\n  \"targets\": [{\"target\": {"
                    + "\"block\": \"minecraft:stone\","
                    + "\"predicate_type\": \"minecraft:block_match\"},"
                    + "\"state\": \"minecraft:coal_ore\"}],"
                    + "\"size\": 16,"
                    + "\"discard_chance_on_air_exposure\": 0,"
                    + "\"type\": \"minecraft:ore\"}";
    private static final String PLACED_ORE_COMPACT =
            "{\"feature\":\"minecraft:ore_coal\",\"placement\":["
                    + "{\"type\":\"minecraft:count\",\"count\":20},"
                    + "{\"type\":\"minecraft:in_square\"},"
                    + "{\"type\":\"minecraft:height_range\",\"height\":{"
                    + "\"type\":\"minecraft:uniform\","
                    + "\"min_inclusive\":{\"absolute\":0},"
                    + "\"max_inclusive\":{\"absolute\":127}}},"
                    + "{\"type\":\"minecraft:biome\"}]}";
    private static final String PLACED_ORE_REORDERED =
            "{\n  \"placement\": [{\"count\": 20,"
                    + "\"type\": \"minecraft:count\"},"
                    + "{\"type\": \"minecraft:in_square\"},"
                    + "{\"height\": {\"max_inclusive\": {\"absolute\": 127},"
                    + "\"min_inclusive\": {\"absolute\": 0},"
                    + "\"type\": \"minecraft:uniform\"},"
                    + "\"type\": \"minecraft:height_range\"},"
                    + "{\"type\": \"minecraft:biome\"}],"
                    + "\"feature\": \"minecraft:ore_coal\"}";
    private static final String PLACED_MONSTER_ROOM_COMPACT =
            "{\"feature\":\"minecraft:monster_room\",\"placement\":["
                    + "{\"type\":\"minecraft:count\",\"count\":8},"
                    + "{\"type\":\"minecraft:in_square\"},"
                    + "{\"type\":\"minecraft:height_range\",\"height\":{"
                    + "\"type\":\"minecraft:uniform\","
                    + "\"min_inclusive\":{\"absolute\":0},"
                    + "\"max_inclusive\":{\"absolute\":127}}},"
                    + "{\"type\":\"minecraft:biome\"}]}";
    private static final String CONFIGURED_SPRING_COMPACT =
            "{\"type\":\"minecraft:spring_feature\","
                    + "\"hole_count\":1,\"requires_block_below\":true,"
                    + "\"rock_count\":4,\"state\":{"
                    + "\"id\":\"minecraft:water\",\"properties\":{"
                    + "\"falling\":\"false\"}},"
                    + "\"valid_blocks\":[\"minecraft:stone\"]}";
    private static final String PLACED_SPRING_COMPACT =
            "{\"feature\":\"minecraft:spring_water\",\"placement\":["
                    + "{\"type\":\"minecraft:count\",\"count\":50},"
                    + "{\"type\":\"minecraft:in_square\"},"
                    + "{\"type\":\"minecraft:height_range\",\"height\":{"
                    + "\"type\":\"minecraft:biased_to_bottom\",\"inner\":8,"
                    + "\"min_inclusive\":{\"absolute\":0},"
                    + "\"max_inclusive\":{\"absolute\":127}}},"
                    + "{\"type\":\"minecraft:biome\"}]}";
    private static final String CONFIGURED_NETHER_OPEN_SPRING_COMPACT =
            "{\"type\":\"minecraft:spring_feature\","
                    + "\"hole_count\":1,\"requires_block_below\":false,"
                    + "\"rock_count\":4,\"state\":{"
                    + "\"id\":\"minecraft:lava\",\"properties\":{"
                    + "\"falling\":\"false\"}},"
                    + "\"valid_blocks\":[\"minecraft:netherrack\"]}";
    private static final String PLACED_NETHER_OPEN_SPRING_COMPACT =
            "{\"feature\":\"minecraft:spring_nether_open\",\"placement\":["
                    + "{\"type\":\"minecraft:count\",\"count\":8},"
                    + "{\"type\":\"minecraft:in_square\"},"
                    + "{\"type\":\"minecraft:height_range\",\"height\":{"
                    + "\"type\":\"minecraft:uniform\","
                    + "\"min_inclusive\":{\"absolute\":4},"
                    + "\"max_inclusive\":{\"absolute\":123}}},"
                    + "{\"type\":\"minecraft:biome\"}]}";
    private static final String PLACED_CLASSIC_HELL_SPRING_COMPACT =
            "{\"feature\":\"minecraft:spring_lava_overworld\",\"placement\":["
                    + "{\"type\":\"minecraft:count\",\"count\":5},"
                    + "{\"type\":\"minecraft:in_square\"},"
                    + "{\"type\":\"minecraft:height_range\",\"height\":{"
                    + "\"type\":\"minecraft:biased_to_bottom\",\"inner\":8,"
                    + "\"min_inclusive\":{\"absolute\":0},"
                    + "\"max_inclusive\":{\"absolute\":55}}},"
                    + "{\"type\":\"minecraft:biome\"}]}";
    private static final String CONFIGURED_CLAY_COMPACT =
            "{\"type\":\"minecraft:legacy_clay\","
                    + "\"discard_chance_on_air_exposure\":0.0,"
                    + "\"size\":32,\"targets\":[{"
                    + "\"state\":\"minecraft:clay\",\"target\":{"
                    + "\"predicate_type\":\"minecraft:block_match\","
                    + "\"block\":\"minecraft:sand\"}}]}";
    private static final String PLACED_CLAY_COMPACT =
            "{\"feature\":\"minecraft:clay\",\"placement\":["
                    + "{\"type\":\"minecraft:count\",\"count\":10},"
                    + "{\"type\":\"minecraft:in_square\"},"
                    + "{\"type\":\"minecraft:height_range\",\"height\":{"
                    + "\"type\":\"minecraft:uniform\","
                    + "\"min_inclusive\":{\"absolute\":0},"
                    + "\"max_inclusive\":{\"absolute\":127}}},"
                    + "{\"type\":\"minecraft:block_predicate_filter\","
                    + "\"predicate\":{\"type\":\"minecraft:matching_fluids\","
                    + "\"fluids\":\"minecraft:water\"}},"
                    + "{\"type\":\"minecraft:biome\"}]}";
    private static final String CONFIGURED_WATER_LAKE_COMPACT =
            "{\"type\":\"minecraft:lake\","
                    + "\"barrier\":{\"type\":"
                    + "\"minecraft:simple_state_provider\","
                    + "\"state\":\"minecraft:air\"},"
                    + "\"can_place_feature\":{\"type\":\"minecraft:true\"},"
                    + "\"can_replace_with_air_or_fluid\":{"
                    + "\"type\":\"minecraft:true\"},"
                    + "\"can_replace_with_barrier\":{"
                    + "\"type\":\"minecraft:true\"},"
                    + "\"fluid\":{\"type\":"
                    + "\"minecraft:simple_state_provider\","
                    + "\"state\":\"minecraft:water\"}}";
    private static final String PLACED_WATER_LAKE_COMPACT =
            "{\"feature\":\"minecraft:lake_water\",\"placement\":["
                    + "{\"type\":\"minecraft:rarity_filter\",\"chance\":4},"
                    + "{\"type\":\"minecraft:in_square\"},"
                    + "{\"type\":\"minecraft:height_range\",\"height\":{"
                    + "\"type\":\"minecraft:uniform\","
                    + "\"max_inclusive\":{\"absolute\":127},"
                    + "\"min_inclusive\":{\"absolute\":0}}},"
                    + "{\"type\":\"minecraft:biome\"}]}";

    @Test
    public void advancementLayoutChangesSynchronizedFingerprint()
            throws Exception {
        Path baseline = createOverride(
                "data/minecraft/advancement/open_inventory.json",
                ADVANCEMENT_COMPACT);
        Path override = createOverride(
                "data/minecraft/advancement/open_inventory.json",
                ADVANCEMENT_COMPACT.replace("\"column\":0", "\"column\":1"));
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(override)));
        } finally {
            deleteTree(baseline);
            deleteTree(override);
        }
    }

    @Test
    public void advancementWhitespaceAndKeyOrderKeepFingerprint()
            throws Exception {
        Path compact = createOverride(
                "data/minecraft/advancement/open_inventory.json",
                ADVANCEMENT_COMPACT);
        Path reordered = createOverride(
                "data/minecraft/advancement/open_inventory.json",
                ADVANCEMENT_REORDERED);
        try {
            assertEquals(captureInFreshJvm(compact),
                    captureInFreshJvm(reordered));
        } finally {
            deleteTree(compact);
            deleteTree(reordered);
        }
    }

    @Test
    public void advancementRequirementOrderAndRepeatsKeepFingerprint()
            throws Exception {
        Path first = createOverride(
                "data/minecraft/advancement/open_inventory.json",
                advancementWithRequirements(
                        "[[\"first\",\"second\"],[\"first\"]]"));
        Path reordered = createOverride(
                "data/minecraft/advancement/open_inventory.json",
                advancementWithRequirements(
                        "[[\"first\"],[\"second\",\"first\",\"first\"]]"));
        try {
            assertEquals(captureInFreshJvm(first),
                    captureInFreshJvm(reordered));
        } finally {
            deleteTree(first);
            deleteTree(reordered);
        }
    }

    @Test
    public void commonMiningOverrideChangesSynchronizedFingerprint()
            throws Exception {
        Path root = createOverride(
                "data/minecraft/tags/block/legacy_mining/pickaxe_6.json",
                "{\"replace\":true,\"values\":[\"minecraft:glass\"]}");
        try {
            assertFalse(RegistryDataFingerprint.BUILT_IN_SYNCHRONIZED_DATA.equals(
                    captureInFreshJvm(root)));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void clientOnlyMiningSupplementDoesNotChangeCommonFingerprint()
            throws Exception {
        Path root = createOverride(
                "data/minecraft/tags/block/legacy_mining/"
                        + "client_axe_1_5_supplement.json",
                "{\"replace\":true,\"values\":[\"minecraft:glass\"]}");
        try {
            assertEquals(RegistryDataFingerprint.BUILT_IN_SYNCHRONIZED_DATA,
                    captureInFreshJvm(root));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void wolfFoodMembershipChangesSynchronizedFingerprint()
            throws Exception {
        Path root = createOverride(
                "data/minecraft/tags/item/wolf_food.json",
                "{\"values\":[\"minecraft:porkchop\","
                        + "\"minecraft:cooked_porkchop\","
                        + "\"minecraft:bread\"]}");
        try {
            assertFalse(RegistryDataFingerprint.BUILT_IN_SYNCHRONIZED_DATA.equals(
                    captureInFreshJvm(root)));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void wolfFoodOrderDoesNotChangeSynchronizedFingerprint()
            throws Exception {
        Path root = createOverride(
                "data/minecraft/tags/item/wolf_food.json",
                "{\"replace\":true,\"values\":[\"minecraft:cooked_porkchop\","
                        + "\"minecraft:porkchop\"]}");
        try {
            assertEquals(RegistryDataFingerprint.BUILT_IN_SYNCHRONIZED_DATA,
                    captureInFreshJvm(root));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void recyclingTombstoneChangesSynchronizedFingerprint()
            throws Exception {
        Path root = createOverride(
                "data/minecraft/recycling/iron_pickaxe.json",
                "{\"type\":\"mcose:recycling\",\"enabled\":false}");
        try {
            assertFalse(RegistryDataFingerprint.BUILT_IN_SYNCHRONIZED_DATA.equals(
                    captureInFreshJvm(root)));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void cookingNumberProviderOverrideChangesSynchronizedFingerprint()
            throws Exception {
        Path root = createOverride(
                "data/minecraft/number_provider/cooking/time_coal.json",
                "800.0");
        try {
            assertFalse(RegistryDataFingerprint.BUILT_IN_SYNCHRONIZED_DATA.equals(
                    captureInFreshJvm(root)));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void semanticallyEquivalentCookingNumberProviderOverrideKeepsFingerprint()
            throws Exception {
        Path root = createOverride(
                "data/minecraft/number_provider/cooking/time_coal.json",
                "1600");
        try {
            assertEquals(RegistryDataFingerprint.BUILT_IN_SYNCHRONIZED_DATA,
                    captureInFreshJvm(root));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void entityLootOverrideChangesSynchronizedFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path override = createOverride(
                "data/minecraft/loot_table/entities/zombie.json",
                ZOMBIE_LOOT_COMPACT.replace("\"max\":2", "\"max\":1"));
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(override)));
        } finally {
            deleteTree(baseline);
            deleteTree(override);
        }
    }

    @Test
    public void customNamespaceLootTableDiscoveryChangesSynchronizedFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path custom = createOverride(
                "data/fingerprint_probe/loot_table/chests/custom.json",
                "{\"type\":\"minecraft:chest\",\"pools\":[{"
                        + "\"name\":\"main\",\"rolls\":1,\"entries\":[{"
                        + "\"type\":\"minecraft:item\","
                        + "\"name\":\"minecraft:stick\"}]}]}");
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(custom)));
        } finally {
            deleteTree(baseline);
            deleteTree(custom);
        }
    }

    @Test
    public void whitespaceOnlyEntityLootOverrideKeepsSynchronizedFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path whitespaceOnly = createOverride(
                "data/minecraft/loot_table/entities/zombie.json",
                ZOMBIE_LOOT_COMPACT);
        try {
            assertEquals(captureInFreshJvm(baseline),
                    captureInFreshJvm(whitespaceOnly));
        } finally {
            deleteTree(baseline);
            deleteTree(whitespaceOnly);
        }
    }

    @Test
    public void biomeSpawnSemanticOverrideChangesSynchronizedFingerprint()
            throws Exception {
        Path baseline = createOverride(
                "data/fingerprint_probe/worldgen/biome/probe.json",
                BIOME_SPAWN_COMPACT);
        Path override = createOverride(
                "data/fingerprint_probe/worldgen/biome/probe.json",
                BIOME_SPAWN_COMPACT.replace("\"weight\":100", "\"weight\":99"));
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(override)));
        } finally {
            deleteTree(baseline);
            deleteTree(override);
        }
    }

    @Test
    public void customNamespaceBiomeSpawnTableChangesSynchronizedFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path custom = createOverride(
                "data/fingerprint_probe/worldgen/biome/probe.json",
                BIOME_SPAWN_COMPACT);
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(custom)));
        } finally {
            deleteTree(baseline);
            deleteTree(custom);
        }
    }

    @Test
    public void biomeSpawnWhitespaceAndKeyOrderKeepSynchronizedFingerprint()
            throws Exception {
        Path compact = createOverride(
                "data/fingerprint_probe/worldgen/biome/probe.json",
                BIOME_SPAWN_COMPACT);
        Path reordered = createOverride(
                "data/fingerprint_probe/worldgen/biome/probe.json",
                BIOME_SPAWN_REORDERED);
        try {
            assertEquals(captureInFreshJvm(compact),
                    captureInFreshJvm(reordered));
        } finally {
            deleteTree(compact);
            deleteTree(reordered);
        }
    }

    @Test
    public void serverSpawnConfigGatesDoNotChangeRawBiomeFingerprint()
            throws Exception {
        Path resources = createEmptyRoot();
        Path enabled = createServerConfig(true);
        Path disabled = createServerConfig(false);
        try {
            assertEquals(captureInFreshJvm(resources, enabled),
                    captureInFreshJvm(resources, disabled));
        } finally {
            deleteTree(resources);
            deleteTree(enabled);
            deleteTree(disabled);
        }
    }

    @Test
    public void registeredBiomeWithoutTableKeepsSynchronizedFingerprint()
            throws Exception {
        Path root = createEmptyRoot();
        try {
            assertEquals(captureInFreshJvm(root),
                    captureInFreshJvm(root, null, true));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void dimensionTypeSemanticOverrideChangesSynchronizedFingerprint()
            throws Exception {
        Path baseline = createOverride(
                "data/fingerprint_probe/dimension_type/probe.json",
                DIMENSION_TYPE_COMPACT);
        Path override = createOverride(
                "data/fingerprint_probe/dimension_type/probe.json",
                DIMENSION_TYPE_COMPACT.replace("\"ambient_light\":0.05",
                        "\"ambient_light\":0.15"));
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(override)));
        } finally {
            deleteTree(baseline);
            deleteTree(override);
        }
    }

    @Test
    public void customNamespaceDimensionTypeChangesSynchronizedFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path custom = createOverride(
                "data/fingerprint_probe/dimension_type/probe.json",
                DIMENSION_TYPE_COMPACT);
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(custom)));
        } finally {
            deleteTree(baseline);
            deleteTree(custom);
        }
    }

    @Test
    public void dimensionTypeLightProviderKindChangesSynchronizedFingerprint()
            throws Exception {
        String uniformSeven = DIMENSION_TYPE_COMPACT.replace(
                "\"min_inclusive\":0", "\"min_inclusive\":7");
        String constantSeven = uniformSeven.replace(
                "{\"type\":\"minecraft:uniform\","
                        + "\"min_inclusive\":7,\"max_inclusive\":7}",
                "7");
        Path uniform = createOverride(
                "data/fingerprint_probe/dimension_type/probe.json",
                uniformSeven);
        Path constant = createOverride(
                "data/fingerprint_probe/dimension_type/probe.json",
                constantSeven);
        try {
            assertFalse(captureInFreshJvm(uniform).equals(
                    captureInFreshJvm(constant)));
        } finally {
            deleteTree(uniform);
            deleteTree(constant);
        }
    }

    @Test
    public void dimensionTypeWhitespaceAndKeyOrderKeepSynchronizedFingerprint()
            throws Exception {
        Path compact = createOverride(
                "data/fingerprint_probe/dimension_type/probe.json",
                DIMENSION_TYPE_COMPACT);
        Path reordered = createOverride(
                "data/fingerprint_probe/dimension_type/probe.json",
                DIMENSION_TYPE_REORDERED);
        try {
            assertEquals(captureInFreshJvm(compact),
                    captureInFreshJvm(reordered));
        } finally {
            deleteTree(compact);
            deleteTree(reordered);
        }
    }

    @Test
    public void worldPresetGeneratorOverrideChangesSynchronizedFingerprint()
            throws Exception {
        Path baseline = createOverride(
                "data/fingerprint_probe/worldgen/world_preset/probe.json",
                WORLD_PRESET_COMPACT);
        Path override = createOverride(
                "data/fingerprint_probe/worldgen/world_preset/probe.json",
                WORLD_PRESET_COMPACT.replace("\"minecraft:default\"",
                        "\"minecraft:flat\""));
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(override)));
        } finally {
            deleteTree(baseline);
            deleteTree(override);
        }
    }

    @Test
    public void customNamespaceWorldPresetChangesSynchronizedFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path custom = createOverride(
                "data/fingerprint_probe/worldgen/world_preset/probe.json",
                WORLD_PRESET_COMPACT);
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(custom)));
        } finally {
            deleteTree(baseline);
            deleteTree(custom);
        }
    }

    @Test
    public void worldPresetWhitespaceAndStemOrderKeepSynchronizedFingerprint()
            throws Exception {
        Path compact = createOverride(
                "data/fingerprint_probe/worldgen/world_preset/probe.json",
                WORLD_PRESET_COMPACT);
        Path reordered = createOverride(
                "data/fingerprint_probe/worldgen/world_preset/probe.json",
                WORLD_PRESET_REORDERED);
        try {
            assertEquals(captureInFreshJvm(compact),
                    captureInFreshJvm(reordered));
        } finally {
            deleteTree(compact);
            deleteTree(reordered);
        }
    }

    @Test
    public void javaProviderClassRegistrationDoesNotChangeFingerprint()
            throws Exception {
        Path root = createEmptyRoot();
        try {
            assertEquals(captureInFreshJvm(root),
                    captureInFreshJvm(root, null, false, true));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void configuredOreSemanticOverrideChangesFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path override = createOverride(
                "data/minecraft/worldgen/feature/ore_coal.json",
                CONFIGURED_ORE_COMPACT.replace("\"size\":16", "\"size\":15"));
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(override)));
        } finally {
            deleteTree(baseline);
            deleteTree(override);
        }
    }

    @Test
    public void placedOreSemanticOverrideChangesFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path override = createOverride(
                "data/minecraft/worldgen/placed_feature/ore_coal.json",
                PLACED_ORE_COMPACT.replace("\"count\":20", "\"count\":19"));
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(override)));
        } finally {
            deleteTree(baseline);
            deleteTree(override);
        }
    }

    @Test
    public void monsterRoomPlacementOverrideChangesFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path override = createOverride(
                "data/minecraft/worldgen/placed_feature/monster_room.json",
                PLACED_MONSTER_ROOM_COMPACT.replace("\"count\":8", "\"count\":7"));
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(override)));
        } finally {
            deleteTree(baseline);
            deleteTree(override);
        }
    }

    @Test
    public void configuredSpringSemanticOverrideChangesFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path override = createOverride(
                "data/minecraft/worldgen/feature/spring_water.json",
                CONFIGURED_SPRING_COMPACT.replace(
                        "\"rock_count\":4", "\"rock_count\":3"));
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(override)));
        } finally {
            deleteTree(baseline);
            deleteTree(override);
        }
    }

    @Test
    public void placedSpringInnerOverrideChangesFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path override = createOverride(
                "data/minecraft/worldgen/placed_feature/spring_water.json",
                PLACED_SPRING_COMPACT.replace(
                        "\"inner\":8", "\"inner\":7"));
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(override)));
        } finally {
            deleteTree(baseline);
            deleteTree(override);
        }
    }

    @Test
    public void netherOpenSpringConfigurationChangesFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path override = createOverride(
                "data/minecraft/worldgen/feature/spring_nether_open.json",
                CONFIGURED_NETHER_OPEN_SPRING_COMPACT.replace(
                        "\"requires_block_below\":false",
                        "\"requires_block_below\":true"));
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(override)));
        } finally {
            deleteTree(baseline);
            deleteTree(override);
        }
    }

    @Test
    public void netherOpenSpringPlacementChangesFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path override = createOverride(
                "data/minecraft/worldgen/placed_feature/spring_open.json",
                PLACED_NETHER_OPEN_SPRING_COMPACT.replace(
                        "\"max_inclusive\":{\"absolute\":123}",
                        "\"max_inclusive\":{\"absolute\":122}"));
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(override)));
        } finally {
            deleteTree(baseline);
            deleteTree(override);
        }
    }

    @Test
    public void classicHellSpringCountOverrideChangesFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path override = createOverride(
                "data/minecraft/worldgen/placed_feature/"
                        + "spring_lava_classic_hell.json",
                PLACED_CLASSIC_HELL_SPRING_COMPACT.replace(
                        "\"count\":5", "\"count\":4"));
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(override)));
        } finally {
            deleteTree(baseline);
            deleteTree(override);
        }
    }

    @Test
    public void configuredClayFieldsChangeFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path outputOverride = createOverride(
                "data/minecraft/worldgen/feature/clay.json",
                CONFIGURED_CLAY_COMPACT.replace(
                        "\"state\":\"minecraft:clay\"",
                        "\"state\":\"minecraft:gravel\""));
        Path targetOverride = createOverride(
                "data/minecraft/worldgen/feature/clay.json",
                CONFIGURED_CLAY_COMPACT.replace(
                        "\"block\":\"minecraft:sand\"",
                        "\"block\":\"minecraft:dirt\""));
        Path sizeOverride = createOverride(
                "data/minecraft/worldgen/feature/clay.json",
                CONFIGURED_CLAY_COMPACT.replace(
                        "\"size\":32", "\"size\":31"));
        try {
            String expected = captureInFreshJvm(baseline);
            assertFalse(expected.equals(captureInFreshJvm(outputOverride)));
            assertFalse(expected.equals(captureInFreshJvm(targetOverride)));
            assertFalse(expected.equals(captureInFreshJvm(sizeOverride)));
        } finally {
            deleteTree(baseline);
            deleteTree(outputOverride);
            deleteTree(targetOverride);
            deleteTree(sizeOverride);
        }
    }

    @Test
    public void placedClayFluidPredicateChangesFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path override = createOverride(
                "data/minecraft/worldgen/placed_feature/clay.json",
                PLACED_CLAY_COMPACT.replace(
                        "\"fluids\":\"minecraft:water\"",
                        "\"fluids\":\"minecraft:lava\""));
        try {
            assertFalse(captureInFreshJvm(baseline).equals(
                    captureInFreshJvm(override)));
        } finally {
            deleteTree(baseline);
            deleteTree(override);
        }
    }

    @Test
    public void configuredLakeFluidAndBarrierChangeFingerprint()
            throws Exception {
        Path water = createOverride(
                "data/fingerprint_probe/worldgen/feature/lake_probe.json",
                CONFIGURED_WATER_LAKE_COMPACT);
        Path lava = createOverride(
                "data/fingerprint_probe/worldgen/feature/lake_probe.json",
                CONFIGURED_WATER_LAKE_COMPACT
                        .replace("minecraft:air", "minecraft:stone")
                        .replace("minecraft:water", "minecraft:lava"));
        try {
            assertFalse(captureInFreshJvm(water).equals(
                    captureInFreshJvm(lava)));
        } finally {
            deleteTree(water);
            deleteTree(lava);
        }
    }

    @Test
    public void placedLakeRarityAndHeightChangeFingerprint()
            throws Exception {
        Path baseline = createEmptyRoot();
        Path rarityOverride = createOverride(
                "data/minecraft/worldgen/placed_feature/lake_water.json",
                PLACED_WATER_LAKE_COMPACT.replace(
                        "\"chance\":4", "\"chance\":5"));
        Path heightOverride = createOverride(
                "data/minecraft/worldgen/placed_feature/lake_water.json",
                PLACED_WATER_LAKE_COMPACT.replace(
                        "\"absolute\":127", "\"absolute\":126"));
        try {
            String expected = captureInFreshJvm(baseline);
            assertFalse(expected.equals(captureInFreshJvm(rarityOverride)));
            assertFalse(expected.equals(captureInFreshJvm(heightOverride)));
        } finally {
            deleteTree(baseline);
            deleteTree(rarityOverride);
            deleteTree(heightOverride);
        }
    }

    @Test
    public void featureWhitespaceAndKeyOrderKeepFingerprint()
            throws Exception {
        Path compactConfigured = createOverride(
                "data/minecraft/worldgen/feature/ore_coal.json",
                CONFIGURED_ORE_COMPACT);
        Path reorderedConfigured = createOverride(
                "data/minecraft/worldgen/feature/ore_coal.json",
                CONFIGURED_ORE_REORDERED);
        Path compactPlaced = createOverride(
                "data/minecraft/worldgen/placed_feature/ore_coal.json",
                PLACED_ORE_COMPACT);
        Path reorderedPlaced = createOverride(
                "data/minecraft/worldgen/placed_feature/ore_coal.json",
                PLACED_ORE_REORDERED);
        try {
            assertEquals(captureInFreshJvm(compactConfigured),
                    captureInFreshJvm(reorderedConfigured));
            assertEquals(captureInFreshJvm(compactPlaced),
                    captureInFreshJvm(reorderedPlaced));
        } finally {
            deleteTree(compactConfigured);
            deleteTree(reorderedConfigured);
            deleteTree(compactPlaced);
            deleteTree(reorderedPlaced);
        }
    }

    private static String advancementWithRequirements(String requirements) {
        String criteria = "\"criteria\":{\"legacy_trigger\":{"
                + "\"trigger\":\"minecraft:legacy_trigger\"}},"
                + "\"requirements\":[[\"legacy_trigger\"]]";
        String replacement = "\"criteria\":{"
                + "\"first\":{\"trigger\":\"minecraft:legacy_trigger\"},"
                + "\"second\":{\"trigger\":\"minecraft:legacy_trigger\"}},"
                + "\"requirements\":" + requirements;
        return ADVANCEMENT_COMPACT.replace(criteria, replacement);
    }

    private static Path createEmptyRoot() throws Exception {
        return Files.createTempDirectory("mcose-fingerprint-data-");
    }

    private static Path createOverride(String relative, String json)
            throws Exception {
        Path root = createEmptyRoot();
        Path target = root.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.write(target, json.getBytes(StandardCharsets.UTF_8));
        return root;
    }

    private static Path createServerConfig(boolean enabled) throws Exception {
        Path root = createEmptyRoot();
        String yaml = "config-version: 1\n"
                + "mechanics:\n"
                + "  spawn_squids: " + enabled + "\n"
                + "  spawn_slimes: " + enabled + "\n"
                + "  spawn_wolves: " + enabled + "\n";
        Files.write(root.resolve("uberbukkit.yml"),
                yaml.getBytes(StandardCharsets.UTF_8));
        return root;
    }

    private static String captureInFreshJvm(Path root) throws Exception {
        return captureInFreshJvm(root, null, false);
    }

    private static String captureInFreshJvm(Path root, Path workingDirectory)
            throws Exception {
        return captureInFreshJvm(root, workingDirectory, false);
    }

    private static String captureInFreshJvm(
            Path root,
            Path workingDirectory,
            boolean registerTablelessBiome) throws Exception {
        return captureInFreshJvm(
                root, workingDirectory, registerTablelessBiome, false);
    }

    private static String captureInFreshJvm(
            Path root,
            Path workingDirectory,
            boolean registerTablelessBiome,
            boolean registerProviderOnly) throws Exception {
        File javaExecutable = new File(
                new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name", "").toLowerCase().contains("win")
                        ? "java.exe" : "java");
        ProcessBuilder builder = new ProcessBuilder(
                javaExecutable.getAbsolutePath(),
                "-Dmcose.resourcesDir=" + root.toAbsolutePath(),
                "-cp", System.getProperty("java.class.path"),
                Probe.class.getName(),
                Boolean.toString(registerTablelessBiome),
                Boolean.toString(registerProviderOnly))
                .redirectErrorStream(true);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        Process process = builder.start();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        InputStream output = process.getInputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = output.read(buffer)) >= 0) {
            captured.write(buffer, 0, read);
        }
        int exit = process.waitFor();
        String text = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(text, 0, exit);
        String marker = "FINGERPRINT=";
        for (String line : text.split("\\r?\\n")) {
            if (line.startsWith(marker)) return line.substring(marker.length());
        }
        throw new AssertionError("Fingerprint probe produced no result: " + text);
    }

    private static void deleteTree(Path root) throws Exception {
        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
    }

    public static final class Probe {
        public static void main(String[] args) {
            if (Block.STONE == null || Item.STICK == null) {
                throw new AssertionError("Legacy registries did not initialize");
            }
            if (args.length > 0 && Boolean.parseBoolean(args[0])) {
                BiomeRegistryBootstrap.initialize();
                if (!BiomeRegistryApi.register(
                        new ResourceLocation("fingerprint_probe", "tableless"),
                        new TablelessBiome())) {
                    throw new AssertionError("Could not register tableless biome");
                }
            }
            if (args.length > 1 && Boolean.parseBoolean(args[1])) {
                DimensionTypeRegistryBootstrap.initialize();
                if (!DimensionTypeRegistryApi.register(
                        new ResourceLocation("fingerprint_probe", "provider_only"),
                        ProviderOnly.class)) {
                    throw new AssertionError(
                            "Could not register provider-only dimension bridge");
                }
            }
            System.out.println("FINGERPRINT="
                    + RegistryDataFingerprint.captureSynchronizedData());
        }
    }

    public static final class ProviderOnly {}

    public static final class TablelessBiome extends BiomeBase {
        public TablelessBiome() {
            super();
        }
    }
}
