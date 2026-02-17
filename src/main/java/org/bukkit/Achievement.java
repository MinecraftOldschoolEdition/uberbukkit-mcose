package org.bukkit;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an achievement, which may be given to players
 */
public enum Achievement {
    OPEN_INVENTORY(0),
    MINE_WOOD(1),
    BUILD_WORKBENCH(2),
    BUILD_PICKAXE(3),
    BUILD_FURNACE(4),
    ACQUIRE_IRON(5),
    BUILD_HOE(6),
    MAKE_BREAD(7),
    BAKE_CAKE(8),
    BUILD_BETTER_PICKAXE(9),
    COOK_FISH(10),
    ON_A_RAIL(11),
    BUILD_SWORD(12),
    KILL_ENEMY(13),
    KILL_COW(14),
    FLY_PIG(15),
    COOK_BACON(16),
    DIAMONDS(17),
    OBSIDIAN(18),
    HOT_STUFF(19),
    PORTAL(20),
    GHAST_HUNTER(21),
    BLAZING_HELL(22),
    BUILD_BOW(23),
    SNIPE_SKELETON(24),
    FULL_IRON(25),
    FULL_DIAMOND(26),
    SLEEP_IN_BED(27),
    CRAFT_MAP(28),
    BOOKSHELF(29),
    JUKEBOX(30),
    EXPLOSION(31),
    PISTON(32),
    REPEATER(33),
    SHEAR_SHEEP(34),
    RAINBOW_WOOL(35),
    GROW_WHEAT(36),
    EGG_HUNT(37),
    BOAT_TRAVEL(38),
    OVERKILL(39);

    /**
     * The offset used to distinguish Achievements and Statistics
     */
    public final static int STATISTIC_OFFSET = 5242880;
    private final static Map<Integer, Achievement> achievements = new HashMap<Integer, Achievement>();
    private final int id;

    private Achievement(int id) {
        this.id = STATISTIC_OFFSET + id;
    }

    /**
     * Gets the ID for this achievement.
     * <p>
     * Note that this is offset using {@link #STATISTIC_OFFSET}
     *
     * @return ID of this achievement
     */
    public int getId() {
        return id;
    }

    /**
     * Gets the achievement associated with the given ID.
     * <p>
     * Note that the ID must already be offset using {@link #STATISTIC_OFFSET}
     *
     * @param id ID of the achievement to return
     * @return Achievement with the given ID
     */
    public static Achievement getAchievement(int id) {
        return achievements.get(id);
    }

    static {
        for (Achievement ach : values()) {
            achievements.put(ach.getId(), ach);
        }
    }
}
