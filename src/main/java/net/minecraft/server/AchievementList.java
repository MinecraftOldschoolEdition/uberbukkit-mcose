package net.minecraft.server;

import java.util.ArrayList;
import java.util.List;

public class AchievementList {

    public static int a; // minDisplayColumn
    public static int b; // minDisplayRow
    public static int c; // maxDisplayColumn
    public static int d; // maxDisplayRow
    public static List e = new ArrayList();
    
    // ============================================
    // CORE PROGRESSION (center spine going down)
    // ============================================
    public static Achievement openInventory = (new Achievement(0, "openInventory", 0, 0, Item.BOOK, (Achievement) null)).a().c();
    public static Achievement mineWood = (new Achievement(1, "mineWood", 2, 0, Block.LOG, openInventory)).c();
    public static Achievement buildWorkBench = (new Achievement(2, "buildWorkBench", 4, 0, Block.WORKBENCH, mineWood)).c();
    
    // ============================================
    // MINING PATH (down from workbench)
    // ============================================
    public static Achievement buildPickaxe = (new Achievement(3, "buildPickaxe", 4, 2, Item.WOOD_PICKAXE, buildWorkBench)).c();
    public static Achievement buildBetterPickaxe = (new Achievement(9, "buildBetterPickaxe", 6, 3, Item.STONE_PICKAXE, buildPickaxe)).c();
    public static Achievement buildFurnace = (new Achievement(4, "buildFurnace", 4, 4, Block.BURNING_FURNACE, buildPickaxe)).c();
    public static Achievement acquireIron = (new Achievement(5, "acquireIron", 4, 6, Item.IRON_INGOT, buildFurnace)).c();
    
    // ============================================
    // COOKING BRANCH (right from furnace)
    // ============================================
    public static Achievement cookFish = (new Achievement(10, "cookFish", 6, 4, Item.COOKED_FISH, buildFurnace)).c();
    public static Achievement cookBacon = (new Achievement(16, "cookBacon", 6, 5, Item.GRILLED_PORK, buildFurnace)).c();
    
    // ============================================
    // DIAMONDS & NETHER BRANCH (left from iron)
    // ============================================
    public static Achievement diamonds = (new Achievement(17, "diamonds", 2, 7, Item.DIAMOND, acquireIron)).c();
    public static Achievement overkill = (new Achievement(39, "overkill", 0, 7, Item.DIAMOND_SWORD, diamonds)).b().c();
    public static Achievement fullDiamond = (new Achievement(26, "fullDiamond", 1, 8, Item.DIAMOND_CHESTPLATE, diamonds)).b().c();
    public static Achievement jukebox = (new Achievement(30, "jukebox", 3, 8, Block.JUKEBOX, diamonds)).c();
    public static Achievement obsidian = (new Achievement(18, "obsidian", 0, 9, Block.OBSIDIAN, diamonds)).c();
    public static Achievement portal = (new Achievement(20, "portal", -2, 10, Block.PORTAL, obsidian)).b().c();
    public static Achievement ghastHunter = (new Achievement(21, "ghastHunter", -4, 11, Item.SULPHUR, portal)).b().c();
    public static Achievement blazingHell = (new Achievement(22, "blazingHell", -2, 12, Block.GLOWSTONE, portal)).c();
    
    // ============================================
    // ARMOR BRANCH (up-left from iron)
    // ============================================
    public static Achievement fullIron = (new Achievement(25, "fullIron", 2, 5, Item.IRON_CHESTPLATE, acquireIron)).c();
    
    // ============================================
    // BUCKETS BRANCH (down from iron)
    // ============================================
    public static Achievement hotStuff = (new Achievement(19, "hotStuff", 4, 8, Item.LAVA_BUCKET, acquireIron)).c();
    
    // ============================================
    // WOOL & ANIMALS BRANCH (right from iron)
    // ============================================
    public static Achievement shearSheep = (new Achievement(34, "shearSheep", 6, 7, Item.SHEARS, acquireIron)).c();
    public static Achievement sleepInBed = (new Achievement(27, "sleepInBed", 8, 7, Item.BED, shearSheep)).c();
    public static Achievement rainbowWool = (new Achievement(35, "rainbowWool", 7, 8, new ItemStack(Block.WOOL, 1, 14), shearSheep)).b().c();
    
    // ============================================
    // REDSTONE BRANCH (up from iron)
    // ============================================
    public static Achievement repeater = (new Achievement(33, "repeater", 3, 5, Item.DIODE, acquireIron)).c();
    public static Achievement piston = (new Achievement(32, "piston", 2, 4, Block.PISTON, repeater)).c();
    
    // ============================================
    // TRAVEL BRANCH (from iron)
    // ============================================
    public static Achievement onARail = (new Achievement(11, "onARail", 6, 6, Block.RAILS, acquireIron)).b().c();
    
    // ============================================
    // FARMING BRANCH (up-left from workbench)
    // ============================================
    public static Achievement buildHoe = (new Achievement(6, "buildHoe", 2, -1, Item.WOOD_HOE, buildWorkBench)).c();
    public static Achievement growWheat = (new Achievement(36, "growWheat", 0, -2, Item.WHEAT, buildHoe)).c();
    public static Achievement makeBread = (new Achievement(7, "makeBread", -2, -2, Item.BREAD, growWheat)).c();
    public static Achievement bakeCake = (new Achievement(8, "bakeCake", -1, -3, Item.CAKE, growWheat)).b().c();
    
    // ============================================
    // COMBAT BRANCH (up-right from workbench)
    // ============================================
    public static Achievement buildSword = (new Achievement(12, "buildSword", 6, -1, Item.WOOD_SWORD, buildWorkBench)).c();
    public static Achievement killEnemy = (new Achievement(13, "killEnemy", 8, -2, Item.BONE, buildSword)).c();
    public static Achievement flyPig = (new Achievement(15, "flyPig", 10, -3, Item.SADDLE, killEnemy)).b().c();
    public static Achievement explosion = (new Achievement(31, "explosion", 10, -1, Block.TNT, killEnemy)).c();
    public static Achievement killCow = (new Achievement(14, "killCow", 8, 0, Item.LEATHER, buildSword)).c();
    public static Achievement eggHunt = (new Achievement(37, "eggHunt", 10, 0, Item.EGG, killCow)).c();
    public static Achievement bookshelf = (new Achievement(29, "bookshelf", 10, 1, Block.BOOKSHELF, killCow)).c();
    
    // ============================================
    // ARCHERY BRANCH (up from workbench)
    // ============================================
    public static Achievement buildBow = (new Achievement(23, "buildBow", 4, -2, Item.BOW, buildWorkBench)).c();
    public static Achievement snipeSkeleton = (new Achievement(24, "snipeSkeleton", 4, -4, Item.ARROW, buildBow)).b().c();
    
    // ============================================
    // BUILDING & EXPLORATION BRANCH (from workbench)
    // ============================================
    public static Achievement craftMap = (new Achievement(28, "craftMap", 6, 1, Item.MAP, buildWorkBench)).c();
    public static Achievement boatTravel = (new Achievement(38, "boatTravel", 2, 1, Item.BOAT, buildWorkBench)).b().c();
    
    // Legacy field aliases for compatibility
    public static Achievement f = openInventory;
    public static Achievement g = mineWood;
    public static Achievement h = buildWorkBench;
    public static Achievement i = buildPickaxe;
    public static Achievement j = buildFurnace;
    public static Achievement k = acquireIron;
    public static Achievement l = buildHoe;
    public static Achievement m = makeBread;
    public static Achievement n = bakeCake;
    public static Achievement o = buildBetterPickaxe;
    public static Achievement p = cookFish;
    public static Achievement q = onARail;
    public static Achievement r = buildSword;
    public static Achievement s = killEnemy;
    public static Achievement t = killCow;
    public static Achievement u = flyPig;

    public AchievementList() {
    }

    public static void a() {
    }

    static {
        // Add padding to the display bounds for better scrolling
        a -= 3;
        b -= 3;
        c += 3;
        d += 3;
        System.out.println(e.size() + " achievements");
    }
}
