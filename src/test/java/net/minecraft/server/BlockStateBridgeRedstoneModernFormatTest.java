package net.minecraft.server;

import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BlockStateBridgeRedstoneModernFormatTest {
    @Test
    public void wallClockSignalStaysFullStrengthWheneverDaylightPowersIt() {
        boolean sawPoweredTime = false;
        boolean sawUnpoweredTime = false;
        for (long worldTime = 0L; worldTime < 24000L; ++worldTime) {
            int power = BlockWallClock.getPowerLevelForWorldTime(worldTime);
            assertTrue("clock power at " + worldTime, power == 0 || power == 15);
            sawPoweredTime |= power == 15;
            sawUnpoweredTime |= power == 0;
        }

        assertTrue(sawPoweredTime);
        assertTrue(sawUnpoweredTime);
        assertEquals(15, BlockWallClock.getPowerLevelForWorldTime(0L));
        assertEquals(15, BlockWallClock.getPowerLevelForWorldTime(6000L));
        assertEquals(15, BlockWallClock.getPowerLevelForWorldTime(12000L));
        assertEquals(0, BlockWallClock.getPowerLevelForWorldTime(18000L));
    }

    @Test
    public void runtimeMetadataUsesSnapshotStyleCanonicalStates() {
        assertState(Block.PISTON.id, 10, Block.PISTON.id,
                "minecraft:piston", "facing", "north", "extended", "true");
        assertState(Block.PISTON_STICKY.id, 5, Block.PISTON_STICKY.id,
                "minecraft:sticky_piston", "facing", "east", "extended", "false");
        assertState(Block.PISTON_EXTENSION.id, 13, Block.PISTON_EXTENSION.id,
                "minecraft:piston_head", "facing", "east", "type", "sticky", "short", "false");
        assertState(Block.PISTON_MOVING.id, 2, Block.PISTON_MOVING.id,
                "minecraft:moving_piston", "facing", "north", "type", "normal");
        assertState(Block.REDSTONE_WIRE.id, 12, Block.REDSTONE_WIRE.id,
                "minecraft:redstone_wire", "power", "12", "north", "none", "east", "none",
                "south", "none", "west", "none");

        BlockStateKey floorTorch = assertState(Block.REDSTONE_TORCH_ON.id, 5,
                Block.REDSTONE_TORCH_ON.id, "minecraft:redstone_torch", "lit", "true");
        assertNull(floorTorch.getProperty("facing"));
        assertState(Block.REDSTONE_TORCH_OFF.id, 1, Block.REDSTONE_TORCH_OFF.id,
                "minecraft:redstone_wall_torch", "facing", "east", "lit", "false");
        assertState(Block.DIODE_ON.id, 13, Block.DIODE_ON.id,
                "minecraft:repeater", "facing", "west", "delay", "4", "locked", "false",
                "powered", "true");
        assertState(Block.STONE_PLATE.id, 1, Block.STONE_PLATE.id,
                "minecraft:stone_pressure_plate", "powered", "true");
        assertState(Block.WOOD_PLATE.id, 0, Block.WOOD_PLATE.id,
                "minecraft:oak_pressure_plate", "powered", "false");
        assertState(Block.STONE_BUTTON.id, 9, Block.STONE_BUTTON.id,
                "minecraft:stone_button", "face", "wall", "facing", "east", "powered", "true");
        assertState(Block.WOODEN_DOOR.id, 6, Block.WOODEN_DOOR.id,
                "minecraft:oak_door", "facing", "west", "half", "lower", "hinge", "left",
                "open", "true", "powered", "false");
        assertState(Block.IRON_DOOR_BLOCK.id, 14, Block.IRON_DOOR_BLOCK.id,
                "minecraft:iron_door", "facing", "west", "half", "upper", "hinge", "left",
                "open", "true", "powered", "false");
        assertState(Block.TRAP_DOOR.id, 6, Block.TRAP_DOOR.id,
                "minecraft:oak_trapdoor", "facing", "west", "half", "bottom", "open", "true",
                "powered", "false", "waterlogged", "false");
        assertState(Block.FENCE_GATE.id, 7, Block.FENCE_GATE.id,
                "minecraft:oak_fence_gate", "facing", "east", "open", "true", "powered", "false",
                "in_wall", "false");
        assertState(Block.FENCE_GATE_COMPAT.id, 5, Block.FENCE_GATE.id,
                "minecraft:oak_fence_gate", "facing", "west", "open", "true", "powered", "false",
                "in_wall", "false");

        BlockStateKey clock = assertState(Block.WALL_CLOCK.id, 4, Block.WALL_CLOCK.id,
                "minecraft:wall_clock", "facing", "west");
        assertNull(clock.getProperty("power"));
        assertEquals(15, BlockWallClock.getPowerLevelForWorldTime(6000L));
        assertEquals(0, BlockWallClock.getPowerLevelForWorldTime(18000L));
    }

    @Test
    public void regionCoreV2MigratesLegacyShapedControlStates() {
        NBTTagCompound level = new NBTTagCompound();
        NBTTagList palette = new NBTTagList();
        palette.a(stateTag("minecraft:piston", "facing", "north", "extended", "true", "sticky", "true"));
        palette.a(stateTag("minecraft:redstone_torch", "facing", "west", "lit", "false"));
        palette.a(stateTag("minecraft:stone_button", "rotation", "1", "powered", "true", "legacy_meta", "9"));
        palette.a(stateTag("minecraft:oak_fence_gate_compat", "rotation", "3", "open", "true", "legacy_meta", "7"));
        palette.a(stateTag("minecraft:oak_door", "rotation", "2", "open", "true", "half", "upper", "legacy_meta", "14"));
        palette.a(stateTag("minecraft:repeater", "facing", "east", "delay", "4", "powered", "true"));
        level.a(BlockStateCodec.KEY_PALETTE, palette);
        level.a(BlockStateCodec.KEY_DATA, new byte[4096]);
        level.a(BlockStateCodec.KEY_BITS, (byte)3);
        level.a(BlockStateCodec.KEY_VERSION, 1);

        assertTrue(BlockStateCodec.canonicalizeStatePalette(level));
        assertEquals(BlockStateCodec.CURRENT_VERSION, level.e(BlockStateCodec.KEY_VERSION));

        NBTTagList converted = level.l(BlockStateCodec.KEY_PALETTE);
        assertConverted(converted, 0, "minecraft:sticky_piston", "facing", "north", "extended", "true");
        assertConverted(converted, 1, "minecraft:redstone_wall_torch", "facing", "east", "lit", "false");
        assertConverted(converted, 2, "minecraft:stone_button", "face", "wall", "facing", "east", "powered", "true");
        assertConverted(converted, 3, "minecraft:oak_fence_gate", "facing", "east", "open", "true",
                "powered", "false", "in_wall", "false");
        assertConverted(converted, 4, "minecraft:oak_door", "facing", "west", "half", "upper",
                "hinge", "left", "open", "true", "powered", "false");
        assertConverted(converted, 5, "minecraft:repeater", "facing", "west", "delay", "4",
                "locked", "false", "powered", "true");
        assertFalse(BlockStateCodec.canonicalizeStatePalette(level));
    }

    @Test
    public void unrepresentableModernPropertiesRequestLosslessPalettePreservation() {
        assertTrue(BlockStateBridge.toLegacy(state("minecraft:repeater", "locked", "true")).fallbackUsed);
        assertTrue(BlockStateBridge.toLegacy(state("minecraft:piston_head", "short", "true")).fallbackUsed);
        assertTrue(BlockStateBridge.toLegacy(state("minecraft:oak_trapdoor", "half", "top")).fallbackUsed);
        assertTrue(BlockStateBridge.toLegacy(state("minecraft:oak_fence_gate", "in_wall", "true")).fallbackUsed);
        assertTrue(BlockStateBridge.toLegacy(state("minecraft:oak_door", "hinge", "right")).fallbackUsed);
    }

    @Test
    public void everyRepresentableRuntimeControlStateRoundTripsExactly() {
        assertMetadataValues(Block.PISTON.id, 0, 1, 2, 3, 4, 5, 8, 9, 10, 11, 12, 13);
        assertMetadataValues(Block.PISTON_STICKY.id, 0, 1, 2, 3, 4, 5, 8, 9, 10, 11, 12, 13);
        assertMetadataValues(Block.PISTON_EXTENSION.id, 0, 1, 2, 3, 4, 5, 8, 9, 10, 11, 12, 13);
        assertMetadataValues(Block.PISTON_MOVING.id, 0, 1, 2, 3, 4, 5, 8, 9, 10, 11, 12, 13);
        assertMetadataRange(Block.REDSTONE_WIRE.id, 0, 15);
        assertMetadataRange(Block.REDSTONE_TORCH_OFF.id, 1, 5);
        assertMetadataRange(Block.REDSTONE_TORCH_ON.id, 1, 5);
        assertMetadataRange(Block.DIODE_OFF.id, 0, 15);
        assertMetadataRange(Block.DIODE_ON.id, 0, 15);
        assertMetadataRange(Block.STONE_PLATE.id, 0, 1);
        assertMetadataRange(Block.WOOD_PLATE.id, 0, 1);
        assertMetadataValues(Block.STONE_BUTTON.id, 1, 2, 3, 4, 9, 10, 11, 12);
        assertMetadataRange(Block.WOODEN_DOOR.id, 0, 15);
        assertMetadataRange(Block.IRON_DOOR_BLOCK.id, 0, 15);
        assertMetadataRange(Block.TRAP_DOOR.id, 0, 7);
        assertMetadataRange(Block.FENCE_GATE.id, 0, 7);
        assertMetadataValues(Block.WALL_CLOCK.id, 2, 3, 4, 5);
    }

    private static BlockStateKey assertState(int inputBlockId, int metadata, int expectedRuntimeId,
                                             String expectedName, String... properties) {
        BlockStateKey state = BlockStateBridge.fromLegacy(inputBlockId, metadata);
        assertEquals(expectedName, state.getBlockKey().toString());
        for (int i = 0; i < properties.length; i += 2) {
            assertEquals(properties[i], properties[i + 1], state.getProperty(properties[i]));
        }
        assertNull(state.getProperty(BlockStateBridge.PROP_LEGACY_META));

        BlockStateBridge.LegacyBlockData decoded = BlockStateBridge.toLegacy(state);
        assertEquals(expectedRuntimeId, decoded.blockId);
        assertEquals(metadata, decoded.metadata);
        assertFalse(decoded.fallbackUsed);
        return state;
    }

    private static void assertMetadataRange(int blockId, int firstMetadata, int lastMetadata) {
        for (int metadata = firstMetadata; metadata <= lastMetadata; ++metadata) {
            assertMetadataValues(blockId, metadata);
        }
    }

    private static void assertMetadataValues(int blockId, int... metadataValues) {
        for (int metadata : metadataValues) {
            BlockStateKey state = BlockStateBridge.fromLegacy(blockId, metadata);
            BlockStateBridge.LegacyBlockData decoded = BlockStateBridge.toLegacy(state);
            assertEquals("block id for " + state, blockId, decoded.blockId);
            assertEquals("metadata for " + state, metadata, decoded.metadata);
            assertFalse("fallback for " + state, decoded.fallbackUsed);
        }
    }

    private static void assertConverted(NBTTagList palette, int index, String expectedName,
                                        String... properties) {
        NBTTagCompound state = (NBTTagCompound)palette.a(index);
        assertEquals(expectedName, state.getString("Name"));
        NBTTagCompound stateProperties = state.k("Properties");
        for (int i = 0; i < properties.length; i += 2) {
            assertEquals(properties[i], properties[i + 1], stateProperties.getString(properties[i]));
        }
        assertFalse(stateProperties.hasKey(BlockStateBridge.PROP_LEGACY_META));
    }

    private static NBTTagCompound stateTag(String name, String... properties) {
        NBTTagCompound state = new NBTTagCompound();
        state.setString("Name", name);
        NBTTagCompound stateProperties = new NBTTagCompound();
        for (int i = 0; i < properties.length; i += 2) {
            stateProperties.setString(properties[i], properties[i + 1]);
        }
        state.a("Properties", stateProperties);
        return state;
    }

    private static BlockStateKey state(String name, String property, String value) {
        return new BlockStateKey(new ResourceLocation(name)).withProperty(property, value);
    }
}
