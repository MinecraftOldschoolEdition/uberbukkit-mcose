package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

/**
 * Canonical namespaced keys for built-in achievements.
 */
public final class AchievementKeys {
    public static final int EXPECTED_COUNT = 40;

    public static final ResourceLocation OPEN_INVENTORY = create("open_inventory");
    public static final ResourceLocation MINE_WOOD = create("mine_wood");
    public static final ResourceLocation BUILD_WORKBENCH = create("build_workbench");
    public static final ResourceLocation BUILD_PICKAXE = create("build_pickaxe");
    public static final ResourceLocation BUILD_FURNACE = create("build_furnace");
    public static final ResourceLocation ACQUIRE_IRON = create("acquire_iron");
    public static final ResourceLocation BUILD_BETTER_PICKAXE = create("build_better_pickaxe");
    public static final ResourceLocation BUILD_HOE = create("build_hoe");
    public static final ResourceLocation MAKE_BREAD = create("make_bread");
    public static final ResourceLocation BAKE_CAKE = create("bake_cake");
    public static final ResourceLocation GROW_WHEAT = create("grow_wheat");
    public static final ResourceLocation COOK_FISH = create("cook_fish");
    public static final ResourceLocation COOK_BACON = create("cook_bacon");
    public static final ResourceLocation DIAMONDS = create("diamonds");
    public static final ResourceLocation OBSIDIAN = create("obsidian");
    public static final ResourceLocation HOT_STUFF = create("hot_stuff");
    public static final ResourceLocation PORTAL = create("portal");
    public static final ResourceLocation GHAST_HUNTER = create("ghast_hunter");
    public static final ResourceLocation BLAZING_HELL = create("blazing_hell");
    public static final ResourceLocation BUILD_SWORD = create("build_sword");
    public static final ResourceLocation KILL_ENEMY = create("kill_enemy");
    public static final ResourceLocation KILL_COW = create("kill_cow");
    public static final ResourceLocation FLY_PIG = create("fly_pig");
    public static final ResourceLocation OVERKILL = create("overkill");
    public static final ResourceLocation BUILD_BOW = create("build_bow");
    public static final ResourceLocation SNIPE_SKELETON = create("snipe_skeleton");
    public static final ResourceLocation FULL_IRON = create("full_iron");
    public static final ResourceLocation FULL_DIAMOND = create("full_diamond");
    public static final ResourceLocation SLEEP_IN_BED = create("sleep_in_bed");
    public static final ResourceLocation EGG_HUNT = create("egg_hunt");
    public static final ResourceLocation SHEAR_SHEEP = create("shear_sheep");
    public static final ResourceLocation RAINBOW_WOOL = create("rainbow_wool");
    public static final ResourceLocation CRAFT_MAP = create("craft_map");
    public static final ResourceLocation BOOKSHELF = create("bookshelf");
    public static final ResourceLocation JUKEBOX = create("jukebox");
    public static final ResourceLocation EXPLOSION = create("explosion");
    public static final ResourceLocation PISTON = create("piston");
    public static final ResourceLocation REPEATER = create("repeater");
    public static final ResourceLocation ON_A_RAIL = create("on_a_rail");
    public static final ResourceLocation BOAT_TRAVEL = create("boat_travel");

    private AchievementKeys() {
    }

    private static ResourceLocation create(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
