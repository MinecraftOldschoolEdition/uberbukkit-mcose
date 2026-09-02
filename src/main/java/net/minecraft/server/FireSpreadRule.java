package net.minecraft.server;

/** Immutable ignition and burn odds for one canonical block registration. */
public final class FireSpreadRule {
    public static final FireSpreadRule ZERO = new FireSpreadRule(0, 0);

    private final int igniteOdds;
    private final int burnOdds;

    public FireSpreadRule(int igniteOdds, int burnOdds) {
        this.igniteOdds = igniteOdds;
        this.burnOdds = burnOdds;
    }

    public int getIgniteOdds() {
        return this.igniteOdds;
    }

    public int getBurnOdds() {
        return this.burnOdds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FireSpreadRule)) return false;
        FireSpreadRule rule = (FireSpreadRule) other;
        return this.igniteOdds == rule.igniteOdds
                && this.burnOdds == rule.burnOdds;
    }

    @Override
    public int hashCode() {
        return 31 * this.igniteOdds + this.burnOdds;
    }

    @Override
    public String toString() {
        return "FireSpreadRule{igniteOdds=" + this.igniteOdds
                + ", burnOdds=" + this.burnOdds + "}";
    }
}
