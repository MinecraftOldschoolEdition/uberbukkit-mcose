package net.minecraft.server.registry;

import net.minecraft.server.AchievementList;
import net.minecraft.server.util.ResourceLocation;

/**
 * Bootstrap for the Achievement registry.
 * Registers all built-in achievements.
 */
public final class AchievementRegistryBootstrap {
    private static boolean initialized = false;
    
    private AchievementRegistryBootstrap() {}
    
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        
        // === CORE PROGRESSION ===
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "open_inventory"), AchievementList.openInventory);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "mine_wood"), AchievementList.mineWood);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "build_workbench"), AchievementList.buildWorkBench);
        
        // === MINING PATH ===
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "build_pickaxe"), AchievementList.buildPickaxe);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "build_better_pickaxe"), AchievementList.buildBetterPickaxe);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "build_furnace"), AchievementList.buildFurnace);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "acquire_iron"), AchievementList.acquireIron);
        
        // === COOKING BRANCH ===
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "cook_fish"), AchievementList.cookFish);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "cook_bacon"), AchievementList.cookBacon);
        
        // === DIAMONDS & NETHER BRANCH ===
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "diamonds"), AchievementList.diamonds);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "overkill"), AchievementList.overkill);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "full_diamond"), AchievementList.fullDiamond);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "jukebox"), AchievementList.jukebox);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "obsidian"), AchievementList.obsidian);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "portal"), AchievementList.portal);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "ghast_hunter"), AchievementList.ghastHunter);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "blazing_hell"), AchievementList.blazingHell);
        
        // === ARMOR BRANCH ===
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "full_iron"), AchievementList.fullIron);
        
        // === BUCKETS BRANCH ===
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "hot_stuff"), AchievementList.hotStuff);
        
        // === WOOL & ANIMALS BRANCH ===
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "shear_sheep"), AchievementList.shearSheep);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "sleep_in_bed"), AchievementList.sleepInBed);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "rainbow_wool"), AchievementList.rainbowWool);
        
        // === REDSTONE BRANCH ===
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "repeater"), AchievementList.repeater);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "piston"), AchievementList.piston);
        
        // === TRAVEL BRANCH ===
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "on_a_rail"), AchievementList.onARail);
        
        // === FARMING BRANCH ===
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "build_hoe"), AchievementList.buildHoe);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "grow_wheat"), AchievementList.growWheat);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "make_bread"), AchievementList.makeBread);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "bake_cake"), AchievementList.bakeCake);
        
        // === COMBAT BRANCH ===
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "build_sword"), AchievementList.buildSword);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "kill_enemy"), AchievementList.killEnemy);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "fly_pig"), AchievementList.flyPig);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "explosion"), AchievementList.explosion);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "kill_cow"), AchievementList.killCow);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "egg_hunt"), AchievementList.eggHunt);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "bookshelf"), AchievementList.bookshelf);
        
        // === ARCHERY BRANCH ===
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "build_bow"), AchievementList.buildBow);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "snipe_skeleton"), AchievementList.snipeSkeleton);
        
        // === BUILDING & EXPLORATION BRANCH ===
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "craft_map"), AchievementList.craftMap);
        Registries.ACHIEVEMENT.register(new ResourceLocation("minecraft", "boat_travel"), AchievementList.boatTravel);
        
        System.out.println("[AchievementRegistryBootstrap] Registered " + Registries.ACHIEVEMENT.keys().size() + " achievements");
    }
}
