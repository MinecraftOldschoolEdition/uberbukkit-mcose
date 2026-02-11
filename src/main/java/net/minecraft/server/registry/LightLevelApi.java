package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.Entity;
import net.minecraft.server.EnumSkyBlock;
import net.minecraft.server.MathHelper;
import net.minecraft.server.World;
import net.minecraft.server.util.ResourceLocation;

import java.util.Random;

/**
 * Mod-facing helpers for querying block/world/entity lighting.
 * All methods are read-only and preserve vanilla lighting behavior.
 */
public final class LightLevelApi {
    private LightLevelApi() {}

    public static int getCombinedLight(World world, int x, int y, int z) {
        if (world == null) {
            return 0;
        }
        return clampLight(world.getLightLevel(x, y, z));
    }

    public static int getSkyLight(World world, int x, int y, int z) {
        if (world == null) {
            return 0;
        }
        return clampLight(world.a(EnumSkyBlock.SKY, x, y, z));
    }

    public static int getBlockLight(World world, int x, int y, int z) {
        if (world == null) {
            return 0;
        }
        return clampLight(world.a(EnumSkyBlock.BLOCK, x, y, z));
    }

    public static float getBrightness(World world, int x, int y, int z) {
        if (world == null) {
            return 0.0F;
        }
        return world.n(x, y, z);
    }

    public static boolean canSeeSky(World world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        return world.m(x, y, z);
    }

    public static int getBlockEmission(Block block) {
        return BlockCapabilityRegistryApi.getLightEmission(block);
    }

    public static int getBlockEmission(ResourceLocation blockKey) {
        return BlockCapabilityRegistryApi.getLightEmission(blockKey);
    }

    public static int getBlockEmission(String blockIdentifier) {
        return BlockCapabilityRegistryApi.getLightEmission(blockIdentifier);
    }

    public static int getBlockOpacity(Block block) {
        if (block == null || block.id < 0 || block.id >= Block.q.length) {
            return 0;
        }
        return clampLight(Block.q[block.id]);
    }

    public static int getBlockOpacity(ResourceLocation blockKey) {
        if (blockKey == null) {
            return 0;
        }
        return getBlockOpacity(BlockRegistry.get(blockKey));
    }

    public static int getBlockOpacity(String blockIdentifier) {
        if (blockIdentifier == null) {
            return 0;
        }
        String normalized = BlockRegistry.normalizeInputIdentifier(blockIdentifier);
        if (normalized == null) {
            return 0;
        }
        return getBlockOpacity(new ResourceLocation(normalized));
    }

    public static int getEntityCombinedLight(Entity entity) {
        if (entity == null || entity.world == null) {
            return 0;
        }
        int[] pos = sampleEntityLightPosition(entity);
        return getCombinedLight(entity.world, pos[0], pos[1], pos[2]);
    }

    public static int getEntitySkyLight(Entity entity) {
        if (entity == null || entity.world == null) {
            return 0;
        }
        int[] pos = sampleEntityLightPosition(entity);
        return getSkyLight(entity.world, pos[0], pos[1], pos[2]);
    }

    public static int getEntityBlockLight(Entity entity) {
        if (entity == null || entity.world == null) {
            return 0;
        }
        int[] pos = sampleEntityLightPosition(entity);
        return getBlockLight(entity.world, pos[0], pos[1], pos[2]);
    }

    public static float getEntityBrightness(Entity entity, float partialTicks) {
        if (entity == null) {
            return 0.0F;
        }
        return entity.c(partialTicks);
    }

    public static boolean isEntityInDirectSunlight(Entity entity) {
        if (entity == null || entity.world == null) {
            return false;
        }

        World world = entity.world;
        if (!world.d()) {
            return false;
        }

        int[] pos = sampleEntityLightPosition(entity);
        if (!canSeeSky(world, pos[0], pos[1], pos[2])) {
            return false;
        }

        return getEntitySkyLight(entity) > 11;
    }

    /**
     * Mirrors vanilla zombie/skeleton sunlight combustion gate for modded mobs.
     */
    public static boolean passesUndeadSunlightCombustCheck(Entity entity, Random random) {
        if (entity == null || entity.world == null || random == null) {
            return false;
        }

        World world = entity.world;
        if (!world.d()) {
            return false;
        }

        float brightness = entity.c(1.0F);
        if (brightness <= 0.5F) {
            return false;
        }

        if (!world.isChunkLoaded(MathHelper.floor(entity.locX), MathHelper.floor(entity.locY), MathHelper.floor(entity.locZ))) {
            return false;
        }

        return random.nextFloat() * 30.0F < (brightness - 0.4F) * 2.0F;
    }

    private static int[] sampleEntityLightPosition(Entity entity) {
        int x = MathHelper.floor(entity.locX);
        double yOffset = (entity.boundingBox.e - entity.boundingBox.b) * 0.66D;
        int y = MathHelper.floor(entity.locY - (double) entity.height + yOffset);
        int z = MathHelper.floor(entity.locZ);
        return new int[] { x, y, z };
    }

    private static int clampLight(int light) {
        if (light < 0) {
            return 0;
        }
        if (light > 15) {
            return 15;
        }
        return light;
    }
}
