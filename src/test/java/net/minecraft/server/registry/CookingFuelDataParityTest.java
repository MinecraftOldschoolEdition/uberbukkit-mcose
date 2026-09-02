package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.DataComponents;
import net.minecraft.server.ItemComponentDefaults;
import net.minecraft.server.ItemStack;
import net.minecraft.server.NBTBase;
import net.minecraft.server.Material;
import net.minecraft.server.item.component.CookingFuel;
import net.minecraft.server.registry.number.NumberProviders;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;
import uk.betacraft.uberbukkit.Uberbukkit;

public class CookingFuelDataParityTest {
    @BeforeClass
    public static void initializeRegistries() {
        assertNotNull(Block.STONE);
        assertNotNull(Item.STICK);
        ItemRegistryBootstrap.initialize();
        NumberProviderRegistryBootstrap.initialize();
        ItemCapabilityRegistryBootstrap.initialize();
    }

    @Test
    public void builtInAttachmentsPreserveTheLegacyFuelOracle() {
        Map<ResourceLocation, CookingFuel> bindings =
                ItemCapabilityRegistryApi.snapshotCookingFuelBindings();
        Map<ResourceLocation, ResourceLocation> expected =
                new LinkedHashMap<ResourceLocation, ResourceLocation>();

        for (int blockId = 0; blockId < Block.byId.length; blockId++) {
            Block block = Block.byId[blockId];
            Item item = blockId < Item.byId.length ? Item.byId[blockId] : null;
            if (block == null || block.material != Material.WOOD || item == null) {
                continue;
            }
            CookingFuel fuel = requireBinding(bindings, item);
            expected.put(ItemRegistry.getKey(item),
                    NumberProviders.COOKING_TIME_WOOD_BLOCKS);
            assertEquals(NumberProviders.COOKING_TIME_WOOD_BLOCKS,
                    fuel.getBurnTimeKey());
            assertEquals(300, ItemCapabilityRegistryApi.getFuelTicks(item));
        }

        assertFuel(bindings, Item.COAL, NumberProviders.COOKING_TIME_COAL, 1600);
        expected.put(ItemRegistry.getKey(Item.COAL),
                NumberProviders.COOKING_TIME_COAL);
        // Coal and charcoal are metadata variants of the same legacy ItemCoal.
        assertEquals(1600, ItemCapabilityRegistryApi.getFuelTicks(
                new ItemStack(Item.COAL, 1, 0)));
        assertEquals(1600, ItemCapabilityRegistryApi.getFuelTicks(
                new ItemStack(Item.COAL, 1, 1)));
        assertFuel(bindings, Item.byId[Block.COAL_BLOCK.id],
                NumberProviders.COOKING_TIME_COAL_BLOCK, 14400);
        expected.put(ItemRegistry.getKey(Item.byId[Block.COAL_BLOCK.id]),
                NumberProviders.COOKING_TIME_COAL_BLOCK);
        assertFuel(bindings, Item.LAVA_BUCKET,
                NumberProviders.COOKING_TIME_LAVA_BUCKET, 20000);
        expected.put(ItemRegistry.getKey(Item.LAVA_BUCKET),
                NumberProviders.COOKING_TIME_LAVA_BUCKET);
        assertFuel(bindings, Item.STICK,
                NumberProviders.COOKING_TIME_WOOD_ITEMS_EXTRA_SMALL, 100);
        expected.put(ItemRegistry.getKey(Item.STICK),
                NumberProviders.COOKING_TIME_WOOD_ITEMS_EXTRA_SMALL);
        CookingFuel sapling = requireBinding(
                bindings, Item.byId[Block.SAPLING.id]);
        assertEquals(NumberProviders.COOKING_TIME_DRY_PLANTS,
                sapling.getBurnTimeKey());
        assertEquals(100, sapling.resolveBurnTimeTicks());
        expected.put(ItemRegistry.getKey(Item.byId[Block.SAPLING.id]),
                NumberProviders.COOKING_TIME_DRY_PLANTS);
        assertEquals("client/server canonical binding count", 24, bindings.size());
        assertEquals(expected.keySet(), bindings.keySet());
        for (Map.Entry<ResourceLocation, ResourceLocation> entry
                : expected.entrySet()) {
            assertEquals(entry.getValue(),
                    bindings.get(entry.getKey()).getBurnTimeKey());
        }
        assertEquals(0, ItemCapabilityRegistryApi.getFuelTicks(Item.FEATHER));
    }

    @Test
    public void everyAttachmentUsesReloadableDefaultSpeedWithoutChangingFurnaceLogic() {
        for (CookingFuel fuel
                : ItemCapabilityRegistryApi.snapshotCookingFuelBindings().values()) {
            assertEquals(NumberProviders.COOKING_DEFAULT_SPEED_MULTIPLIER,
                    fuel.getSpeedMultiplierKey());
            assertEquals(1.0F, fuel.resolveSpeedMultiplier(), 0.0F);
        }
    }

    @Test
    public void cookingFuelIsPublishedThroughDefaultComponentsAndItsStableNbtSchema() {
        CookingFuel attached = ItemCapabilityRegistryApi.getCookingFuel(Item.COAL);
        CookingFuel defaultComponent = ItemComponentDefaults.defaultsFor(Item.COAL)
                .get(DataComponents.COOKING_FUEL);
        assertSame(attached, defaultComponent);

        NBTBase encoded = DataComponents.COOKING_FUEL.write(defaultComponent);
        CookingFuel decoded = DataComponents.COOKING_FUEL.read(encoded);
        assertEquals(defaultComponent, decoded);
        assertEquals(NumberProviders.COOKING_TIME_COAL,
                decoded.getBurnTimeKey());
        assertEquals(NumberProviders.COOKING_DEFAULT_SPEED_MULTIPLIER,
                decoded.getSpeedMultiplierKey());
    }

    @Test
    public void saplingAssignmentIsStableWhileThePvnElevenGateRemainsExact()
            throws Exception {
        Field target = Uberbukkit.class.getDeclaredField("pvn");
        target.setAccessible(true);
        Object previous = target.get(null);
        Item sapling = Item.byId[Block.SAPLING.id];
        CookingFuel attached = ItemCapabilityRegistryApi.getCookingFuel(sapling);
        assertNotNull(attached);
        try {
            target.set(null, Integer.valueOf(10));
            assertSame(attached, ItemCapabilityRegistryApi.getCookingFuel(sapling));
            assertEquals(0, ItemCapabilityRegistryApi.getFuelTicks(sapling));

            target.set(null, Integer.valueOf(11));
            assertSame(attached, ItemCapabilityRegistryApi.getCookingFuel(sapling));
            assertEquals(100, ItemCapabilityRegistryApi.getFuelTicks(sapling));
        } finally {
            target.set(null, previous);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void literalRuntimeRegistrationOverridesAttachedProvider() throws Exception {
        Field byKeyField = ItemCapabilityRegistryApi.class.getDeclaredField(
                "furnaceFuelByKey");
        Field byIdField = ItemCapabilityRegistryApi.class.getDeclaredField(
                "furnaceFuelByLegacyId");
        byKeyField.setAccessible(true);
        byIdField.setAccessible(true);
        Map<ResourceLocation, Integer> byKey =
                (Map<ResourceLocation, Integer>)byKeyField.get(null);
        Map<Integer, Integer> byId = (Map<Integer, Integer>)byIdField.get(null);
        ResourceLocation key = ItemRegistry.getKey(Item.STICK);
        Integer oldKey = byKey.get(key);
        Integer oldId = byId.get(Integer.valueOf(Item.STICK.id));
        try {
            assertEquals(100, ItemCapabilityRegistryApi.getFuelTicks(Item.STICK));
            ItemCapabilityRegistryApi.registerFuel(Item.STICK, 777);
            assertEquals(777, ItemCapabilityRegistryApi.getFuelTicks(Item.STICK));
            assertEquals(NumberProviders.COOKING_TIME_WOOD_ITEMS_EXTRA_SMALL,
                    ItemCapabilityRegistryApi.getCookingFuel(Item.STICK).getBurnTimeKey());
        } finally {
            if (oldKey == null) byKey.remove(key); else byKey.put(key, oldKey);
            if (oldId == null) byId.remove(Integer.valueOf(Item.STICK.id));
            else byId.put(Integer.valueOf(Item.STICK.id), oldId);
        }
        assertEquals(100, ItemCapabilityRegistryApi.getFuelTicks(Item.STICK));
    }

    private static void assertFuel(
            Map<ResourceLocation, CookingFuel> bindings,
            Item item,
            ResourceLocation burnTime,
            int ticks) {
        CookingFuel fuel = requireBinding(bindings, item);
        assertEquals(burnTime, fuel.getBurnTimeKey());
        assertEquals(ticks, fuel.resolveBurnTimeTicks());
        assertEquals(ticks, ItemCapabilityRegistryApi.getFuelTicks(item));
    }

    private static CookingFuel requireBinding(
            Map<ResourceLocation, CookingFuel> bindings,
            Item item) {
        assertNotNull(item);
        ResourceLocation key = ItemRegistry.getKey(item);
        assertNotNull(key);
        CookingFuel fuel = bindings.get(key);
        assertNotNull("Missing CookingFuel attachment for " + key, fuel);
        return fuel;
    }
}
