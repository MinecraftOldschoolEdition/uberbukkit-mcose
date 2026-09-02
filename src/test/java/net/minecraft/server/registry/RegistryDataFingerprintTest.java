package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.StatisticList;
import org.junit.BeforeClass;
import org.junit.Test;

public class RegistryDataFingerprintTest {
    @BeforeClass
    public static void initializeLegacyStaticsInProductionOrder() {
        BlockRegistryBootstrap.initialize();
        ItemRegistryBootstrap.initialize();
        AchievementRegistryBootstrap.initialize();
        StatisticList.a();
        assertTrue(Block.STONE != null);
        assertTrue(Item.STICK != null);
    }

    @Test
    public void builtInSemanticFingerprintMatchesServerSchemaTwentySix() {
        assertEquals(26, RegistryDataFingerprint.SCHEMA_VERSION);
        String fingerprint = RegistryDataFingerprint.captureSynchronizedData();
        assertEquals(RegistryDataFingerprint.BUILT_IN_SYNCHRONIZED_DATA,
                fingerprint);
        assertTrue(RegistryDataFingerprint.isBuiltInSynchronizedData(fingerprint));
    }
}
