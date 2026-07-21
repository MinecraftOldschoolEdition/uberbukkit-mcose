package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.ItemBlock;
import net.minecraft.server.ItemRecord;
import net.minecraft.server.util.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Modern, namespaced identity for built-in server content.
 *
 * Legacy numeric IDs are still projected for old saves, old packets, and the
 * Bukkit plugin API, but vanilla identity should come from these keys.
 */
public final class VanillaRegistryKeys {
    private static final Map<String, String> BLOCK_FIELD_OVERRIDES = new HashMap<String, String>();
    private static final Map<String, String> ITEM_FIELD_OVERRIDES = new HashMap<String, String>();
    private static final Map<String, String> ENTITY_NAME_OVERRIDES = new HashMap<String, String>();

    private static IdentityHashMap<Block, ResourceLocation> blockKeys;
    private static IdentityHashMap<Item, ResourceLocation> itemKeys;

    static {
        block("GRASS", "grass_block");
        block("WOOD", "oak_planks");
        block("SAPLING", "oak_sapling");
        block("WATER", "water");
        block("STATIONARY_WATER", "water_still");
        block("LAVA", "lava");
        block("STATIONARY_LAVA", "lava_still");
        block("LOG", "oak_log");
        block("LEAVES", "oak_leaves");
        block("WET_SPONGE", "wet_sponge");
        block("WALL_CLOCK", "wall_clock");
        block("SANDSTONE", "sandstone");
        block("NOTE_BLOCK", "note_block");
        block("GOLDEN_RAIL", "powered_rail");
        block("DETECTOR_RAIL", "detector_rail");
        block("PISTON_STICKY", "sticky_piston");
        block("PISTON", "piston");
        block("PISTON_EXTENSION", "piston_head");
        block("PISTON_MOVING", "moving_piston");
        block("WEB", "cobweb");
        block("LONG_GRASS", "tall_grass");
        block("DEAD_BUSH", "dead_bush");
        block("WOOL", "white_wool");
        block("YELLOW_FLOWER", "dandelion");
        block("RED_ROSE", "poppy");
        block("BROWN_MUSHROOM", "brown_mushroom");
        block("RED_MUSHROOM", "red_mushroom");
        block("GOLD_BLOCK", "gold_block");
        block("IRON_BLOCK", "iron_block");
        block("DOUBLE_STEP", "double_stone_slab");
        block("STEP", "stone_slab");
        block("BRICK", "bricks");
        block("BOOKSHELF", "bookshelf");
        block("MOSSY_COBBLESTONE", "mossy_cobblestone");
        block("TORCH", "torch");
        block("MOB_SPAWNER", "mob_spawner");
        block("WOOD_STAIRS", "oak_stairs");
        block("COBBLESTONE_STAIRS", "cobblestone_stairs");
        block("REDSTONE_WIRE", "redstone_wire");
        block("WORKBENCH", "crafting_table");
        block("CROPS", "wheat");
        block("SOIL", "farmland");
        block("FURNACE", "furnace");
        block("BURNING_FURNACE", "lit_furnace");
        block("SIGN_POST", "oak_sign");
        block("WALL_SIGN", "oak_wall_sign");
        block("RAILS", "rail");
        block("WOODEN_DOOR", "oak_door");
        block("IRON_DOOR_BLOCK", "iron_door");
        block("STONE_PLATE", "stone_pressure_plate");
        block("WOOD_PLATE", "oak_pressure_plate");
        block("GLOWING_REDSTONE_ORE", "lit_redstone_ore");
        block("REDSTONE_TORCH_OFF", "redstone_torch_off");
        block("REDSTONE_TORCH_ON", "redstone_torch");
        block("STONE_BUTTON", "stone_button");
        block("SNOW_BLOCK", "snow_block");
        block("CLAY", "clay");
        block("SUGAR_CANE_BLOCK", "sugar_cane");
        block("FENCE", "oak_fence");
        block("SOUL_SAND", "soul_sand");
        block("GLOWSTONE", "glowstone");
        block("PUMPKIN", "pumpkin_state");
        block("PUMPKIN_PLAIN", "pumpkin");
        block("CARVED_PUMPKIN", "carved_pumpkin");
        block("JACK_O_LANTERN", "jack_o_lantern");
        block("PUMPKIN_STEM", "pumpkin_stem");
        block("MELON_STEM", "melon_stem");
        block("DIODE_OFF", "repeater");
        block("DIODE_ON", "lit_repeater");
        block("LOCKED_CHEST", "locked_chest");
        block("CAKE_BLOCK", "cake");
        block("TRAP_DOOR", "oak_trapdoor");
        block("FENCE_GATE_COMPAT", "oak_fence_gate_compat");
        block("FENCE_GATE", "oak_fence_gate");
        block("STONE_BRICK", "stone_bricks");
        block("STONE_BRICK_STAIRS", "stone_brick_stairs");
        block("BRICK_STAIRS", "brick_stairs");
        block("BROWN_MUSHROOM_CAP", "brown_mushroom_block");
        block("RED_MUSHROOM_CAP", "red_mushroom_block");

        item("IRON_SPADE", "iron_shovel");
        item("IRON_PICKAXE", "iron_pickaxe");
        item("IRON_AXE", "iron_axe");
        item("IRON_INGOT", "iron_ingot");
        item("GOLD_INGOT", "gold_ingot");
        item("APPLE", "apple");
        item("BOWL", "bowl");
        item("MUSHROOM_SOUP", "mushroom_stew");
        item("IRON_SWORD", "iron_sword");
        item("WOOD_SWORD", "wooden_sword");
        item("WOOD_SPADE", "wooden_shovel");
        item("WOOD_PICKAXE", "wooden_pickaxe");
        item("WOOD_AXE", "wooden_axe");
        item("STONE_SPADE", "stone_shovel");
        item("DIAMOND_SPADE", "diamond_shovel");
        item("GOLD_SWORD", "golden_sword");
        item("GOLD_SPADE", "golden_shovel");
        item("GOLD_PICKAXE", "golden_pickaxe");
        item("GOLD_AXE", "golden_axe");
        item("STRING", "string");
        item("SULPHUR", "gunpowder");
        item("WOOD_HOE", "wooden_hoe");
        item("IRON_HOE", "iron_hoe");
        item("GOLD_HOE", "golden_hoe");
        item("SEEDS", "wheat_seeds");
        item("IRON_HELMET", "iron_helmet");
        item("IRON_CHESTPLATE", "iron_chestplate");
        item("IRON_LEGGINGS", "iron_leggings");
        item("IRON_BOOTS", "iron_boots");
        item("CHAINMAIL_HELMET", "chainmail_helmet");
        item("CHAINMAIL_CHESTPLATE", "chainmail_chestplate");
        item("CHAINMAIL_LEGGINGS", "chainmail_leggings");
        item("CHAINMAIL_BOOTS", "chainmail_boots");
        item("LEATHER_HELMET", "leather_helmet");
        item("LEATHER_CHESTPLATE", "leather_chestplate");
        item("LEATHER_LEGGINGS", "leather_leggings");
        item("LEATHER_BOOTS", "leather_boots");
        item("GOLD_HELMET", "golden_helmet");
        item("GOLD_CHESTPLATE", "golden_chestplate");
        item("GOLD_LEGGINGS", "golden_leggings");
        item("GOLD_BOOTS", "golden_boots");
        item("PORK", "porkchop");
        item("GRILLED_PORK", "cooked_porkchop");
        item("GOLDEN_APPLE", "golden_apple");
        item("WOOD_DOOR", "oak_door");
        item("BUCKET", "bucket");
        item("WATER_BUCKET", "water_bucket");
        item("LAVA_BUCKET", "lava_bucket");
        item("MINECART", "minecart");
        item("IRON_DOOR", "iron_door");
        item("MILK_BUCKET", "milk_bucket");
        item("SNOW_BALL", "snowball");
        item("CLAY_BRICK", "brick");
        item("CLAY_BALL", "clay_ball");
        item("SUGAR_CANE", "sugar_cane");
        item("STORAGE_MINECART", "chest_minecart");
        item("POWERED_MINECART", "furnace_minecart");
        item("WATCH", "clock");
        item("GLOWSTONE_DUST", "glowstone_dust");
        item("RAW_FISH", "cod");
        item("COOKED_FISH", "cooked_cod");
        item("INK_SACK", "dye");
        item("STONE_BRICK_ITEM", "stone_bricks");
        item("DIODE", "repeater");
        item("MAP", "map");
        item("WRITABLE_BOOK", "writable_book");
        item("PUMPKIN_SEED", "pumpkin_seeds");
        item("MELON_SEED", "melon_seeds");
        item("NAME_TAG", "name_tag");

        entity("SnowMan", "snow_golem");
        entity("snowman", "snow_golem");
        entity("snow_man", "snow_golem");
        entity("PrimedTnt", "tnt");
        entity("primedtnt", "tnt");
        entity("primed_tnt", "tnt");
        entity("FallingSand", "falling_block");
        entity("fallingsand", "falling_block");
        entity("falling_sand", "falling_block");
        entity("PigZombie", "zombified_piglin");
        entity("pigzombie", "zombified_piglin");
        entity("pig_zombie", "zombified_piglin");
        entity("MapHanging", "map_hanging");
        entity("maphanging", "map_hanging");
    }

    private VanillaRegistryKeys() {}

    public static ResourceLocation blockKey(Block block) {
        if (block == null) {
            return null;
        }
        ensureBlockKeys();
        return blockKeys.get(block);
    }

    static boolean isCanonicalBlockKeyForOther(ResourceLocation key, Block block) {
        if (key == null) {
            return false;
        }
        ensureBlockKeys();
        for (Map.Entry<Block, ResourceLocation> entry : blockKeys.entrySet()) {
            if (key.equals(entry.getValue())) {
                return entry.getKey() != block;
            }
        }
        return isDeclaredCanonicalBlockKey(key);
    }

    public static ResourceLocation itemKey(Item item) {
        if (item == null) {
            return null;
        }
        if (item instanceof ItemRecord) {
            String record = ((ItemRecord)item).a;
            if (record != null && record.length() > 0) {
                return minecraft("record_" + sanitizePath(record));
            }
        }
        ensureItemKeys();
        ResourceLocation key = itemKeys.get(item);
        if (key != null) {
            return key;
        }
        if (item instanceof ItemBlock && item.id >= 0 && item.id < Block.byId.length) {
            ResourceLocation blockKey = blockKey(Block.byId[item.id]);
            if (blockKey != null) {
                return blockKey;
            }
        }
        return null;
    }

    static boolean isCanonicalItemKeyForOther(ResourceLocation key, Item item) {
        if (key == null) {
            return false;
        }
        ensureItemKeys();
        for (Map.Entry<Item, ResourceLocation> entry : itemKeys.entrySet()) {
            if (key.equals(entry.getValue())) {
                return entry.getKey() != item;
            }
        }
        if (item instanceof ItemBlock) {
            Block block = item.id >= 0 && item.id < Block.byId.length ? Block.byId[item.id] : null;
            if (isCanonicalBlockKeyForOther(key, block)) {
                return true;
            }
        }
        return isDeclaredCanonicalItemKey(key);
    }

    static boolean isCanonicalBlockItemKeyForOther(ResourceLocation key, Item item) {
        Block block = item instanceof ItemBlock && item.id >= 0 && item.id < Block.byId.length
                ? Block.byId[item.id]
                : null;
        return isCanonicalBlockKeyForOther(key, block);
    }

    public static ResourceLocation entityKey(String legacyName) {
        if (legacyName == null || legacyName.length() == 0) {
            return null;
        }
        String override = ENTITY_NAME_OVERRIDES.get(legacyName);
        if (override != null) {
            return minecraft(override);
        }
        return minecraft(RegistryKeyPolicy.toSnakeCase(legacyName));
    }

    private static synchronized void ensureBlockKeys() {
        if (blockKeys == null) {
            blockKeys = new IdentityHashMap<Block, ResourceLocation>();
        }
        Field[] fields = Block.class.getFields();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (!Modifier.isStatic(field.getModifiers()) || !Block.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                Block block = (Block)field.get(null);
                if (block == null || blockKeys.containsKey(block)) {
                    continue;
                }
                String path = BLOCK_FIELD_OVERRIDES.get(field.getName());
                if (path == null) {
                    path = RegistryKeyPolicy.canonicalizePath(RegistryKeyPolicy.toSnakeCase(field.getName()));
                }
                blockKeys.put(block, minecraft(path));
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isDeclaredCanonicalBlockKey(ResourceLocation key) {
        Field[] fields = Block.class.getFields();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (!Modifier.isStatic(field.getModifiers()) || !Block.class.isAssignableFrom(field.getType())) {
                continue;
            }
            String path = BLOCK_FIELD_OVERRIDES.get(field.getName());
            if (path == null) {
                path = RegistryKeyPolicy.canonicalizePath(RegistryKeyPolicy.toSnakeCase(field.getName()));
            }
            if (key.equals(minecraft(path))) {
                return true;
            }
        }
        return false;
    }

    private static synchronized void ensureItemKeys() {
        if (itemKeys == null) {
            itemKeys = new IdentityHashMap<Item, ResourceLocation>();
        }
        Field[] fields = Item.class.getFields();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (!Modifier.isStatic(field.getModifiers()) || !Item.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                Item item = (Item)field.get(null);
                if (item == null || itemKeys.containsKey(item)) {
                    continue;
                }
                String path = ITEM_FIELD_OVERRIDES.get(field.getName());
                if (path == null) {
                    path = RegistryKeyPolicy.canonicalizePath(RegistryKeyPolicy.toSnakeCase(field.getName()));
                }
                itemKeys.put(item, minecraft(path));
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isDeclaredCanonicalItemKey(ResourceLocation key) {
        Field[] fields = Item.class.getFields();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (!Modifier.isStatic(field.getModifiers()) || !Item.class.isAssignableFrom(field.getType())) {
                continue;
            }
            String path = ITEM_FIELD_OVERRIDES.get(field.getName());
            if (path == null) {
                path = RegistryKeyPolicy.canonicalizePath(RegistryKeyPolicy.toSnakeCase(field.getName()));
            }
            if (key.equals(minecraft(path))) {
                return true;
            }
        }
        return false;
    }

    private static void block(String fieldName, String path) {
        BLOCK_FIELD_OVERRIDES.put(fieldName, path);
    }

    private static void item(String fieldName, String path) {
        ITEM_FIELD_OVERRIDES.put(fieldName, path);
    }

    private static void entity(String legacyName, String path) {
        ENTITY_NAME_OVERRIDES.put(legacyName, path);
    }

    private static ResourceLocation minecraft(String path) {
        return new ResourceLocation(RegistryKeyPolicy.DEFAULT_NAMESPACE, path);
    }

    private static String sanitizePath(String value) {
        String lower = RegistryKeyPolicy.normalizePath(value);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '/' || c == '-') {
                out.append(c);
            }
        }
        return out.toString();
    }
}
