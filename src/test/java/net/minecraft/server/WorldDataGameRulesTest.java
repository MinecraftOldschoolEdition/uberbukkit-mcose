package net.minecraft.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorldDataGameRulesTest {
    @Test
    public void punchRulesRoundTripThroughModernAndLegacyKeys() {
        WorldData worldData = new WorldData(1234L, "gamerule-persistence");
        worldData.setPunchToPrimeTNT(true);
        worldData.setPunchSheepForWool(true);

        NBTTagCompound saved = worldData.a();
        NBTTagCompound gameRules = saved.k("GameRules");
        assertTrue(gameRules.m("punchToPrimeTNT"));
        assertTrue(gameRules.m("punchSheepForWool"));
        assertTrue(saved.m("PunchToPrimeTNT"));
        assertTrue(saved.m("PunchSheepForWool"));

        WorldData restored = new WorldData(saved);
        assertTrue(restored.getPunchToPrimeTNT());
        assertTrue(restored.getPunchSheepForWool());
    }

    @Test
    public void explicitFalseRulesOverrideLegacyCompatibilityDefaults() {
        NBTTagCompound level = new NBTTagCompound();
        NBTTagCompound gameRules = new NBTTagCompound();
        gameRules.a("punchToPrimeTNT", false);
        gameRules.a("punchSheepForWool", false);
        level.a("GameRules", gameRules);

        WorldData worldData = new WorldData(level);

        assertFalse(worldData.getPunchToPrimeTNT());
        assertFalse(worldData.getPunchSheepForWool());
    }
}
