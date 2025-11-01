package net.minecraft.server.registry;

import net.minecraft.server.BiomeBase;
import net.minecraft.server.util.ResourceLocation;

import java.lang.reflect.Field;

public final class BiomeRegistryBootstrap {
    private static boolean initialized = false;

    private BiomeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        try {
            // Register all static BiomeBase fields with namespaced keys
            Field[] fields = BiomeBase.class.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field f = fields[i];
                if ((f.getModifiers() & java.lang.reflect.Modifier.STATIC) == 0) continue;
                if (!BiomeBase.class.isAssignableFrom(f.getType())) continue;
                try {
                    BiomeBase biome = (BiomeBase) f.get(null);
                    if (biome == null) continue;
                    String name = biome.n; // display name set via a(String)
                    if (name == null || name.length() == 0) name = f.getName();
                    String snake = toSnakeCase(name);
                    ResourceLocation key = new ResourceLocation("minecraft", snake);
                    Registries.BIOME.registerIfAbsent(key, biome);
                    // also allow field-name alias if different
                    String fname = toSnakeCase(f.getName());
                    if (!fname.equals(snake)) {
                        Registries.BIOME.registerIfAbsent(new ResourceLocation("minecraft", fname), biome);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static String toSnakeCase(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) out.append('_');
                out.append(Character.toLowerCase(c));
            } else if (c == ' ' || c == '.' || c == '/') {
                out.append('_');
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }
}


