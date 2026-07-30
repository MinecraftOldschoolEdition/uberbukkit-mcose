package org.bukkit.command.defaults;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.server.GameType;
import org.bukkit.command.CommandAutocompleteRegistry;
import org.junit.Test;

public class GameModeCommandAutocompleteTest {
    @Test
    public void commandTreeUsesCanonicalSpectatorSuggestions() throws Exception {
        Field field = CommandAutocompleteRegistry.class.getDeclaredField("GAMEMODE_VALUES");
        field.setAccessible(true);
        String[] registryValues = ((String[])field.get(null)).clone();

        assertArrayEquals(GameType.getCommandSuggestions(), registryValues);
        assertTrue(Arrays.asList(registryValues).contains("spectator"));
    }
}
