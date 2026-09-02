package net.minecraft.server.registry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraft.server.Block;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

/** Locks server block loot to the complete client/resource-pack manifest. */
public class BlockLootResourceParityTest {
    private static final List<String> EXPECTED_BLOCK_PATHS = Arrays.asList(
            "bed", "bedrock", "bookshelf", "brick_stairs", "bricks",
            "brown_mushroom", "brown_mushroom_block", "cactus", "cake",
            "carved_pumpkin", "chest", "clay", "coal_block", "coal_ore",
            "cobblestone", "cobblestone_stairs", "cobweb", "crafting_table",
            "dandelion", "dead_bush", "detector_rail", "diamond_block",
            "diamond_ore", "dirt", "dispenser", "double_stone_slab",
            "farmland", "fire", "furnace", "glass", "glowstone",
            "gold_block", "gold_ore", "grass_block", "gravel", "ice",
            "iron_block", "iron_door", "iron_ore", "jack_o_lantern",
            "jukebox", "ladder", "lapis_block", "lapis_ore", "lava",
            "lava_still", "lever", "lit_furnace", "lit_redstone_ore",
            "lit_repeater", "locked_chest", "melon", "melon_stem",
            "mob_spawner", "mossy_cobblestone", "moving_piston",
            "netherrack", "note_block", "oak_door", "oak_fence",
            "oak_fence_gate", "oak_fence_gate_compat", "oak_leaves",
            "oak_log", "oak_planks", "oak_pressure_plate", "oak_sapling",
            "oak_sign", "oak_stairs", "oak_trapdoor", "oak_wall_sign",
            "obsidian", "piston", "piston_head", "poppy", "portal",
            "powered_rail", "pumpkin", "pumpkin_state", "pumpkin_stem",
            "rail", "red_mushroom", "red_mushroom_block", "redstone_block",
            "redstone_ore", "redstone_torch", "redstone_torch_off",
            "redstone_wire", "repeater", "sand", "sandstone", "snow",
            "snow_block", "soul_sand", "sponge", "sticky_piston", "stone",
            "stone_brick_stairs", "stone_bricks", "stone_button",
            "stone_pressure_plate", "stone_slab", "sugar_cane", "tall_grass",
            "tnt", "torch", "wall_clock", "water", "water_still",
            "wet_sponge", "wheat", "white_wool");

    @BeforeClass
    public static void initializeRegistries() {
        BlockRegistryBootstrap.initialize();
        ItemRegistryBootstrap.initialize();
    }

    @Test
    public void manifestExactlyMatchesAllCanonicalBuiltInBlocks() {
        assertNotNull(Block.STONE);
        SortedSet<String> actual = new TreeSet<String>();
        for (ResourceLocation key : BlockRegistry.primaryKeys()) {
            actual.add(key.toString());
        }
        assertEquals(112, EXPECTED_BLOCK_PATHS.size());
        assertEquals(canonicalKeys(), actual);
    }

    @Test
    public void resourcePackClientAndServerMirrorsAreByteIdentical()
            throws Exception {
        Path developer = developerRoot();
        Path authoritative = developer.resolve(
                "resourcepack/data/minecraft/loot_table/blocks");
        Path client = developer.resolve(
                "client-source/minecraft/resources/data/minecraft/loot_table/blocks");
        Path game = developer.resolve(
                "client-source/minecraft/game/resources/data/minecraft/loot_table/blocks");
        Path server = developer.resolve(
                "uberbukkit-mcose/src/main/resources/data/minecraft/loot_table/blocks");
        SortedSet<String> expectedFiles = expectedFiles();

        assertEquals(expectedFiles, jsonFiles(authoritative));
        assertEquals(expectedFiles, jsonFiles(client));
        assertEquals(expectedFiles, jsonFiles(game));
        assertEquals(expectedFiles, jsonFiles(server));

        for (String relative : expectedFiles) {
            byte[] expected = Files.readAllBytes(authoritative.resolve(relative));
            assertArrayEquals(relative + " client mirror", expected,
                    Files.readAllBytes(client.resolve(relative)));
            assertArrayEquals(relative + " game mirror", expected,
                    Files.readAllBytes(game.resolve(relative)));
            assertArrayEquals(relative + " server mirror", expected,
                    Files.readAllBytes(server.resolve(relative)));
        }
    }

    @Test
    public void everyCanonicalResourceDecodesAndBindsByIdentity()
            throws Exception {
        Path root = developerRoot().resolve(
                "resourcepack/data/minecraft/loot_table/blocks");
        LootTables.initialize();
        for (String path : EXPECTED_BLOCK_PATHS) {
            ResourceLocation blockKey = new ResourceLocation("minecraft", path);
            Block block = BlockRegistry.get(blockKey);
            assertNotNull(path, block);
            assertTrue(path, LootTables.isBuiltInBlock(block));
            assertNotNull(path, LootTables.getBlockLootTable(block));

            Path file = root.resolve(path + ".json");
            String json = new String(
                    Files.readAllBytes(file), StandardCharsets.UTF_8);
            LootTable decoded = LootTableCodec.decode(
                    new ResourceLocation("minecraft", "blocks/" + path),
                    JsonParser.parseString(json).getAsJsonObject());
            assertTrue(path, decoded instanceof BlockLootTable);
        }
        assertEquals(112 + 16, LootTables.builtInTableKeys().size());
    }

    private static SortedSet<String> canonicalKeys() {
        SortedSet<String> result = new TreeSet<String>();
        for (String path : EXPECTED_BLOCK_PATHS) {
            result.add("minecraft:" + path);
        }
        return result;
    }

    private static SortedSet<String> expectedFiles() {
        SortedSet<String> result = new TreeSet<String>();
        for (String path : EXPECTED_BLOCK_PATHS) {
            result.add(path + ".json");
        }
        return result;
    }

    private static SortedSet<String> jsonFiles(Path root) throws Exception {
        assertTrue("Missing block-loot directory: " + root,
                Files.isDirectory(root));
        SortedSet<String> result = new TreeSet<String>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".json"))
                    .forEach(path -> result.add(root.relativize(path)
                            .toString().replace('\\', '/')));
        }
        return result;
    }

    private static Path developerRoot() {
        Path cursor = java.nio.file.Paths.get("")
                .toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("client-source"))
                    && Files.isDirectory(cursor.resolve("resourcepack"))
                    && Files.isDirectory(cursor.resolve("uberbukkit-mcose"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException(
                "Could not locate client-source, resourcepack, and UberBukkit checkouts");
    }
}
