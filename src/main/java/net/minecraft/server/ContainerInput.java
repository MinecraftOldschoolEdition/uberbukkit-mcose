package net.minecraft.server;

/**
 * Snapshot-style container input kinds encoded over the legacy window-click
 * packet without changing its wire size.
 */
public final class ContainerInput {
    public static final int PICKUP = 0;
    public static final int QUICK_MOVE = 1;
    public static final int SWAP = 2;
    public static final int CLONE = 3;
    public static final int THROW = 4;
    public static final int QUICK_CRAFT = 5;
    public static final int PICKUP_ALL = 6;

    private static final int EXTENDED_MARKER = 0x80;
    private static final int ACTION_MASK = 0x70;
    private static final int BUTTON_MASK = 0x0F;

    private ContainerInput() {
    }

    public static boolean isValid(int input) {
        return input >= PICKUP && input <= PICKUP_ALL;
    }

    public static boolean requiresExtendedEncoding(int input) {
        return input >= SWAP;
    }

    public static int encodeButton(int input, int button) {
        if (!requiresExtendedEncoding(input)) {
            return button;
        }
        if (!isValid(input) || button < 0 || button > BUTTON_MASK) {
            throw new IllegalArgumentException("Invalid container input " + input + " button " + button);
        }
        return EXTENDED_MARKER | input << 4 | button;
    }

    public static boolean isExtendedButton(int encodedButton) {
        return (encodedButton & EXTENDED_MARKER) != 0;
    }

    public static int decodeInput(int encodedButton, boolean shiftClick) {
        if (!isExtendedButton(encodedButton)) {
            return shiftClick ? QUICK_MOVE : PICKUP;
        }
        return (encodedButton & ACTION_MASK) >>> 4;
    }

    public static int decodeButton(int encodedButton) {
        return isExtendedButton(encodedButton) ? encodedButton & BUTTON_MASK : encodedButton;
    }

    public static int getQuickCraftType(int mask) {
        return mask >> 2 & 3;
    }

    public static int getQuickCraftHeader(int mask) {
        return mask & 3;
    }

    public static int getQuickCraftMask(int header, int type) {
        return header & 3 | (type & 3) << 2;
    }
}
