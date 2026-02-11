package net.minecraft.server.registry;

import net.minecraft.server.TileEntity;
import net.minecraft.server.util.ResourceLocation;

import java.lang.reflect.Field;
import java.util.Map;

public final class BlockEntityTypeRegistryBootstrap {
    private static boolean initialized = false;

    private BlockEntityTypeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        try {
            Field nameToClass = findField(TileEntity.class, new String[]{"a","nameToClassMap","nameToClassMapping"});
            if (nameToClass != null) {
                nameToClass.setAccessible(true);
                Object o = nameToClass.get(null);
                if (o instanceof Map) {
                    Map<?,?> map = (Map<?,?>)o;
                    for (Object k : map.keySet()) {
                        String oldName = String.valueOf(k);
                        Class<?> teClazz = (Class<?>)map.get(k);
                        if (teClazz == null) continue;
                        String path = oldName.toLowerCase().replace(' ', '_');
                        BlockEntityTypeRegistryApi.register(new ResourceLocation("minecraft", path), teClazz);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static Field findField(Class<?> cls, String[] names) {
        for (int i = 0; i < names.length; i++) {
            try { return cls.getDeclaredField(names[i]); } catch (Throwable ignored) {}
        }
        return null;
    }
}

