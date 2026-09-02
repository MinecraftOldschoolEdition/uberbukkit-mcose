package net.minecraft.network.chat.numbers;

public final class FixedFormat implements NumberFormat {
    private final String value;
    public FixedFormat(String value) { this.value = value == null ? "" : value; }
    public String value() { return this.value; }
    public String format(int ignored) { return this.value; }
}
