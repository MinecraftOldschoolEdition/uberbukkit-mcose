package net.minecraft.server;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ItemInWorldManagerSecondaryUseTest {
    @Test
    public void sneakingWithAnItemSuppressesFenceGateActivation() {
        assertTrue(ItemInWorldManager.shouldSuppressBlockUse(true, true));
    }

    @Test
    public void emptyHandOrNormalUseStillActivatesFenceGate() {
        assertFalse(ItemInWorldManager.shouldSuppressBlockUse(true, false));
        assertFalse(ItemInWorldManager.shouldSuppressBlockUse(false, true));
    }
}
