package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityInteractEvent;

import java.util.Random;

// CraftBukkit start
// CraftBukkit end

public class BlockSoil extends Block {
    private static final String WET_FARMLAND_TICK_RATE_CONFIG = "world.settings.farmland-tick-rate.wet";
    private static final String DRY_FARMLAND_TICK_RATE_CONFIG = "world.settings.farmland-tick-rate.dry";
    private static final int VANILLA_FARMLAND_TICK_RATE = 1;
    private static int wetFarmlandTickRate = Integer.MIN_VALUE;
    private static int dryFarmlandTickRate = Integer.MIN_VALUE;

    protected BlockSoil(int i) {
        super(i, Material.EARTH);
        this.textureId = 87;
        this.a(true);
        this.a(0.0F, 0.0F, 0.0F, 1.0F, 0.9375F, 1.0F);
        this.f(255);
    }

    public AxisAlignedBB e(World world, int i, int j, int k) {
        return AxisAlignedBB.b((double) (i + 0), (double) (j + 0), (double) (k + 0), (double) (i + 1), (double) (j + 1), (double) (k + 1));
    }

    public boolean a() {
        return false;
    }

    public boolean b() {
        return false;
    }

    public int a(int i, int j) {
        return i == 1 && j > 0 ? this.textureId - 1 : (i == 1 ? this.textureId : 2);
    }

    public void a(World world, int i, int j, int k, Random random) {
        if (this.shouldSkipConfiguredFarmlandTick(world, i, j, k)) {
            return;
        }

        if (random.nextInt(5) == 0) {
            if (!this.h(world, i, j, k) && !world.s(i, j + 1, k)) {
                int l = world.getData(i, j, k);

                if (l > 0) {
                    world.setData(i, j, k, l - 1);
                } else if (!this.g(world, i, j, k)) {
                    world.setTypeId(i, j, k, Block.DIRT.id);
                }
            } else {
                world.setData(i, j, k, 7);
            }
        }
    }

    private boolean shouldSkipConfiguredFarmlandTick(World world, int i, int j, int k) {
        int wetTickRate = getWetFarmlandTickRate();
        int dryTickRate = getDryFarmlandTickRate();

        if (wetTickRate == VANILLA_FARMLAND_TICK_RATE && dryTickRate == VANILLA_FARMLAND_TICK_RATE) {
            return false;
        }

        int moisture = world.getData(i, j, k);
        int tickRate = moisture > 0 ? wetTickRate : dryTickRate;

        if (tickRate == VANILLA_FARMLAND_TICK_RATE) {
            return false;
        }

        if (tickRate < VANILLA_FARMLAND_TICK_RATE) {
            return true;
        }

        long staggeredTick = (long) MinecraftServer.currentTick + (long) farmlandTickHash(i, j, k);
        return staggeredTick % (long) tickRate != 0L;
    }

    private static int getWetFarmlandTickRate() {
        if (wetFarmlandTickRate == Integer.MIN_VALUE) {
            wetFarmlandTickRate = getConfigInt(WET_FARMLAND_TICK_RATE_CONFIG, VANILLA_FARMLAND_TICK_RATE);
        }

        return wetFarmlandTickRate;
    }

    private static int getDryFarmlandTickRate() {
        if (dryFarmlandTickRate == Integer.MIN_VALUE) {
            dryFarmlandTickRate = getConfigInt(DRY_FARMLAND_TICK_RATE_CONFIG, VANILLA_FARMLAND_TICK_RATE);
        }

        return dryFarmlandTickRate;
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

    private static int farmlandTickHash(int i, int j, int k) {
        return (j + k * 31) * 31 + i;
    }

    public void b(World world, int i, int j, int k, Entity entity) {
        if (world.random.nextInt(4) == 0) {
            // CraftBukkit start - Interact Soil
            org.bukkit.event.Cancellable cancellable;
            if (entity instanceof EntityHuman) {
                cancellable = CraftEventFactory.callPlayerInteractEvent((EntityHuman) entity, org.bukkit.event.block.Action.PHYSICAL, i, j, k, -1, null);
            } else {
                cancellable = new EntityInteractEvent(entity.getBukkitEntity(), world.getWorld().getBlockAt(i, j, k));
                world.getServer().getPluginManager().callEvent((EntityInteractEvent) cancellable);
            }

            if (cancellable.isCancelled()) {
                return;
            }
            // CraftBukkit end

            world.setTypeId(i, j, k, Block.DIRT.id);
        }
    }

    private boolean g(World world, int i, int j, int k) {
        byte b0 = 0;

        for (int l = i - b0; l <= i + b0; ++l) {
            for (int i1 = k - b0; i1 <= k + b0; ++i1) {
                int j1 = world.getTypeIdIfLoaded(l, j + 1, i1);
                if (j1 == Block.CROPS.id || j1 == Block.PUMPKIN_STEM.id || j1 == Block.MELON_STEM.id) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean h(World world, int i, int j, int k) {
        int minY = Math.max(j, 0);
        int maxY = Math.min(j + 1, 127);

        if (minY > maxY) {
            return false;
        }

        for (int l = i - 4; l <= i + 4; ++l) {
            if (l < -32000000 || l >= 32000000) {
                continue;
            }

            for (int j1 = k - 4; j1 <= k + 4; ++j1) {
                if (j1 < -32000000 || j1 > 32000000) {
                    continue;
                }

                Chunk chunk = world.getChunkIfLoaded(l >> 4, j1 >> 4);

                if (chunk == null) {
                    continue;
                }

                int localX = l & 15;
                int localZ = j1 & 15;

                for (int i1 = minY; i1 <= maxY; ++i1) {
                    Block block = Block.byId[chunk.getTypeId(localX, i1, localZ)];

                    if (block != null && block.material == Material.WATER) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void doPhysics(World world, int i, int j, int k, int l) {
        super.doPhysics(world, i, j, k, l);
        Material material = world.getMaterial(i, j + 1, k);

        if (material.isBuildable()) {
            world.setTypeId(i, j, k, Block.DIRT.id);
        }
    }

    public int a(int i, Random random) {
        return Block.DIRT.a(0, random);
    }
}
