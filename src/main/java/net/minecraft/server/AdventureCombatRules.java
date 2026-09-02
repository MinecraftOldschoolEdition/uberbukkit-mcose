package net.minecraft.server;

public final class AdventureCombatRules {
    private static final int MAX_DRAW_TICKS = 20;

    private AdventureCombatRules() {
    }

    public static float getBowPowerForTime(int chargeTicks) {
        float power = (float) Math.max(0, chargeTicks) / (float) MAX_DRAW_TICKS;
        power = (power * power + power * 2.0F) / 3.0F;
        return power > 1.0F ? 1.0F : power;
    }
}
