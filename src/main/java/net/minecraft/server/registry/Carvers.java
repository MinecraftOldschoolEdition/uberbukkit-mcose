package net.minecraft.server.registry;

import net.minecraft.server.*;
import net.minecraft.server.util.ResourceLocation;

public final class Carvers {
    private Carvers() {}

    public static MapGenBase create(ResourceLocation key) {
        if (key == null) return new MapGenCaves();
        String path = key.getPath();
        if ("nether_cave".equals(path)) return new MapGenCavesHell();
        return new MapGenCaves();
    }

    public static MapGenBase create(String namespaced) {
        ResourceLocation key = namespaced.indexOf(':') >= 0 ? new ResourceLocation(namespaced) : new ResourceLocation("minecraft", namespaced);
        CarverType t = Registries.CARVER.get(key);
        if (t == null) return create(key);
        return create(key);
    }
}



