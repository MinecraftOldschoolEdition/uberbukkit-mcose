package net.minecraft.server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EntityFireballDirectImpactDamageTest {
    @Test
    public void playerReflectedFireballOneHitsGhast() {
        assertEquals(1000, EntityFireball.getDirectImpactDamage(true, true));
    }

    @Test
    public void normalFireballsAndNonGhastHitsKeepLegacyDirectDamage() {
        assertEquals(0, EntityFireball.getDirectImpactDamage(true, false));
        assertEquals(0, EntityFireball.getDirectImpactDamage(false, true));
        assertEquals(0, EntityFireball.getDirectImpactDamage(false, false));
    }
}
