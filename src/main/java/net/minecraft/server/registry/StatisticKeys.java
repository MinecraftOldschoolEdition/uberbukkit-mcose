package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

/**
 * Canonical namespaced keys for built-in player statistics.
 */
public final class StatisticKeys {
    public static final String PREFIX_GENERIC = "generic/";
    public static final String PREFIX_BLOCK_MINED = "block/mined/";
    public static final String PREFIX_ITEM_USED = "item/used/";
    public static final String PREFIX_ITEM_CRAFTED = "item/crafted/";
    public static final String PREFIX_ITEM_DEPLETED = "item/depleted/";
    public static final String PREFIX_ITEM_PICKUP = "item/pickup/";

    public static final ResourceLocation START_GAME = create(PREFIX_GENERIC + "start_game");
    public static final ResourceLocation CREATE_WORLD = create(PREFIX_GENERIC + "create_world");
    public static final ResourceLocation LOAD_WORLD = create(PREFIX_GENERIC + "load_world");
    public static final ResourceLocation JOIN_MULTIPLAYER = create(PREFIX_GENERIC + "join_multiplayer");
    public static final ResourceLocation LEAVE_GAME = create(PREFIX_GENERIC + "leave_game");
    public static final ResourceLocation PLAY_ONE_MINUTE = create(PREFIX_GENERIC + "play_one_minute");
    public static final ResourceLocation DISTANCE_WALKED_CM = create(PREFIX_GENERIC + "distance_walked_cm");
    public static final ResourceLocation DISTANCE_SWUM_CM = create(PREFIX_GENERIC + "distance_swum_cm");
    public static final ResourceLocation DISTANCE_FALLEN_CM = create(PREFIX_GENERIC + "distance_fallen_cm");
    public static final ResourceLocation DISTANCE_CLIMBED_CM = create(PREFIX_GENERIC + "distance_climbed_cm");
    public static final ResourceLocation DISTANCE_FLOWN_CM = create(PREFIX_GENERIC + "distance_flown_cm");
    public static final ResourceLocation DISTANCE_DOVE_CM = create(PREFIX_GENERIC + "distance_dove_cm");
    public static final ResourceLocation DISTANCE_BY_MINECART_CM = create(PREFIX_GENERIC + "distance_by_minecart_cm");
    public static final ResourceLocation DISTANCE_BY_BOAT_CM = create(PREFIX_GENERIC + "distance_by_boat_cm");
    public static final ResourceLocation DISTANCE_BY_PIG_CM = create(PREFIX_GENERIC + "distance_by_pig_cm");
    public static final ResourceLocation JUMP = create(PREFIX_GENERIC + "jump");
    public static final ResourceLocation DROP = create(PREFIX_GENERIC + "drop");
    public static final ResourceLocation DAMAGE_DEALT = create(PREFIX_GENERIC + "damage_dealt");
    public static final ResourceLocation DAMAGE_TAKEN = create(PREFIX_GENERIC + "damage_taken");
    public static final ResourceLocation DEATHS = create(PREFIX_GENERIC + "deaths");
    public static final ResourceLocation MOB_KILLS = create(PREFIX_GENERIC + "mob_kills");
    public static final ResourceLocation PLAYER_KILLS = create(PREFIX_GENERIC + "player_kills");
    public static final ResourceLocation FISH_CAUGHT = create(PREFIX_GENERIC + "fish_caught");

    private StatisticKeys() {
    }

    private static ResourceLocation create(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
