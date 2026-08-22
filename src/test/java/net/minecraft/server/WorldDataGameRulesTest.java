package net.minecraft.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorldDataGameRulesTest {
    @Test
    public void defaultOffRulesStartDisabled() {
        assertFalse(new WorldData(1234L, "gamerule-defaults").getToggleFoodStacking());
        assertFalse(new WorldData(1234L, "gamerule-defaults").getHoeGrassForSeeds());
    }

    @Test
    public void punchRulesRoundTripThroughModernAndLegacyKeys() {
        WorldData worldData = new WorldData(1234L, "gamerule-persistence");
        worldData.setPunchToPrimeTNT(true);
        worldData.setPunchSheepForWool(true);
        worldData.setToggleFoodStacking(true);
        worldData.setHoeGrassForSeeds(true);

        NBTTagCompound saved = worldData.a();
        NBTTagCompound gameRules = saved.k("GameRules");
        assertTrue(gameRules.m("punchToPrimeTNT"));
        assertTrue(gameRules.m("punchSheepForWool"));
        assertTrue(gameRules.m("toggleFoodStacking"));
        assertTrue(gameRules.m("hoeGrassForSeeds"));
        assertTrue(saved.m("PunchToPrimeTNT"));
        assertTrue(saved.m("PunchSheepForWool"));
        assertTrue(saved.m("ToggleFoodStacking"));
        assertTrue(saved.m("HoeGrassForSeeds"));

        WorldData restored = new WorldData(saved);
        assertTrue(restored.getPunchToPrimeTNT());
        assertTrue(restored.getPunchSheepForWool());
        assertTrue(restored.getToggleFoodStacking());
        assertTrue(restored.getHoeGrassForSeeds());
    }

    @Test
    public void explicitFalseRulesOverrideLegacyCompatibilityDefaults() {
        NBTTagCompound level = new NBTTagCompound();
        NBTTagCompound gameRules = new NBTTagCompound();
        gameRules.a("punchToPrimeTNT", false);
        gameRules.a("punchSheepForWool", false);
        gameRules.a("toggleFoodStacking", false);
        gameRules.a("hoeGrassForSeeds", false);
        level.a("GameRules", gameRules);

        WorldData worldData = new WorldData(level);

        assertFalse(worldData.getPunchToPrimeTNT());
        assertFalse(worldData.getPunchSheepForWool());
        assertFalse(worldData.getToggleFoodStacking());
        assertFalse(worldData.getHoeGrassForSeeds());
    }
}
