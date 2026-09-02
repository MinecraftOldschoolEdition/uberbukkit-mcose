package net.minecraft.server.Alpha;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.server.Block;
import net.minecraft.server.BlockStateBridge;
import net.minecraft.server.BlockStateKey;
import net.minecraft.server.World;
import net.minecraft.server.WorldGenerator;
import net.minecraft.server.registry.BlockRegistry;
import net.minecraft.server.util.ResourceLocation;

public class AlphaWorldGenLiquids extends WorldGenerator {
	private int liquidBlockId;
	private BlockStateKey liquidState;
	private boolean requiresBlockBelow;
	private int rockCount;
	private int holeCount;
	private int[] validBlockIds;

	public AlphaWorldGenLiquids(int var1) {
		this(BlockStateBridge.fromLegacy(var1, 0),
				true, 4, 1, legacyStone());
	}

	public AlphaWorldGenLiquids(String blockId) {
		this(new BlockStateKey(new ResourceLocation(blockId)),
				true, 4, 1, legacyStone());
	}

	public AlphaWorldGenLiquids(
			BlockStateKey state,
			boolean requiresBlockBelow,
			int rockCount,
			int holeCount,
			List<ResourceLocation> validBlocks) {
		if (state == null || validBlocks == null || validBlocks.isEmpty()) {
			throw new IllegalArgumentException(
					"Spring state and valid blocks are required");
		}
		this.liquidState = state;
		this.liquidBlockId = BlockStateBridge.toLegacy(state).blockId;
		this.requiresBlockBelow = requiresBlockBelow;
		this.rockCount = rockCount;
		this.holeCount = holeCount;
		this.validBlockIds = resolveValidBlockIds(validBlocks);
	}

	public boolean a(World var1, Random var2, int var3, int var4, int var5) {
		if (!this.isValidBlock(var1.getTypeId(var3, var4 + 1, var5))) {
			return false;
		} else if (this.requiresBlockBelow
				&& !this.isValidBlock(var1.getTypeId(var3, var4 - 1, var5))) {
			return false;
		} else if (var1.getTypeId(var3, var4, var5) != 0
				&& !this.isValidBlock(var1.getTypeId(var3, var4, var5))) {
			return false;
		} else {
			int var6 = 0;
			if (this.isValidBlock(var1.getTypeId(var3 - 1, var4, var5))) {
				++var6;
			}

			if (this.isValidBlock(var1.getTypeId(var3 + 1, var4, var5))) {
				++var6;
			}

			if (this.isValidBlock(var1.getTypeId(var3, var4, var5 - 1))) {
				++var6;
			}

			if (this.isValidBlock(var1.getTypeId(var3, var4, var5 + 1))) {
				++var6;
			}

			if (this.isValidBlock(var1.getTypeId(var3, var4 - 1, var5))) {
				++var6;
			}

			int var7 = 0;
			if (var1.getTypeId(var3 - 1, var4, var5) == 0) {
				++var7;
			}

			if (var1.getTypeId(var3 + 1, var4, var5) == 0) {
				++var7;
			}

			if (var1.getTypeId(var3, var4, var5 - 1) == 0) {
				++var7;
			}

			if (var1.getTypeId(var3, var4, var5 + 1) == 0) {
				++var7;
			}

			if (var1.getTypeId(var3, var4 - 1, var5) == 0) {
				++var7;
			}

			if (var6 == this.rockCount && var7 == this.holeCount) {
				setGeneratedBlockAndData(var1, var3, var4, var5, this.liquidState);
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

	private static List<ResourceLocation> legacyStone() {
		return Collections.singletonList(
				new ResourceLocation("minecraft", "stone"));
	}
}
