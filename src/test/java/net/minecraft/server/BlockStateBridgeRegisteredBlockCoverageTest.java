package net.minecraft.server;

import net.minecraft.server.registry.BlockRegistry;
import net.minecraft.server.registry.LegacyIdBridge;
import net.minecraft.server.util.ResourceLocation;
import net.minecraft.server.registry.VanillaRegistryKeys;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BlockStateBridgeRegisteredBlockCoverageTest {

    @Test
    public void everyRegisteredLegacyIdMatchesItsReviewedModernCounterpart() {
        String[] expectedKeys = reviewedModernKeys();

        for (int blockId = 0; blockId < Block.byId.length; ++blockId) {
            String expectedPath = expectedKeys[blockId];
            Block block = Block.byId[blockId];
            if (expectedPath == null) {
                assertNull("legacy block slot " + blockId + " should remain unregistered", block);
                assertNull("unregistered legacy ID " + blockId + " should not have a primary key",
                        LegacyIdBridge.blockKeyFromId(blockId));
                continue;
            }

            ResourceLocation expectedKey = new ResourceLocation("minecraft", expectedPath);
            assertNotNull("missing legacy block ID " + blockId + " for " + expectedKey, block);
            assertEquals("declared counterpart for legacy block ID " + blockId,
                    expectedKey, VanillaRegistryKeys.blockKey(block));
            assertEquals("registered counterpart for legacy block ID " + blockId,
                    expectedKey, BlockRegistry.getKey(block));
            assertSame("primary registry lookup for legacy block ID " + blockId,
                    block, BlockRegistry.get(expectedKey));
            assertEquals("reverse bridge for legacy block ID " + blockId,
                    Integer.valueOf(blockId), LegacyIdBridge.blockIdFromKey(expectedKey.toString()));
            assertEquals("forward bridge for legacy block ID " + blockId,
                    expectedKey.toString(), LegacyIdBridge.blockKeyFromId(blockId));
            assertEquals("RegionCore state counterpart for legacy block ID " + blockId,
                    expectedRegionCoreStateKey(blockId, expectedKey),
                    BlockStateBridge.fromLegacy(blockId, 0).getBlockKey());
        }
    }

    @Test
    public void everyLegacyBlockIdSurvivesRegionCoreProjection() {
        StringBuilder failures = new StringBuilder();

        for (int blockId = 0; blockId < Block.byId.length; ++blockId) {
            for (int metadata = 0; metadata < 16; ++metadata) {
                BlockStateKey state = BlockStateBridge.fromLegacy(blockId, metadata);
                BlockStateBridge.LegacyBlockData decoded = BlockStateBridge.toLegacy(state);
                int expectedBlockId = expectedRegionCoreProjectionId(blockId, metadata);
                if (decoded.fallbackUsed || decoded.blockId != expectedBlockId) {
                    failures.append("id=").append(blockId)
                            .append(" meta=").append(metadata)
                            .append(" state=").append(state)
                            .append(" decoded=").append(decoded.blockId)
                            .append(':').append(decoded.metadata)
                            .append(" fallback=").append(decoded.fallbackUsed)
                            .append('\n');
                }
            }

            BlockStateKey state = BlockStateBridge.fromLegacy(blockId, 0);
            if (blockId == 0) {
                assertEquals(new ResourceLocation("minecraft", "air"), state.getBlockKey());
            } else if (Block.byId[blockId] == null) {
                assertEquals(new ResourceLocation("legacy", "block_" + blockId), state.getBlockKey());
            }
        }

        assertEquals("RegionCore skipped or substituted legacy block IDs:\n" + failures, 0, failures.length());
    }

    @Test
    public void registeredBlocksDoNotNeedNumericCollisionKeys() {
        StringBuilder failures = new StringBuilder();

        for (int blockId = 1; blockId < Block.byId.length; ++blockId) {
            if (Block.byId[blockId] == null) {
                continue;
            }

            String path = BlockStateBridge.fromLegacy(blockId, 0).getBlockKey().getPath();
            if (path.matches(".*_(legacy|compat)_[0-9]+$")) {
                failures.append("id=").append(blockId).append(" key=").append(path).append('\n');
            }
        }

        assertEquals("Registered blocks still use collision-only RegionCore keys:\n" + failures, 0, failures.length());
    }

    @Test
    public void lateInitializedCompatibilityFenceGateKeepsItsRegistryKeyAndMigratesItsState() {
        ResourceLocation expectedKey = new ResourceLocation("minecraft", "oak_fence_gate_compat");
        assertEquals(expectedKey, BlockRegistry.getKey(Block.FENCE_GATE_COMPAT));
        assertSame(Block.FENCE_GATE_COMPAT, BlockRegistry.get(expectedKey));

        BlockStateKey state = BlockStateBridge.fromLegacy(Block.FENCE_GATE_COMPAT.id, 0);
        assertEquals(new ResourceLocation("minecraft", "oak_fence_gate"), state.getBlockKey());
        BlockStateBridge.LegacyBlockData decoded = BlockStateBridge.toLegacy(state);
        assertEquals(Block.FENCE_GATE.id, decoded.blockId);
        assertFalse(decoded.fallbackUsed);

        BlockStateBridge.LegacyBlockData historical = BlockStateBridge.toLegacy(
                new BlockStateKey(new ResourceLocation("minecraft", "oak_fence_gate_compat_150"))
                        .withProperty(BlockStateBridge.PROP_LEGACY_META, "7"));
        assertEquals(Block.FENCE_GATE.id, historical.blockId);
        assertEquals(7, historical.metadata);
        assertFalse(historical.fallbackUsed);
    }

    @Test
    public void snowLayerAndSnowBlockKeepDistinctModernCounterparts() {
        assertSnowState(Block.SNOW.id, "snow");
        assertSnowState(Block.SNOW_BLOCK.id, "snow_block");
        assertSame(Block.SNOW, BlockRegistry.get(new ResourceLocation("minecraft", "snow")));
        assertSame(Block.SNOW_BLOCK, BlockRegistry.get(new ResourceLocation("minecraft", "snow_block")));

        assertHistoricalSnowBlockState("minecraft:snow_compat_80");
        assertHistoricalSnowBlockState("minecraft:snow_legacy_80");
    }

    @Test
    public void historicalServerBlockNamesRemainReadableAliases() {
        assertHistoricalBlockAlias(Block.RAILS, "rails");
        assertHistoricalBlockAlias(Block.CAKE_BLOCK, "cake_block");
    }

    @Test
    public void paletteCodecKeepsEveryLegacyBlockId() {
        byte[] blocks = new byte[32768];
        byte[] metadata = new byte[16384];
        int position = 0;
        for (int blockId = 0; blockId < Block.byId.length; ++blockId) {
            for (int meta = 0; meta < 16; ++meta) {
                blocks[position] = (byte)blockId;
                setNibble(metadata, position, meta);
                ++position;
            }
        }

        NBTTagCompound level = new NBTTagCompound();
        assertTrue(BlockStateCodec.writeStateData(level, blocks, metadata));
        BlockStateCodec.DecodedState decoded = BlockStateCodec.readStateData(level);
        assertNotNull(decoded);
        assertFalse(decoded.usedNearestFallback);

        for (int index = 0; index < position; ++index) {
            int blockId = blocks[index] & 255;
            int metadataValue = index & 15;
            assertEquals("block at palette audit position " + index,
                    expectedRegionCoreProjectionId(blockId, metadataValue), decoded.blocks[index] & 255);
        }
    }

    private static void assertSnowState(int blockId, String expectedPath) {
        ResourceLocation expectedKey = new ResourceLocation("minecraft", expectedPath);
        for (int metadata = 0; metadata < 16; ++metadata) {
            BlockStateKey state = BlockStateBridge.fromLegacy(blockId, metadata);
            assertEquals(expectedKey, state.getBlockKey());
            assertEquals(Integer.toString(metadata), state.getProperty(BlockStateBridge.PROP_LEGACY_META));
            BlockStateBridge.LegacyBlockData decoded = BlockStateBridge.toLegacy(state);
            assertEquals(blockId, decoded.blockId);
            assertEquals(metadata, decoded.metadata);
            assertFalse(decoded.fallbackUsed);
        }
    }

    private static void assertHistoricalSnowBlockState(String name) {
        BlockStateBridge.LegacyBlockData decoded = BlockStateBridge.toLegacy(
                new BlockStateKey(new ResourceLocation(name))
                        .withProperty(BlockStateBridge.PROP_LEGACY_META, "7"));
        assertEquals(Block.SNOW_BLOCK.id, decoded.blockId);
        assertEquals(7, decoded.metadata);
        assertFalse(decoded.fallbackUsed);
        assertEquals(new ResourceLocation("minecraft", "snow_block"),
                BlockStateBridge.fromLegacy(decoded.blockId, decoded.metadata).getBlockKey());
    }

    private static void assertHistoricalBlockAlias(Block block, String path) {
        ResourceLocation key = new ResourceLocation("minecraft", path);
        assertSame("historical block alias " + key, block, BlockRegistry.get(key));
        assertEquals("historical block bridge " + key, Integer.valueOf(block.id),
                LegacyIdBridge.blockIdFromKey(key.toString()));
    }

    private static String[] reviewedModernKeys() {
        String[] keys = new String[256];
        expect(keys, 1, "stone");
        expect(keys, 2, "grass_block");
        expect(keys, 3, "dirt");
        expect(keys, 4, "cobblestone");
        expect(keys, 5, "oak_planks");
        expect(keys, 6, "oak_sapling");
        expect(keys, 7, "bedrock");
        expect(keys, 8, "water");
        expect(keys, 9, "water_still");
        expect(keys, 10, "lava");
        expect(keys, 11, "lava_still");
        expect(keys, 12, "sand");
        expect(keys, 13, "gravel");
        expect(keys, 14, "gold_ore");
        expect(keys, 15, "iron_ore");
        expect(keys, 16, "coal_ore");
        expect(keys, 17, "oak_log");
        expect(keys, 18, "oak_leaves");
        expect(keys, 19, "sponge");
        expect(keys, 20, "glass");
        expect(keys, 21, "lapis_ore");
        expect(keys, 22, "lapis_block");
        expect(keys, 23, "dispenser");
        expect(keys, 24, "sandstone");
        expect(keys, 25, "note_block");
        expect(keys, 26, "bed");
        expect(keys, 27, "powered_rail");
        expect(keys, 28, "detector_rail");
        expect(keys, 29, "sticky_piston");
        expect(keys, 30, "cobweb");
        expect(keys, 31, "tall_grass");
        expect(keys, 32, "dead_bush");
        expect(keys, 33, "piston");
        expect(keys, 34, "piston_head");
        expect(keys, 35, "white_wool");
        expect(keys, 36, "moving_piston");
        expect(keys, 37, "dandelion");
        expect(keys, 38, "poppy");
        expect(keys, 39, "brown_mushroom");
        expect(keys, 40, "red_mushroom");
        expect(keys, 41, "gold_block");
        expect(keys, 42, "iron_block");
        expect(keys, 43, "double_stone_slab");
        expect(keys, 44, "stone_slab");
        expect(keys, 45, "bricks");
        expect(keys, 46, "tnt");
        expect(keys, 47, "bookshelf");
        expect(keys, 48, "mossy_cobblestone");
        expect(keys, 49, "obsidian");
        expect(keys, 50, "torch");
        expect(keys, 51, "fire");
        expect(keys, 52, "mob_spawner");
        expect(keys, 53, "oak_stairs");
        expect(keys, 54, "chest");
        expect(keys, 55, "redstone_wire");
        expect(keys, 56, "diamond_ore");
        expect(keys, 57, "diamond_block");
        expect(keys, 58, "crafting_table");
        expect(keys, 59, "wheat");
        expect(keys, 60, "farmland");
        expect(keys, 61, "furnace");
        expect(keys, 62, "lit_furnace");
        expect(keys, 63, "oak_sign");
        expect(keys, 64, "oak_door");
        expect(keys, 65, "ladder");
        expect(keys, 66, "rail");
        expect(keys, 67, "cobblestone_stairs");
        expect(keys, 68, "oak_wall_sign");
        expect(keys, 69, "lever");
        expect(keys, 70, "stone_pressure_plate");
        expect(keys, 71, "iron_door");
        expect(keys, 72, "oak_pressure_plate");
        expect(keys, 73, "redstone_ore");
        expect(keys, 74, "lit_redstone_ore");
        expect(keys, 75, "redstone_torch_off");
        expect(keys, 76, "redstone_torch");
        expect(keys, 77, "stone_button");
        expect(keys, 78, "snow");
        expect(keys, 79, "ice");
        expect(keys, 80, "snow_block");
        expect(keys, 81, "cactus");
        expect(keys, 82, "clay");
        expect(keys, 83, "sugar_cane");
        expect(keys, 84, "jukebox");
        expect(keys, 85, "oak_fence");
        expect(keys, 86, "pumpkin_state");
        expect(keys, 87, "netherrack");
        expect(keys, 88, "soul_sand");
        expect(keys, 89, "glowstone");
        expect(keys, 90, "portal");
        expect(keys, 91, "jack_o_lantern");
        expect(keys, 92, "cake");
        expect(keys, 93, "repeater");
        expect(keys, 94, "lit_repeater");
        expect(keys, 95, "locked_chest");
        expect(keys, 96, "oak_trapdoor");
        expect(keys, 98, "stone_bricks");
        expect(keys, 99, "brown_mushroom_block");
        expect(keys, 100, "red_mushroom_block");
        expect(keys, 101, "wall_clock");
        expect(keys, 103, "melon");
        expect(keys, 104, "pumpkin_stem");
        expect(keys, 105, "melon_stem");
        expect(keys, 108, "brick_stairs");
        expect(keys, 109, "stone_brick_stairs");
        expect(keys, 150, "oak_fence_gate_compat");
        expect(keys, 152, "redstone_block");
        expect(keys, 170, "wet_sponge");
        expect(keys, 173, "coal_block");
        expect(keys, 187, "pumpkin");
        expect(keys, 188, "oak_fence_gate");
        expect(keys, 189, "carved_pumpkin");
        return keys;
    }

    private static void expect(String[] keys, int blockId, String path) {
        keys[blockId] = path;
    }

    private static ResourceLocation expectedRegionCoreStateKey(int blockId, ResourceLocation primaryKey) {
        switch (blockId) {
            case 9:
                return new ResourceLocation("minecraft", "water");
            case 11:
                return new ResourceLocation("minecraft", "lava");
            case 29:
                return new ResourceLocation("minecraft", "sticky_piston");
            case 43:
                return new ResourceLocation("minecraft", "stone_slab");
            case 75:
                return new ResourceLocation("minecraft", "redstone_torch");
            case 86:
                return new ResourceLocation("minecraft", "carved_pumpkin");
            case 94:
                return new ResourceLocation("minecraft", "repeater");
            case 150:
                return new ResourceLocation("minecraft", "oak_fence_gate");
            default:
                return primaryKey;
        }
    }

    private static int expectedRegionCoreProjectionId(int blockId, int metadata) {
        if (blockId == 86) {
            return metadata <= 3 ? Block.CARVED_PUMPKIN.id : Block.PUMPKIN_PLAIN.id;
        }
        if (blockId == 150) {
            return Block.FENCE_GATE.id;
        }
        if (blockId == Block.RED_MUSHROOM_CAP.id && (metadata == 10 || metadata == 15)) {
            return Block.BROWN_MUSHROOM_CAP.id;
        }
        return blockId;
    }

    private static void setNibble(byte[] values, int index, int value) {
        int byteIndex = index >> 1;
        int previous = values[byteIndex] & 255;
        if ((index & 1) == 0) {
            values[byteIndex] = (byte)((previous & 240) | (value & 15));
        } else {
            values[byteIndex] = (byte)((previous & 15) | ((value & 15) << 4));
        }
    }
}
