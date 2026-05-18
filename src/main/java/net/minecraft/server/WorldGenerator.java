package net.minecraft.server;

import java.util.Random;

import net.minecraft.server.util.ResourceLocation;
import org.bukkit.BlockChangeDelegate;

public abstract class WorldGenerator {

    public WorldGenerator() {
    }

    public abstract boolean a(World world, Random random, int i, int j, int k);

    public void a(double d0, double d1, double d2) {
    }

    protected final BlockStateKey stateFromBlockId(int blockId) {
        return BlockStateBridge.fromLegacy(blockId, 0);
    }

    protected final BlockStateKey stateFromBlockId(int blockId, int metadata) {
        return BlockStateBridge.fromLegacy(blockId, metadata);
    }

    protected final BlockStateKey stateFromIdentifier(String blockId) {
        return new BlockStateKey(new ResourceLocation(blockId));
    }

    protected final boolean setGeneratedBlock(World world, int x, int y, int z, BlockStateKey state) {
        return world != null && world.setBlockState(x, y, z, state);
    }

    protected final boolean setGeneratedBlockAndData(World world, int x, int y, int z, BlockStateKey state) {
        return world != null && world.setBlockStateAndData(x, y, z, state);
    }

    protected final boolean setGeneratedBlock(World world, int x, int y, int z, int blockId) {
        return setGeneratedBlock(world, x, y, z, stateFromBlockId(blockId));
    }

    protected final boolean setGeneratedBlock(World world, int x, int y, int z, int blockId, int metadata) {
        return setGeneratedBlock(world, x, y, z, stateFromBlockId(blockId, metadata));
    }

    protected final boolean setGeneratedBlock(World world, int x, int y, int z, String blockId) {
        return setGeneratedBlock(world, x, y, z, stateFromIdentifier(blockId));
    }

    protected final boolean setGeneratedBlock(BlockChangeDelegate world, int x, int y, int z, BlockStateKey state) {
        if (world == null) {
            return false;
        }
        if (world instanceof World) {
            return ((World)world).setBlockState(x, y, z, state);
        }
        BlockStateBridge.LegacyBlockData legacy = BlockStateBridge.toLegacy(state);
        return world.setRawTypeIdAndData(x, y, z, legacy.blockId, legacy.metadata);
    }

    protected final boolean setGeneratedBlock(BlockChangeDelegate world, int x, int y, int z, int blockId) {
        return setGeneratedBlock(world, x, y, z, stateFromBlockId(blockId));
    }

    protected final boolean setGeneratedBlock(BlockChangeDelegate world, int x, int y, int z, int blockId, int metadata) {
        return setGeneratedBlock(world, x, y, z, stateFromBlockId(blockId, metadata));
    }

    protected final boolean setGeneratedBlock(BlockChangeDelegate world, int x, int y, int z, String blockId) {
        return setGeneratedBlock(world, x, y, z, stateFromIdentifier(blockId));
    }

    protected final boolean setGeneratedBlockAndData(World world, int x, int y, int z, int blockId) {
        return setGeneratedBlockAndData(world, x, y, z, stateFromBlockId(blockId));
    }

    protected final boolean setGeneratedBlockAndData(World world, int x, int y, int z, int blockId, int metadata) {
        return setGeneratedBlockAndData(world, x, y, z, stateFromBlockId(blockId, metadata));
    }

    protected final boolean setGeneratedBlockAndData(World world, int x, int y, int z, String blockId) {
        return setGeneratedBlockAndData(world, x, y, z, stateFromIdentifier(blockId));
    }
}
