package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class EntityActivationRangeDefaultTest {
    @Test
    public void paperStyleEntityThrottlingIsOptIn() {
        assertFalse(PoseidonConfig.DEFAULT_ENTITY_ACTIVATION_ENABLED);
    }
}
