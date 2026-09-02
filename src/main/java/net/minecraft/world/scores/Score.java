package net.minecraft.world.scores;

import net.minecraft.network.chat.numbers.NumberFormat;

public class Score implements ReadOnlyScoreInfo {
    private int value;
    private boolean locked = true;
    private String display;
    private NumberFormat numberFormat;
    public int value() { return this.value; }
    public void value(int value) { this.value = value; }
    public boolean isLocked() { return this.locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public String display() { return this.display; }
    public void display(String display) { this.display = display; }
    public NumberFormat numberFormat() { return this.numberFormat; }
    public void numberFormat(NumberFormat format) { this.numberFormat = format; }
}
