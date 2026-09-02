package net.minecraft.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.server.util.ResourceLocation;

/** Stable registry keys used by the legacy-supported damage source set. */
public final class DamageTypes {
    public static final ResourceLocation GENERIC = key("generic");
    public static final ResourceLocation PLAYER_ATTACK = key("player_attack");
    public static final ResourceLocation MOB_ATTACK = key("mob_attack");
    /** Compatibility aliases for the pre-registry server damage adapter. */
    public static final ResourceLocation PLAYER = PLAYER_ATTACK;
    public static final ResourceLocation MOB = MOB_ATTACK;
    public static final ResourceLocation ARROW = key("arrow");
    public static final ResourceLocation FIREBALL = key("fireball");
    public static final ResourceLocation EXPLOSION = key("explosion");
    public static final ResourceLocation IN_FIRE = key("in_fire");
    public static final ResourceLocation ON_FIRE = key("on_fire");
    public static final ResourceLocation LAVA = key("lava");
    public static final ResourceLocation IN_WALL = key("in_wall");
    public static final ResourceLocation DROWN = key("drown");
    public static final ResourceLocation FALL = key("fall");
    public static final ResourceLocation OUT_OF_WORLD = key("out_of_world");
    public static final ResourceLocation CACTUS = key("cactus");
    public static final ResourceLocation LIGHTNING_BOLT = key("lightning_bolt");

    private static final List<ResourceLocation> BUILT_IN_TYPES =
            Collections.unmodifiableList(Arrays.asList(
                    GENERIC, PLAYER_ATTACK, MOB_ATTACK, ARROW, FIREBALL,
                    EXPLOSION, IN_FIRE, ON_FIRE, LAVA, IN_WALL, DROWN,
                    FALL, OUT_OF_WORLD, CACTUS, LIGHTNING_BOLT));

    private DamageTypes() {}

    public static List<ResourceLocation> builtInTypes() {
        return BUILT_IN_TYPES;
    }

    public static ResourceLocation canonicalizeLegacy(ResourceLocation key) {
        if (key == null) return GENERIC;
        if ("minecraft".equals(key.getNamespace())) {
            if ("player".equals(key.getPath())) return PLAYER_ATTACK;
            if ("mob".equals(key.getPath())) return MOB_ATTACK;
        }
        return key;
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
