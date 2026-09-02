package net.minecraft.server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ItemBowAdventureCombatTest {
    @Test
    public void usesTheTwentyTick263ChargeCurve() {
        assertEquals(0.0F, AdventureCombatRules.getBowPowerForTime(0), 0.000001F);
        assertEquals(0.07F, AdventureCombatRules.getBowPowerForTime(2), 0.000001F);
        assertEquals(5.0F / 12.0F, AdventureCombatRules.getBowPowerForTime(10), 0.000001F);
        assertEquals(1.0F, AdventureCombatRules.getBowPowerForTime(20), 0.000001F);
        assertEquals(1.0F, AdventureCombatRules.getBowPowerForTime(40), 0.000001F);
    }
}
