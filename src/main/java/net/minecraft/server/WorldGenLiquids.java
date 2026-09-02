package net.minecraft.server;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.server.registry.BlockRegistry;
import net.minecraft.server.util.ResourceLocation;

public class WorldGenLiquids extends WorldGenerator {

    private int a;
    private BlockStateKey state;
    private boolean requiresBlockBelow;
    private int rockCount;
    private int holeCount;
    private int[] validBlockIds;

    public WorldGenLiquids(int i) {
        this(stateFromLegacy(i), true, 4, 1, legacyStone());
    }

    public WorldGenLiquids(String blockId) {
        this(new BlockStateKey(new ResourceLocation(blockId)),
                true, 4, 1, legacyStone());
    }

    public WorldGenLiquids(
            BlockStateKey state,
            boolean requiresBlockBelow,
            int rockCount,
            int holeCount,
            List<ResourceLocation> validBlocks) {
        if (state == null || validBlocks == null || validBlocks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Spring state and valid blocks are required");
        }
        this.state = state;
        this.a = BlockStateBridge.toLegacy(state).blockId;
        this.requiresBlockBelow = requiresBlockBelow;
        this.rockCount = rockCount;
        this.holeCount = holeCount;
        this.validBlockIds = resolveValidBlockIds(validBlocks);
    }

    public boolean a(World world, Random random, int i, int j, int k) {
        if (!this.isValidBlock(world.getTypeId(i, j + 1, k))) {
            return false;
        } else if (this.requiresBlockBelow
                && !this.isValidBlock(world.getTypeId(i, j - 1, k))) {
            return false;
        } else if (world.getTypeId(i, j, k) != 0
                && !this.isValidBlock(world.getTypeId(i, j, k))) {
            return false;
        } else {
            int l = 0;

            if (this.isValidBlock(world.getTypeId(i - 1, j, k))) {
                ++l;
            }

            if (this.isValidBlock(world.getTypeId(i + 1, j, k))) {
                ++l;
            }

            if (this.isValidBlock(world.getTypeId(i, j, k - 1))) {
                ++l;
            }

            if (this.isValidBlock(world.getTypeId(i, j, k + 1))) {
                ++l;
            }

            if (this.isValidBlock(world.getTypeId(i, j - 1, k))) {
                ++l;
            }

            int i1 = 0;

            if (world.isEmpty(i - 1, j, k)) {
                ++i1;
            }

            if (world.isEmpty(i + 1, j, k)) {
                ++i1;
            }

            if (world.isEmpty(i, j, k - 1)) {
                ++i1;
            }

            if (world.isEmpty(i, j, k + 1)) {
                ++i1;
            }

            if (world.isEmpty(i, j - 1, k)) {
                ++i1;
            }

            if (l == this.rockCount && i1 == this.holeCount) {
                setGeneratedBlockAndData(world, i, j, k, this.state);
                boolean previousImmediateUpdates = world.a;
                world.a = true;
                try {
                    Block.byId[this.a].a(world, i, j, k, random);
                } finally {
                    world.a = previousImmediateUpdates;
                }
            }

            return true;
        }
    }

    private boolean isValidBlock(int blockId) {
        for (int i = 0; i < this.validBlockIds.length; i++) {
            if (this.validBlockIds[i] == blockId) return true;
        }
        return false;
    }

    private static int[] resolveValidBlockIds(List<ResourceLocation> validBlocks) {
        int[] ids = new int[validBlocks.size()];
        for (int i = 0; i < validBlocks.size(); i++) {
            Block block = BlockRegistry.get(validBlocks.get(i));
            if (block == null) {
                throw new IllegalArgumentException(
                        "Unknown spring valid block " + validBlocks.get(i));
            }
            ids[i] = block.id;
        }
        return ids;
    }

    private static BlockStateKey stateFromLegacy(int blockId) {
        return BlockStateBridge.fromLegacy(blockId, 0);
    }

    private static List<ResourceLocation> legacyStone() {
        return Collections.singletonList(
                new ResourceLocation("minecraft", "stone"));
    }
}
