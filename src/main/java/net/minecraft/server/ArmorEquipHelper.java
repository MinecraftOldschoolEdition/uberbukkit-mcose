package net.minecraft.server;

/** Maps modern armor types (helmet first) to the legacy armor array order. */
public final class ArmorEquipHelper {
    private ArmorEquipHelper() {
    }

    public static int getArmorInventoryIndex(int armorType) {
        return 3 - armorType;
    }
}
