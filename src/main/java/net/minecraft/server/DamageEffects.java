package net.minecraft.server;

/** 26.3 hurt-presentation metadata retained behind the legacy adapter. */
public enum DamageEffects {
    HURT("hurt"),
    THORNS("thorns"),
    DROWNING("drowning"),
    BURNING("burning"),
    POKING("poking"),
    FREEZING("freezing");

    private final String serializedName;

    DamageEffects(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public static DamageEffects byName(String name) {
        for (DamageEffects value : values()) {
            if (value.serializedName.equals(name)) return value;
        }
        return null;
    }
}
