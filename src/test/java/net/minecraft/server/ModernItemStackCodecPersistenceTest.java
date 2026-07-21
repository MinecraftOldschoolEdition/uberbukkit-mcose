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

public class ModernItemStackCodecPersistenceTest {

    @BeforeClass
    public static void initializeBlocksBeforeItems() {
        assertNotNull(Block.STONE);
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
}
