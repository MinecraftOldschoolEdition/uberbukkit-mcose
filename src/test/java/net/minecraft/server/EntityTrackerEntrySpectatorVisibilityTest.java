package net.minecraft.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EntityTrackerEntrySpectatorVisibilityTest {
    @Test
    public void normalPlayersCannotTrackSpectatorsButSpectatorsCan() {
        assertFalse(EntityTrackerEntry.spectatorVisibilityAllowsTracking(true, false));
        assertTrue(EntityTrackerEntry.spectatorVisibilityAllowsTracking(true, true));
        assertTrue(EntityTrackerEntry.spectatorVisibilityAllowsTracking(false, false));
        assertTrue(EntityTrackerEntry.spectatorVisibilityAllowsTracking(false, true));
    }
}
