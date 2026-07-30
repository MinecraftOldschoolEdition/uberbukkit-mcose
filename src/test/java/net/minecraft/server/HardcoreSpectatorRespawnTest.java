package net.minecraft.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HardcoreSpectatorRespawnTest {
    @Test
    public void deadHardcorePlayerEntersSpectatorAfterRespawn() {
        assertTrue(NetServerHandler.shouldEnterSpectatorAfterRespawn(true, true));
        assertFalse(NetServerHandler.shouldEnterSpectatorAfterRespawn(true, false));
        assertFalse(NetServerHandler.shouldEnterSpectatorAfterRespawn(false, true));
    }

    @Test
    public void hardcoreSpectatorPersistsAcrossReconnect() {
        assertTrue(ServerConfigurationManager.shouldPreserveHardcoreSpectator(
                GameType.HARDCORE.getId(), GameType.SPECTATOR.getId()));
        assertFalse(ServerConfigurationManager.shouldPreserveHardcoreSpectator(
                GameType.SURVIVAL.getId(), GameType.SPECTATOR.getId()));
        assertFalse(ServerConfigurationManager.shouldPreserveHardcoreSpectator(
                GameType.HARDCORE.getId(), GameType.HARDCORE.getId()));
    }
}
