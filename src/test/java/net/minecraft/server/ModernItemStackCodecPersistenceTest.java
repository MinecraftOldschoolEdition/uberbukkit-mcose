package net.minecraft.server;

import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ModernItemStackCodecPersistenceTest {

    @BeforeClass
    public static void initializeBlocksBeforeItems() {
        assertNotNull(Block.STONE);
    }

    @Test
    public void damageableItemDefaultsIncludeZeroDamage() {
        ItemStack pristine = new ItemStack(Item.IRON_PICKAXE, 1, 0);

        assertEquals(Integer.valueOf(Item.IRON_PICKAXE.e()),
                pristine.getPatchedComponents().get(DataComponents.MAX_DAMAGE));
        assertEquals(Integer.valueOf(1),
                pristine.getPatchedComponents().get(DataComponents.MAX_STACK_SIZE));
        assertEquals(Integer.valueOf(0),
                pristine.getPatchedComponents().get(DataComponents.DAMAGE));
    }

    @Test
    public void removedMaxStackSizeResolvesToTheSnapshotFallbackOfOne() {
        ItemStack stack = new ItemStack(Block.STONE, 1, 0);
        stack.applyComponents(DataComponentPatch.builder()
                .remove(DataComponents.MAX_STACK_SIZE)
                .build());

        assertEquals(1, stack.getMaxStackSize());
        assertFalse(stack.isStackable());
    }

    @Test
    public void effectiveDurabilityComponentsDriveDamageAccessors() {
        ItemStack overridden = new ItemStack(Item.IRON_PICKAXE, 1, 0);
        overridden.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.MAX_DAMAGE, Integer.valueOf(10))
                .set(DataComponents.DAMAGE, Integer.valueOf(14))
                .build());

        assertTrue(overridden.d());
        assertTrue(overridden.f());
        assertEquals(10, overridden.i());

        ItemStack noDamageValue = new ItemStack(Item.IRON_PICKAXE, 1, 0);
        noDamageValue.applyComponents(DataComponentPatch.builder()
                .remove(DataComponents.DAMAGE)
                .build());
        assertFalse(noDamageValue.d());
        assertFalse(noDamageValue.f());

        ItemStack noMaximum = new ItemStack(Item.IRON_PICKAXE, 1, 0);
        noMaximum.applyComponents(DataComponentPatch.builder()
                .remove(DataComponents.MAX_DAMAGE)
                .build());
        assertFalse(noMaximum.d());
        assertFalse(noMaximum.f());
        assertEquals(0, noMaximum.i());
    }

    @Test
    public void damageMutationPreservesEveryUnrelatedPatchEntry() {
        ResourceLocation unknownValue = new ResourceLocation("example", "durability_value");
        ResourceLocation unknownRemoval = new ResourceLocation("example", "durability_removal");
        ItemStack stack = new ItemStack(Item.IRON_PICKAXE, 1, 0);
        stack.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.MAX_DAMAGE, Integer.valueOf(10))
                .set(DataComponents.CUSTOM_NAME, "component tool")
                .remove(DataComponents.ENCHANTMENTS)
                .setUnknown(unknownValue, new NBTTagString("opaque"))
                .removeUnknown(unknownRemoval)
                .build());

        stack.setItemDamage(7);
        assertDurabilityPatch(stack, 7, unknownValue, unknownRemoval);

        stack.damage(3, null);
        assertEquals(0, stack.count);
        assertDurabilityPatch(stack, 10, unknownValue, unknownRemoval);
    }

    @Test
    public void builtInItemsKeepCanonicalPrimaryNamesAndHistoricalAliasesAreReadOnly() {
        assertEquals("minecraft:sign", ItemRegistry.getKey(Item.SIGN).toString());
        assertEquals("minecraft:oak_door", ItemRegistry.getKey(Item.WOOD_DOOR).toString());
        assertEquals("minecraft:iron_door", ItemRegistry.getKey(Item.IRON_DOOR).toString());
        assertEquals("minecraft:repeater", ItemRegistry.getKey(Item.DIODE).toString());

        assertSame(Item.SIGN, ItemRegistry.get(new ResourceLocation("minecraft:sign")));
        assertSame(Item.SIGN, ItemRegistry.get(new ResourceLocation("minecraft:sign_compat_323")));
        assertSame(Item.WOOD_DOOR, ItemRegistry.get(new ResourceLocation("minecraft:oak_door_compat_324")));
        assertSame(Item.DIODE, ItemRegistry.get(new ResourceLocation("minecraft:repeater_compat_356")));

        Item slab = Item.byId[Block.STEP.id];
        assertEquals("minecraft:stone_slab", ItemRegistry.getKey(slab).toString());
        assertSame(slab, ItemRegistry.get(new ResourceLocation("minecraft:stone_slab")));
        assertSame(slab, ItemRegistry.get(new ResourceLocation("minecraft:stone_slab_block")));
        assertSame(Item.byId[Block.DOUBLE_STEP.id],
                ItemRegistry.get(new ResourceLocation("minecraft:double_stone_slab")));
    }

    @Test
    public void modernSavesContainNoNumericLegacyShadowFields() {
        ItemStack original = new ItemStack(Item.SIGN, 2, 0);
        NBTTagCompound customData = new NBTTagCompound();
        customData.setString("marker", "kept");
        original.setTag(customData);

        NBTTagCompound saved = new NBTTagCompound();
        saved.a("id", (short)Item.MINECART.id);
        saved.setString("name", "minecraft:minecart");
        saved.a("Count", (byte)99);
        saved.a("Damage", (short)7);
        saved.a("tag", new NBTTagCompound());
        original.save(saved);

        assertEquals(3, saved.e("mcose_stack_format"));
        assertEquals("minecraft:sign", saved.getString("item"));
        assertEquals(2, saved.e("count"));
        assertFalse(saved.hasKey("id"));
        assertFalse(saved.hasKey("name"));
        assertFalse(saved.hasKey("Count"));
        assertFalse(saved.hasKey("Damage"));
        assertFalse(saved.hasKey("tag"));

        ItemStack decoded = ItemStack.parse(saved);
        assertNotNull(decoded);
        assertSame(Item.SIGN, decoded.getItem());
        assertEquals(2, decoded.count);
        assertNotNull(decoded.getTag());
        assertEquals("kept", decoded.getTag().getString("marker"));
    }

    @Test
    public void woodenSlabStacksPersistAsCanonicalSingleSlabs() {
        ItemStack original = new ItemStack(Block.STEP, 1, 2);
        NBTTagCompound saved = original.save(new NBTTagCompound());

        assertEquals("minecraft:stone_slab", saved.getString("item"));
        assertFalse(saved.hasKey("id"));
        assertFalse(saved.hasKey("Damage"));

        ItemStack decoded = ItemStack.parse(saved);
        assertNotNull(decoded);
        assertEquals(Block.STEP.id, decoded.id);
        assertEquals(2, decoded.getItemDamage());
    }

    @Test
    public void numericFallbackIsLimitedToLegacyAndRegionCoreOneInputs() {
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.a("id", (short)Item.SIGN.id);
        legacy.a("Count", (byte)1);
        legacy.a("Damage", (short)0);
        assertSame(Item.SIGN, ItemStack.parse(legacy).getItem());

        NBTTagCompound regionCoreOne = unresolvedModernStack(2);
        assertSame(Item.SIGN, ItemStack.parse(regionCoreOne).getItem());

        NBTTagCompound current = unresolvedModernStack(3);
        assertNull(ItemStack.parse(current));
    }

    private static NBTTagCompound unresolvedModernStack(int formatVersion) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.a("mcose_stack_format", formatVersion);
        nbt.setString("item", "example:missing_item");
        nbt.a("count", 1);
        nbt.a("id", (short)Item.SIGN.id);
        return nbt;
    }

    private static void assertDurabilityPatch(
            ItemStack stack,
            int expectedDamage,
            ResourceLocation unknownValue,
            ResourceLocation unknownRemoval) {
        DataComponentPatch patch = stack.getComponents();
        assertEquals(Integer.valueOf(10), patch.get(DataComponents.MAX_DAMAGE));
        assertEquals(Integer.valueOf(expectedDamage), patch.get(DataComponents.DAMAGE));
        assertEquals("component tool", patch.get(DataComponents.CUSTOM_NAME));
        assertTrue(patch.getRemovedTypes().contains(DataComponents.ENCHANTMENTS));
        assertEquals(new NBTTagString("opaque"), patch.getUnknownComponents().get(unknownValue));
        assertTrue(patch.getUnknownRemovedComponents().contains(unknownRemoval));
    }
}
