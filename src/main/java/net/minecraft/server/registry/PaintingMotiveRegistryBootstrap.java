package net.minecraft.server.registry;

import net.minecraft.server.EnumArt;
import net.minecraft.server.util.ResourceLocation;

public final class PaintingMotiveRegistryBootstrap {
    private static boolean initialized = false;

    private PaintingMotiveRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            for (EnumArt art : EnumArt.values()) {
                String title = art.A; // display name string
                if (title == null || title.length() == 0) title = art.name();
                String snake = toSnake(title);
                PaintingMotiveRegistryApi.register(new ResourceLocation("minecraft", snake), art);
                // Alias: enum constant name (lowercase)
                String lc = art.name().toLowerCase();
                if (!lc.equals(snake)) {
                    PaintingMotiveRegistryApi.register(new ResourceLocation("minecraft", lc), art);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static String toSnake(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) { if (i > 0) out.append('_'); out.append(Character.toLowerCase(c)); }
            else if (c == ' ' || c == '-') { out.append('_'); }
            else { out.append(Character.toLowerCase(c)); }
        }
        return out.toString();
    }
}

