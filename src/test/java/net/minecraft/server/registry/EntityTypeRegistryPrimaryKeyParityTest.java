package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.EntityPigZombie;
import net.minecraft.server.EntitySnowman;
import net.minecraft.server.StatisticList;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class EntityTypeRegistryPrimaryKeyParityTest {
    @BeforeClass
    public static void initializeLegacyStaticsInProductionOrder() {
        StatisticList.a();
        EntityTypeRegistryBootstrap.initialize();
    }

    @Test
    public void primaryKeysExcludeLegacyBaseTypesAndUseModernSpecialNames() {
        Collection<ResourceLocation> primaryView =
                EntityTypeRegistry.primaryKeys();
        Set<ResourceLocation> primary =
                new HashSet<ResourceLocation>(primaryView);

        assertEquals(primary.size(), primaryView.size());
        assertFalse(primary.contains(key("mob")));
        assertFalse(primary.contains(key("monster")));
        assertFalse(primary.contains(key("snowman")));
        assertFalse(primary.contains(key("pig_zombie")));

        assertTrue(primary.contains(key("snow_golem")));
        assertTrue(primary.contains(key("zombified_piglin")));
        assertEquals(key("snow_golem"),
                EntityTypeRegistry.getKey(EntitySnowman.class));
        assertEquals(key("zombified_piglin"),
                EntityTypeRegistry.getKey(EntityPigZombie.class));
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
