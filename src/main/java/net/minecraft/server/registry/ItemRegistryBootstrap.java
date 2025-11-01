package net.minecraft.server.registry;

import net.minecraft.server.Item;
import net.minecraft.server.util.ResourceLocation;

public final class ItemRegistryBootstrap {
    private static boolean initialized = false;

    private ItemRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        for (int id = 0; id < Item.byId.length; id++) {
            Item it = Item.byId[id];
            if (it == null) continue;
            String name = it.a();
            if (name == null || name.length() == 0) name = "item_" + id;
            if (name.startsWith("item.")) name = name.substring(5);
            String snake = toSnakeCase(name);
            ResourceLocation primary = new ResourceLocation("minecraft", snake);
            Registries.ITEM.registerIfAbsent(primary, it);
            // If this is a block item for the brick block, expose a unique alias to avoid collision with the clay brick item
            if ("brick".equals(snake) && it instanceof net.minecraft.server.ItemBlock) {
                Registries.ITEM.registerIfAbsent(new ResourceLocation("minecraft","brick_block"), it);
            }

            // Slab variants: map subtype keys to single slab item; bound by version support
            if ("stone_slab".equals(snake) || "stone_slab_top".equals(snake) || "stone_slab_half".equals(snake) || "stairSingle".equals(snake)) {
                // Beta server supports 4 slab metas: stone(0), sand(1), wood(2), cobble(3)
                registerSlabVariant(it, "stone_slab", 0);
                registerSlabVariant(it, "sand_slab", 1);
                // Alias sandstone_slab -> sand_slab for convenience
                registerSlabVariant(it, "sandstone_slab", 1);
                registerSlabVariant(it, "wooden_slab", 2);
                registerSlabVariant(it, "cobblestone_slab", 3);
            }

            // Rename stone_brick_smooth_stairs -> stone_brick_stairs for canonical name
            if ("stone_brick_smooth_stairs".equals(snake) || "smooth_stone_brick_stairs".equals(snake)) {
                Registries.ITEM.registerIfAbsent(new ResourceLocation("minecraft","stone_brick_stairs"), it);
            }

            // Dyes: register color_dye keys with metadata defaults
            if (it instanceof net.minecraft.server.ItemDye) {
                String[] dyeColors = net.minecraft.server.ItemDye.a;
                for (int dm = 0; dm < dyeColors.length; dm++) {
                    String cname = canonicalizeDyeColor(toSnakeCase(dyeColors[dm]));
                    ResourceLocation rl = new ResourceLocation("minecraft", cname + "_dye");
                    Registries.ITEM.registerIfAbsent(rl, it);
                    VariantDefaults.put(rl, dm);
                }
            }
            // Wool: register color_wool keys with metadata defaults
            if (it instanceof net.minecraft.server.ItemCloth) {
                String[] woolColors = new String[]{
                    "white","orange","magenta","light_blue","yellow","lime","pink","gray",
                    "light_gray","cyan","purple","blue","brown","green","red","black"
                };
                for (int dm = 0; dm < woolColors.length; dm++) {
                    ResourceLocation rl = new ResourceLocation("minecraft", woolColors[dm] + "_wool");
                    Registries.ITEM.registerIfAbsent(rl, it);
                    VariantDefaults.put(rl, dm);
                }
            }
            // Logs: oak/spruce/birch
            if (it instanceof net.minecraft.server.ItemLog) {
                registerMeta(it, "oak_log", 0);
                registerMeta(it, "spruce_log", 1);
                registerMeta(it, "birch_log", 2);
            }
            // Saplings: oak/spruce/birch
            if (it instanceof net.minecraft.server.ItemSapling) {
                registerMeta(it, "oak_sapling", 0);
                registerMeta(it, "spruce_sapling", 1);
                registerMeta(it, "birch_sapling", 2);
            }
            // Stone bricks
            if (it instanceof net.minecraft.server.ItemStoneBrick) {
                registerMeta(it, "stone_brick", 0);
                registerMeta(it, "mossy_stone_brick", 1);
                registerMeta(it, "cracked_stone_brick", 2);
                registerMeta(it, "chiseled_stone_brick", 3);
            }
        }
    }

    private static void registerSlabVariant(Item it, String keyPath, int damage) {
        ResourceLocation rl = new ResourceLocation("minecraft", keyPath);
        Registries.ITEM.registerIfAbsent(rl, it);
        VariantDefaults.put(rl, damage);
    }

    private static void registerMeta(Item it, String keyPath, int damage) {
        ResourceLocation rl = new ResourceLocation("minecraft", keyPath);
        Registries.ITEM.registerIfAbsent(rl, it);
        VariantDefaults.put(rl, damage);
    }

    private static String toSnakeCase(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) out.append('_');
                out.append(Character.toLowerCase(c));
            } else if (c == '.') {
                out.append('_');
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    private static String canonicalizeDyeColor(String s) {
        if ("lightblue".equals(s)) return "light_blue";
        if ("silver".equals(s)) return "light_gray";
        return s;
    }
}


