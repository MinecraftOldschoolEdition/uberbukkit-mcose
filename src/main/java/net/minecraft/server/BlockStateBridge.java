package net.minecraft.server;

import net.minecraft.server.registry.BlockRegistry;
import net.minecraft.server.registry.BlockRegistryBootstrap;
import net.minecraft.server.registry.LegacyIdBridge;
import net.minecraft.server.util.ResourceLocation;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic legacy <id,meta> <-> canonical block-state mapping.
 * Unknown or unmappable states fall back to nearest legacy projection.
 */
public final class BlockStateBridge {
    public static final String PROP_LEGACY_META = "legacy_meta";
    private static final String PROP_FACING = "facing";
    private static final String PROP_LIT = "lit";
    private static final String PROP_POWERED = "powered";
    private static final String PROP_DELAY = "delay";
    private static final String PROP_POWER = "power";
    private static final String PROP_AGE = "age";
    private static final String PROP_LEVEL = "level";
    private static final String PROP_FALLING = "falling";
    private static final String PROP_FLUID = "fluid";
    private static final String PROP_VARIANT = "variant";
    private static final String PROP_EXTENDED = "extended";
    private static final String PROP_STICKY = "sticky";
    private static final String PROP_OPEN = "open";
    private static final String PROP_HALF = "half";
    private static final String PROP_FACE = "face";
    private static final String PROP_HINGE = "hinge";
    private static final String PROP_LOCKED = "locked";
    private static final String PROP_SHORT = "short";
    private static final String PROP_IN_WALL = "in_wall";
    private static final String PROP_WATERLOGGED = "waterlogged";
    private static final String PROP_ROTATION = "rotation";
    private static final String PROP_SHAPE = "shape";
    private static final String PROP_TYPE = "type";
    private static final String PROP_PART = "part";
    private static final String PROP_OCCUPIED = "occupied";
    private static final String PROP_MOUNT = "mount";
    private static final String PROP_NORTH = "north";
    private static final String PROP_EAST = "east";
    private static final String PROP_SOUTH = "south";
    private static final String PROP_WEST = "west";
    private static final String PROP_UP = "up";
    private static final String PROP_DOWN = "down";

    private BlockStateBridge() {}

    public static BlockStateKey fromLegacy(int blockId, int metadata) {
        ensureLegacyBridgeReady();
        int safeMeta = metadata & 15;
        ResourceLocation blockKey = canonicalKeyForLegacy(blockId);
        BlockStateKey key = new BlockStateKey(blockKey);

        if (isRedstoneTorchId(blockId)) {
            boolean lit = blockId == getRedstoneTorchOnId();
            int orientation = safeMeta & 7;
            if (orientation >= 1 && orientation <= 4) {
                return new BlockStateKey(new ResourceLocation("minecraft", "redstone_wall_torch"))
                        .withProperty(PROP_FACING, redstoneWallTorchFacingFromMeta(orientation))
                        .withProperty(PROP_LIT, boolString(lit));
            }
            return new BlockStateKey(new ResourceLocation("minecraft", "redstone_torch"))
                    .withProperty(PROP_LIT, boolString(lit));
        }

        if (isRepeaterId(blockId)) {
            return new BlockStateKey(new ResourceLocation("minecraft", "repeater"))
                    .withProperty(PROP_FACING, repeaterFacingFromMeta(safeMeta & 3))
                    .withProperty(PROP_DELAY, Integer.toString(((safeMeta >> 2) & 3) + 1))
                    .withProperty(PROP_LOCKED, "false")
                    .withProperty(PROP_POWERED, boolString(blockId == getRepeaterOnId()));
        }

        if (isPistonBaseId(blockId)) {
            String path = blockId == getStickyPistonId() ? "sticky_piston" : "piston";
            return new BlockStateKey(new ResourceLocation("minecraft", path))
                    .withProperty(PROP_FACING, pistonFacingFromMeta(safeMeta & 7))
                    .withProperty(PROP_EXTENDED, boolString((safeMeta & 8) != 0));
        }

        if (isPistonHeadId(blockId)) {
            return key.withProperty(PROP_FACING, pistonFacingFromMeta(safeMeta & 7))
                    .withProperty(PROP_TYPE, (safeMeta & 8) != 0 ? "sticky" : "normal")
                    .withProperty(PROP_SHORT, "false");
        }

        if (isMovingPistonId(blockId)) {
            return key.withProperty(PROP_FACING, pistonFacingFromMeta(safeMeta & 7))
                    .withProperty(PROP_TYPE, (safeMeta & 8) != 0 ? "sticky" : "normal");
        }

        if (isFluidId(blockId)) {
            return key.withProperty(PROP_FLUID, isWaterId(blockId) ? "water" : "lava")
                    .withProperty(PROP_VARIANT, (blockId == getFlowingWaterId() || blockId == getFlowingLavaId()) ? "flowing" : "still")
                    .withProperty(PROP_LEVEL, Integer.toString(safeMeta))
                    .withProperty(PROP_FALLING, boolString((safeMeta & 8) != 0));
        }

        if (isRedstoneWireId(blockId)) {
            return key.withProperty(PROP_POWER, Integer.toString(clamp(safeMeta, 0, 15)))
                    .withProperty("north", "none")
                    .withProperty("east", "none")
                    .withProperty("south", "none")
                    .withProperty("west", "none");
        }

        if (isWallClockId(blockId)) {
            return key.withProperty(PROP_FACING, wallClockFacingFromMeta(safeMeta));
        }

        if (isTorchId(blockId)) {
            return key.withProperty(PROP_FACING, torchFacingFromMeta(safeMeta))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isFurnaceId(blockId)) {
            return key.withProperty(PROP_FACING, horizontalFacingFromMeta(safeMeta))
                    .withProperty(PROP_LIT, boolString(blockId == getLitFurnaceId()))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isDispenserId(blockId)) {
            return key.withProperty(PROP_FACING, horizontalFacingFromMeta(safeMeta))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isChestId(blockId)) {
            return key.withProperty(PROP_FACING, horizontalFacingFromMeta(safeMeta))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isLadderId(blockId)) {
            return key.withProperty(PROP_FACING, horizontalFacingFromMeta(safeMeta))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isWallSignId(blockId)) {
            return key.withProperty(PROP_FACING, horizontalFacingFromMeta(safeMeta))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isStandingSignId(blockId)) {
            return key.withProperty(PROP_ROTATION, Integer.toString(safeMeta))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isButtonId(blockId)) {
            return key.withProperty(PROP_FACE, "wall")
                    .withProperty(PROP_FACING, buttonFacingFromMeta(safeMeta & 7))
                    .withProperty(PROP_POWERED, boolString((safeMeta & 8) != 0));
        }

        if (isLeverId(blockId)) {
            return key.withProperty(PROP_ROTATION, Integer.toString(safeMeta & 7))
                    .withProperty(PROP_MOUNT, leverMountFromMeta(safeMeta & 7))
                    .withProperty(PROP_FACING, leverFacingFromMeta(safeMeta & 7))
                    .withProperty(PROP_POWERED, boolString((safeMeta & 8) != 0))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isPressurePlateId(blockId)) {
            return key.withProperty(PROP_POWERED, boolString(safeMeta > 0));
        }

		if (isStemId(blockId)) {
			if (BlockStem.isAttachedMetadata(safeMeta)) {
				String path = blockId == getPumpkinStemId() ? "attached_pumpkin_stem" : "attached_melon_stem";
				return new BlockStateKey(new ResourceLocation("minecraft", path))
						.withProperty(PROP_FACING, BlockStem.attachedFacing(safeMeta));
			}
			if (safeMeta <= BlockStem.MAX_AGE) {
				return key.withProperty(PROP_AGE, Integer.toString(safeMeta));
			}
			return key.withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
		}

        if (isHugeMushroomBlockId(blockId)) {
            return hugeMushroomStateFromLegacy(blockId, safeMeta);
        }

        if (isPumpkinId(blockId)) {
            if (blockId == getPumpkinPlainId() || blockId == getPumpkinId() && safeMeta > 3) {
                return new BlockStateKey(new ResourceLocation("minecraft", "pumpkin"));
            }
            int rotation = safeMeta & 3;
            String pumpkinPath = blockId == getJackOLanternId() ? "jack_o_lantern" : "carved_pumpkin";
            return new BlockStateKey(new ResourceLocation("minecraft", pumpkinPath))
                    .withProperty(PROP_FACING, pumpkinFacingFromMeta(rotation));
        }

        if (isTrapdoorId(blockId)) {
            int rotation = safeMeta & 3;
            return key.withProperty(PROP_FACING, trapdoorFacingFromMeta(rotation))
                    .withProperty(PROP_HALF, "bottom")
                    .withProperty(PROP_OPEN, boolString((safeMeta & 4) != 0))
                    .withProperty(PROP_POWERED, "false")
                    .withProperty(PROP_WATERLOGGED, "false");
        }

        if (isFenceGateId(blockId)) {
            int rotation = safeMeta & 3;
            return new BlockStateKey(new ResourceLocation("minecraft", "oak_fence_gate"))
                    .withProperty(PROP_FACING, fenceGateFacingFromMeta(rotation))
                    .withProperty(PROP_OPEN, boolString((safeMeta & 4) != 0))
                    .withProperty(PROP_POWERED, "false")
                    .withProperty(PROP_IN_WALL, "false");
        }

        if (isStairsId(blockId)) {
            int rotation = safeMeta & 3;
            return key.withProperty(PROP_ROTATION, Integer.toString(rotation))
                    .withProperty(PROP_FACING, stairsFacingFromMeta(rotation))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isSlabId(blockId)) {
            ResourceLocation slabKey = canonicalSlabKeyFromMeta(safeMeta);
            if (slabKey != null) {
                return new BlockStateKey(slabKey)
                        .withProperty(PROP_TYPE, blockId == getDoubleSlabId() ? "double" : "bottom");
            }

            // Unsupported metadata can only originate at a legacy/runtime
            // compatibility boundary. Preserve it explicitly without making
            // numeric IDs part of normal RegionCore slab state identity.
            return key.withProperty(PROP_TYPE, blockId == getDoubleSlabId() ? "double" : "bottom")
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isRailId(blockId)) {
            int shapeMeta = (blockId == getPoweredRailId() || blockId == getDetectorRailId()) ? (safeMeta & 7) : safeMeta;
            boolean powered = (blockId == getPoweredRailId() || blockId == getDetectorRailId()) && (safeMeta & 8) != 0;
            return key.withProperty(PROP_SHAPE, railShapeFromMeta(shapeMeta, blockId == getRailId()))
                    .withProperty(PROP_POWERED, boolString(powered))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isDoorId(blockId)) {
            int rotation = safeMeta & 3;
            return key.withProperty(PROP_FACING, doorFacingFromRotation(rotation))
                    .withProperty(PROP_OPEN, boolString((safeMeta & 4) != 0))
                    .withProperty(PROP_HALF, (safeMeta & 8) != 0 ? "upper" : "lower")
                    .withProperty(PROP_HINGE, "left")
                    .withProperty(PROP_POWERED, "false");
        }

        if (isBedId(blockId)) {
            int rotation = safeMeta & 3;
            return key.withProperty(PROP_ROTATION, Integer.toString(rotation))
                    .withProperty(PROP_PART, (safeMeta & 8) != 0 ? "foot" : "head")
                    .withProperty(PROP_OCCUPIED, boolString((safeMeta & 4) != 0))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isRedstoneOreId(blockId)) {
            return key.withProperty(PROP_LIT, boolString(blockId == getLitRedstoneOreId()))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        return key.withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
    }

    public static LegacyBlockData toLegacy(BlockStateKey key) {
        ensureLegacyBridgeReady();
        if (key == null) {
            return new LegacyBlockData(0, 0, true);
        }

        ResourceLocation blockKey = normalizeStateBlockKey(key.getBlockKey());
        String namespace = blockKey.getNamespace().toLowerCase(Locale.ROOT);
        String path = blockKey.getPath().toLowerCase(Locale.ROOT);
        Map<String, String> props = key.getProperties();
        Integer bridged = LegacyIdBridge.blockIdFromKey(blockKey.toString());
        if (bridged == null) {
            Block block = BlockRegistry.get(blockKey);
            if (block != null) {
                bridged = Integer.valueOf(block.id);
            }
        }
        if (bridged != null && !"minecraft".equals(namespace)) {
            return toRegisteredLegacy(bridged.intValue(), path, props);
        }

        if ("minecraft".equals(namespace) && "air".equals(path)) {
            int meta = clamp(parseInt(props.get(PROP_LEGACY_META), 0), 0, 15);
            return new LegacyBlockData(0, meta, false);
        }

		if ("minecraft".equals(namespace) && isPath(path, "attached_pumpkin_stem", "attached_melon_stem")) {
			int id = isPath(path, "attached_pumpkin_stem") ? getPumpkinStemId() : getMelonStemId();
			return new LegacyBlockData(id, BlockStem.attachedMetadata(props.get(PROP_FACING)), false);
		}

		if (isLegacyId(bridged, getPumpkinStemId()) || isLegacyId(bridged, getMelonStemId())
				|| isPath(path, "pumpkin_stem", "melon_stem")) {
			int id = isLegacyId(bridged, getMelonStemId()) || isPath(path, "melon_stem")
					? getMelonStemId() : getPumpkinStemId();
			int meta = props.containsKey(PROP_LEGACY_META)
					? clamp(parseInt(props.get(PROP_LEGACY_META), 0), 0, 15)
					: clamp(parseInt(props.get(PROP_AGE), 0), 0, BlockStem.MAX_AGE);
			return new LegacyBlockData(id, meta, false);
		}

        if ("minecraft".equals(namespace)
                && isPath(path, "brown_mushroom_block", "red_mushroom_block", "mushroom_stem")) {
            return hugeMushroomStateToLegacy(path, props);
        }

        // Slabs have shared one legacy block ID per half/double shape, with the
        // material stored in metadata. Older registry revisions wrote both
        // halves as minecraft:stone_slab, then disambiguated ID 44 with either
        // a _legacy_44 or _compat_44 suffix. Decode the saved shape before the
        // current registry key (where stone_slab now means ID 44) can erase it.
        if ("minecraft".equals(namespace) && isSlabStatePath(path, bridged)) {
            Integer collisionId = parseRegistryCollisionId(path);
            int id;
            if (isLegacyId(collisionId, getSlabId()) || isLegacyId(collisionId, getDoubleSlabId())) {
                id = collisionId.intValue();
            } else if ("double".equalsIgnoreCase(props.get(PROP_TYPE)) || path.startsWith("double_")) {
                id = getDoubleSlabId();
            } else {
                id = getSlabId();
            }
            int fallback = slabMetaFromPath(path, props.get(PROP_VARIANT));
            int meta = resolveMeta(props, fallback);
            return new LegacyBlockData(id, meta, false);
        }

        if (isPath(path, "redstone_torch", "redstone_wall_torch", "lit_redstone_torch", "redstone_torch_on", "redstone_torch_off", "redstone_torch_unlit")) {
            boolean lit = getBoolean(props, PROP_LIT, !isPath(path, "redstone_torch_off", "redstone_torch_unlit"));
            int id = lit ? getRedstoneTorchOnId() : getRedstoneTorchOffId();
            int meta;
            if (isPath(path, "redstone_wall_torch")) {
                meta = redstoneWallTorchMetaFromFacing(props.get(PROP_FACING));
            } else if (props.get(PROP_FACING) != null) {
                // RegionCore v1 stored legacy support-facing values on the
                // otherwise floor-only redstone_torch name.
                meta = torchMetaFromFacing(props.get(PROP_FACING));
            } else {
                meta = 5;
            }
            return new LegacyBlockData(id, meta, false);
        }

        if (isPath(path, "repeater", "lit_repeater", "redstone_repeater", "redstone_repeater_on", "redstone_repeater_off", "diode")) {
            boolean powered = getBoolean(props, PROP_POWERED, isPath(path, "lit_repeater", "redstone_repeater_on"));
            int id = powered ? getRepeaterOnId() : getRepeaterOffId();
            int facing = props.containsKey(PROP_LOCKED)
                    ? repeaterMetaFromFacing(props.get(PROP_FACING))
                    : regionCoreV1RepeaterMetaFromFacing(props.get(PROP_FACING));
            int delay = clamp(parseInt(props.get(PROP_DELAY), 1), 1, 4);
            int meta = (facing & 3) | ((delay - 1) << 2);
            return new LegacyBlockData(id, meta, getBoolean(props, PROP_LOCKED, false));
        }

        if (isPath(path, "piston", "sticky_piston")) {
            boolean sticky = getBoolean(props, PROP_STICKY, isPath(path, "sticky_piston"));
            int id = sticky ? getStickyPistonId() : getPistonId();
            int facing = pistonMetaFromFacing(props.get(PROP_FACING));
            boolean extended = getBoolean(props, PROP_EXTENDED, false);
            int meta = (facing & 7) | (extended ? 8 : 0);
            return new LegacyBlockData(id, meta, false);
        }

        if (isPath(path, "piston_head")) {
            int facing = pistonMetaFromFacing(props.get(PROP_FACING));
            boolean sticky = "sticky".equalsIgnoreCase(props.get(PROP_TYPE))
                    || getBoolean(props, PROP_STICKY, false);
            int meta = (facing & 7) | (sticky ? 8 : 0);
            return new LegacyBlockData(getPistonHeadId(), meta, getBoolean(props, PROP_SHORT, false));
        }

        if (isPath(path, "moving_piston", "piston_moving")) {
            int facing = pistonMetaFromFacing(props.get(PROP_FACING));
            boolean sticky = "sticky".equalsIgnoreCase(props.get(PROP_TYPE))
                    || getBoolean(props, PROP_STICKY, false);
            int meta = (facing & 7) | (sticky ? 8 : 0);
            return new LegacyBlockData(getMovingPistonId(), meta, false);
        }

        if (isLegacyId(bridged, getFlowingWaterId()) || isLegacyId(bridged, getStillWaterId())
                || isLegacyId(bridged, getFlowingLavaId()) || isLegacyId(bridged, getStillLavaId())
                || isPath(path, "water", "water_still", "flowing_water", "lava", "lava_still", "flowing_lava", "stationary_water", "stationary_lava")) {
            boolean water = isPath(path, "water", "water_still", "flowing_water", "stationary_water")
                    || isLegacyId(bridged, getFlowingWaterId()) || isLegacyId(bridged, getStillWaterId())
                    || "water".equalsIgnoreCase(props.get(PROP_FLUID));
            int level = clamp(parseInt(props.get(PROP_LEVEL), 0), 0, 15);
            boolean falling = getBoolean(props, PROP_FALLING, (level & 8) != 0);
            String variant = props.get(PROP_VARIANT);
            boolean flowing = "flowing".equalsIgnoreCase(variant)
                    || isPath(path, "flowing_water", "flowing_lava")
                    || (variant == null && (isLegacyId(bridged, getFlowingWaterId()) || isLegacyId(bridged, getFlowingLavaId())))
                    || (!"still".equalsIgnoreCase(variant) && !isPath(path, "stationary_water", "stationary_lava") && ((level & 7) > 0 || falling));
            int meta = (level & 7) | (falling ? 8 : 0);
            int id;
            if (water) {
                id = flowing ? getFlowingWaterId() : getStillWaterId();
            } else {
                id = flowing ? getFlowingLavaId() : getStillLavaId();
            }
            return new LegacyBlockData(id, meta, false);
        }

        if (isPath(path, "redstone_wire", "redstone_dust")) {
            int power = clamp(parseInt(props.get(PROP_POWER), 0), 0, 15);
            return new LegacyBlockData(getRedstoneWireId(), power, false);
        }

        if (isPath(path, "clock_sensor", "wall_clock")) {
            return new LegacyBlockData(getWallClockId(), wallClockMetaFromFacing(props.get(PROP_FACING)), false);
        }

        if (isLegacyId(bridged, getTorchId()) || isPath(path, "torch", "wall_torch")) {
            int meta = resolveMeta(props, torchMetaFromFacing(props.get(PROP_FACING)));
            return new LegacyBlockData(getTorchId(), meta, false);
        }

        if (isLegacyId(bridged, getFurnaceId()) || isLegacyId(bridged, getLitFurnaceId()) || isPath(path, "furnace", "lit_furnace", "furnace_lit")) {
            boolean lit = getBoolean(props, PROP_LIT, isLegacyId(bridged, getLitFurnaceId()) || isPath(path, "lit_furnace", "furnace_lit"));
            int id = lit ? getLitFurnaceId() : getFurnaceId();
            int meta = resolveMeta(props, horizontalMetaFromFacing(props.get(PROP_FACING), 3));
            return new LegacyBlockData(id, meta, false);
        }

        if (isLegacyId(bridged, getDispenserId()) || isPath(path, "dispenser")) {
            int meta = resolveMeta(props, horizontalMetaFromFacing(props.get(PROP_FACING), 3));
            return new LegacyBlockData(getDispenserId(), meta, false);
        }

        if (isLegacyId(bridged, getChestId()) || isPath(path, "chest", "trapped_chest", "ender_chest")) {
            int meta = resolveMeta(props, horizontalMetaFromFacing(props.get(PROP_FACING), 3));
            return new LegacyBlockData(getChestId(), meta, false);
        }

        if (isLegacyId(bridged, getLadderId()) || isPath(path, "ladder")) {
            int meta = resolveMeta(props, horizontalMetaFromFacing(props.get(PROP_FACING), 2));
            return new LegacyBlockData(getLadderId(), meta, false);
        }

        if (isLegacyId(bridged, getWallSignId()) || isPath(path, "oak_wall_sign", "wall_sign", "sign_wall")) {
            int meta = resolveMeta(props, horizontalMetaFromFacing(props.get(PROP_FACING), 2));
            return new LegacyBlockData(getWallSignId(), meta, false);
        }

        if (isLegacyId(bridged, getStandingSignId()) || isPath(path, "oak_sign", "standing_sign", "sign_post")) {
            int meta = resolveMeta(props, clamp(parseInt(props.get(PROP_ROTATION), 0), 0, 15));
            return new LegacyBlockData(getStandingSignId(), meta, false);
        }

        if (isLegacyId(bridged, getButtonId()) || isPath(path, "button", "stone_button", "wooden_button")) {
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), buttonMetaFromFacing(props.get(PROP_FACING))), 1, 4);
            int meta = resolveMeta(props, orientation | (getBoolean(props, PROP_POWERED, false) ? 8 : 0));
            boolean unsupportedFace = props.get(PROP_FACE) != null && !"wall".equalsIgnoreCase(props.get(PROP_FACE));
            return new LegacyBlockData(getButtonId(), meta, unsupportedFace);
        }

        if (isLegacyId(bridged, getLeverId()) || isPath(path, "lever")) {
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), leverMetaFromProperties(props)), 1, 6);
            int meta = resolveMeta(props, orientation | (getBoolean(props, PROP_POWERED, false) ? 8 : 0));
            return new LegacyBlockData(getLeverId(), meta, false);
        }

        if (isLegacyId(bridged, getStonePressurePlateId()) || isLegacyId(bridged, getWoodPressurePlateId()) || isPath(path, "pressure_plate", "stone_pressure_plate", "oak_pressure_plate", "wooden_pressure_plate")) {
            int id = isLegacyId(bridged, getWoodPressurePlateId()) || isPath(path, "oak_pressure_plate", "wooden_pressure_plate") ? getWoodPressurePlateId() : getStonePressurePlateId();
            int meta = resolveMeta(props, getBoolean(props, PROP_POWERED, false) ? 1 : 0);
            return new LegacyBlockData(id, meta, false);
        }

        if (isLegacyId(bridged, getPumpkinId()) || isLegacyId(bridged, getPumpkinPlainId())
                || isLegacyId(bridged, getCarvedPumpkinId()) || isLegacyId(bridged, getJackOLanternId())
                || isPath(path, "pumpkin", "pumpkin_state", "carved_pumpkin", "lit_pumpkin", "jack_o_lantern")) {
            int id;
            if (isLegacyId(bridged, getJackOLanternId()) || isPath(path, "lit_pumpkin", "jack_o_lantern")) {
                id = getJackOLanternId();
            } else if (isLegacyId(bridged, getCarvedPumpkinId()) || isPath(path, "carved_pumpkin")) {
                id = getCarvedPumpkinId();
            } else if (isLegacyId(bridged, getPumpkinPlainId()) || isPath(path, "pumpkin")) {
                id = getPumpkinPlainId();
            } else {
                id = getPumpkinId();
            }
            int fallback = pumpkinMetaFromFacing(props.get(PROP_FACING));
            int meta = resolveMeta(props, clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3));
            return new LegacyBlockData(id, meta, false);
        }

        if (isLegacyId(bridged, getTrapdoorId()) || isPath(path, "oak_trapdoor", "trapdoor", "wooden_trapdoor", "iron_trapdoor")) {
            int fallback = trapdoorMetaFromFacing(props.get(PROP_FACING));
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3);
            int baseMeta = orientation | (getBoolean(props, PROP_OPEN, false) ? 4 : 0);
            int meta = resolveMeta(props, baseMeta);
            boolean unsupported = "top".equalsIgnoreCase(props.get(PROP_HALF))
                    || getBoolean(props, PROP_POWERED, false)
                    || getBoolean(props, PROP_WATERLOGGED, false);
            return new LegacyBlockData(getTrapdoorId(), meta, unsupported);
        }

        if (isLegacyId(bridged, getFenceGateId()) || isLegacyId(bridged, getFenceGateCompatId())
                || isLegacyId(parseRegistryCollisionId(path), getFenceGateCompatId())
                || isPath(path, "oak_fence_gate", "oak_fence_gate_compat", "fence_gate", "fencegate")) {
            int fallback = fenceGateMetaFromFacing(props.get(PROP_FACING));
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3);
            int baseMeta = orientation | (getBoolean(props, PROP_OPEN, false) ? 4 : 0);
            int meta = resolveMeta(props, baseMeta);
            boolean unsupported = getBoolean(props, PROP_POWERED, false)
                    || getBoolean(props, PROP_IN_WALL, false);
            return new LegacyBlockData(getFenceGateId(), meta, unsupported);
        }

        if (isLegacyId(bridged, getWoodStairsId()) || isLegacyId(bridged, getCobbleStairsId()) || isLegacyId(bridged, getBrickStairsId()) || isLegacyId(bridged, getStoneBrickStairsId())) {
            int id = bridged.intValue();
            int fallback = stairsMetaFromFacing(props.get(PROP_FACING));
            int meta = resolveMeta(props, clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3));
            return new LegacyBlockData(id, meta, false);
        }

        if (isLegacyId(bridged, getSlabId()) || isLegacyId(bridged, getDoubleSlabId())) {
            int id = bridged.intValue();
            int fallback = slabMetaFromVariant(props.get(PROP_VARIANT));
            int meta = resolveMeta(props, fallback);
            return new LegacyBlockData(id, meta, false);
        }

        if (isLegacyId(bridged, getRailId()) || isLegacyId(bridged, getPoweredRailId()) || isLegacyId(bridged, getDetectorRailId()) || isPath(path, "rail", "powered_rail", "golden_rail", "detector_rail")) {
            int id = isLegacyId(bridged, getPoweredRailId()) || isPath(path, "powered_rail", "golden_rail")
                    ? getPoweredRailId()
                    : (isLegacyId(bridged, getDetectorRailId()) || isPath(path, "detector_rail") ? getDetectorRailId() : getRailId());
            int fallback = railMetaFromShape(props.get(PROP_SHAPE), id == getRailId());
            int baseMeta = (id == getPoweredRailId() || id == getDetectorRailId())
                    ? ((fallback & 7) | (getBoolean(props, PROP_POWERED, false) ? 8 : 0))
                    : (fallback & 15);
            int meta = resolveMeta(props, baseMeta);
            return new LegacyBlockData(id, meta, false);
        }

        if (isLegacyId(bridged, getWoodDoorId()) || isLegacyId(bridged, getIronDoorId()) || isPath(path, "oak_door", "wooden_door", "wood_door", "iron_door")) {
            int id = isLegacyId(bridged, getIronDoorId()) || isPath(path, "iron_door") ? getIronDoorId() : getWoodDoorId();
            int fallback = clamp(parseInt(props.get(PROP_ROTATION), doorRotationFromFacing(props.get(PROP_FACING))), 0, 3);
            int baseMeta = fallback
                    | (getBoolean(props, PROP_OPEN, false) ? 4 : 0)
                    | ("upper".equalsIgnoreCase(props.get(PROP_HALF)) ? 8 : 0);
            int meta = resolveMeta(props, baseMeta);
            boolean unsupported = "right".equalsIgnoreCase(props.get(PROP_HINGE))
                    || getBoolean(props, PROP_POWERED, false);
            return new LegacyBlockData(id, meta, unsupported);
        }

        if (isLegacyId(bridged, getBedId()) || isPath(path, "bed")) {
            int fallback = clamp(parseInt(props.get(PROP_ROTATION), bedRotationFromFacing(props.get(PROP_FACING))), 0, 3);
            int baseMeta = fallback
                    | (getBoolean(props, PROP_OCCUPIED, false) ? 4 : 0)
                    | ("foot".equalsIgnoreCase(props.get(PROP_PART)) ? 8 : 0);
            int meta = resolveMeta(props, baseMeta);
            return new LegacyBlockData(getBedId(), meta, false);
        }

        if (isLegacyId(bridged, getRedstoneOreId()) || isLegacyId(bridged, getLitRedstoneOreId()) || isPath(path, "redstone_ore", "lit_redstone_ore", "glowing_redstone_ore")) {
            int id = isLegacyId(bridged, getLitRedstoneOreId()) || isPath(path, "lit_redstone_ore", "glowing_redstone_ore") || getBoolean(props, PROP_LIT, false)
                    ? getLitRedstoneOreId()
                    : getRedstoneOreId();
            int meta = resolveMeta(props, 0);
            return new LegacyBlockData(id, meta, false);
        }

        if (bridged == null) {
            Integer legacySynthetic = parseLegacySyntheticId(namespace, path);
            if (legacySynthetic != null) {
                int meta = props.containsKey(PROP_LEGACY_META)
                        ? clamp(parseInt(props.get(PROP_LEGACY_META), 0), 0, 15)
                        : inferMetadataFromProperties(legacySynthetic, path, props);
                return new LegacyBlockData(legacySynthetic.intValue(), meta, false);
            }
            if ("minecraft".equals(namespace)) {
                Integer vanillaLegacyId = vanillaLegacyIdForPath(path);
                if (vanillaLegacyId != null) {
                    int meta = clamp(parseInt(props.get(PROP_LEGACY_META), 0), 0, 15);
                    return new LegacyBlockData(vanillaLegacyId.intValue(), meta, false);
                }
            }
        }
        int id = bridged == null ? 0 : bridged.intValue();
        int meta = props.containsKey(PROP_LEGACY_META)
                ? clamp(parseInt(props.get(PROP_LEGACY_META), 0), 0, 15)
                : inferMetadataFromProperties(bridged, path, props);
        boolean fallbackUsed = bridged == null;
        return new LegacyBlockData(id, meta, fallbackUsed);
    }

    public static final class LegacyBlockData {
        public final int blockId;
        public final int metadata;
        public final boolean fallbackUsed;

        public LegacyBlockData(int blockId, int metadata, boolean fallbackUsed) {
            this.blockId = blockId;
            this.metadata = metadata & 15;
            this.fallbackUsed = fallbackUsed;
        }
    }

    private static ResourceLocation canonicalKeyForLegacy(int blockId) {
        if (blockId == 0) {
            return new ResourceLocation("minecraft", "air");
        }
        if (isRedstoneTorchId(blockId)) {
            return new ResourceLocation("minecraft", "redstone_torch");
        }
        if (isRepeaterId(blockId)) {
            return new ResourceLocation("minecraft", "repeater");
        }
        if (isPistonBaseId(blockId)) {
            return new ResourceLocation("minecraft", blockId == getStickyPistonId() ? "sticky_piston" : "piston");
        }
        if (isPistonHeadId(blockId)) {
            return new ResourceLocation("minecraft", "piston_head");
        }
        if (isMovingPistonId(blockId)) {
            return new ResourceLocation("minecraft", "moving_piston");
        }
        if (isFluidId(blockId)) {
            return new ResourceLocation("minecraft", isWaterId(blockId) ? "water" : "lava");
        }

        Block block = blockId >= 0 && blockId < Block.byId.length ? Block.byId[blockId] : null;
        ResourceLocation key = block == null ? null : BlockRegistry.getKey(block);
        if (key == null) {
            String bridged = LegacyIdBridge.blockKeyFromId(blockId);
            if (bridged != null) {
                key = new ResourceLocation(bridged);
            }
        }
        if (key == null) {
            key = new ResourceLocation("legacy", "block_" + blockId);
        }
        return key;
    }

    private static void ensureLegacyBridgeReady() {
        try {
            BlockRegistryBootstrap.initialize();
        } catch (Throwable ignored) {
        }
    }

    static String normalizeStateName(String name) {
        if (name == null || name.length() == 0) {
            return "minecraft:air";
        }
        return normalizeStateBlockKey(new ResourceLocation(name)).toString();
    }

    private static ResourceLocation normalizeStateBlockKey(ResourceLocation key) {
        if (key == null) {
            return new ResourceLocation("minecraft:air");
        }
        String namespace = key.getNamespace();
        String path = key.getPath();
        if (!"minecraft".equalsIgnoreCase(namespace)) {
            return key;
        }
        String repairedPath = collapseCharacterSeparatedPath(path);
        if (repairedPath.equals(path)) {
            return key;
        }
        return new ResourceLocation(namespace, repairedPath);
    }

    private static String collapseCharacterSeparatedPath(String path) {
        if (path == null || path.indexOf('_') < 0) {
            return path == null ? "" : path;
        }

        String normalized = path.toLowerCase(Locale.ROOT);
        int nonEmptySegments = 0;
        boolean allSegmentsSingleCharacters = true;
        int segmentLength = 0;
        for (int i = 0; i <= normalized.length(); i++) {
            char c = i < normalized.length() ? normalized.charAt(i) : '_';
            if (c == '_') {
                if (segmentLength > 0) {
                    nonEmptySegments++;
                    if (segmentLength != 1) {
                        allSegmentsSingleCharacters = false;
                    }
                }
                segmentLength = 0;
            } else {
                segmentLength++;
            }
        }
        if (!allSegmentsSingleCharacters || nonEmptySegments < 3) {
            return path;
        }

        StringBuilder out = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c != '_') {
                out.append(c);
                continue;
            }
            int runStart = i;
            while (i + 1 < normalized.length() && normalized.charAt(i + 1) == '_') {
                i++;
            }
            if (i > runStart && out.length() > 0 && out.charAt(out.length() - 1) != '_') {
                out.append('_');
            }
        }
        return out.toString();
    }

    private static Integer parseLegacySyntheticId(String namespace, String path) {
        if ("legacy".equals(namespace) && path != null && path.startsWith("block_")) {
            return parseLegacyId(path.substring("block_".length()));
        }

        if (!"minecraft".equals(namespace)) {
            return null;
        }
        return parseRegistryCollisionId(path);
    }

    private static Integer parseRegistryCollisionId(String path) {
        if (path == null) {
            return null;
        }
        int marker = path.lastIndexOf("_legacy_");
        int markerLength = "_legacy_".length();
        if (marker <= 0) {
            marker = path.lastIndexOf("_compat_");
            markerLength = "_compat_".length();
        }
        if (marker <= 0) {
            return null;
        }
        return parseLegacyId(path.substring(marker + markerLength));
    }

    private static Integer parseLegacyId(String value) {
        int parsed = parseInt(value, -1);
        if (parsed < 0 || parsed > 255) {
            return null;
        }
        return Integer.valueOf(parsed);
    }

    private static Integer vanillaLegacyIdForPath(String path) {
        if (path == null) {
            return null;
        }
        if (isPath(path, "stone")) return Integer.valueOf(Block.STONE.id);
        if (isPath(path, "grass", "grass_block")) return Integer.valueOf(Block.GRASS.id);
        if (isPath(path, "dirt")) return Integer.valueOf(Block.DIRT.id);
        if (isPath(path, "cobblestone")) return Integer.valueOf(Block.COBBLESTONE.id);
        if (isPath(path, "oak_planks", "planks", "wood")) return Integer.valueOf(Block.WOOD.id);
        if (isPath(path, "oak_sapling", "sapling")) return Integer.valueOf(Block.SAPLING.id);
        if (isPath(path, "bedrock")) return Integer.valueOf(Block.BEDROCK.id);
        if (isPath(path, "sand")) return Integer.valueOf(Block.SAND.id);
        if (isPath(path, "gravel")) return Integer.valueOf(Block.GRAVEL.id);
        if (isPath(path, "gold_ore")) return Integer.valueOf(Block.GOLD_ORE.id);
        if (isPath(path, "iron_ore")) return Integer.valueOf(Block.IRON_ORE.id);
        if (isPath(path, "coal_ore")) return Integer.valueOf(Block.COAL_ORE.id);
        if (isPath(path, "oak_log", "log")) return Integer.valueOf(Block.LOG.id);
        if (isPath(path, "oak_leaves", "leaves")) return Integer.valueOf(Block.LEAVES.id);
        if (isPath(path, "sponge")) return Integer.valueOf(Block.SPONGE.id);
        if (isPath(path, "wet_sponge")) return Integer.valueOf(Block.WET_SPONGE.id);
        if (isPath(path, "glass")) return Integer.valueOf(Block.GLASS.id);
        if (isPath(path, "lapis_ore")) return Integer.valueOf(Block.LAPIS_ORE.id);
        if (isPath(path, "lapis_block")) return Integer.valueOf(Block.LAPIS_BLOCK.id);
        if (isPath(path, "redstone_block")) return Integer.valueOf(Block.REDSTONE_BLOCK.id);
        if (isPath(path, "coal_block")) return Integer.valueOf(Block.COAL_BLOCK.id);
        if (isPath(path, "sandstone")) return Integer.valueOf(Block.SANDSTONE.id);
        if (isPath(path, "note_block")) return Integer.valueOf(Block.NOTE_BLOCK.id);
        if (isPath(path, "cobweb", "web")) return Integer.valueOf(Block.WEB.id);
        if (isPath(path, "tall_grass", "long_grass")) return Integer.valueOf(Block.LONG_GRASS.id);
        if (isPath(path, "dead_bush")) return Integer.valueOf(Block.DEAD_BUSH.id);
        if (isPath(path, "white_wool", "wool")) return Integer.valueOf(Block.WOOL.id);
        if (isPath(path, "dandelion", "yellow_flower")) return Integer.valueOf(Block.YELLOW_FLOWER.id);
        if (isPath(path, "poppy", "red_rose")) return Integer.valueOf(Block.RED_ROSE.id);
        if (isPath(path, "brown_mushroom")) return Integer.valueOf(Block.BROWN_MUSHROOM.id);
        if (isPath(path, "red_mushroom")) return Integer.valueOf(Block.RED_MUSHROOM.id);
        if (isPath(path, "gold_block")) return Integer.valueOf(Block.GOLD_BLOCK.id);
        if (isPath(path, "iron_block")) return Integer.valueOf(Block.IRON_BLOCK.id);
        if (isPath(path, "bricks", "brick")) return Integer.valueOf(Block.BRICK.id);
        if (isPath(path, "bookshelf")) return Integer.valueOf(Block.BOOKSHELF.id);
        if (isPath(path, "mossy_cobblestone")) return Integer.valueOf(Block.MOSSY_COBBLESTONE.id);
        if (isPath(path, "mob_spawner")) return Integer.valueOf(Block.MOB_SPAWNER.id);
        if (isPath(path, "crafting_table", "workbench")) return Integer.valueOf(Block.WORKBENCH.id);
        if (isPath(path, "wheat", "crops")) return Integer.valueOf(Block.CROPS.id);
        if (isPath(path, "farmland")) return Integer.valueOf(Block.SOIL.id);
        if (isPath(path, "snow", "snow_layer")) return Integer.valueOf(Block.SNOW.id);
        if (isPath(path, "ice")) return Integer.valueOf(Block.ICE.id);
        if (isPath(path, "snow_block")) return Integer.valueOf(Block.SNOW_BLOCK.id);
        if (isPath(path, "cactus")) return Integer.valueOf(Block.CACTUS.id);
        if (isPath(path, "clay")) return Integer.valueOf(Block.CLAY.id);
        if (isPath(path, "sugar_cane", "reeds")) return Integer.valueOf(Block.SUGAR_CANE_BLOCK.id);
        if (isPath(path, "jukebox")) return Integer.valueOf(Block.JUKEBOX.id);
        if (isPath(path, "oak_fence", "fence")) return Integer.valueOf(Block.FENCE.id);
        if (isPath(path, "melon")) return Integer.valueOf(Block.MELON.id);
        if (isPath(path, "pumpkin_stem")) return Integer.valueOf(Block.PUMPKIN_STEM.id);
        if (isPath(path, "melon_stem")) return Integer.valueOf(Block.MELON_STEM.id);
        if (isPath(path, "netherrack")) return Integer.valueOf(Block.NETHERRACK.id);
        if (isPath(path, "soul_sand")) return Integer.valueOf(Block.SOUL_SAND.id);
        if (isPath(path, "glowstone")) return Integer.valueOf(Block.GLOWSTONE.id);
        if (isPath(path, "portal")) return Integer.valueOf(Block.PORTAL.id);
        if (isPath(path, "brown_mushroom_block")) return Integer.valueOf(Block.BROWN_MUSHROOM_CAP.id);
        if (isPath(path, "red_mushroom_block")) return Integer.valueOf(Block.RED_MUSHROOM_CAP.id);
        if (isPath(path, "cake")) return Integer.valueOf(Block.CAKE_BLOCK.id);
        if (isPath(path, "locked_chest")) return Integer.valueOf(Block.LOCKED_CHEST.id);
        if (isPath(path, "stone_bricks", "stonebrick")) return Integer.valueOf(Block.STONE_BRICK.id);
        return null;
    }

    private static int resolveMeta(Map<String, String> props, int fallback) {
        return clamp(parseInt(props.get(PROP_LEGACY_META), fallback), 0, 15);
    }

    private static BlockStateKey hugeMushroomStateFromLegacy(int blockId, int metadata) {
        boolean stem = metadata == 10 || metadata == 15;
        String path = stem ? "mushroom_stem"
                : blockId == getRedMushroomBlockId() ? "red_mushroom_block" : "brown_mushroom_block";
        boolean north = false;
        boolean east = false;
        boolean south = false;
        boolean west = false;
        boolean up = false;
        boolean down = false;

        if (metadata >= 1 && metadata <= 9) {
            int row = (metadata - 1) / 3;
            int column = (metadata - 1) % 3;
            north = row == 0;
            south = row == 2;
            west = column == 0;
            east = column == 2;
            up = true;
        } else if (metadata == 10) {
            north = east = south = west = true;
        } else if (metadata == 14 || metadata == 15) {
            north = east = south = west = up = down = true;
        }

        BlockStateKey state = hugeMushroomState(path, north, east, south, west, up, down);
        if (metadata >= 11 && metadata <= 13) {
            state = state.withProperty(PROP_LEGACY_META, Integer.toString(metadata));
        }
        return state;
    }

    private static BlockStateKey hugeMushroomState(String path, boolean north, boolean east,
                                                    boolean south, boolean west, boolean up, boolean down) {
        return new BlockStateKey(new ResourceLocation("minecraft", path))
                .withProperty(PROP_DOWN, boolString(down))
                .withProperty(PROP_EAST, boolString(east))
                .withProperty(PROP_NORTH, boolString(north))
                .withProperty(PROP_SOUTH, boolString(south))
                .withProperty(PROP_UP, boolString(up))
                .withProperty(PROP_WEST, boolString(west));
    }

    private static LegacyBlockData hugeMushroomStateToLegacy(String path, Map<String, String> props) {
        boolean stem = "mushroom_stem".equals(path);
        int blockId = "red_mushroom_block".equals(path) ? getRedMushroomBlockId() : getBrownMushroomBlockId();
        if (!stem && props.containsKey(PROP_LEGACY_META)) {
            return new LegacyBlockData(blockId, clamp(parseInt(props.get(PROP_LEGACY_META), 0), 0, 15), false);
        }

        boolean north = getBoolean(props, PROP_NORTH, true);
        boolean east = getBoolean(props, PROP_EAST, true);
        boolean south = getBoolean(props, PROP_SOUTH, true);
        boolean west = getBoolean(props, PROP_WEST, true);
        boolean up = getBoolean(props, PROP_UP, true);
        boolean down = getBoolean(props, PROP_DOWN, true);
        boolean allFaces = north && east && south && west && up && down;
        boolean noFaces = !north && !east && !south && !west && !up && !down;

        if (stem) {
            int metadata = !up && !down && north && east && south && west ? 10 : 15;
            return new LegacyBlockData(getBrownMushroomBlockId(), metadata, false);
        }
        if (allFaces) {
            return new LegacyBlockData(blockId, 14, false);
        }
        if (noFaces) {
            return new LegacyBlockData(blockId, 0, false);
        }

        int row = north && !south ? 0 : south && !north ? 2 : 1;
        int column = west && !east ? 0 : east && !west ? 2 : 1;
        return new LegacyBlockData(blockId, row * 3 + column + 1, false);
    }

    private static boolean isLegacyId(Integer bridged, int expected) {
        return bridged != null && bridged.intValue() == expected;
    }

    private static int inferMetadataFromProperties(Integer bridged, String path, Map<String, String> props) {
        int legacyId = bridged == null ? -1 : bridged.intValue();

        if (legacyId == getTorchId() || isPath(path, "torch", "wall_torch")) {
            return clamp(torchMetaFromFacing(props.get(PROP_FACING)), 0, 15);
        }

        if (legacyId == getFurnaceId() || legacyId == getLitFurnaceId() || isPath(path, "furnace", "lit_furnace", "furnace_lit")) {
            return clamp(horizontalMetaFromFacing(props.get(PROP_FACING), 3), 0, 15);
        }

        if (legacyId == getDispenserId() || isPath(path, "dispenser")) {
            return clamp(horizontalMetaFromFacing(props.get(PROP_FACING), 3), 0, 15);
        }

        if (legacyId == getChestId() || isPath(path, "chest")) {
            return clamp(horizontalMetaFromFacing(props.get(PROP_FACING), 3), 0, 15);
        }

        if (legacyId == getLadderId() || isPath(path, "ladder")) {
            return clamp(horizontalMetaFromFacing(props.get(PROP_FACING), 2), 0, 15);
        }

        if (legacyId == getWallSignId() || isPath(path, "oak_wall_sign", "wall_sign", "sign_wall")) {
            return clamp(horizontalMetaFromFacing(props.get(PROP_FACING), 2), 0, 15);
        }

        if (legacyId == getStandingSignId() || isPath(path, "oak_sign", "standing_sign", "sign_post")) {
            return clamp(parseInt(props.get(PROP_ROTATION), 0), 0, 15);
        }

        if (legacyId == getButtonId() || isPath(path, "button", "stone_button", "wooden_button")) {
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), buttonMetaFromFacing(props.get(PROP_FACING))), 1, 4);
            return orientation | (getBoolean(props, PROP_POWERED, false) ? 8 : 0);
        }

        if (legacyId == getLeverId() || isPath(path, "lever")) {
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), leverMetaFromProperties(props)), 1, 6);
            return orientation | (getBoolean(props, PROP_POWERED, false) ? 8 : 0);
        }

        if (legacyId == getStonePressurePlateId() || legacyId == getWoodPressurePlateId() || isPath(path, "pressure_plate", "stone_pressure_plate", "oak_pressure_plate", "wooden_pressure_plate")) {
            return getBoolean(props, PROP_POWERED, false) ? 1 : 0;
        }

        if (legacyId == getPumpkinId() || legacyId == getPumpkinPlainId() || legacyId == getCarvedPumpkinId()
                || legacyId == getJackOLanternId()
                || isPath(path, "pumpkin", "pumpkin_state", "carved_pumpkin", "lit_pumpkin", "jack_o_lantern")) {
            int fallback = pumpkinMetaFromFacing(props.get(PROP_FACING));
            return clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3);
        }

        if (legacyId == getTrapdoorId() || isPath(path, "oak_trapdoor", "trapdoor", "wooden_trapdoor", "iron_trapdoor")) {
            int fallback = trapdoorMetaFromFacing(props.get(PROP_FACING));
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3);
            return orientation | (getBoolean(props, PROP_OPEN, false) ? 4 : 0);
        }

        if (legacyId == getFenceGateId() || isPath(path, "oak_fence_gate", "fence_gate", "fencegate")) {
            int fallback = fenceGateMetaFromFacing(props.get(PROP_FACING));
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3);
            return orientation | (getBoolean(props, PROP_OPEN, false) ? 4 : 0);
        }

        if (isStairsId(legacyId)) {
            int fallback = stairsMetaFromFacing(props.get(PROP_FACING));
            return clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3);
        }

        if (legacyId == getSlabId() || legacyId == getDoubleSlabId()) {
            return clamp(slabMetaFromVariant(props.get(PROP_VARIANT)), 0, 5);
        }

        if (legacyId == getRailId() || legacyId == getPoweredRailId() || legacyId == getDetectorRailId() || isPath(path, "rail", "powered_rail", "golden_rail", "detector_rail")) {
            int fallback = railMetaFromShape(props.get(PROP_SHAPE), legacyId == getRailId());
            if (legacyId == getPoweredRailId() || legacyId == getDetectorRailId()) {
                return (fallback & 7) | (getBoolean(props, PROP_POWERED, false) ? 8 : 0);
            }
            return fallback & 15;
        }

        if (legacyId == getWoodDoorId() || legacyId == getIronDoorId() || isPath(path, "oak_door", "wooden_door", "wood_door", "iron_door")) {
            int rotation = clamp(parseInt(props.get(PROP_ROTATION), doorRotationFromFacing(props.get(PROP_FACING))), 0, 3);
            int meta = rotation;
            if (getBoolean(props, PROP_OPEN, false)) {
                meta |= 4;
            }
            if ("upper".equalsIgnoreCase(props.get(PROP_HALF))) {
                meta |= 8;
            }
            return meta;
        }

        if (legacyId == getBedId() || isPath(path, "bed")) {
            int rotation = clamp(parseInt(props.get(PROP_ROTATION), bedRotationFromFacing(props.get(PROP_FACING))), 0, 3);
            int meta = rotation;
            if (getBoolean(props, PROP_OCCUPIED, false)) {
                meta |= 4;
            }
            if ("foot".equalsIgnoreCase(props.get(PROP_PART))) {
                meta |= 8;
            }
            return meta;
        }

        return 0;
    }

    private static boolean isPath(String path, String... candidates) {
        for (int i = 0; i < candidates.length; i++) {
            if (candidates[i].equals(path)) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.length() == 0) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static LegacyBlockData toRegisteredLegacy(int blockId, String path, Map<String, String> props) {
        int legacyMeta = clamp(parseInt(props.get(PROP_LEGACY_META), 0), 0, 15);
        Block block = blockId >= 0 && blockId < Block.byId.length ? Block.byId[blockId] : null;
        if (block instanceof BlockPiston || isPath(path, "piston", "sticky_piston")) {
            int facing = props.get(PROP_FACING) == null ? (legacyMeta & 7) : pistonMetaFromFacing(props.get(PROP_FACING));
            boolean extended = getBoolean(props, PROP_EXTENDED, (legacyMeta & 8) != 0);
            return new LegacyBlockData(blockId, (facing & 7) | (extended ? 8 : 0), false);
        }
        return new LegacyBlockData(blockId, legacyMeta, false);
    }

    private static boolean getBoolean(Map<String, String> props, String name, boolean fallback) {
        String raw = props.get(name);
        if (raw == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    private static String boolString(boolean value) {
        return value ? "true" : "false";
    }

    private static String torchFacingFromMeta(int meta) {
        switch (meta & 7) {
            case 1: return "west";
            case 2: return "east";
            case 3: return "north";
            case 4: return "south";
            case 5: return "up";
            default: return "up";
        }
    }

    private static int torchMetaFromFacing(String facing) {
        if ("west".equals(facing)) return 1;
        if ("east".equals(facing)) return 2;
        if ("north".equals(facing)) return 3;
        if ("south".equals(facing)) return 4;
        if ("up".equals(facing)) return 5;
        return 5;
    }

    private static String repeaterFacingFromMeta(int meta) {
        switch (meta & 3) {
            case 0: return "south";
            case 1: return "west";
            case 2: return "north";
            case 3: return "east";
            default: return "south";
        }
    }

    private static int repeaterMetaFromFacing(String facing) {
        if ("south".equals(facing)) return 0;
        if ("west".equals(facing)) return 1;
        if ("north".equals(facing)) return 2;
        if ("east".equals(facing)) return 3;
        return 0;
    }

    private static int regionCoreV1RepeaterMetaFromFacing(String facing) {
        if ("north".equals(facing)) return 0;
        if ("east".equals(facing)) return 1;
        if ("south".equals(facing)) return 2;
        if ("west".equals(facing)) return 3;
        return 0;
    }

    private static String pistonFacingFromMeta(int meta) {
        switch (meta & 7) {
            case 0: return "down";
            case 1: return "up";
            case 2: return "north";
            case 3: return "south";
            case 4: return "west";
            case 5: return "east";
            default: return "north";
        }
    }

    private static int pistonMetaFromFacing(String facing) {
        if ("down".equals(facing)) return 0;
        if ("up".equals(facing)) return 1;
        if ("north".equals(facing)) return 2;
        if ("south".equals(facing)) return 3;
        if ("west".equals(facing)) return 4;
        if ("east".equals(facing)) return 5;
        return 2;
    }

    private static String horizontalFacingFromMeta(int meta) {
        switch (meta & 7) {
            case 2: return "north";
            case 3: return "south";
            case 4: return "west";
            case 5: return "east";
            default: return "north";
        }
    }

    private static int horizontalMetaFromFacing(String facing, int fallback) {
        if ("north".equals(facing)) return 2;
        if ("south".equals(facing)) return 3;
        if ("west".equals(facing)) return 4;
        if ("east".equals(facing)) return 5;
        return fallback;
    }

    private static String pumpkinFacingFromMeta(int meta) {
        switch (meta & 3) {
            case 0: return "north";
            case 1: return "east";
            case 2: return "south";
            case 3: return "west";
            default: return "north";
        }
    }

    private static int pumpkinMetaFromFacing(String facing) {
        if ("north".equals(facing)) return 0;
        if ("east".equals(facing)) return 1;
        if ("south".equals(facing)) return 2;
        if ("west".equals(facing)) return 3;
        return 0;
    }

    private static String trapdoorFacingFromMeta(int meta) {
        switch (meta & 3) {
            case 0: return "north";
            case 1: return "south";
            case 2: return "west";
            case 3: return "east";
            default: return "north";
        }
    }

    private static int trapdoorMetaFromFacing(String facing) {
        if ("north".equals(facing)) return 0;
        if ("south".equals(facing)) return 1;
        if ("west".equals(facing)) return 2;
        if ("east".equals(facing)) return 3;
        return 0;
    }

    private static String fenceGateFacingFromMeta(int meta) {
        switch (meta & 3) {
            case 0: return "south";
            case 1: return "west";
            case 2: return "north";
            case 3: return "east";
            default: return "south";
        }
    }

    private static int fenceGateMetaFromFacing(String facing) {
        if ("south".equals(facing)) return 0;
        if ("west".equals(facing)) return 1;
        if ("north".equals(facing)) return 2;
        if ("east".equals(facing)) return 3;
        return 0;
    }

    private static String stairsFacingFromMeta(int meta) {
        switch (meta & 3) {
            case 0: return "east";
            case 1: return "west";
            case 2: return "south";
            case 3: return "north";
            default: return "east";
        }
    }

    private static int stairsMetaFromFacing(String facing) {
        if ("east".equals(facing)) return 0;
        if ("west".equals(facing)) return 1;
        if ("south".equals(facing)) return 2;
        if ("north".equals(facing)) return 3;
        return 2;
    }

    private static String slabVariantFromMeta(int meta) {
        switch (meta & 15) {
            case 1: return "sand";
            case 2: return "wood";
            case 3: return "cobble";
            case 4: return "brick";
            case 5: return "smooth_stone_brick";
            default: return "stone";
        }
    }

    private static int slabMetaFromVariant(String variant) {
        if (variant == null) return 0;
        if ("1".equals(variant) || "sand".equalsIgnoreCase(variant) || "sandstone".equalsIgnoreCase(variant)) return 1;
        if ("2".equals(variant) || "wood".equalsIgnoreCase(variant) || "wooden".equalsIgnoreCase(variant) || "planks".equalsIgnoreCase(variant) || "oak".equalsIgnoreCase(variant)) return 2;
        if ("3".equals(variant) || "cobble".equalsIgnoreCase(variant) || "cobblestone".equalsIgnoreCase(variant)) return 3;
        if ("4".equals(variant) || "brick".equalsIgnoreCase(variant) || "bricks".equalsIgnoreCase(variant)) return 4;
        if ("5".equals(variant) || "smoothstonebrick".equalsIgnoreCase(variant) || "smooth_stone_brick".equalsIgnoreCase(variant) || "stone_brick".equalsIgnoreCase(variant)) return 5;
        return 0;
    }

    private static ResourceLocation canonicalSlabKeyFromMeta(int meta) {
        String path;
        switch (meta & 15) {
            case 0: path = "stone_slab"; break;
            case 1: path = "sandstone_slab"; break;
            case 2: path = "wooden_slab"; break;
            case 3: path = "cobblestone_slab"; break;
            case 4: path = "brick_slab"; break;
            case 5: path = "stone_brick_slab"; break;
            default: return null;
        }
        return new ResourceLocation("minecraft", path);
    }

    private static int slabMetaFromPath(String path, String variant) {
        if (variant != null) {
            return slabMetaFromVariant(variant);
        }
        if (isPath(path, "wooden_slab", "wood_slab", "oak_slab")) return 2;
        if (isPath(path, "sandstone_slab")) return 1;
        if (isPath(path, "cobblestone_slab")) return 3;
        if (isPath(path, "brick_slab")) return 4;
        if (isPath(path, "stone_brick_slab", "smooth_stone_brick_slab")) return 5;
        return 0;
    }

    private static boolean isSlabStatePath(String path, Integer bridged) {
        if (isLegacyId(bridged, getSlabId()) || isLegacyId(bridged, getDoubleSlabId())) {
            return true;
        }
        Integer collisionId = parseRegistryCollisionId(path);
        if (isLegacyId(collisionId, getSlabId()) || isLegacyId(collisionId, getDoubleSlabId())) {
            return true;
        }
        return isPath(path,
                "stone_slab", "double_stone_slab",
                "wooden_slab", "wood_slab", "oak_slab",
                "sandstone_slab", "cobblestone_slab", "brick_slab",
                "stone_brick_slab", "smooth_stone_brick_slab");
    }

    private static String railShapeFromMeta(int meta, boolean allowCorners) {
        switch (meta & 15) {
            case 0: return "north_south";
            case 1: return "east_west";
            case 2: return "ascending_east";
            case 3: return "ascending_west";
            case 4: return "ascending_north";
            case 5: return "ascending_south";
            case 6: return allowCorners ? "south_east" : "north_south";
            case 7: return allowCorners ? "south_west" : "north_south";
            case 8: return allowCorners ? "north_west" : "east_west";
            case 9: return allowCorners ? "north_east" : "east_west";
            default: return "north_south";
        }
    }

    private static int railMetaFromShape(String shape, boolean allowCorners) {
        if ("east_west".equals(shape)) return 1;
        if ("ascending_east".equals(shape)) return 2;
        if ("ascending_west".equals(shape)) return 3;
        if ("ascending_north".equals(shape)) return 4;
        if ("ascending_south".equals(shape)) return 5;
        if (allowCorners) {
            if ("south_east".equals(shape)) return 6;
            if ("south_west".equals(shape)) return 7;
            if ("north_west".equals(shape)) return 8;
            if ("north_east".equals(shape)) return 9;
        }
        return 0;
    }

    private static int buttonMetaFromFacing(String facing) {
        if ("east".equals(facing)) return 1;
        if ("west".equals(facing)) return 2;
        if ("south".equals(facing)) return 3;
        if ("north".equals(facing)) return 4;
        return 1;
    }

    private static String buttonFacingFromMeta(int meta) {
        switch (meta & 7) {
            case 1: return "east";
            case 2: return "west";
            case 3: return "south";
            case 4: return "north";
            default: return "east";
        }
    }

    private static String redstoneWallTorchFacingFromMeta(int meta) {
        return buttonFacingFromMeta(meta);
    }

    private static int redstoneWallTorchMetaFromFacing(String facing) {
        return buttonMetaFromFacing(facing);
    }

    private static String leverMountFromMeta(int meta) {
        int orientation = meta & 7;
        if (orientation == 5 || orientation == 6) {
            return "floor";
        }
        return "wall";
    }

    private static String leverFacingFromMeta(int meta) {
        switch (meta & 7) {
            case 1: return "west";
            case 2: return "east";
            case 3: return "north";
            case 4: return "south";
            case 5: return "north";
            case 6: return "east";
            default: return "north";
        }
    }

    private static int leverMetaFromProperties(Map<String, String> props) {
        String mount = props.get(PROP_MOUNT);
        String facing = props.get(PROP_FACING);
        if ("floor".equalsIgnoreCase(mount) || "ceiling".equalsIgnoreCase(mount)) {
            return ("east".equals(facing) || "west".equals(facing)) ? 6 : 5;
        }
        return legacyWallMountMetaFromFacing(facing);
    }

    private static int legacyWallMountMetaFromFacing(String facing) {
        if ("west".equals(facing)) return 1;
        if ("east".equals(facing)) return 2;
        if ("north".equals(facing)) return 3;
        if ("south".equals(facing)) return 4;
        return 1;
    }

    private static int doorRotationFromFacing(String facing) {
        if ("east".equals(facing)) return 0;
        if ("south".equals(facing)) return 1;
        if ("west".equals(facing)) return 2;
        if ("north".equals(facing)) return 3;
        return 0;
    }

    private static String doorFacingFromRotation(int rotation) {
        switch (rotation & 3) {
            case 0: return "east";
            case 1: return "south";
            case 2: return "west";
            case 3: return "north";
            default: return "east";
        }
    }

    private static int bedRotationFromFacing(String facing) {
        if ("south".equals(facing)) return 0;
        if ("west".equals(facing)) return 1;
        if ("north".equals(facing)) return 2;
        if ("east".equals(facing)) return 3;
        return 0;
    }

    private static String wallClockFacingFromMeta(int meta) {
        switch (meta) {
            case 2: return "north";
            case 3: return "south";
            case 4: return "west";
            case 5: return "east";
            default: return "north";
        }
    }

    private static int wallClockMetaFromFacing(String facing) {
        if ("north".equals(facing)) return 2;
        if ("south".equals(facing)) return 3;
        if ("west".equals(facing)) return 4;
        if ("east".equals(facing)) return 5;
        return 2;
    }

    private static boolean isRedstoneTorchId(int blockId) {
        return blockId == getRedstoneTorchOffId() || blockId == getRedstoneTorchOnId();
    }

    private static boolean isTorchId(int blockId) {
        return blockId == getTorchId();
    }

    private static boolean isRepeaterId(int blockId) {
        return blockId == getRepeaterOffId() || blockId == getRepeaterOnId();
    }

    private static boolean isPistonBaseId(int blockId) {
        return blockId == getPistonId() || blockId == getStickyPistonId();
    }

    private static boolean isPistonHeadId(int blockId) {
        return blockId == getPistonHeadId();
    }

    private static boolean isMovingPistonId(int blockId) {
        return blockId == getMovingPistonId();
    }

    private static boolean isFluidId(int blockId) {
        return isWaterId(blockId) || isLavaId(blockId);
    }

    private static boolean isWaterId(int blockId) {
        return blockId == getFlowingWaterId() || blockId == getStillWaterId();
    }

    private static boolean isLavaId(int blockId) {
        return blockId == getFlowingLavaId() || blockId == getStillLavaId();
    }

    private static boolean isRedstoneWireId(int blockId) {
        return blockId == getRedstoneWireId();
    }

    private static boolean isWallClockId(int blockId) {
        return blockId == getWallClockId();
    }

    private static boolean isFurnaceId(int blockId) {
        return blockId == getFurnaceId() || blockId == getLitFurnaceId();
    }

    private static boolean isDispenserId(int blockId) {
        return blockId == getDispenserId();
    }

    private static boolean isChestId(int blockId) {
        return blockId == getChestId();
    }

    private static boolean isLadderId(int blockId) {
        return blockId == getLadderId();
    }

    private static boolean isWallSignId(int blockId) {
        return blockId == getWallSignId();
    }

    private static boolean isStandingSignId(int blockId) {
        return blockId == getStandingSignId();
    }

    private static boolean isButtonId(int blockId) {
        return blockId == getButtonId();
    }

    private static boolean isLeverId(int blockId) {
        return blockId == getLeverId();
    }

    private static boolean isPressurePlateId(int blockId) {
        return blockId == getStonePressurePlateId() || blockId == getWoodPressurePlateId();
    }

	private static boolean isStemId(int blockId) {
		return blockId == getPumpkinStemId() || blockId == getMelonStemId();
	}

    private static boolean isHugeMushroomBlockId(int blockId) {
        return blockId == getBrownMushroomBlockId() || blockId == getRedMushroomBlockId();
    }

    private static boolean isPumpkinId(int blockId) {
        return blockId == getPumpkinId() || blockId == getPumpkinPlainId()
                || blockId == getCarvedPumpkinId() || blockId == getJackOLanternId();
    }

    private static boolean isTrapdoorId(int blockId) {
        return blockId == getTrapdoorId();
    }

    private static boolean isFenceGateId(int blockId) {
        return blockId == getFenceGateId() || blockId == getFenceGateCompatId();
    }

    private static boolean isRailId(int blockId) {
        return blockId == getRailId() || blockId == getPoweredRailId() || blockId == getDetectorRailId();
    }

    private static boolean isStairsId(int blockId) {
        return blockId == getWoodStairsId() || blockId == getCobbleStairsId() || blockId == getBrickStairsId() || blockId == getStoneBrickStairsId();
    }

    private static boolean isSlabId(int blockId) {
        return blockId == getSlabId() || blockId == getDoubleSlabId();
    }

    private static boolean isDoorId(int blockId) {
        return blockId == getWoodDoorId() || blockId == getIronDoorId();
    }

    private static boolean isBedId(int blockId) {
        return blockId == getBedId();
    }

    private static boolean isRedstoneOreId(int blockId) {
        return blockId == getRedstoneOreId() || blockId == getLitRedstoneOreId();
    }

    private static int getRedstoneTorchOffId() {
        return 75;
    }

    private static int getTorchId() {
        return 50;
    }

    private static int getRedstoneTorchOnId() {
        return 76;
    }

    private static int getRepeaterOffId() {
        return 93;
    }

    private static int getRepeaterOnId() {
        return 94;
    }

    private static int getPistonId() {
        return 33;
    }

    private static int getStickyPistonId() {
        return 29;
    }

    private static int getPistonHeadId() {
        return 34;
    }

    private static int getMovingPistonId() {
        return 36;
    }

    private static int getFlowingWaterId() {
        return 8;
    }

    private static int getStillWaterId() {
        return 9;
    }

    private static int getFlowingLavaId() {
        return 10;
    }

    private static int getStillLavaId() {
        return 11;
    }

    private static int getRedstoneWireId() {
        return 55;
    }

    private static int getWallClockId() {
        return 101;
    }

    private static int getFurnaceId() {
        return 61;
    }

    private static int getLitFurnaceId() {
        return 62;
    }

    private static int getDispenserId() {
        return 23;
    }

    private static int getChestId() {
        return 54;
    }

    private static int getLadderId() {
        return 65;
    }

    private static int getWallSignId() {
        return 68;
    }

    private static int getStandingSignId() {
        return 63;
    }

    private static int getButtonId() {
        return 77;
    }

    private static int getLeverId() {
        return 69;
    }

    private static int getStonePressurePlateId() {
        return 70;
    }

    private static int getWoodPressurePlateId() {
        return 72;
    }

	private static int getPumpkinStemId() {
		return 104;
	}

	private static int getMelonStemId() {
		return 105;
	}

    private static int getBrownMushroomBlockId() {
        return 99;
    }

    private static int getRedMushroomBlockId() {
        return 100;
    }

    private static int getPumpkinId() {
        return 86;
    }

    private static int getPumpkinPlainId() {
        return 187;
    }

    private static int getCarvedPumpkinId() {
        return 189;
    }

    private static int getJackOLanternId() {
        return 91;
    }

    private static int getTrapdoorId() {
        return 96;
    }

    private static int getFenceGateId() {
        return 188;
    }

    private static int getFenceGateCompatId() {
        return 150;
    }

    private static int getRailId() {
        return 66;
    }

    private static int getPoweredRailId() {
        return 27;
    }

    private static int getDetectorRailId() {
        return 28;
    }

    private static int getSlabId() {
        return 44;
    }

    private static int getDoubleSlabId() {
        return 43;
    }

    private static int getWoodStairsId() {
        return 53;
    }

    private static int getCobbleStairsId() {
        return 67;
    }

    private static int getBrickStairsId() {
        return 108;
    }

    private static int getStoneBrickStairsId() {
        return 109;
    }

    private static int getWoodDoorId() {
        return 64;
    }

    private static int getIronDoorId() {
        return 71;
    }

    private static int getBedId() {
        return 26;
    }

    private static int getRedstoneOreId() {
        return 73;
    }

    private static int getLitRedstoneOreId() {
        return 74;
    }
}
