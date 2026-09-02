package net.minecraft.world.scores;

import net.minecraft.network.chat.numbers.NumberFormat;

public interface ScoreAccess extends ReadOnlyScoreInfo {
    int get();
    void set(int value);
    default int add(int count) { int result = get() + count; set(result); return result; }
    default int increment() { return add(1); }
    default void reset() { set(0); }
    String display();
    void display(String value);
    void numberFormatOverride(NumberFormat format);
    boolean locked();
    void unlock();
    void lock();
}
