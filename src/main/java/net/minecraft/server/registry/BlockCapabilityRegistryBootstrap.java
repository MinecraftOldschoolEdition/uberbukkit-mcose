package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.BlockWallClock;
import net.minecraft.server.IBlockAccess;
import net.minecraft.server.World;

/**
 * Bootstrap for block capability defaults.
 */
public final class BlockCapabilityRegistryBootstrap {
    private static boolean initialized = false;

    private BlockCapabilityRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        int count = BlockCapabilityRegistryApi.bootstrapDefaults();
        BlockCapabilityRegistryApi.registerRedstoneBehavior("minecraft:redstone_block", true, 15);
        BlockCapabilityRegistryApi.registerLightEmission(
                "minecraft:redstone_block",
                BlockCapabilityRegistryApi.getLightEmission(Block.REDSTONE_TORCH_ON)
        );
        // Explicitly wire leaves into decay behavior in the capability API.
        BlockCapabilityRegistryApi.registerDecayBehavior("minecraft:leaves", true);
        registerWallClockCapabilities();
        System.out.println("[BlockCapabilityRegistryBootstrap] Registered " + count + " block capabilities");
    }

    private static void registerWallClockCapabilities() {
        BlockCapability.DirectRedstonePowerResolver directResolver = new BlockCapability.DirectRedstonePowerResolver() {
            public int getPower(World world, int x, int y, int z, int side) {
                return BlockWallClock.getPowerLevel(world);
            }
        };

        BlockCapability.IndirectRedstonePowerResolver indirectResolver = new BlockCapability.IndirectRedstonePowerResolver() {
            public int getPower(IBlockAccess blockAccess, int x, int y, int z, int side) {
                return BlockWallClock.getPowerLevel(blockAccess);
            }
        };

        BlockCapabilityRegistryApi.register(
                Block.WALL_CLOCK,
                new BlockCapability(true, 15, directResolver, indirectResolver)
        );
    }
}
