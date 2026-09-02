package net.minecraft.server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EntityHumanSprintTest {
    @Test
    public void sprintUsesTheModernThirtyPercentMovementSpeedModifier() {
        assertEquals(0.13F, EntityHuman.adventureMovementSpeed(0.1F, true, true), 0.000001F);
        assertEquals(0.1F, EntityHuman.adventureMovementSpeed(0.1F, false, true), 0.000001F);
        assertEquals(0.1F, EntityHuman.adventureMovementSpeed(0.1F, true, false), 0.000001F);
    }
}
