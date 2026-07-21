package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

import java.util.Random;

// CraftBukkit start
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BlockFadeEvent;
//CraftBukkit end

public class BlockGrass extends Block {
    private static final String GRASS_TICK_RATE_CONFIG = "world.settings.grass-spread-tick-rate";
    private static final int VANILLA_GRASS_TICK_RATE = 1;
    private static int grassTickRate = Integer.MIN_VALUE;

    protected BlockGrass(int i) {
        super(i, Material.GRASS);
        this.textureId = 3;
        this.a(true);
    }

    public void a(World world, int i, int j, int k, Random random) {
        if (!world.isStatic) {
            if (this.shouldSkipConfiguredGrassTick(i, j, k)) {
                return;
            }

            Chunk sourceChunk = world.getChunkIfLoaded(i >> 4, k >> 4);
            if (sourceChunk == null) {
                return;
            }

            int aboveTypeId = getTypeIdIfLoaded(world, sourceChunk, i, j + 1, k);
            Block aboveBlock = Block.byId[aboveTypeId];
            Material material = aboveBlock == null ? Material.AIR : aboveBlock.material;
            int aboveLightLevel = world.getLightLevelIfLoaded(i, j + 1, k);
            if (aboveLightLevel < 4 && material.blocksLight()) {
                if (random.nextInt(4) != 0) {
                    return;
                }

                // CraftBukkit start
                org.bukkit.World bworld = world.getWorld();
                org.bukkit.block.BlockState blockState = bworld.getBlockAt(i, j, k).getState();
                blockState.setTypeId(Block.DIRT.id);

                BlockFadeEvent event = new BlockFadeEvent(blockState.getBlock(), blockState);
                world.getServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    blockState.update(true);
                }
                // CraftBukkit end
            } else if (aboveLightLevel >= 9) {
                int l = i + random.nextInt(3) - 1;
                int i1 = j + random.nextInt(5) - 3;
                int j1 = k + random.nextInt(3) - 1;

                if (l == i && i1 == j && j1 == k) {
                    return;
                }

                int spreadAboveTypeId = getTypeIdIfLoaded(world, sourceChunk, l, i1 + 1, j1);
                Block spreadAboveBlock = Block.byId[spreadAboveTypeId];
                Material spreadMaterial = spreadAboveBlock == null ? Material.AIR : spreadAboveBlock.material;

                if (getTypeIdIfLoaded(world, sourceChunk, l, i1, j1) == Block.DIRT.id && world.getLightLevelIfLoaded(l, i1 + 1, j1) >= 4 && !spreadMaterial.blocksLight()) {
                    // CraftBukkit start
                    org.bukkit.World bworld = world.getWorld();
                    org.bukkit.block.BlockState blockState = bworld.getBlockAt(l, i1, j1).getState();
                    blockState.setTypeId(this.id);

                    BlockSpreadEvent event = new BlockSpreadEvent(blockState.getBlock(), bworld.getBlockAt(i, j, k), blockState);
                    world.getServer().getPluginManager().callEvent(event);

                    if (!event.isCancelled()) {
                        blockState.update(true);
                    }
                    // CraftBukkit end
                }
            }
        }
    }

    private static int getTypeIdIfLoaded(World world, Chunk sourceChunk, int i, int j, int k) {
        if (i < -32000000 || k < -32000000 || i >= 32000000 || k > 32000000 || j < 0 || j >= 128) {
            return 0;
        }

        int chunkX = i >> 4;
        int chunkZ = k >> 4;
        Chunk chunk = sourceChunk != null && sourceChunk.x == chunkX && sourceChunk.z == chunkZ
                ? sourceChunk
                : world.getChunkIfLoaded(chunkX, chunkZ);

        return chunk == null ? 0 : chunk.getTypeId(i & 15, j, k & 15);
    }

    private boolean shouldSkipConfiguredGrassTick(int i, int j, int k) {
        int tickRate = getGrassTickRate();

        if (tickRate == VANILLA_GRASS_TICK_RATE) {
            return false;
        }

        if (tickRate < VANILLA_GRASS_TICK_RATE) {
            return true;
        }

        long staggeredTick = (long) MinecraftServer.currentTick + (long) grassTickHash(i, j, k);
        return staggeredTick % (long) tickRate != 0L;
    }

    private static int getGrassTickRate() {
        if (grassTickRate == Integer.MIN_VALUE) {
            grassTickRate = getConfigInt(GRASS_TICK_RATE_CONFIG, VANILLA_GRASS_TICK_RATE);
        }

        return grassTickRate;
    }

    private static int getConfigInt(String key, int defaultValue) {
        Object value = PoseidonConfig.getInstance().getConfigOption(key, Integer.valueOf(defaultValue));

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static int grassTickHash(int i, int j, int k) {
        return (j + k * 31) * 31 + i;
    }

    public int a(int i, Random random) {
        return Block.DIRT.a(0, random);
    }
}
