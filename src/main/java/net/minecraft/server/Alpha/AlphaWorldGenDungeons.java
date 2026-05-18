package net.minecraft.server.Alpha;

import java.util.Random;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.Material;
import net.minecraft.server.TileEntityChest;
import net.minecraft.server.TileEntityMobSpawner;
import net.minecraft.server.World;
import net.minecraft.server.WorldGenerator;

public class AlphaWorldGenDungeons extends WorldGenerator {
	public boolean a(World var1, Random var2, int var3, int var4, int var5) {
		byte var6 = 3;
		int var7 = var2.nextInt(2) + 2;
		int var8 = var2.nextInt(2) + 2;
		int var9 = 0;

		int var10;
		int var11;
		int var12;
		for (var10 = var3 - var7 - 1; var10 <= var3 + var7 + 1; ++var10) {
			for (var11 = var4 - 1; var11 <= var4 + var6 + 1; ++var11) {
				for (var12 = var5 - var8 - 1; var12 <= var5 + var8 + 1; ++var12) {
					Material var13 = var1.getMaterial(var10, var11, var12);
					if (var11 == var4 - 1 && !var13.isSolid()) {
						return false;
					}

					if (var11 == var4 + var6 + 1 && !var13.isSolid()) {
						return false;
					}

					if ((var10 == var3 - var7 - 1 || var10 == var3 + var7 + 1 || var12 == var5 - var8 - 1 || var12 == var5 + var8 + 1) && var11 == var4 && var1.getTypeId(var10, var11, var12) == 0 && var1.getTypeId(var10, var11 + 1, var12) == 0) {
						++var9;
					}
				}
			}
		}

		if (var9 >= 1 && var9 <= 5) {
			for (var10 = var3 - var7 - 1; var10 <= var3 + var7 + 1; ++var10) {
				for (var11 = var4 + var6; var11 >= var4 - 1; --var11) {
					for (var12 = var5 - var8 - 1; var12 <= var5 + var8 + 1; ++var12) {
						if (var10 != var3 - var7 - 1 && var11 != var4 - 1 && var12 != var5 - var8 - 1 && var10 != var3 + var7 + 1 && var11 != var4 + var6 + 1 && var12 != var5 + var8 + 1) {
							setGeneratedBlockAndData(var1, var10, var11, var12, 0);
						} else if (var11 >= 0 && !var1.getMaterial(var10, var11 - 1, var12).isSolid()) {
							setGeneratedBlockAndData(var1, var10, var11, var12, 0);
						} else if (var1.getMaterial(var10, var11, var12).isSolid()) {
							if (var11 == var4 - 1 && var2.nextInt(4) != 0) {
								setGeneratedBlockAndData(var1, var10, var11, var12, Block.MOSSY_COBBLESTONE.id);
							} else {
								setGeneratedBlockAndData(var1, var10, var11, var12, Block.COBBLESTONE.id);
							}
						}
					}
				}
			}

			label110:
			for (var10 = 0; var10 < 2; ++var10) {
				for (var11 = 0; var11 < 3; ++var11) {
					var12 = var3 + var2.nextInt(var7 * 2 + 1) - var7;
					int var14 = var5 + var2.nextInt(var8 * 2 + 1) - var8;
					if (var1.getTypeId(var12, var4, var14) == 0) {
						int var15 = 0;
						if (var1.getMaterial(var12 - 1, var4, var14).isSolid()) {
							++var15;
						}

						if (var1.getMaterial(var12 + 1, var4, var14).isSolid()) {
							++var15;
						}

						if (var1.getMaterial(var12, var4, var14 - 1).isSolid()) {
							++var15;
						}

						if (var1.getMaterial(var12, var4, var14 + 1).isSolid()) {
							++var15;
						}

						if (var15 == 1) {
							setGeneratedBlockAndData(var1, var12, var4, var14, Block.CHEST.id);
							TileEntityChest var16 = (TileEntityChest)var1.getTileEntity(var12, var4, var14);
							int var17 = 0;

							while (true) {
								if (var17 >= 8) {
									continue label110;
								}

								ItemStack var18 = this.pickCheckLootItem(var2);
								if (var18 != null) {
									var16.setItem(var2.nextInt(var16.getSize()), var18);
								}

								++var17;
							}
						}
					}
				}
			}

			setGeneratedBlockAndData(var1, var3, var4, var5, Block.MOB_SPAWNER.id);
			TileEntityMobSpawner var19 = (TileEntityMobSpawner)var1.getTileEntity(var3, var4, var5);
			var19.a(this.pickMobSpawner(var2));
			return true;
		} else {
			return false;
		}
	}

	private ItemStack pickCheckLootItem(Random var1) {
		int var2 = var1.nextInt(12);
		if(var2 == 0) {
			return new ItemStack(Item.SADDLE);
		} else if(var2 == 1) {
			return new ItemStack(Item.IRON_INGOT, var1.nextInt(4) + 1);
		} else if(var2 == 2) {
			return new ItemStack(Item.BREAD);
		} else if(var2 == 3) {
			return new ItemStack(Item.WHEAT, var1.nextInt(4) + 1);
		} else if(var2 == 4) {
			return new ItemStack(Item.SULPHUR, var1.nextInt(4) + 1);
		} else if(var2 == 5) {
			return new ItemStack(Item.STRING, var1.nextInt(4) + 1);
		} else if(var2 == 6) {
			return new ItemStack(Item.BUCKET);
		} else if(var2 == 7 && var1.nextInt(100) == 0) {
			return new ItemStack(Item.GOLDEN_APPLE);
		} else if(var2 == 8 && var1.nextInt(2) == 0) {
			return new ItemStack(Item.REDSTONE, var1.nextInt(4) + 1);
		} else if(var2 == 9 && var1.nextInt(10) == 0) {
			return new ItemStack(Item.byId[Item.GOLD_RECORD.id + var1.nextInt(2)]);
		} else if(var2 == 10) {
			return new ItemStack(Item.NAME_TAG);
		} else {
			return null;
		}
	}

	private String pickMobSpawner(Random var1) {
		int var2 = var1.nextInt(4);
		return var2 == 0 ? "Skeleton" : (var2 == 1 ? "Zombie" : (var2 == 2 ? "Zombie" : (var2 == 3 ? "Spider" : "")));
	}
}
