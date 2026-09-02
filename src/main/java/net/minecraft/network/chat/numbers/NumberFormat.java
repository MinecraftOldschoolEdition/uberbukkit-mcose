package net.minecraft.network.chat.numbers;

/** String-component adaptation of the 26.3 score number-format contract. */
public interface NumberFormat {
    String format(int value);
}
