package net.minecraft.world.scores;

import net.minecraft.network.chat.numbers.NumberFormat;

public final class PlayerScoreEntry {
    private final String owner;
    private final int value;
    private final String display;
    private final NumberFormat numberFormatOverride;
    public PlayerScoreEntry(String owner, int value, String display, NumberFormat format) {
        this.owner = owner; this.value = value; this.display = display; this.numberFormatOverride = format;
    }
    public String owner() { return this.owner; }
    public int value() { return this.value; }
    public String display() { return this.display; }
    public NumberFormat numberFormatOverride() { return this.numberFormatOverride; }
    public boolean isHidden() { return this.owner.startsWith("#"); }
    public String ownerName() { return this.display == null ? this.owner : this.display; }
    public String formatValue(NumberFormat fallback) {
        return (this.numberFormatOverride == null ? fallback : this.numberFormatOverride).format(this.value);
    }
}
