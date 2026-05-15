package net.minecraft.server;

public final class CombatEntry {
    private final DeathDamageSource source;
    private final int damage;
    private final FallLocation fallLocation;
    private final float fallDistance;

    public CombatEntry(DeathDamageSource source, int damage, FallLocation fallLocation, float fallDistance) {
        this.source = source;
        this.damage = damage;
        this.fallLocation = fallLocation;
        this.fallDistance = fallDistance;
    }

    public DeathDamageSource getSource() {
        return this.source;
    }

    public int getDamage() {
        return this.damage;
    }

    public FallLocation getFallLocation() {
        return this.fallLocation;
    }

    public float getFallDistance() {
        return this.fallDistance;
    }
}
