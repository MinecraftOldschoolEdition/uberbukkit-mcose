package net.minecraft.server;

public enum DeathMessageType {
    DEFAULT("default"),
    FALL_VARIANTS("fall_variants"),
    INTENTIONAL_GAME_DESIGN("intentional_game_design");

    private final String serializedName;

    DeathMessageType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public static DeathMessageType byName(String name) {
        for (DeathMessageType value : values()) {
            if (value.serializedName.equals(name)) return value;
        }
        return null;
    }
}
