package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

import java.util.List;

final class EntityActivationRange {
    private static final int DEFAULT_MONSTER_RANGE = 32;
    private static final int DEFAULT_ANIMAL_RANGE = 32;
    private static final int DEFAULT_WATER_RANGE = 16;
    private static final int DEFAULT_MISC_CREATURE_RANGE = 16;
    private static final int DEFAULT_INACTIVE_CHECK_INTERVAL = 20;
    private static final int DEFAULT_NEW_ENTITY_IMMUNITY_TICKS = 200;

    private static boolean loaded;
    private static boolean enabled;
    private static int monsterRangeSq;
    private static int animalRangeSq;
    private static int waterRangeSq;
    private static int miscCreatureRangeSq;
    private static int maxActivationRange;
    private static int inactiveCheckInterval;
    private static int newEntityImmunityTicks;

    private EntityActivationRange() {
    }

    static void activateEntities(World world) {
        loadConfig();
        long currentTick = world.getBlockTickTime();

        if (!enabled || world.players.isEmpty()) {
            activateAll(world.entityList, currentTick);
            return;
        }

        // Preserve dynamic always-active state and the rare unchunked-entity
        // fallback with one linear pass. Spatially indexed entities are marked
        // from nearby player chunks below instead of scanning every player.
        for (int i = 0; i < world.entityList.size(); ++i) {
            Entity entity = (Entity) world.entityList.get(i);
            if (isAlwaysActive(entity) || (!entity.bG && isNearActivePlayer(entity, world.players))) {
                entity.activatedTick = currentTick;
            }
        }

        for (int i = 0; i < world.players.size(); ++i) {
            Entity player = (Entity) world.players.get(i);
            player.activatedTick = currentTick;
            activateNearbyLoadedChunks(world, player, currentTick);
        }
    }

    static boolean checkIfActive(Entity entity, World world) {
        loadConfig();

        if (!enabled || isAlwaysActive(entity)) {
            return true;
        }

        long currentTick = world.getBlockTickTime();
        if (entity.activatedTick >= currentTick) {
            return true;
        }

        if (entity.ticksLived < newEntityImmunityTicks || hasActivityImmunity(entity)) {
            entity.activatedTick = currentTick;
            return true;
        }

        return inactiveCheckInterval <= 1
                || Math.floorMod(currentTick + (long) entity.id, (long) inactiveCheckInterval) == 0L;
    }

    static void inactiveTick(Entity entity) {
        ++entity.ticksLived;
        entity.bl = entity.bm;
        entity.lastX = entity.locX;
        entity.lastY = entity.locY;
        entity.lastZ = entity.locZ;
        entity.lastPitch = entity.pitch;
        entity.lastYaw = entity.yaw;
        if (entity instanceof EntityLiving) {
            // EntityLiving.c_ increments this every normal tick; keeping it in
            // wall-clock ticks preserves vanilla despawn timing while AI sleeps.
            ++((EntityLiving) entity).ay;
        }
    }

    private static void activateNearbyLoadedChunks(World world, Entity player, long currentTick) {
        int minChunkX = MathHelper.floor((player.locX - (double) maxActivationRange) / 16.0D);
        int maxChunkX = MathHelper.floor((player.locX + (double) maxActivationRange) / 16.0D);
        int minChunkZ = MathHelper.floor((player.locZ - (double) maxActivationRange) / 16.0D);
        int maxChunkZ = MathHelper.floor((player.locZ + (double) maxActivationRange) / 16.0D);
        ChunkProviderServer serverProvider = world.chunkProvider instanceof ChunkProviderServer
                ? (ChunkProviderServer) world.chunkProvider
                : null;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; ++chunkX) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; ++chunkZ) {
                Chunk chunk = serverProvider != null
                        ? serverProvider.getChunkAtIfLoadedMainThreadNoCache(chunkX, chunkZ)
                        : world.getChunkIfLoaded(chunkX, chunkZ);
                if (chunk != null) {
                    activateChunkEntities(chunk, player, currentTick);
                }
            }
        }
    }

    private static void activateChunkEntities(Chunk chunk, Entity player, long currentTick) {
        for (int slice = 0; slice < chunk.entitySlices.length; ++slice) {
            List entities = chunk.entitySlices[slice];
            for (int i = 0; i < entities.size(); ++i) {
                Entity entity = (Entity) entities.get(i);
                if (entity == null || entity.activatedTick >= currentTick) {
                    continue;
                }

                int rangeSq = getActivationRangeSq(entity);
                if (rangeSq < 0) {
                    entity.activatedTick = currentTick;
                    continue;
                }

                double deltaX = entity.locX - player.locX;
                double deltaZ = entity.locZ - player.locZ;
                if (deltaX * deltaX + deltaZ * deltaZ <= (double) rangeSq) {
                    entity.activatedTick = currentTick;
                }
            }
        }
    }

    private static void activateAll(List entities, long currentTick) {
        for (int i = 0; i < entities.size(); ++i) {
            ((Entity) entities.get(i)).activatedTick = currentTick;
        }
    }

    private static boolean isNearActivePlayer(Entity entity, List players) {
        int rangeSq = getActivationRangeSq(entity);
        if (rangeSq < 0) {
            return true;
        }

        for (int i = 0; i < players.size(); ++i) {
            Entity player = (Entity) players.get(i);
            double deltaX = entity.locX - player.locX;
            double deltaZ = entity.locZ - player.locZ;
            if (deltaX * deltaX + deltaZ * deltaZ <= (double) rangeSq) {
                return true;
            }
        }

        return false;
    }

    private static int getActivationRangeSq(Entity entity) {
        if (entity instanceof EntityMonster || entity instanceof IMonster || entity instanceof EntityFlying) {
            return monsterRangeSq;
        }

        if (entity instanceof EntityWaterAnimal) {
            return waterRangeSq;
        }

        if (entity instanceof EntityAnimal) {
            return animalRangeSq;
        }

        if (entity instanceof EntityCreature) {
            return miscCreatureRangeSq;
        }

        return -1;
    }

    private static boolean isAlwaysActive(Entity entity) {
        if (!(entity instanceof EntityCreature) && !(entity instanceof EntityFlying)) {
            return true;
        }

        return entity instanceof EntityHuman
                || entity instanceof EntityItem
                || entity instanceof EntityTNTPrimed
                || entity instanceof EntityFallingSand
                || entity instanceof EntityMinecart
                || entity instanceof EntityBoat
                || entity instanceof EntityArrow
                || entity instanceof EntitySnowball
                || entity instanceof EntityEgg
                || entity instanceof EntityFireball
                || entity instanceof EntityWeather
                || entity instanceof EntityPainting
                || entity instanceof EntityMapHanging
                || entity instanceof EntityHerobrine
                || entity.vehicle != null
                || entity.passenger != null;
    }

    private static boolean hasActivityImmunity(Entity entity) {
        if (entity.fireTicks > 0 || entity.airBorne || entity.velocityChanged || !entity.onGround || hasSignificantMotion(entity)) {
            return true;
        }

        if (entity instanceof EntityLiving) {
            EntityLiving living = (EntityLiving) entity;
            if (living.hurtTicks > 0 || living.attackTicks > 0 || living.deathTicks > 0 || living.noDamageTicks > 0 || living.health <= 0) {
                return true;
            }
        }

        if (entity instanceof EntityCreature && ((EntityCreature) entity).target != null) {
            return true;
        }

        if (entity instanceof EntityAnimal && ((EntityAnimal) entity).isLeashed()) {
            return true;
        }

        if (entity instanceof EntityWolf) {
            EntityWolf wolf = (EntityWolf) entity;
            if (wolf.isLeashFleeing() || wolf.isAngry()) {
                return true;
            }
        }

        if (entity instanceof EntityCreeper && ((EntityCreeper) entity).fuseTicks > 0) {
            return true;
        }

        return entity instanceof EntitySheep && ((EntitySheep) entity).isSheared();
    }

    private static boolean hasSignificantMotion(Entity entity) {
        return entity.motX * entity.motX + entity.motY * entity.motY + entity.motZ * entity.motZ > 0.0004D;
    }

    private static void loadConfig() {
        if (loaded) {
            return;
        }

        PoseidonConfig config = PoseidonConfig.getInstance();
        enabled = getConfigBoolean(config, "settings.entity-activation.enabled", true);
        int monsterRange = getConfigInt(config, "settings.entity-activation.monster-range", DEFAULT_MONSTER_RANGE, 1, 256);
        int animalRange = getConfigInt(config, "settings.entity-activation.animal-range", DEFAULT_ANIMAL_RANGE, 1, 256);
        int waterRange = getConfigInt(config, "settings.entity-activation.water-range", DEFAULT_WATER_RANGE, 1, 256);
        int miscCreatureRange = getConfigInt(config, "settings.entity-activation.misc-creature-range", DEFAULT_MISC_CREATURE_RANGE, 1, 256);
        monsterRangeSq = square(monsterRange);
        animalRangeSq = square(animalRange);
        waterRangeSq = square(waterRange);
        miscCreatureRangeSq = square(miscCreatureRange);
        maxActivationRange = Math.max(Math.max(monsterRange, animalRange), Math.max(waterRange, miscCreatureRange));
        inactiveCheckInterval = getConfigInt(config, "settings.entity-activation.inactive-check-interval", DEFAULT_INACTIVE_CHECK_INTERVAL, 1, 200);
        newEntityImmunityTicks = getConfigInt(config, "settings.entity-activation.new-entity-immunity-ticks", DEFAULT_NEW_ENTITY_IMMUNITY_TICKS, 0, 1200);
        loaded = true;
    }

    private static int square(int value) {
        return value * value;
    }

    private static boolean getConfigBoolean(PoseidonConfig config, String key, boolean defaultValue) {
        try {
            Object value = config.getConfigOption(key, Boolean.valueOf(defaultValue));
            if (value instanceof Boolean) {
                return ((Boolean) value).booleanValue();
            }

            return Boolean.valueOf(String.valueOf(value)).booleanValue();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static int getConfigInt(PoseidonConfig config, String key, int defaultValue, int min, int max) {
        int value = defaultValue;
        try {
            Object option = config.getConfigOption(key, Integer.valueOf(defaultValue));
            if (option instanceof Number) {
                value = ((Number) option).intValue();
            } else {
                value = Integer.parseInt(String.valueOf(option));
            }
        } catch (Exception e) {
            value = defaultValue;
        }

        return Math.max(min, Math.min(max, value));
    }
}
