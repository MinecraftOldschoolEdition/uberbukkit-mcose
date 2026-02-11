package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

public final class ParticleTypeRegistryBootstrap {
    private static boolean initialized = false;

    private ParticleTypeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        reg("bubble", new ParticleType("bubble"));
        reg("smoke", new ParticleType("smoke"));
        reg("note", new ParticleType("note"));
        reg("portal", new ParticleType("portal"));
        reg("explosion", new ParticleType("explode"));
        reg("huge_explosion", new ParticleType("hugeexplosion"));
        reg("flame", new ParticleType("flame"));
        reg("lava", new ParticleType("lava"));
        reg("footstep", new ParticleType("footstep"));
        reg("splash", new ParticleType("splash"));
        reg("large_smoke", new ParticleType("largesmoke"));
        reg("red_dust", new ParticleType("reddust"));
        reg("snowball", new ParticleType("snowballpoof"));
        reg("snow_shovel", new ParticleType("snowshovel"));
        reg("slime", new ParticleType("slime"));
        reg("heart", new ParticleType("heart"));
    }

    private static void reg(String path, ParticleType t) {
        try { ParticleTypeRegistryApi.register(new ResourceLocation("minecraft", path), t); } catch (Throwable ignored) {}
    }
}

