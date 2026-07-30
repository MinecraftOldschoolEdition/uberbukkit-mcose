package net.minecraft.server.registry;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import net.minecraft.server.EntityPlayer;
import org.junit.Test;
import sun.misc.Unsafe;

public class PlayerCapabilityRegistrySpectatorTest {
    @Test
    public void spectatorModeForcesFlightAndInvulnerabilityWithoutInstabuild() throws Exception {
        EntityPlayer player = (EntityPlayer) unsafe().allocateInstance(EntityPlayer.class);
        player.gameMode = 3;
        player.onGround = true;

        assertTrue(PlayerCapabilityRegistryApi.canFly(player));
        assertTrue(PlayerCapabilityRegistryApi.isFlying(player));
        assertTrue(PlayerCapabilityRegistryApi.isInvulnerable(player));
        assertFalse(PlayerCapabilityRegistryApi.canInstabuild(player));
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
