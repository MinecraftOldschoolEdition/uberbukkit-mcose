package net.minecraft.server;

import net.minecraft.server.registry.BlockLootTable;
import net.minecraft.server.registry.LootTables;
import net.minecraft.server.util.ResourceLocation;

/** Package-owned bridge from the legacy Block hook to data-driven block loot. */
final class BlockDropEngine {
    private BlockDropEngine() {}

    static void drop(
            final Block block,
            final World world,
            final int x,
            final int y,
            final int z,
            final int rawMetadata,
            final float rawChance) {
        if (world.isStatic) {
            return;
        }

        BlockLootTable table = LootTables.getBlockLootTable(block);
        if (table == null) {
            if (LootTables.isBuiltInBlock(block)) {
                ResourceLocation key = LootTables.getBuiltInBlockKey(block);
                throw new IllegalStateException(
                        "Missing published block loot table for built-in " + key);
            }
            dropLegacyVirtual(block, world, x, y, z, rawMetadata, rawChance);
            return;
        }

        BlockLootTable.Adapter adapter = table.getAdapter();
        if (adapter != null) {
            if (adapter == BlockLootTable.Adapter.MOVING_PISTON) {
                dropMovingPiston(world, x, y, z);
                return;
            }
            throw new IllegalStateException(
                    "Unsupported block loot adapter " + adapter);
        }

        table.generate(
                rawMetadata,
                rawChance,
                world.random,
                new BlockLootTable.DropSink() {
                    public void emit(
                            ItemStack stack,
                            BlockLootTable.SpawnMode mode) {
                        if (stack == null || mode == null) {
                            throw new IllegalStateException(
                                    "Block loot emitted a null stack or spawn mode");
                        }
                        if (mode == BlockLootTable.SpawnMode.BLOCK_DEFAULT) {
                            block.a(world, x, y, z, stack);
                            return;
                        }
                        if (mode == BlockLootTable.SpawnMode.LEGACY_PLANT) {
                            spawnLegacyPlant(world, x, y, z, stack);
                            return;
                        }
                        throw new IllegalStateException(
                                "Unsupported block loot spawn mode " + mode);
                    }
                });
    }

    private static void dropLegacyVirtual(
            Block block,
            World world,
            int x,
            int y,
            int z,
            int rawMetadata,
            float rawChance) {
        int count = block.a(world.random);
        for (int i = 0; i < count; ++i) {
            // Preserve Bukkit's zero-chance plugin contract for unbound
            // plugin/mod blocks. Canonical vanilla drops use the client table.
            if (world.random.nextFloat() < rawChance) {
                int itemId = block.a(rawMetadata, world.random);
                if (itemId > 0) {
                    block.a(
                            world,
                            x,
                            y,
                            z,
                            new ItemStack(
                                    itemId,
                                    1,
                                    block.a_(rawMetadata)));
                }
            }
        }
    }

    private static void spawnLegacyPlant(
            World world,
            int x,
            int y,
            int z,
            ItemStack stack) {
        float spread = 0.7F;
        float offsetX = world.random.nextFloat() * spread
                + (1.0F - spread) * 0.5F;
        float offsetY = world.random.nextFloat() * spread
                + (1.0F - spread) * 0.5F;
        float offsetZ = world.random.nextFloat() * spread
                + (1.0F - spread) * 0.5F;
        EntityItem entityItem = new EntityItem(
                world,
                (double)((float)x + offsetX),
                (double)((float)y + offsetY),
                (double)((float)z + offsetZ),
                stack);
        entityItem.pickupDelay = 10;
        world.addEntity(entityItem);
    }

    private static void dropMovingPiston(
            World world,
            int x,
            int y,
            int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityPiston)) {
            return;
        }
        TileEntityPiston piston = (TileEntityPiston)tile;
        Block storedBlock = Block.byId[piston.a()];
        drop(
                storedBlock,
                world,
                x,
                y,
                z,
                piston.e(),
                1.0F);
    }
}
