package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.IBlockAccess;
import net.minecraft.server.World;
import net.minecraft.server.util.ResourceLocation;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * API-facing registry for block interaction capabilities.
 * Exposes redstone semantics, light emission, and decay behavior properties.
 */
public final class BlockCapabilityRegistryApi {
    private static final Map<ResourceLocation, BlockCapability> byKey = new LinkedHashMap<ResourceLocation, BlockCapability>();
    private static final Map<Block, BlockCapability> byBlock = new IdentityHashMap<Block, BlockCapability>();
    private static final int CAKE_MAX_SLICES = 6;
    private static final int CAKE_SLICE_HEAL_AMOUNT = 3;

    private BlockCapabilityRegistryApi() {}

    public static synchronized boolean register(ResourceLocation blockKey, BlockCapability capability) {
        if (blockKey == null || capability == null) {
            return false;
        }

        Block block = BlockRegistry.get(blockKey);
        if (block == null) {
            return false;
        }

        ResourceLocation canonical = BlockRegistry.getKey(block);
        if (canonical == null) {
            canonical = blockKey;
        }

        byKey.put(canonical, capability);
        byBlock.put(block, capability);
        applyBlockProperties(block, capability);
        return true;
    }

    public static boolean register(String blockIdentifier, BlockCapability capability) {
        if (blockIdentifier == null) {
            return false;
        }
        String normalized = BlockRegistry.normalizeInputIdentifier(blockIdentifier);
        if (normalized == null) {
            return false;
        }
        return register(new ResourceLocation(normalized), capability);
    }

    public static synchronized boolean register(Block block, BlockCapability capability) {
        if (block == null || capability == null) {
            return false;
        }

        ResourceLocation key = BlockRegistry.getKey(block);
        if (key == null) {
            return false;
        }

        byKey.put(key, capability);
        byBlock.put(block, capability);
        applyBlockProperties(block, capability);
        return true;
    }

    public static boolean registerRedstoneBehavior(ResourceLocation blockKey, boolean powerSource, int maxPower) {
        if (blockKey == null) {
            return false;
        }
        return registerRedstoneBehavior(BlockRegistry.get(blockKey), powerSource, maxPower);
    }

    public static boolean registerRedstoneBehavior(String blockIdentifier, boolean powerSource, int maxPower) {
        if (blockIdentifier == null) {
            return false;
        }
        String normalized = BlockRegistry.normalizeInputIdentifier(blockIdentifier);
        if (normalized == null) {
            return false;
        }
        return registerRedstoneBehavior(new ResourceLocation(normalized), powerSource, maxPower);
    }

    public static boolean registerRedstoneBehavior(Block block, boolean powerSource, int maxPower) {
        BlockCapability existing = get(block);
        int light = existing != null && existing.hasExplicitLightEmission() ? existing.getLightEmission() : BlockCapability.UNSET_LIGHT_EMISSION;
        BlockCapability.LightEmissionResolver lightResolver = existing == null ? null : existing.getLightEmissionResolver();
        BlockCapability.DirectRedstonePowerResolver directResolver = existing == null ? null : existing.getDirectResolver();
        BlockCapability.IndirectRedstonePowerResolver indirectResolver = existing == null ? null : existing.getIndirectResolver();
        boolean decayEnabled = existing != null && existing.isDecayEnabled();
        return register(block, new BlockCapability(powerSource, maxPower, directResolver, indirectResolver, light, lightResolver, decayEnabled));
    }

    public static boolean registerLightEmission(ResourceLocation blockKey, int lightEmission) {
        if (blockKey == null) {
            return false;
        }
        return registerLightEmission(BlockRegistry.get(blockKey), lightEmission);
    }

    public static boolean registerLightEmission(String blockIdentifier, int lightEmission) {
        if (blockIdentifier == null) {
            return false;
        }
        String normalized = BlockRegistry.normalizeInputIdentifier(blockIdentifier);
        if (normalized == null) {
            return false;
        }
        return registerLightEmission(new ResourceLocation(normalized), lightEmission);
    }

    public static boolean registerLightEmission(Block block, int lightEmission) {
        if (block == null) {
            return false;
        }
        BlockCapability existing = get(block);
        boolean powerSource = existing != null ? existing.isRedstonePowerSource() : block.isPowerSource();
        int maxPower = existing != null ? existing.getMaxRedstonePower() : (block == Block.REDSTONE_WIRE ? 15 : (powerSource ? 15 : 0));
        BlockCapability.DirectRedstonePowerResolver directResolver = existing == null ? null : existing.getDirectResolver();
        BlockCapability.IndirectRedstonePowerResolver indirectResolver = existing == null ? null : existing.getIndirectResolver();
        BlockCapability.LightEmissionResolver lightResolver = existing == null ? null : existing.getLightEmissionResolver();
        boolean decayEnabled = existing != null && existing.isDecayEnabled();
        return register(block, new BlockCapability(powerSource, maxPower, directResolver, indirectResolver, lightEmission, lightResolver, decayEnabled));
    }

    public static boolean registerDecayBehavior(ResourceLocation blockKey, boolean decayEnabled) {
        if (blockKey == null) {
            return false;
        }
        return registerDecayBehavior(BlockRegistry.get(blockKey), decayEnabled);
    }

    public static boolean registerDecayBehavior(String blockIdentifier, boolean decayEnabled) {
        if (blockIdentifier == null) {
            return false;
        }
        String normalized = BlockRegistry.normalizeInputIdentifier(blockIdentifier);
        if (normalized == null) {
            return false;
        }
        return registerDecayBehavior(new ResourceLocation(normalized), decayEnabled);
    }

    public static boolean registerDecayBehavior(Block block, boolean decayEnabled) {
        if (block == null) {
            return false;
        }

        BlockCapability existing = get(block);
        boolean powerSource = existing != null ? existing.isRedstonePowerSource() : block.isPowerSource();
        int maxPower = existing != null ? existing.getMaxRedstonePower() : (block == Block.REDSTONE_WIRE ? 15 : (powerSource ? 15 : 0));
        int light = existing != null && existing.hasExplicitLightEmission() ? existing.getLightEmission() : BlockCapability.UNSET_LIGHT_EMISSION;
        BlockCapability.DirectRedstonePowerResolver directResolver = existing == null ? null : existing.getDirectResolver();
        BlockCapability.IndirectRedstonePowerResolver indirectResolver = existing == null ? null : existing.getIndirectResolver();
        BlockCapability.LightEmissionResolver lightResolver = existing == null ? null : existing.getLightEmissionResolver();
        return register(block, new BlockCapability(powerSource, maxPower, directResolver, indirectResolver, light, lightResolver, decayEnabled));
    }

    public static synchronized BlockCapability get(ResourceLocation blockKey) {
        if (blockKey == null) {
            return null;
        }

        Block block = BlockRegistry.get(blockKey);
        if (block == null) {
            return byKey.get(blockKey);
        }

        BlockCapability capability = byBlock.get(block);
        if (capability != null) {
            return capability;
        }

        ResourceLocation canonical = BlockRegistry.getKey(block);
        if (canonical == null) {
            canonical = blockKey;
        }
        return byKey.get(canonical);
    }

    public static synchronized BlockCapability get(Block block) {
        if (block == null) {
            return null;
        }
        return byBlock.get(block);
    }

    public static synchronized Set<ResourceLocation> keys() {
        return Collections.unmodifiableSet(byKey.keySet());
    }

    public static synchronized int size() {
        return byKey.size();
    }

    public static boolean isRedstonePowerSource(Block block) {
        if (block == null) {
            return false;
        }

        BlockCapability capability = get(block);
        if (capability != null) {
            return capability.isRedstonePowerSource();
        }

        return block.isPowerSource();
    }

    public static int getMaxRedstonePower(Block block) {
        if (block == null) {
            return 0;
        }

        BlockCapability capability = get(block);
        if (capability != null) {
            return BlockCapability.clampPower(capability.getMaxRedstonePower());
        }

        if (block == Block.REDSTONE_WIRE) {
            return 15;
        }

        return block.isPowerSource() ? 15 : 0;
    }

    public static int getDirectRedstonePower(World world, int x, int y, int z, int side) {
        if (world == null) {
            return 0;
        }

        int blockId = world.getTypeId(x, y, z);
        if (blockId <= 0 || blockId >= Block.byId.length) {
            return 0;
        }

        Block block = Block.byId[blockId];
        if (block == null) {
            return 0;
        }

        BlockCapability capability = get(block);
        if (capability != null) {
            BlockCapability.DirectRedstonePowerResolver resolver = capability.getDirectResolver();
            if (resolver != null) {
                return BlockCapability.clampPower(resolver.getPower(world, x, y, z, side));
            }
            if (!capability.isRedstonePowerSource()) {
                return 0;
            }

            int vanilla = 0;
            if (block == Block.REDSTONE_WIRE) {
                vanilla = BlockCapability.clampPower(world.getData(x, y, z));
            } else if (block.d(world, x, y, z, side)) {
                vanilla = 15;
            }

            return BlockCapability.clampPower(Math.min(vanilla, capability.getMaxRedstonePower()));
        }

        if (block == Block.REDSTONE_WIRE) {
            return BlockCapability.clampPower(world.getData(x, y, z));
        }

        return block.d(world, x, y, z, side) ? 15 : 0;
    }

    public static int getIndirectRedstonePower(IBlockAccess blockAccess, int x, int y, int z, int side) {
        if (blockAccess == null) {
            return 0;
        }

        int blockId = blockAccess.getTypeId(x, y, z);
        if (blockId <= 0 || blockId >= Block.byId.length) {
            return 0;
        }

        Block block = Block.byId[blockId];
        if (block == null) {
            return 0;
        }

        BlockCapability capability = get(block);
        if (capability != null) {
            BlockCapability.IndirectRedstonePowerResolver resolver = capability.getIndirectResolver();
            if (resolver != null) {
                return BlockCapability.clampPower(resolver.getPower(blockAccess, x, y, z, side));
            }
            if (!capability.isRedstonePowerSource()) {
                return 0;
            }

            int vanilla = 0;
            if (block == Block.REDSTONE_WIRE) {
                vanilla = BlockCapability.clampPower(blockAccess.getData(x, y, z));
            } else if (block.a(blockAccess, x, y, z, side)) {
                vanilla = 15;
            }

            return BlockCapability.clampPower(Math.min(vanilla, capability.getMaxRedstonePower()));
        }

        if (!block.a(blockAccess, x, y, z, side)) {
            return 0;
        }

        if (block == Block.REDSTONE_WIRE) {
            return BlockCapability.clampPower(blockAccess.getData(x, y, z));
        }

        return 15;
    }

    public static int getLightEmission(Block block) {
        if (block == null || block.id < 0 || block.id >= Block.s.length) {
            return 0;
        }

        BlockCapability capability = get(block);
        if (capability != null && capability.hasExplicitLightEmission()) {
            return BlockCapability.clampLight(capability.getLightEmission());
        }

        return BlockCapability.clampLight(Block.s[block.id]);
    }

    public static int getLightEmission(ResourceLocation blockKey) {
        if (blockKey == null) {
            return 0;
        }
        return getLightEmission(BlockRegistry.get(blockKey));
    }

    public static int getLightEmission(String blockIdentifier) {
        if (blockIdentifier == null) {
            return 0;
        }
        String normalized = BlockRegistry.normalizeInputIdentifier(blockIdentifier);
        if (normalized == null) {
            return 0;
        }
        return getLightEmission(new ResourceLocation(normalized));
    }

    public static int getLightEmission(IBlockAccess blockAccess, int x, int y, int z) {
        if (blockAccess == null) {
            return 0;
        }

        int blockId = blockAccess.getTypeId(x, y, z);
        if (blockId <= 0 || blockId >= Block.byId.length) {
            return 0;
        }

        Block block = Block.byId[blockId];
        if (block == null) {
            return 0;
        }

        BlockCapability capability = get(block);
        if (capability != null) {
            BlockCapability.LightEmissionResolver resolver = capability.getLightEmissionResolver();
            if (resolver != null) {
                return BlockCapability.clampLight(resolver.getLightEmission(blockAccess, x, y, z));
            }
            if (capability.hasExplicitLightEmission()) {
                return BlockCapability.clampLight(capability.getLightEmission());
            }
        }

        return BlockCapability.clampLight(Block.s[block.id]);
    }

    public static boolean isDecayEnabled(Block block) {
        if (block == null) {
            return false;
        }

        BlockCapability capability = get(block);
        if (capability != null) {
            return capability.isDecayEnabled();
        }

        return block == Block.LEAVES;
    }

    public static int getCakeMaxSlices() {
        return CAKE_MAX_SLICES;
    }

    public static int getCakeSliceHealAmount() {
        return CAKE_SLICE_HEAL_AMOUNT;
    }

    public static int clampCakeSlicesEaten(int metadata) {
        if (metadata < 0) {
            return 0;
        }
        return metadata > CAKE_MAX_SLICES ? CAKE_MAX_SLICES : metadata;
    }

    public static int getNextCakeSliceMetadata(int metadata) {
        return clampCakeSlicesEaten(metadata) + 1;
    }

    static synchronized int bootstrapDefaults() {
        int registered = 0;
        for (int i = 0; i < Block.byId.length; i++) {
            Block block = Block.byId[i];
            if (block == null) {
                continue;
            }

            boolean source = block.isPowerSource();
            if (block == Block.DIODE_OFF || block == Block.DIODE_ON) {
                // Vanilla repeaters output directional power despite isPowerSource() returning false.
                source = true;
            }
            int maxPower = block == Block.REDSTONE_WIRE ? 15 : (source ? 15 : 0);
            int light = BlockCapability.clampLight(Block.s[i]);
            boolean decayEnabled = block == Block.LEAVES;
            if (register(block, new BlockCapability(source, maxPower, null, null, light, null, decayEnabled))) {
                registered++;
            }
        }
        return registered;
    }

    private static void applyBlockProperties(Block block, BlockCapability capability) {
        if (block == null || capability == null) {
            return;
        }
        if (!capability.hasExplicitLightEmission()) {
            return;
        }
        if (block.id < 0 || block.id >= Block.s.length) {
            return;
        }
        Block.s[block.id] = BlockCapability.clampLight(capability.getLightEmission());
    }
}
