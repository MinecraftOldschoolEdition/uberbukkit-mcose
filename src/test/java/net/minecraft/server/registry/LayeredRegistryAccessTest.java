package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;

public class LayeredRegistryAccessTest {
    @Test
    public void registryViewRemainsOnItsCapturedGeneration() {
        SimpleRegistry<String> live = new SimpleRegistry<String>();
        ResourceLocation first = id("first");
        ResourceLocation second = id("second");
        live.register(first, "one");
        RegistryView<String> captured = live.snapshotView();

        live.register(second, "two");

        assertEquals(1, captured.size());
        assertEquals("one", captured.get(first));
        assertNull(captured.get(second));
        assertEquals(2, live.snapshotView().size());
    }

    @Test
    public void replacementClearsDownstreamLayersAndPreservesOlderSnapshot() {
        ResourceLocation staticKey = id("block");
        ResourceLocation worldKey = id("painting_variant");
        ResourceLocation dimensionKey = id("level_stem");
        ResourceLocation reloadableKey = id("recipe");
        RegistryAccess staticAccess = access(staticKey, "stone");
        RegistryAccess oldWorld = access(worldKey, "kebab");
        RegistryAccess dimensions = access(dimensionKey, "overworld");
        RegistryAccess reloadable = access(reloadableKey, "crafting");

        LayeredRegistryAccess<RegistryLayer> original =
                new LayeredRegistryAccess<RegistryLayer>(RegistryLayer.orderedValues())
                        .replaceFrom(RegistryLayer.STATIC,
                                staticAccess, oldWorld, dimensions, reloadable);
        RegistryAccess newWorld = access(worldKey, "aztec");
        LayeredRegistryAccess<RegistryLayer> replaced =
                original.replaceFrom(RegistryLayer.WORLD, newWorld);

        assertSame(staticAccess.lookupUntyped(staticKey),
                replaced.getLayer(RegistryLayer.STATIC).lookupUntyped(staticKey));
        assertEquals("aztec", replaced.<String>getLayer(RegistryLayer.WORLD)
                .lookup(worldKey).values().iterator().next());
        assertEquals(0, replaced.getLayer(RegistryLayer.DIMENSIONS).size());
        assertEquals(0, replaced.getLayer(RegistryLayer.RELOADABLE).size());
        assertEquals("kebab", original.<String>getLayer(RegistryLayer.WORLD)
                .lookup(worldKey).values().iterator().next());
        assertNull(original.getAccessForLoading(RegistryLayer.WORLD).lookupUntyped(worldKey));
        assertSame(staticAccess.lookupUntyped(staticKey),
                original.getAccessForLoading(RegistryLayer.WORLD).lookupUntyped(staticKey));
    }

    @Test(expected = IllegalStateException.class)
    public void duplicateRegistryOwnershipAcrossLayersIsRejected() {
        ResourceLocation duplicate = id("recipe");
        new LayeredRegistryAccess<RegistryLayer>(RegistryLayer.orderedValues())
                .replaceFrom(RegistryLayer.STATIC,
                        access(duplicate, "static"), access(duplicate, "world"));
    }

    private static RegistryAccess access(ResourceLocation registryKey, String value) {
        SimpleRegistry<String> registry = new SimpleRegistry<String>();
        registry.register(id(value), value);
        return RegistryAccess.builder().add(registryKey, registry).build();
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
