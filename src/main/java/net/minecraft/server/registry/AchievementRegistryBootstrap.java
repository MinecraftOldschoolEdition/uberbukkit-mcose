package net.minecraft.server.registry;

import net.minecraft.server.AchievementList;

/**
 * Bootstrap for the Achievement registry.
 * Registers the built-in achievements.
 */
public final class AchievementRegistryBootstrap {
    private static boolean initialized = false;

    private AchievementRegistryBootstrap() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        AchievementRegistryApi.register(AchievementKeys.OPEN_INVENTORY, AchievementList.openInventory);
        AchievementRegistryApi.register(AchievementKeys.MINE_WOOD, AchievementList.mineWood);
        AchievementRegistryApi.register(AchievementKeys.BUILD_WORKBENCH, AchievementList.buildWorkBench);
        AchievementRegistryApi.register(AchievementKeys.BUILD_PICKAXE, AchievementList.buildPickaxe);
        AchievementRegistryApi.register(AchievementKeys.BUILD_FURNACE, AchievementList.buildFurnace);
        AchievementRegistryApi.register(AchievementKeys.ACQUIRE_IRON, AchievementList.acquireIron);
        AchievementRegistryApi.register(AchievementKeys.BUILD_BETTER_PICKAXE, AchievementList.buildBetterPickaxe);
        AchievementRegistryApi.register(AchievementKeys.BUILD_HOE, AchievementList.buildHoe);
        AchievementRegistryApi.register(AchievementKeys.MAKE_BREAD, AchievementList.makeBread);
        AchievementRegistryApi.register(AchievementKeys.BAKE_CAKE, AchievementList.bakeCake);
        AchievementRegistryApi.register(AchievementKeys.GROW_WHEAT, AchievementList.growWheat);
        AchievementRegistryApi.register(AchievementKeys.COOK_FISH, AchievementList.cookFish);
        AchievementRegistryApi.register(AchievementKeys.COOK_BACON, AchievementList.cookBacon);
        AchievementRegistryApi.register(AchievementKeys.DIAMONDS, AchievementList.diamonds);
        AchievementRegistryApi.register(AchievementKeys.OBSIDIAN, AchievementList.obsidian);
        AchievementRegistryApi.register(AchievementKeys.HOT_STUFF, AchievementList.hotStuff);
        AchievementRegistryApi.register(AchievementKeys.PORTAL, AchievementList.portal);
        AchievementRegistryApi.register(AchievementKeys.GHAST_HUNTER, AchievementList.ghastHunter);
        AchievementRegistryApi.register(AchievementKeys.BLAZING_HELL, AchievementList.blazingHell);
        AchievementRegistryApi.register(AchievementKeys.BUILD_SWORD, AchievementList.buildSword);
        AchievementRegistryApi.register(AchievementKeys.KILL_ENEMY, AchievementList.killEnemy);
        AchievementRegistryApi.register(AchievementKeys.KILL_COW, AchievementList.killCow);
        AchievementRegistryApi.register(AchievementKeys.FLY_PIG, AchievementList.flyPig);
        AchievementRegistryApi.register(AchievementKeys.OVERKILL, AchievementList.overkill);
        AchievementRegistryApi.register(AchievementKeys.BUILD_BOW, AchievementList.buildBow);
        AchievementRegistryApi.register(AchievementKeys.SNIPE_SKELETON, AchievementList.snipeSkeleton);
        AchievementRegistryApi.register(AchievementKeys.FULL_IRON, AchievementList.fullIron);
        AchievementRegistryApi.register(AchievementKeys.FULL_DIAMOND, AchievementList.fullDiamond);
        AchievementRegistryApi.register(AchievementKeys.SLEEP_IN_BED, AchievementList.sleepInBed);
        AchievementRegistryApi.register(AchievementKeys.EGG_HUNT, AchievementList.eggHunt);
        AchievementRegistryApi.register(AchievementKeys.SHEAR_SHEEP, AchievementList.shearSheep);
        AchievementRegistryApi.register(AchievementKeys.RAINBOW_WOOL, AchievementList.rainbowWool);
        AchievementRegistryApi.register(AchievementKeys.CRAFT_MAP, AchievementList.craftMap);
        AchievementRegistryApi.register(AchievementKeys.BOOKSHELF, AchievementList.bookshelf);
        AchievementRegistryApi.register(AchievementKeys.JUKEBOX, AchievementList.jukebox);
        AchievementRegistryApi.register(AchievementKeys.EXPLOSION, AchievementList.explosion);
        AchievementRegistryApi.register(AchievementKeys.PISTON, AchievementList.piston);
        AchievementRegistryApi.register(AchievementKeys.REPEATER, AchievementList.repeater);
        AchievementRegistryApi.register(AchievementKeys.ON_A_RAIL, AchievementList.onARail);
        AchievementRegistryApi.register(AchievementKeys.BOAT_TRAVEL, AchievementList.boatTravel);

        int count = AchievementRegistryApi.size();
        if (count != AchievementKeys.EXPECTED_COUNT) {
            System.err.println("[AchievementRegistryBootstrap] Expected " + AchievementKeys.EXPECTED_COUNT + " achievements, got " + count);
        } else {
            System.out.println("[AchievementRegistryBootstrap] Registered " + count + " achievements");
        }
    }
}
