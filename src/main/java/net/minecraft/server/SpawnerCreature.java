package net.minecraft.server;

import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

// CraftBukkit

public final class SpawnerCreature {

    private static Set b = new HashSet();
    protected static final Class[] a = new Class[] { EntitySpider.class, EntityZombie.class, EntitySkeleton.class };
    private static final int SKY_WORLD_SPAWN_CAP_DIVISOR = 512;
    private static final int SKY_WORLD_MONSTER_CAP_BASE = 32;
    private static final int SKY_WORLD_MONSTER_CAP_PER_PLAYER = 8;
    private static final int SKY_WORLD_NETHER_MONSTER_CAP_BASE = 24;
    private static final int SKY_WORLD_NETHER_MONSTER_CAP_PER_PLAYER = 6;
    private static final int SKY_WORLD_CREATURE_CAP_BASE = 8;
    private static final int SKY_WORLD_CREATURE_CAP_PER_PLAYER = 2;

    public SpawnerCreature() {
    }

    protected static ChunkPosition a(World world, int i, int j) {
        int k = i + world.random.nextInt(16);
        int l = world.random.nextInt(128);
        int i1 = j + world.random.nextInt(16);

        return new ChunkPosition(k, l, i1);
    }

    public static final int spawnEntities(World world, boolean flag, boolean flag1) {
        if (!flag && !flag1) {
            return 0;
        } else {
            b.clear();

            int i;
            int j;

            for (i = 0; i < world.players.size(); ++i) {
                EntityHuman entityhuman = (EntityHuman) world.players.get(i);
                int k = MathHelper.floor(entityhuman.locX / 16.0D);

                j = MathHelper.floor(entityhuman.locZ / 16.0D);
                byte b0 = 8;

                for (int l = -b0; l <= b0; ++l) {
                    for (int i1 = -b0; i1 <= b0; ++i1) {
                        b.add(new ChunkCoordIntPair(l + k, i1 + j));
                    }
                }
            }

            i = 0;
            ChunkCoordinates chunkcoordinates = world.getSpawn();
            EnumCreatureType[] aenumcreaturetype = EnumCreatureType.values();

            j = aenumcreaturetype.length;

            boolean useSkyWorldMobCaps = usesSkyWorldMobCaps(world);
            int playerCount = world.players.size();

            labelTypes:
            for (int j1 = 0; j1 < j; ++j1) {
                EnumCreatureType enumcreaturetype = aenumcreaturetype[j1];
                int creatureCount = world.a(enumcreaturetype.a());
                int creatureCap = getCreatureCapForWorld(world, enumcreaturetype, b.size(), playerCount, useSkyWorldMobCaps);

                if ((!enumcreaturetype.d() || flag1) && (enumcreaturetype.d() || flag) && isCreatureUnderCap(creatureCount, creatureCap, useSkyWorldMobCaps)) {
                    Iterator iterator = b.iterator();

                        label113:
                    while (iterator.hasNext()) {
                        ChunkCoordIntPair chunkcoordintpair = (ChunkCoordIntPair) iterator.next();
                        BiomeBase biomebase = world.getWorldChunkManager().a(chunkcoordintpair);
                        List list = biomebase.a(enumcreaturetype);

                        if (list != null && !list.isEmpty()) {
                            int k1 = 0;

                            BiomeMeta biomemeta;

                            for (Iterator iterator1 = list.iterator(); iterator1.hasNext(); k1 += biomemeta.b) {
                                biomemeta = (BiomeMeta) iterator1.next();
                            }

                            int l1 = world.random.nextInt(k1);

                            biomemeta = (BiomeMeta) list.get(0);
                            Iterator iterator2 = list.iterator();

                            while (iterator2.hasNext()) {
                                BiomeMeta biomemeta1 = (BiomeMeta) iterator2.next();

                                l1 -= biomemeta1.b;
                                if (l1 < 0) {
                                    biomemeta = biomemeta1;
                                    break;
                                }
                            }

                            ChunkPosition chunkposition = a(world, chunkcoordintpair.x * 16, chunkcoordintpair.z * 16);
                            int i2 = chunkposition.x;
                            int j2 = chunkposition.y;
                            int k2 = chunkposition.z;

                            if (!world.e(i2, j2, k2) && world.getMaterial(i2, j2, k2) == enumcreaturetype.c()) {
                                int l2 = 0;

                                for (int i3 = 0; i3 < 3; ++i3) {
                                    int j3 = i2;
                                    int k3 = j2;
                                    int l3 = k2;
                                    byte b1 = 6;

                                    for (int i4 = 0; i4 < 4; ++i4) {
                                        j3 += world.random.nextInt(b1) - world.random.nextInt(b1);
                                        k3 += world.random.nextInt(1) - world.random.nextInt(1);
                                        l3 += world.random.nextInt(b1) - world.random.nextInt(b1);
                                        if (a(enumcreaturetype, world, j3, k3, l3)) {
                                            float f = (float) j3 + 0.5F;
                                            float f1 = (float) k3;
                                            float f2 = (float) l3 + 0.5F;

                                            if (world.a((double) f, (double) f1, (double) f2, 24.0D) == null) {
                                                float f3 = f - (float) chunkcoordinates.x;
                                                float f4 = f1 - (float) chunkcoordinates.y;
                                                float f5 = f2 - (float) chunkcoordinates.z;
                                                float f6 = f3 * f3 + f4 * f4 + f5 * f5;

                                                if (f6 >= 576.0F) {
                                                    EntityLiving entityliving;

                                                    try {
                                                        entityliving = (EntityLiving) biomemeta.a.getConstructor(new Class[] { World.class }).newInstance(new Object[] { world });
                                                    } catch (Exception exception) {
                                                        exception.printStackTrace();
                                                        return i;
                                                    }

                                                    entityliving.setPositionRotation((double) f, (double) f1, (double) f2, world.random.nextFloat() * 360.0F, 0.0F);
                                                    if (entityliving.d()) {
                                                        ++l2;
                                                        // CraftBukkit - added a reason for spawning this creature
                                                        world.addEntity(entityliving, SpawnReason.NATURAL);
                                                        a(entityliving, world, f, f1, f2);
                                                        if (useSkyWorldMobCaps) {
                                                            ++creatureCount;
                                                            if (creatureCount >= creatureCap) {
                                                                i += l2;
                                                                continue labelTypes;
                                                            }
                                                        }
                                                        if (l2 >= entityliving.l()) {
                                                            continue label113;
                                                        }
                                                    }

                                                    i += l2;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return i;
        }
    }

    private static boolean usesSkyWorldMobCaps(World world) {
        return getTerrainType(world) == 3;
    }

    private static int getTerrainType(World world) {
        if (world != null && world.worldData != null) {
            int terrainType = world.worldData.getTerrainType();
            if (terrainType != 0) {
                return terrainType;
            }
        }

        if (world instanceof WorldServer) {
            try {
                MinecraftServer server = ((WorldServer)world).server;
                WorldServer overworld = server != null ? server.getWorldServer(0) : null;
                if (overworld != null && overworld.worldData != null) {
                    return overworld.worldData.getTerrainType();
                }
            } catch (Throwable ignore) {}
        }

        return world != null && world.worldData != null ? world.worldData.getTerrainType() : 0;
    }

    private static int getCreatureCapForWorld(World world, EnumCreatureType creatureType, int eligibleChunks, int playerCount, boolean useSkyWorldMobCaps) {
        int vanillaCap = creatureType.b() * eligibleChunks / 256;
        if (!useSkyWorldMobCaps) {
            return vanillaCap;
        }

        int reducedCap = creatureType.b() * eligibleChunks / SKY_WORLD_SPAWN_CAP_DIVISOR;
        int absoluteCap = getSkyWorldAbsoluteCreatureCap(world, creatureType, playerCount);
        if (absoluteCap <= 0) {
            return 0;
        }

        int cap = Math.min(reducedCap, absoluteCap);
        return cap <= 0 ? 1 : cap;
    }

    private static int getSkyWorldAbsoluteCreatureCap(World world, EnumCreatureType creatureType, int playerCount) {
        int players = Math.max(1, playerCount);
        boolean isNether = world != null && world.worldProvider instanceof WorldProviderHell;
        if (creatureType == EnumCreatureType.MONSTER) {
            return isNether
                    ? SKY_WORLD_NETHER_MONSTER_CAP_BASE + players * SKY_WORLD_NETHER_MONSTER_CAP_PER_PLAYER
                    : SKY_WORLD_MONSTER_CAP_BASE + players * SKY_WORLD_MONSTER_CAP_PER_PLAYER;
        }
        if (creatureType == EnumCreatureType.CREATURE) {
            return SKY_WORLD_CREATURE_CAP_BASE + players * SKY_WORLD_CREATURE_CAP_PER_PLAYER;
        }
        if (creatureType == EnumCreatureType.WATER_CREATURE) {
            return players;
        }

        return Math.max(1, creatureType.b() / 2);
    }

    private static boolean isCreatureUnderCap(int creatureCount, int creatureCap, boolean useStrictCap) {
        return useStrictCap ? creatureCount < creatureCap : creatureCount <= creatureCap;
    }

    private static boolean a(EnumCreatureType enumcreaturetype, World world, int i, int j, int k) {
        return enumcreaturetype.c() == Material.WATER ? world.getMaterial(i, j, k).isLiquid() && !world.e(i, j + 1, k) : world.e(i, j - 1, k) && !world.e(i, j, k) && !world.getMaterial(i, j, k).isLiquid() && !world.e(i, j + 1, k);
    }

    private static void a(EntityLiving entityliving, World world, float f, float f1, float f2) {
        if (entityliving instanceof EntitySpider && world.random.nextInt(100) == 0) {
            EntitySkeleton entityskeleton = new EntitySkeleton(world);

            entityskeleton.setPositionRotation((double) f, (double) f1, (double) f2, entityliving.yaw, 0.0F);
            // CraftBukkit - added a reason for spawning this creature
            world.addEntity(entityskeleton, SpawnReason.NATURAL);
            entityskeleton.mount(entityliving);
        } else if (entityliving instanceof EntitySheep) {
            ((EntitySheep) entityliving).setColor(EntitySheep.a(world.random));
        }
    }

    public static boolean a(World world, List list) {
        boolean flag = false;
        Pathfinder pathfinder = new Pathfinder(world);
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            EntityHuman entityhuman = (EntityHuman) iterator.next();
            Class[] aclass = a;

            if (aclass != null && aclass.length != 0) {
                boolean flag1 = false;

                for (int i = 0; i < 20 && !flag1; ++i) {
                    int j = MathHelper.floor(entityhuman.locX) + world.random.nextInt(32) - world.random.nextInt(32);
                    int k = MathHelper.floor(entityhuman.locZ) + world.random.nextInt(32) - world.random.nextInt(32);
                    int l = MathHelper.floor(entityhuman.locY) + world.random.nextInt(16) - world.random.nextInt(16);

                    if (l < 1) {
                        l = 1;
                    } else if (l > 128) {
                        l = 128;
                    }

                    int i1 = world.random.nextInt(aclass.length);

                    int j1;

                    for (j1 = l; j1 > 2 && !world.e(j, j1 - 1, k); --j1) {
                        ;
                    }

                    while (!a(EnumCreatureType.MONSTER, world, j, j1, k) && j1 < l + 16 && j1 < 128) {
                        ++j1;
                    }

                    if (j1 < l + 16 && j1 < 128) {
                        float f = (float) j + 0.5F;
                        float f1 = (float) j1;
                        float f2 = (float) k + 0.5F;

                        EntityLiving entityliving;

                        try {
                            entityliving = (EntityLiving) aclass[i1].getConstructor(new Class[] { World.class }).newInstance(new Object[] { world });
                        } catch (Exception exception) {
                            exception.printStackTrace();
                            return flag;
                        }

                        entityliving.setPositionRotation((double) f, (double) f1, (double) f2, world.random.nextFloat() * 360.0F, 0.0F);
                        if (entityliving.d()) {
                            PathEntity pathentity = pathfinder.a(entityliving, entityhuman, 32.0F);

                            if (pathentity != null && pathentity.a > 1) {
                                PathPoint pathpoint = pathentity.c();

                                if (Math.abs((double) pathpoint.a - entityhuman.locX) < 1.5D && Math.abs((double) pathpoint.c - entityhuman.locZ) < 1.5D && Math.abs((double) pathpoint.b - entityhuman.locY) < 1.5D) {
                                    ChunkCoordinates chunkcoordinates = BlockBed.f(world, MathHelper.floor(entityhuman.locX), MathHelper.floor(entityhuman.locY), MathHelper.floor(entityhuman.locZ), 1);

                                    if (chunkcoordinates == null) {
                                        chunkcoordinates = new ChunkCoordinates(j, j1 + 1, k);
                                    }

                                    entityliving.setPositionRotation((double) ((float) chunkcoordinates.x + 0.5F), (double) chunkcoordinates.y, (double) ((float) chunkcoordinates.z + 0.5F), 0.0F, 0.0F);
                                    // CraftBukkit - added a reason for spawning this creature
                                    world.addEntity(entityliving, SpawnReason.BED);
                                    a(entityliving, world, (float) chunkcoordinates.x + 0.5F, (float) chunkcoordinates.y, (float) chunkcoordinates.z + 0.5F);
                                    entityhuman.a(true, false, false);
                                    entityliving.Q();
                                    flag = true;
                                    flag1 = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return flag;
    }
}
