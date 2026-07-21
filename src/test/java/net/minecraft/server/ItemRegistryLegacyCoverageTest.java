package net.minecraft.server;

import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.registry.LegacyIdBridge;
import net.minecraft.server.util.ResourceLocation;
import net.minecraft.server.registry.VanillaRegistryKeys;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ItemRegistryLegacyCoverageTest {

    @BeforeClass
    public static void initializeRegistries() {
        assertNotNull(Block.STONE);
        assertNotNull(Item.SIGN);
        LegacyIdBridge.refresh();
    }

    @Test
    public void everyPopulatedLegacyItemIdMatchesItsReviewedModernCounterpart() {
        String[] expectedKeys = reviewedModernKeys();
        int expectedCount = 0;
        int actualCount = 0;

        for (int itemId = 0; itemId < Item.byId.length; ++itemId) {
            String expectedPath = expectedKeys[itemId];
            Item item = Item.byId[itemId];
            if (expectedPath == null) {
                assertNull("legacy item slot " + itemId + " should remain unregistered", item);
                assertNull("unregistered legacy item ID " + itemId + " should not have a bridge key",
                        LegacyIdBridge.itemKeyFromId(itemId));
                continue;
            }

            ++expectedCount;
            if (item != null) {
                ++actualCount;
            }
            ResourceLocation expectedKey = new ResourceLocation("minecraft", expectedPath);
            assertNotNull("missing legacy item ID " + itemId + " for " + expectedKey, item);
            assertEquals("registered counterpart for legacy item ID " + itemId,
                    expectedKey, ItemRegistry.getKey(item));
            assertSame("primary registry lookup for legacy item ID " + itemId,
                    item, ItemRegistry.get(expectedKey));
            assertEquals("forward bridge for legacy item ID " + itemId,
                    expectedKey.toString(), LegacyIdBridge.itemKeyFromId(itemId));
            assertEquals("reverse bridge for legacy item ID " + itemId,
                    Integer.valueOf(itemId), LegacyIdBridge.itemIdFromKey(expectedKey.toString()));

            ResourceLocation declaredKey = VanillaRegistryKeys.itemKey(item);
            if (declaredKey == null) {
                assertEquals("only the combined pumpkin metadata item is fieldless", Block.PUMPKIN.id, itemId);
            } else if (!expectedKey.equals(declaredKey)) {
                assertTrue("unreviewed declared/registered difference for legacy item ID " + itemId
                                + ": declared=" + declaredKey + " registered=" + expectedKey,
                        isReviewedPlacedBlockCollision(itemId, declaredKey, expectedKey));
            }

            assertFalse("legacy item ID " + itemId + " still uses a numeric collision key: " + expectedKey,
                    expectedPath.matches(".*_(legacy|compat)_[0-9]+$"));
        }

        assertEquals("reviewed item table size changed", 239, expectedCount);
        assertEquals("populated legacy item count changed", expectedCount, actualCount);
    }

    @Test
    public void everyPopulatedLegacyItemIdAndMetadataRoundTripsThroughRegionCoreStacks() {
        String[] expectedKeys = reviewedModernKeys();

        for (int itemId = 0; itemId < expectedKeys.length; ++itemId) {
            if (expectedKeys[itemId] == null) {
                continue;
            }
            int normalizedId = normalizedInventoryItemId(itemId);
            String normalizedKey = expectedKeys[normalizedId];
            assertNotNull("missing reviewed normalized key for legacy item ID " + itemId, normalizedKey);

            for (int metadata = 0; metadata < 16; ++metadata) {
                String context = "legacy item ID " + itemId + ", metadata " + metadata;
                NBTTagCompound legacy = new NBTTagCompound();
                legacy.a("id", (short)itemId);
                legacy.a("Count", (byte)3);
                legacy.a("Damage", (short)metadata);

                ItemStack stack = ItemStack.parse(legacy);
                assertNotNull(context, stack);
                assertEquals(context + " normalized ID", normalizedId, stack.id);
                assertEquals(context + " damage before save", metadata, stack.getItemDamage());

                NBTTagCompound modern = stack.save(new NBTTagCompound());
                assertEquals(context + " format", 3, modern.e("mcose_stack_format"));
                assertEquals(context + " modern counterpart", "minecraft:" + normalizedKey,
                        modern.getString("item"));
                assertEquals(context + " count", 3, modern.e("count"));
                assertFalse(context + " retained numeric ID", modern.hasKey("id"));
                assertFalse(context + " retained legacy count", modern.hasKey("Count"));
                assertFalse(context + " retained legacy damage", modern.hasKey("Damage"));

                ItemStack decoded = ItemStack.parse(modern);
                assertNotNull(context + " modern decode", decoded);
                assertEquals(context + " decoded ID", normalizedId, decoded.id);
                assertEquals(context + " decoded damage", metadata, decoded.getItemDamage());
                assertEquals(context + " decoded count", 3, decoded.count);
            }
        }
    }

    @Test
    public void metadataVariantAliasesMatchTheirReviewedLegacyDamageValues() {
        assertVariants(Block.LOG.id,
                new String[]{"oak_log", "spruce_log", "birch_log"});
        assertVariants(Block.LEAVES.id,
                new String[]{"oak_leaves", "spruce_leaves", "birch_leaves"});
        assertVariants(Block.SAPLING.id,
                new String[]{"oak_sapling", "spruce_sapling", "birch_sapling"});
        assertVariants(Block.WOOL.id,
                new String[]{"white_wool", "orange_wool", "magenta_wool", "light_blue_wool",
                        "yellow_wool", "lime_wool", "pink_wool", "gray_wool", "light_gray_wool",
                        "cyan_wool", "purple_wool", "blue_wool", "brown_wool", "green_wool",
                        "red_wool", "black_wool"});
        assertVariants(Block.STEP.id,
                new String[]{"stone_slab", "sandstone_slab", "wooden_slab", "cobblestone_slab",
                        "brick_slab", "stone_brick_slab"});
        assertVariants(Block.STONE_BRICK.id,
                new String[]{"stone_brick", "mossy_stone_brick", "cracked_stone_brick",
                        "chiseled_stone_brick"});
        assertVariants(Item.COAL.id, new String[]{"coal", "charcoal"});
        assertVariants(Item.INK_SACK.id,
                new String[]{"black_dye", "red_dye", "green_dye", "brown_dye", "blue_dye",
                        "purple_dye", "cyan_dye", "light_gray_dye", "gray_dye", "pink_dye",
                        "lime_dye", "yellow_dye", "light_blue_dye", "magenta_dye", "orange_dye",
                        "white_dye"});

        assertVariant(Item.INK_SACK.id, "silver_dye", 7);
        assertVariant(Block.PUMPKIN.id, "pumpkin", 1);
        assertVariant(Block.PUMPKIN.id, "carved_pumpkin", 0);
    }

    @Test
    public void historicalClientAndServerItemNamesRemainReadableAliases() {
        assertHistoricalAlias(66, "rails");
        assertHistoricalAlias(82, "clay_block");
        assertHistoricalAlias(98, "stone_brick_item");
        assertHistoricalAlias(103, "melon_block");
        assertHistoricalAlias(273, "stone_spade");
        assertHistoricalAlias(277, "diamond_spade");
        assertHistoricalAlias(311, "plate_diamond");
        assertHistoricalAlias(312, "legs_diamond");
        assertHistoricalAlias(332, "snow_ball");
        assertHistoricalAlias(336, "clay_brick");
        assertHistoricalAlias(2271, "record_tsuku_no_koibumi");
    }

    private static void assertVariants(int itemId, String[] paths) {
        for (int damage = 0; damage < paths.length; ++damage) {
            assertVariant(itemId, paths[damage], damage);
            ItemStack stack = new ItemStack(itemId, 1, damage);
            assertEquals("variant key for legacy item ID " + itemId + ", metadata " + damage,
                    new ResourceLocation("minecraft", paths[damage]), ItemRegistry.getKeyForStack(stack));
        }
    }

    private static void assertVariant(int itemId, String path, int damage) {
        Item item = Item.byId[itemId];
        ResourceLocation key = new ResourceLocation("minecraft", path);
        assertNotNull("missing item for variant " + key, item);
        assertSame("variant lookup " + key, item, ItemRegistry.get(key));
        assertEquals("variant damage " + key, damage, ItemRegistry.getDefaultDamage(key.toString()));
        assertEquals("variant bridge " + key, Integer.valueOf(itemId), LegacyIdBridge.itemIdFromKey(key.toString()));
    }

    private static void assertHistoricalAlias(int itemId, String path) {
        ResourceLocation key = new ResourceLocation("minecraft", path);
        assertSame("historical alias lookup " + key, Item.byId[itemId], ItemRegistry.get(key));
        assertEquals("historical alias bridge " + key, Integer.valueOf(itemId),
                LegacyIdBridge.itemIdFromKey(key.toString()));
    }

    private static boolean isReviewedPlacedBlockCollision(int itemId, ResourceLocation declared,
            ResourceLocation registered) {
        switch (itemId) {
            case 26:
            case 59:
            case 64:
            case 71:
            case 83:
            case 92:
            case 93:
            case 187:
            case 189:
                return registered.getNamespace().equals(declared.getNamespace())
                        && registered.getPath().equals(declared.getPath() + "_block");
            default:
                return false;
        }
    }

    private static int normalizedInventoryItemId(int itemId) {
        if (itemId == Block.REDSTONE_TORCH_OFF.id) {
            return Block.REDSTONE_TORCH_ON.id;
        }
        if (itemId == Block.SIGN_POST.id || itemId == Block.WALL_SIGN.id) {
            return Item.SIGN.id;
        }
        if (itemId == Block.SUGAR_CANE_BLOCK.id) {
            return Item.SUGAR_CANE.id;
        }
        if (itemId == Block.WOODEN_DOOR.id) {
            return Item.WOOD_DOOR.id;
        }
        if (itemId == Block.IRON_DOOR_BLOCK.id) {
            return Item.IRON_DOOR.id;
        }
        if (itemId == Block.BED.id) {
            return Item.BED.id;
        }
        if (itemId == Block.CAKE_BLOCK.id) {
            return Item.CAKE.id;
        }
        if (itemId == Block.DIODE_OFF.id || itemId == Block.DIODE_ON.id) {
            return Item.DIODE.id;
        }
        return itemId;
    }

    private static String[] reviewedModernKeys() {
        String[] keys = new String[Item.byId.length];
        expect(keys, 1, "stone");
        expect(keys, 2, "grass_block");
        expect(keys, 3, "dirt");
        expect(keys, 4, "cobblestone");
        expect(keys, 5, "oak_planks");
        expect(keys, 6, "oak_sapling");
        expect(keys, 7, "bedrock");
        expect(keys, 8, "water");
        expect(keys, 9, "water_still");
        expect(keys, 10, "lava");
        expect(keys, 11, "lava_still");
        expect(keys, 12, "sand");
        expect(keys, 13, "gravel");
        expect(keys, 14, "gold_ore");
        expect(keys, 15, "iron_ore");
        expect(keys, 16, "coal_ore");
        expect(keys, 17, "oak_log");
        expect(keys, 18, "oak_leaves");
        expect(keys, 19, "sponge");
        expect(keys, 20, "glass");
        expect(keys, 21, "lapis_ore");
        expect(keys, 22, "lapis_block");
        expect(keys, 23, "dispenser");
        expect(keys, 24, "sandstone");
        expect(keys, 25, "note_block");
        expect(keys, 26, "bed_block");
        expect(keys, 27, "powered_rail");
        expect(keys, 28, "detector_rail");
        expect(keys, 29, "sticky_piston");
        expect(keys, 30, "cobweb");
        expect(keys, 31, "tall_grass");
        expect(keys, 32, "dead_bush");
        expect(keys, 33, "piston");
        expect(keys, 34, "piston_head");
        expect(keys, 35, "white_wool");
        expect(keys, 36, "moving_piston");
        expect(keys, 37, "dandelion");
        expect(keys, 38, "poppy");
        expect(keys, 39, "brown_mushroom");
        expect(keys, 40, "red_mushroom");
        expect(keys, 41, "gold_block");
        expect(keys, 42, "iron_block");
        expect(keys, 43, "double_stone_slab");
        expect(keys, 44, "stone_slab");
        expect(keys, 45, "bricks");
        expect(keys, 46, "tnt");
        expect(keys, 47, "bookshelf");
        expect(keys, 48, "mossy_cobblestone");
        expect(keys, 49, "obsidian");
        expect(keys, 50, "torch");
        expect(keys, 51, "fire");
        expect(keys, 52, "mob_spawner");
        expect(keys, 53, "oak_stairs");
        expect(keys, 54, "chest");
        expect(keys, 55, "redstone_wire");
        expect(keys, 56, "diamond_ore");
        expect(keys, 57, "diamond_block");
        expect(keys, 58, "crafting_table");
        expect(keys, 59, "wheat_block");
        expect(keys, 60, "farmland");
        expect(keys, 61, "furnace");
        expect(keys, 62, "lit_furnace");
        expect(keys, 63, "oak_sign");
        expect(keys, 64, "oak_door_block");
        expect(keys, 65, "ladder");
        expect(keys, 66, "rail");
        expect(keys, 67, "cobblestone_stairs");
        expect(keys, 68, "oak_wall_sign");
        expect(keys, 69, "lever");
        expect(keys, 70, "stone_pressure_plate");
        expect(keys, 71, "iron_door_block");
        expect(keys, 72, "oak_pressure_plate");
        expect(keys, 73, "redstone_ore");
        expect(keys, 74, "lit_redstone_ore");
        expect(keys, 75, "redstone_torch_off");
        expect(keys, 76, "redstone_torch");
        expect(keys, 77, "stone_button");
        expect(keys, 78, "snow");
        expect(keys, 79, "ice");
        expect(keys, 80, "snow_block");
        expect(keys, 81, "cactus");
        expect(keys, 82, "clay");
        expect(keys, 83, "sugar_cane_block");
        expect(keys, 84, "jukebox");
        expect(keys, 85, "oak_fence");
        expect(keys, 86, "pumpkin");
        expect(keys, 87, "netherrack");
        expect(keys, 88, "soul_sand");
        expect(keys, 89, "glowstone");
        expect(keys, 90, "portal");
        expect(keys, 91, "jack_o_lantern");
        expect(keys, 92, "cake_block");
        expect(keys, 93, "repeater_block");
        expect(keys, 94, "lit_repeater");
        expect(keys, 95, "locked_chest");
        expect(keys, 96, "oak_trapdoor");
        expect(keys, 98, "stone_bricks");
        expect(keys, 99, "brown_mushroom_block");
        expect(keys, 100, "red_mushroom_block");
        expect(keys, 101, "wall_clock");
        expect(keys, 103, "melon");
        expect(keys, 104, "pumpkin_stem");
        expect(keys, 105, "melon_stem");
        expect(keys, 108, "brick_stairs");
        expect(keys, 109, "stone_brick_stairs");
        expect(keys, 150, "oak_fence_gate_compat");
        expect(keys, 152, "redstone_block");
        expect(keys, 170, "wet_sponge");
        expect(keys, 173, "coal_block");
        expect(keys, 187, "pumpkin_block");
        expect(keys, 188, "oak_fence_gate");
        expect(keys, 189, "carved_pumpkin_block");

        expect(keys, 256, "iron_shovel");
        expect(keys, 257, "iron_pickaxe");
        expect(keys, 258, "iron_axe");
        expect(keys, 259, "flint_and_steel");
        expect(keys, 260, "apple");
        expect(keys, 261, "bow");
        expect(keys, 262, "arrow");
        expect(keys, 263, "coal");
        expect(keys, 264, "diamond");
        expect(keys, 265, "iron_ingot");
        expect(keys, 266, "gold_ingot");
        expect(keys, 267, "iron_sword");
        expect(keys, 268, "wooden_sword");
        expect(keys, 269, "wooden_shovel");
        expect(keys, 270, "wooden_pickaxe");
        expect(keys, 271, "wooden_axe");
        expect(keys, 272, "stone_sword");
        expect(keys, 273, "stone_shovel");
        expect(keys, 274, "stone_pickaxe");
        expect(keys, 275, "stone_axe");
        expect(keys, 276, "diamond_sword");
        expect(keys, 277, "diamond_shovel");
        expect(keys, 278, "diamond_pickaxe");
        expect(keys, 279, "diamond_axe");
        expect(keys, 280, "stick");
        expect(keys, 281, "bowl");
        expect(keys, 282, "mushroom_stew");
        expect(keys, 283, "golden_sword");
        expect(keys, 284, "golden_shovel");
        expect(keys, 285, "golden_pickaxe");
        expect(keys, 286, "golden_axe");
        expect(keys, 287, "string");
        expect(keys, 288, "feather");
        expect(keys, 289, "gunpowder");
        expect(keys, 290, "wooden_hoe");
        expect(keys, 291, "stone_hoe");
        expect(keys, 292, "iron_hoe");
        expect(keys, 293, "diamond_hoe");
        expect(keys, 294, "golden_hoe");
        expect(keys, 295, "wheat_seeds");
        expect(keys, 296, "wheat");
        expect(keys, 297, "bread");
        expect(keys, 298, "leather_helmet");
        expect(keys, 299, "leather_chestplate");
        expect(keys, 300, "leather_leggings");
        expect(keys, 301, "leather_boots");
        expect(keys, 302, "chainmail_helmet");
        expect(keys, 303, "chainmail_chestplate");
        expect(keys, 304, "chainmail_leggings");
        expect(keys, 305, "chainmail_boots");
        expect(keys, 306, "iron_helmet");
        expect(keys, 307, "iron_chestplate");
        expect(keys, 308, "iron_leggings");
        expect(keys, 309, "iron_boots");
        expect(keys, 310, "diamond_helmet");
        expect(keys, 311, "diamond_chestplate");
        expect(keys, 312, "diamond_leggings");
        expect(keys, 313, "diamond_boots");
        expect(keys, 314, "golden_helmet");
        expect(keys, 315, "golden_chestplate");
        expect(keys, 316, "golden_leggings");
        expect(keys, 317, "golden_boots");
        expect(keys, 318, "flint");
        expect(keys, 319, "porkchop");
        expect(keys, 320, "cooked_porkchop");
        expect(keys, 321, "painting");
        expect(keys, 322, "golden_apple");
        expect(keys, 323, "sign");
        expect(keys, 324, "oak_door");
        expect(keys, 325, "bucket");
        expect(keys, 326, "water_bucket");
        expect(keys, 327, "lava_bucket");
        expect(keys, 328, "minecart");
        expect(keys, 329, "saddle");
        expect(keys, 330, "iron_door");
        expect(keys, 331, "redstone");
        expect(keys, 332, "snowball");
        expect(keys, 333, "boat");
        expect(keys, 334, "leather");
        expect(keys, 335, "milk_bucket");
        expect(keys, 336, "brick");
        expect(keys, 337, "clay_ball");
        expect(keys, 338, "sugar_cane");
        expect(keys, 339, "paper");
        expect(keys, 340, "book");
        expect(keys, 341, "slime_ball");
        expect(keys, 342, "chest_minecart");
        expect(keys, 343, "furnace_minecart");
        expect(keys, 344, "egg");
        expect(keys, 345, "compass");
        expect(keys, 346, "fishing_rod");
        expect(keys, 347, "clock");
        expect(keys, 348, "glowstone_dust");
        expect(keys, 349, "cod");
        expect(keys, 350, "cooked_cod");
        expect(keys, 351, "dye");
        expect(keys, 352, "bone");
        expect(keys, 353, "sugar");
        expect(keys, 354, "cake");
        expect(keys, 355, "bed");
        expect(keys, 356, "repeater");
        expect(keys, 357, "cookie");
        expect(keys, 358, "map");
        expect(keys, 359, "shears");
        expect(keys, 360, "writable_book");
        expect(keys, 361, "written_book");
        expect(keys, 362, "melon_slice");
        expect(keys, 363, "pumpkin_seeds");
        expect(keys, 364, "melon_seeds");
        expect(keys, 365, "lead");
        expect(keys, 366, "name_tag");

        expect(keys, 2256, "record_13");
        expect(keys, 2257, "record_cat");
        expect(keys, 2258, "record_blocks");
        expect(keys, 2259, "record_chirp");
        expect(keys, 2260, "record_far");
        expect(keys, 2261, "record_mall");
        expect(keys, 2262, "record_mellohi");
        expect(keys, 2263, "record_stal");
        expect(keys, 2264, "record_strad");
        expect(keys, 2265, "record_ward");
        expect(keys, 2266, "record_11");
        expect(keys, 2267, "record_wait");
        expect(keys, 2268, "record_aria_math");
        expect(keys, 2269, "record_dog");
        expect(keys, 2270, "record_certitudes");
        expect(keys, 2271, "record_tsuki_no_koibumi");
        return keys;
    }

    private static void expect(String[] keys, int itemId, String path) {
        keys[itemId] = path;
    }
}
