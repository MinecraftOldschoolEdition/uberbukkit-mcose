package net.minecraft.network.chat.numbers;

public final class BlankFormat implements NumberFormat {
    public static final BlankFormat INSTANCE = new BlankFormat();
    private BlankFormat() {}
    public String format(int value) { return ""; }
}
