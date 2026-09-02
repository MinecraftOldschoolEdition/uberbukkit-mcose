package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Map;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.ItemStackTemplate;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class CraftingRemainderDataParityTest {
    @BeforeClass
    public static void initializeRegistries() {
        assertNotNull(Block.STONE);
        assertNotNull(Item.BUCKET);
        ItemRegistryBootstrap.initialize();
        ItemCapabilityRegistryBootstrap.initialize();
    }

    @Test
    public void canonicalBindingsAreExactlyTheThreeBetaBuckets() {
        Map<ResourceLocation, ItemStackTemplate> bindings =
                ItemCapabilityRegistryApi.snapshotCraftingRemainderBindings();
        assertEquals(3, bindings.size());
        assertBinding(bindings, "water_bucket");
        assertBinding(bindings, "lava_bucket");
        assertBinding(bindings, "milk_bucket");
        assertNull(bindings.get(key("coal")));
    }

    @Test
    public void everyConsumptionCreatesAnIndependentBucketStack() {
        ItemStack first = ItemCapabilityRegistryApi
                .createCraftingRemainder(Item.WATER_BUCKET);
        ItemStack second = ItemCapabilityRegistryApi
                .createCraftingRemainder(Item.WATER_BUCKET);
        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
        assertSame(Item.BUCKET, first.getItem());
        assertSame(Item.BUCKET, second.getItem());
        first.count = 0;
        assertEquals(1, second.count);
        assertNull(ItemCapabilityRegistryApi
                .createCraftingRemainder(Item.COAL));
    }

    private static void assertBinding(
            Map<ResourceLocation, ItemStackTemplate> bindings,
            String input) {
        ItemStackTemplate template = bindings.get(key(input));
        assertNotNull(template);
        assertSame(Item.BUCKET, template.getItem());
        assertEquals(key("bucket"), template.getItemKey());
        assertEquals(1, template.getCount());
        assertEquals(0, template.getLegacyMetadata());
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
