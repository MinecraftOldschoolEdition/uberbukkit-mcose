package net.minecraft.server;

/** 26.3 damage-scaling metadata. Legacy combat deliberately does not consume it. */
public enum DamageScaling {
    NEVER("never"),
    WHEN_CAUSED_BY_LIVING_NON_PLAYER("when_caused_by_living_non_player"),
    ALWAYS("always");

    private final String serializedName;

    DamageScaling(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public static DamageScaling byName(String name) {
        for (DamageScaling value : values()) {
            if (value.serializedName.equals(name)) return value;
        }
        return null;
    }
}
