package net.minecraft.network.chat.numbers;

public final class StyledFormat implements NumberFormat {
    public static final StyledFormat NO_STYLE = new StyledFormat("");
    public static final StyledFormat SIDEBAR_DEFAULT = new StyledFormat("\u00a7c");
    public static final StyledFormat PLAYER_LIST_DEFAULT = new StyledFormat("\u00a7e");
    private final String style;
    public StyledFormat(String style) { this.style = style == null ? "" : style; }
    public String style() { return this.style; }
    public String format(int value) { return this.style + value; }
}
