package net.minecraft.world.scores;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum TeamColor {
    BLACK(0, "black", '0'), DARK_BLUE(1, "dark_blue", '1'), DARK_GREEN(2, "dark_green", '2'),
    DARK_AQUA(3, "dark_aqua", '3'), DARK_RED(4, "dark_red", '4'),
    DARK_PURPLE(5, "dark_purple", '5'), GOLD(6, "gold", '6'), GRAY(7, "gray", '7'),
    DARK_GRAY(8, "dark_gray", '8'), BLUE(9, "blue", '9'), GREEN(10, "green", 'a'),
    AQUA(11, "aqua", 'b'), RED(12, "red", 'c'), LIGHT_PURPLE(13, "light_purple", 'd'),
    YELLOW(14, "yellow", 'e'), WHITE(15, "white", 'f');
    private final int id;
    private final String name;
    private final char legacyCode;
    public static final List<TeamColor> VALUES = Collections.unmodifiableList(Arrays.asList(values()));
    TeamColor(int id, String name, char legacyCode) { this.id = id; this.name = name; this.legacyCode = legacyCode; }
    public int id() { return this.id; }
    public String getSerializedName() { return this.name; }
    public String legacyPrefix() { return "\u00a7" + this.legacyCode; }
    public DisplaySlot displaySlot() { return DisplaySlot.byId(3 + this.id); }
    public int rgb() {
        int[] colors = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
        return colors[this.id];
    }
    public static TeamColor byId(int id) { return id >= 0 && id < values().length ? values()[id] : null; }
    public static TeamColor byName(String name) {
        for(TeamColor color : values()) if(color.name.equalsIgnoreCase(name)) return color;
        return null;
    }
}
