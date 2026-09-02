package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;

public class RegistryRuntimeSnapshotTest {
    @Test
    public void successfulBootstrapPublishesWorldOwnedAndReloadableLayers() {
        RegistryBootstrap.initialize();
        RegistryRuntimeSnapshot snapshot = RegistryRuntime.current();
        RegistryAccess world = snapshot.worldAccess();
        RegistryAccess reloadable = snapshot.reloadableAccess();

        assertTrue(snapshot.getGeneration() > 0L);
        assertNotNull(world.lookupUntyped(id("block")));
        assertNotNull(world.lookupUntyped(id("painting_variant")));
        assertNotNull(world.lookupUntyped(id("jukebox_song")));
        assertNull(world.lookupUntyped(id("recipe")));
        assertNull(world.lookupUntyped(id("loot_table")));
        assertNull(world.lookupUntyped(id("number_provider")));
        assertNull(world.lookupUntyped(id("advancement")));
        assertNotNull(reloadable.lookupUntyped(id("recipe")));
        assertNotNull(reloadable.lookupUntyped(id("loot_table")));
        assertNotNull(reloadable.lookupUntyped(id("number_provider")));
        assertNotNull(reloadable.lookupUntyped(id("advancement")));
        assertEquals(0, snapshot.getLayers().getLayer(RegistryLayer.DIMENSIONS).size());
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
