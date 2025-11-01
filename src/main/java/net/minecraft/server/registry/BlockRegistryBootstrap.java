package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.util.ResourceLocation;

public final class BlockRegistryBootstrap {
    private static boolean initialized = false;

    private BlockRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        for (int id = 0; id < Block.byId.length; id++) {
            Block b = Block.byId[id];
            if (b == null) continue;
            String name = b.l();
            if (name == null || name.length() == 0) name = "block_" + id;
            if (name.startsWith("tile.")) name = name.substring(5);
            String snake = toSnakeCase(name);
            ResourceLocation key = new ResourceLocation("minecraft", snake);
            Registries.BLOCK.registerIfAbsent(key, b);
            if ("stone_brick_smooth_stairs".equals(snake) || "smooth_stone_brick_stairs".equals(snake)) {
                Registries.BLOCK.registerIfAbsent(new ResourceLocation("minecraft","stone_brick_stairs"), b);
            }
        }
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
}


