package net.minecraft.server;

import java.util.Random;

import net.minecraft.server.registry.LootTable;
import net.minecraft.server.registry.LootTables;
import net.minecraft.server.registry.StructureType;
import net.minecraft.server.registry.StructureTypes;

public class WorldGenDungeons extends WorldGenerator {

    // Stone brick metadata values
    private static final int STONE_BRICK_META = 0;
    private static final int MOSSY_STONE_BRICK_META = 1;
    private static final int CRACKED_STONE_BRICK_META = 2;

    // Cache structure type and loot tables
    private static StructureType dungeonType = null;
    private static LootTable simpleDungeonLoot = null;
    private static LootTable monsterDungeonLoot = null;

    private static void ensureInitialized() {
        if (dungeonType == null || simpleDungeonLoot == null || monsterDungeonLoot == null) {
            StructureTypes.initialize();
            dungeonType = StructureTypes.get(StructureTypes.DUNGEON);
            simpleDungeonLoot = LootTables.get(LootTables.SIMPLE_DUNGEON_LOOT);
            if (simpleDungeonLoot == null) {
                simpleDungeonLoot = LootTables.get(LootTables.DUNGEON);
            }
            monsterDungeonLoot = LootTables.get(LootTables.MONSTER_DUNGEON_LOOT);
            if (monsterDungeonLoot == null) {
                monsterDungeonLoot = simpleDungeonLoot;
            }
        }
    }

    public WorldGenDungeons() {
    }

    public boolean a(World world, Random random, int i, int j, int k) {
        ensureInitialized();

        // Avoid triggering chunk loads during population: if the target chunk isn't already loaded, skip
        if (!world.isLoaded(i, j, k)) {
            return false;
        }

        byte b0 = 3;
        int l = random.nextInt(2) + 2;
        int i1 = random.nextInt(2) + 2;
        int j1 = 0;

        // 25% chance of a monster dungeon using stone bricks
        boolean useStoneBricks = random.nextInt(4) == 0;
        LootTable lootTable = useStoneBricks ? monsterDungeonLoot : simpleDungeonLoot;

        int k1;
        int l1;
        int i2;

        for (k1 = i - l - 1; k1 <= i + l + 1; ++k1) {
            for (l1 = j - 1; l1 <= j + b0 + 1; ++l1) {
                for (i2 = k - i1 - 1; i2 <= k + i1 + 1; ++i2) {
                    Material material = world.getMaterial(k1, l1, i2);

                    if (l1 == j - 1 && !material.isBuildable()) {
                        return false;
                    }

                    if (l1 == j + b0 + 1 && !material.isBuildable()) {
                        return false;
                    }

                    if ((k1 == i - l - 1 || k1 == i + l + 1 || i2 == k - i1 - 1 || i2 == k + i1 + 1) && l1 == j && world.isEmpty(k1, l1, i2) && world.isEmpty(k1, l1 + 1, i2)) {
                        ++j1;
                    }
                }
            }
        }

        if (j1 >= 1 && j1 <= 5) {
            for (k1 = i - l - 1; k1 <= i + l + 1; ++k1) {
                for (l1 = j + b0; l1 >= j - 1; --l1) {
                    for (i2 = k - i1 - 1; i2 <= k + i1 + 1; ++i2) {
                        if (k1 != i - l - 1 && l1 != j - 1 && i2 != k - i1 - 1 && k1 != i + l + 1 && l1 != j + b0 + 1 && i2 != k + i1 + 1) {
                            world.setBlockStateAndData(k1, l1, i2, "minecraft:air");
                        } else if (l1 >= 0 && !world.getMaterial(k1, l1 - 1, i2).isBuildable()) {
                            world.setBlockStateAndData(k1, l1, i2, "minecraft:air");
                        } else if (world.getMaterial(k1, l1, i2).isBuildable()) {
                            if (useStoneBricks) {
                                // Monster dungeon: use stone bricks, mossy stone bricks, and cracked stone bricks
                                if (l1 == j - 1 && random.nextInt(4) != 0) {
                                    // Floor: mostly mossy stone bricks
                                    setGeneratedBlockAndData(world, k1, l1, i2, Block.STONE_BRICK.id, MOSSY_STONE_BRICK_META);
                                } else {
                                    // Walls/ceiling: mix of stone brick variants
                                    int brickType = random.nextInt(3);
                                    int meta = brickType == 0 ? STONE_BRICK_META : (brickType == 1 ? MOSSY_STONE_BRICK_META : CRACKED_STONE_BRICK_META);
                                    setGeneratedBlockAndData(world, k1, l1, i2, Block.STONE_BRICK.id, meta);
                                }
                            } else {
                                // Regular dungeon: cobblestone and mossy cobblestone
                                if (l1 == j - 1 && random.nextInt(4) != 0) {
                                    world.setBlockStateAndData(k1, l1, i2, "minecraft:mossy_cobblestone");
                                } else {
                                    world.setBlockStateAndData(k1, l1, i2, "minecraft:cobblestone");
                                }
                            }
                        }
                    }
                }
            }

            chestPlacement:
            for (k1 = 0; k1 < 2; ++k1) {
                for (l1 = 0; l1 < 3; ++l1) {
                    i2 = i + random.nextInt(l * 2 + 1) - l;
                    int j2 = k + random.nextInt(i1 * 2 + 1) - i1;

                    if (world.isEmpty(i2, j, j2)) {
                        int k2 = 0;

                        if (world.getMaterial(i2 - 1, j, j2).isBuildable()) {
                            ++k2;
                        }

                        if (world.getMaterial(i2 + 1, j, j2).isBuildable()) {
                            ++k2;
                        }

                        if (world.getMaterial(i2, j, j2 - 1).isBuildable()) {
                            ++k2;
                        }

                        if (world.getMaterial(i2, j, j2 + 1).isBuildable()) {
                            ++k2;
                        }

                        if (k2 == 1) {
                            world.setBlockStateAndData(i2, j, j2, "minecraft:chest");
                            TileEntityChest tileentitychest = (TileEntityChest) world.getTileEntity(i2, j, j2);

                            LootTable chestLoot = lootTable != null ? lootTable : simpleDungeonLoot;
                            if (chestLoot != null) {
                                chestLoot.fillInventory(tileentitychest, random);
                            }

                            continue chestPlacement;
                        }
                    }
                }
            }

            world.setBlockStateAndData(i, j, k, "minecraft:mob_spawner");
            TileEntityMobSpawner tileentitymobspawner = (TileEntityMobSpawner) world.getTileEntity(i, j, k);

            // Use structure type to pick spawner mob
            if (dungeonType != null) {
                tileentitymobspawner.a(dungeonType.pickSpawnerMob(random));
            } else {
                tileentitymobspawner.a(this.b(random));
            }
            return true;
        } else {
            return false;
        }
    }

    private String b(Random random) {
        int i = random.nextInt(4);

        return i == 0 ? "Skeleton" : (i == 1 ? "Zombie" : (i == 2 ? "Zombie" : (i == 3 ? "Spider" : "")));
    }
}
