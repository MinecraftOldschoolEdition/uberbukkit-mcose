package net.minecraft.server.registry;

import net.minecraft.server.BiomeBase;
import net.minecraft.server.util.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class BiomeRegistryBootstrap {
    private static boolean initialized = false;

    private BiomeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        // Establish primary keys explicitly before adding any compatibility
        // aliases. Display-name derivation previously produced keys such as
        // seasonal__forest and could silently publish an incomplete registry.
        registerPrimary("rainforest", BiomeBase.RAINFOREST);
        registerPrimary("swampland", BiomeBase.SWAMPLAND);
        registerPrimary("seasonal_forest", BiomeBase.SEASONAL_FOREST);
        registerPrimary("forest", BiomeBase.FOREST);
        registerPrimary("savanna", BiomeBase.SAVANNA);
        registerPrimary("shrubland", BiomeBase.SHRUBLAND);
        registerPrimary("taiga", BiomeBase.TAIGA);
        registerPrimary("desert", BiomeBase.DESERT);
        registerPrimary("plains", BiomeBase.PLAINS);
        registerPrimary("ice_desert", BiomeBase.ICE_DESERT);
        registerPrimary("tundra", BiomeBase.TUNDRA);
        registerPrimary("hell", BiomeBase.HELL);
        registerPrimary("sky", BiomeBase.SKY);

        // Preserve reflective aliases for any additional static biome fields.
        Field[] fields = BiomeBase.class.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if ((field.getModifiers() & Modifier.STATIC) == 0
                    || !BiomeBase.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                BiomeBase biome = (BiomeBase)field.get(null);
                if (biome == null) continue;
                String fieldAlias = toSnakeCase(field.getName());
                registerAlias(fieldAlias, biome);
                if (biome.n != null && biome.n.length() > 0) {
                    registerAlias(toSnakeCase(biome.n), biome);
                }
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException(
                        "Could not inspect biome field " + field.getName(), failure);
            }
        }

        registerAlias("alpha_plains", BiomeBase.PLAINS);
        registerAlias("alpha_taiga", BiomeBase.TAIGA);
        registerAlias("flat_plains", BiomeBase.PLAINS);
        registerAlias("classic_plains", BiomeBase.PLAINS);
        initialized = true;
    }

    private static void registerPrimary(String path, BiomeBase biome) {
        ResourceLocation key = new ResourceLocation("minecraft", path);
        if (biome == null || !BiomeRegistryApi.register(key, biome)
                || BiomeRegistryApi.get(key) != biome
                || !key.equals(BiomeRegistryApi.getKey(biome))) {
            throw new IllegalStateException("Could not register primary biome " + key);
        }
    }

    private static void registerAlias(String path, BiomeBase biome) {
        if (path == null || path.length() == 0 || biome == null) return;
        ResourceLocation key = new ResourceLocation("minecraft", path);
        if (!BiomeRegistryApi.register(key, biome)
                || BiomeRegistryApi.get(key) != biome) {
            throw new IllegalStateException("Could not register biome alias " + key);
        }
    }

    private static String toSnakeCase(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '_') {
                    out.append('_');
                }
                out.append(Character.toLowerCase(c));
            } else if (c == ' ' || c == '.' || c == '/') {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '_') {
                    out.append('_');
                }
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }
}
