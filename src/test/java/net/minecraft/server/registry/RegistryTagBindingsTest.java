package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;

public class RegistryTagBindingsTest {
    private static final ResourceLocation REGISTRY = key("test_registry");
    private static final ResourceLocation TAG = key("ordered");

    @Test
    public void capturesCanonicalValuesAndKeysAsOneImmutableRevision() {
        final Object first = new Object();
        final Object second = new Object();
        final Map<ResourceLocation, Object> values =
                new HashMap<ResourceLocation, Object>();
        final Map<Object, ResourceLocation> keys =
                new java.util.IdentityHashMap<Object, ResourceLocation>();
        values.put(key("first"), first);
        values.put(key("second"), second);
        keys.put(first, key("first"));
        keys.put(second, key("second"));

        ArrayList<ResourceLocation> members = new ArrayList<ResourceLocation>(
                Arrays.asList(key("first"), key("second")));
        LinkedHashMap<ResourceLocation, List<ResourceLocation>> decoded =
                new LinkedHashMap<ResourceLocation, List<ResourceLocation>>();
        decoded.put(TAG, members);
        RegistryTagBindings<Object> bindings = RegistryTagBindings.create(
                REGISTRY, 42L, decoded, resolver(values, keys));

        TagKey<Object> tag = TagKey.create(REGISTRY, TAG);
        members.clear();
        decoded.clear();
        assertEquals(42L, bindings.registryRevision());
        assertTrue(bindings.isForRevision(42L));
        assertFalse(bindings.isForRevision(43L));
        assertEquals(Arrays.asList(key("first"), key("second")),
                bindings.valueKeys(tag));
        assertSame(first, bindings.values(tag).get(0));
        assertTrue(bindings.contains(tag, second));
        try {
            bindings.values(tag).clear();
            fail("Expected immutable tag values");
        } catch (UnsupportedOperationException expected) {}
    }

    @Test
    public void rejectsAliasesAndForeignRegistryTags() {
        final Object value = new Object();
        final Map<ResourceLocation, Object> values =
                new HashMap<ResourceLocation, Object>();
        final Map<Object, ResourceLocation> keys =
                new java.util.IdentityHashMap<Object, ResourceLocation>();
        values.put(key("alias"), value);
        keys.put(value, key("canonical"));
        LinkedHashMap<ResourceLocation, List<ResourceLocation>> decoded =
                new LinkedHashMap<ResourceLocation, List<ResourceLocation>>();
        decoded.put(TAG, Arrays.asList(key("alias")));
        try {
            RegistryTagBindings.create(REGISTRY, 1L, decoded,
                    resolver(values, keys));
            fail("Expected alias tag value to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("alias"));
            assertTrue(expected.getMessage().contains("canonical"));
        }

        RegistryTagBindings<Object> empty =
                RegistryTagBindings.empty(REGISTRY, 1L);
        try {
            empty.values(TagKey.<Object>create(key("other_registry"), TAG));
            fail("Expected foreign tag owner to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("does not belong"));
        }
    }

    private static RegistryTagBindings.Resolver<Object> resolver(
            final Map<ResourceLocation, Object> values,
            final Map<Object, ResourceLocation> keys) {
        return new RegistryTagBindings.Resolver<Object>() {
            public Object get(ResourceLocation key) {
                return values.get(key);
            }

            public ResourceLocation getKey(Object value) {
                return keys.get(value);
            }
        };
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
