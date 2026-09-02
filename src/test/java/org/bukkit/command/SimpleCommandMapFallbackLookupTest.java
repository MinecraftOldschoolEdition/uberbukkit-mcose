package org.bukkit.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class SimpleCommandMapFallbackLookupTest {
    @Test
    public void modernVanillaCommandsAreVisibleThroughOrdinaryLookup() {
        SimpleCommandMap map = new SimpleCommandMap(null);
        assertCommand(map, "title");
        assertCommand(map, "scoreboard");
        assertCommand(map, "team");
        assertCommand(map, "trigger");
        assertCommand(map, "locate");
        assertCommand(map, "seed");
    }

    private static void assertCommand(SimpleCommandMap map, String name) {
        Command command = map.getCommand(name);
        assertNotNull(name + " must be visible in the ordinary command map", command);
        assertEquals(name, command.getName());
    }
}
