package net.minecraft.world.scores;

import net.minecraft.network.chat.numbers.NumberFormat;

public interface ReadOnlyScoreInfo {
    int value();
    boolean isLocked();
    NumberFormat numberFormat();
    default String formatValue(NumberFormat defaultFormat) {
        NumberFormat format = numberFormat();
        return (format == null ? defaultFormat : format).format(value());
    }
}
