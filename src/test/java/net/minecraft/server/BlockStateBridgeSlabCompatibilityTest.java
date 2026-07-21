package net.minecraft.server;

import net.minecraft.server.registry.BlockRegistry;
import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BlockStateBridgeSlabCompatibilityTest {

    @Test
    public void canonicalSlabRegistryKeysAreNotDisplacedByRawAliases() {
        assertEquals("minecraft:double_stone_slab", BlockRegistry.getKey(Block.DOUBLE_STEP).toString());
        assertEquals("minecraft:stone_slab", BlockRegistry.getKey(Block.STEP).toString());
        assertSame(Block.DOUBLE_STEP, BlockRegistry.get(new ResourceLocation("minecraft:double_stone_slab")));
        assertSame(Block.STEP, BlockRegistry.get(new ResourceLocation("minecraft:stone_slab")));
    }

    @Test
    public void writesWoodSlabsAsCanonicalNamespacedPaletteStates() {
        byte[] blocks = new byte[32768];
        byte[] metadata = new byte[16384];
        Arrays.fill(blocks, (byte) 44);
        Arrays.fill(metadata, (byte) 0x22);
        NBTTagCompound level = new NBTTagCompound();

        assertTrue(BlockStateCodec.writeStateData(level, blocks, metadata));

        NBTTagCompound state = (NBTTagCompound) level.l(BlockStateCodec.KEY_PALETTE).a(0);
        assertEquals("minecraft:wooden_slab", state.getString("Name"));
        NBTTagCompound properties = state.k("Properties");
        assertEquals("bottom", properties.getString("type"));
        assertFalse(properties.hasKey(BlockStateBridge.PROP_LEGACY_META));
        assertEquals(BlockStateCodec.CURRENT_VERSION, level.e(BlockStateCodec.KEY_VERSION));
    }

    @Test
    public void roundTripsEveryClientSlabVariant() {
        String[] expectedKeys = new String[] {
                "minecraft:stone_slab",
                "minecraft:sandstone_slab",
                "minecraft:wooden_slab",
                "minecraft:cobblestone_slab",
                "minecraft:brick_slab",
                "minecraft:stone_brick_slab"
        };
        for (int blockId = 43; blockId <= 44; ++blockId) {
            for (int metadata = 0; metadata <= 5; ++metadata) {
                BlockStateKey state = BlockStateBridge.fromLegacy(blockId, metadata);
                BlockStateBridge.LegacyBlockData decoded = BlockStateBridge.toLegacy(state);

                assertEquals(expectedKeys[metadata], state.getBlockKey().toString());
                assertEquals(blockId == 43 ? "double" : "bottom", state.getProperty("type"));
                assertEquals(null, state.getProperty(BlockStateBridge.PROP_LEGACY_META));
                assertEquals(blockId, decoded.blockId);
                assertEquals(metadata, decoded.metadata);
                assertFalse(decoded.fallbackUsed);
            }
        }
    }

    @Test
    public void restoresHistoricalSingleSlabCollisionKeysAndWoodMetadata() {
        assertSlab("minecraft:stone_slab_legacy_44", "bottom", "wood", "2", 44, 2);
        assertSlab("minecraft:stone_slab_compat_44", "bottom", "wood", "2", 44, 2);
    }

    @Test
    public void restoresHistoricalDoubleSlabStoredUnderStoneSlabKey() {
        assertSlab("minecraft:stone_slab", "double", "wood", "2", 43, 2);
    }

    @Test
    public void decodesHistoricalPaletteWithoutProjectingSlabsToAir() {
        NBTTagCompound level = new NBTTagCompound();
        NBTTagList palette = new NBTTagList();
        palette.a(stateTag("minecraft:stone_slab_legacy_44", "bottom", "wood", "2"));
        level.a(BlockStateCodec.KEY_PALETTE, palette);
        level.a(BlockStateCodec.KEY_DATA, new byte[4096]);
        level.a(BlockStateCodec.KEY_BITS, (byte) 1);

        BlockStateCodec.DecodedState decoded = BlockStateCodec.readStateData(level);

        assertNotNull(decoded);
        assertFalse(decoded.usedNearestFallback);
        assertEquals(44, decoded.blocks[0] & 255);
        assertEquals(0x22, decoded.metadata[0] & 255);
    }

    @Test
    public void restoresGenericHistoricalRegistryCollisionKeys() {
        BlockStateBridge.LegacyBlockData decoded = BlockStateBridge.toLegacy(
                new BlockStateKey(new ResourceLocation("minecraft:mushroom_legacy_40"))
                        .withProperty(BlockStateBridge.PROP_LEGACY_META, "0"));

        assertEquals(40, decoded.blockId);
        assertEquals(0, decoded.metadata);
        assertFalse(decoded.fallbackUsed);
    }

    @Test
    public void canonicalizesOldPaletteOnceWithoutReplacingUnknownNamespacedStates() {
        NBTTagCompound level = new NBTTagCompound();
        NBTTagList palette = new NBTTagList();
        palette.a(stateTag("minecraft:stone_slab_legacy_44", "bottom", "wood", "2"));
        NBTTagCompound unknown = new NBTTagCompound();
        unknown.setString("Name", "example:future_block");
        NBTTagCompound unknownProperties = new NBTTagCompound();
        unknownProperties.setString("mode", "future");
        unknown.a("Properties", unknownProperties);
        palette.a(unknown);
        level.a(BlockStateCodec.KEY_PALETTE, palette);
        level.a(BlockStateCodec.KEY_DATA, new byte[4096]);
        level.a(BlockStateCodec.KEY_BITS, (byte)1);

        assertTrue(BlockStateCodec.canonicalizeStatePalette(level));
        assertEquals(BlockStateCodec.CURRENT_VERSION, level.e(BlockStateCodec.KEY_VERSION));

        NBTTagList converted = level.l(BlockStateCodec.KEY_PALETTE);
        NBTTagCompound woodSlab = (NBTTagCompound)converted.a(0);
        assertEquals("minecraft:wooden_slab", woodSlab.getString("Name"));
        assertEquals("bottom", woodSlab.k("Properties").getString("type"));
        assertFalse(woodSlab.k("Properties").hasKey(BlockStateBridge.PROP_LEGACY_META));

        NBTTagCompound preservedUnknown = (NBTTagCompound)converted.a(1);
        assertEquals("example:future_block", preservedUnknown.getString("Name"));
        assertEquals("future", preservedUnknown.k("Properties").getString("mode"));
        assertFalse(BlockStateCodec.canonicalizeStatePalette(level));
    }

    @Test
    public void regionCoreOneWorldsRequireTheCanonicalPaletteUpgrade() {
        assertEquals(WorldSaveVersions.REGIONCORE_2, WorldSaveVersions.currentWriteVersion());
        assertTrue(WorldSaveVersions.requiresRegionCoreConversion(WorldSaveVersions.REGIONCORE_1));
        assertFalse(WorldSaveVersions.requiresRegionCoreConversion(WorldSaveVersions.REGIONCORE_2));
        assertTrue(WorldSaveVersions.usesStatePalette(WorldSaveVersions.REGIONCORE_1));
    }

    private static void assertSlab(String name, String type, String variant, String metadata,
                                   int expectedBlockId, int expectedMetadata) {
        BlockStateBridge.LegacyBlockData decoded = BlockStateBridge.toLegacy(
                state(name, type, variant, metadata));

        assertEquals(expectedBlockId, decoded.blockId);
        assertEquals(expectedMetadata, decoded.metadata);
        assertFalse(decoded.fallbackUsed);
    }

    private static BlockStateKey state(String name, String type, String variant, String metadata) {
        return new BlockStateKey(new ResourceLocation(name))
                .withProperty("type", type)
                .withProperty("variant", variant)
                .withProperty(BlockStateBridge.PROP_LEGACY_META, metadata);
    }

    private static NBTTagCompound stateTag(String name, String type, String variant, String metadata) {
        NBTTagCompound state = new NBTTagCompound();
        state.setString("Name", name);
        NBTTagCompound properties = new NBTTagCompound();
        properties.setString("type", type);
        properties.setString("variant", variant);
        properties.setString(BlockStateBridge.PROP_LEGACY_META, metadata);
        state.a("Properties", properties);
        return state;
    }
}
