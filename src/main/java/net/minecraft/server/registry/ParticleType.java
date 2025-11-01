package net.minecraft.server.registry;

public final class ParticleType {
    private final String legacyKey;

    public ParticleType(String legacyKey) {
        this.legacyKey = legacyKey;
    }

    public String getLegacyKey() { return legacyKey; }
}


