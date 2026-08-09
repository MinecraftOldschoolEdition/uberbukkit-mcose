package org.bukkit.command.defaults;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import net.minecraft.server.WorldData;
import org.bukkit.command.CommandAutocompleteRegistry;
import org.junit.Test;

public class GameruleCommandRuleParityTest {
    private static final String[] BOOLEAN_RULES = {
        "doDayNightCycle",
        "tntexplodes",
        "mobGriefing",
        "doWeatherCycle",
        "doFireTick",
        "showDeathMessages",
        "sleepEnabled",
        "advertiseAchievements",
        "keepInventory",
        "punchToPrimeTNT",
        "punchSheepForWool",
        "toggleFoodStacking"
    };

    private static final String[] INTEGER_RULES = {
        "spawnRadius",
        "spawnProtectionRadius"
    };

    @Test
    public void commandAndAutocompleteExposeEverySupportedRule() throws Exception {
        assertArrayEquals(BOOLEAN_RULES, GameruleCommand.booleanRuleNames());
        assertArrayEquals(INTEGER_RULES, GameruleCommand.integerRuleNames());
        assertArrayEquals(BOOLEAN_RULES, autocompleteRules("BOOLEAN_GAMERULES"));
        assertArrayEquals(INTEGER_RULES, autocompleteRules("INTEGER_GAMERULES"));
    }

    @Test
    public void everyListedBooleanRuleCanBeQueriedAndChanged() {
        WorldData worldData = new WorldData(1234L, "gamerule-command-parity");

        for (String rule : BOOLEAN_RULES) {
            assertTrue(rule, GameruleCommand.setBooleanRuleValue(worldData, rule, false));
            assertEquals(rule, Boolean.FALSE, GameruleCommand.getBooleanRuleValue(worldData, rule));
            assertTrue(rule, GameruleCommand.setBooleanRuleValue(worldData, rule.toUpperCase(), true));
            assertEquals(rule, Boolean.TRUE, GameruleCommand.getBooleanRuleValue(worldData, rule));
        }

        assertFalse(GameruleCommand.setBooleanRuleValue(worldData, "unknownRule", true));
        assertNull(GameruleCommand.getBooleanRuleValue(worldData, "unknownRule"));
    }

    private static String[] autocompleteRules(String fieldName) throws Exception {
        Field field = CommandAutocompleteRegistry.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((String[])field.get(null)).clone();
    }
}
