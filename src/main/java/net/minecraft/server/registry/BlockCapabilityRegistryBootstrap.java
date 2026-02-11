package net.minecraft.server.registry;

import net.minecraft.server.Block;

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
        System.out.println("[BlockCapabilityRegistryBootstrap] Registered " + count + " block capabilities");
    }
}
