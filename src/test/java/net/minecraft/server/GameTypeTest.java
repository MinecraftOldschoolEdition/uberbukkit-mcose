package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class GameTypeTest {
    @Test
    public void stableIdsPreserveHardcoreAndAddSpectator() {
        assertEquals(GameType.SURVIVAL, GameType.byId(0));
        assertEquals(GameType.CREATIVE, GameType.byId(1));
        assertEquals(GameType.HARDCORE, GameType.byId(2));
        assertEquals(GameType.SPECTATOR, GameType.byId(3));
        assertEquals(GameType.SURVIVAL, GameType.byId(-1));
        assertEquals(GameType.SURVIVAL, GameType.byId(4));
    }

    @Test
    public void commandAliasesResolveWithoutStealingSurvivalAlias() {
        assertEquals(GameType.SURVIVAL.getId(), GameType.parse("s"));
        assertEquals(GameType.SPECTATOR.getId(), GameType.parse("spectator"));
        assertEquals(GameType.SPECTATOR.getId(), GameType.parse("sp"));
        assertEquals(GameType.SPECTATOR.getId(), GameType.parse("3"));
    }

    @Test
    public void spectatorIsNonInteractiveWithoutCreativeBuilding() {
        assertTrue(GameType.SPECTATOR.isSpectator());
        assertFalse(GameType.SPECTATOR.canInteract());
        assertFalse(GameType.SPECTATOR.isCreative());
        assertTrue(GameType.CREATIVE.canInteract());
    }

    @Test
    public void commandSuggestionsIncludeSpectatorNameAliasAndId() {
        java.util.List<String> suggestions = Arrays.asList(GameType.getCommandSuggestions());
        assertTrue(suggestions.contains("spectator"));
        assertTrue(suggestions.contains("sp"));
        assertTrue(suggestions.contains("3"));
    }
}
