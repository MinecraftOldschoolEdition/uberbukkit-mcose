package net.minecraft.server;

import net.minecraft.server.registry.EntityTypeRegistryApi;
import net.minecraft.server.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class EntityTypes {

    private static Map a = new HashMap();
    private static Map b = new HashMap();
    private static Map c = new HashMap();
    private static Map d = new HashMap();

    public EntityTypes() {
    }

    private static void a(Class oclass, String s, int i) {
        a.put(s, oclass);
        // Also store lowercase alias for case-insensitive lookups (e.g., /summon cow)
        try { a.put(s.toLowerCase(), oclass); } catch (Throwable ignore) {}
        b.put(oclass, s);
        c.put(Integer.valueOf(i), oclass);
        d.put(oclass, Integer.valueOf(i));
    }

    public static Entity a(String s, World world) {
        Entity fromRegistry = EntityTypeRegistryApi.createEntity(s, world);
        if (fromRegistry != null) {
            return fromRegistry;
        }

        Entity entity = null;

        try {
            Class oclass = (Class) a.get(s);
            if (oclass == null && s != null) {
                oclass = (Class) a.get(s.toLowerCase());
            }

            if (oclass != null) {
                entity = (Entity) oclass.getConstructor(new Class[] { World.class }).newInstance(new Object[] { world });
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        return entity;
    }

    public static Entity a(NBTTagCompound nbttagcompound, World world) {
        Entity entity = null;

        try {
            Entity fromRegistry = EntityTypeRegistryApi.createEntity(nbttagcompound.getString("id"), world);
            if (fromRegistry != null) {
                fromRegistry.e(nbttagcompound);
                return fromRegistry;
            }
        } catch (Throwable ignored) {
        }

        try {
            Class oclass = (Class) a.get(nbttagcompound.getString("id"));

            if (oclass != null) {
                entity = (Entity) oclass.getConstructor(new Class[] { World.class }).newInstance(new Object[] { world });
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        if (entity != null) {
            entity.e(nbttagcompound);
        } else {
            System.out.println("Skipping Entity with id " + nbttagcompound.getString("id"));
        }

        return entity;
    }

    public static int a(Entity entity) {
        if (entity == null) {
            return -1;
        }

        Integer legacy = (Integer) d.get(entity.getClass());
        if (legacy != null) {
            return legacy.intValue();
        }

        ResourceLocation key = EntityTypeRegistryApi.getKey(entity.getClass());
        if (key != null) {
            Integer mapped = EntityTypeRegistryApi.getLegacyId(key.toString());
            return mapped == null ? -1 : mapped.intValue();
        }
        return -1;
    }

    public static String b(Entity entity) {
        if (entity == null) {
            return null;
        }

        ResourceLocation key = EntityTypeRegistryApi.getKey(entity.getClass());
        if (key != null) {
            return key.toString();
        }

        return (String) b.get(entity.getClass());
    }

    static {
        a(EntityArrow.class, "Arrow", 10);
        a(EntitySnowball.class, "Snowball", 11);
        a(EntityItem.class, "Item", 1);
        a(EntityPainting.class, "Painting", 9);
        a(EntityMapHanging.class, "MapHanging", 26); // Wall-mounted maps
        a(EntityLiving.class, "Mob", 48);
        a(EntityMonster.class, "Monster", 49);
        a(EntityCreeper.class, "Creeper", 50);
        a(EntitySkeleton.class, "Skeleton", 51);
        a(EntitySpider.class, "Spider", 52);
        a(EntityGiantZombie.class, "Giant", 53);
        a(EntityZombie.class, "Zombie", 54);
        a(EntitySlime.class, "Slime", 55);
        a(EntityGhast.class, "Ghast", 56);
        a(EntityPigZombie.class, "PigZombie", 57);
        a(EntityPig.class, "Pig", 90);
        a(EntitySheep.class, "Sheep", 91);
        a(EntityCow.class, "Cow", 92);
        a(EntityChicken.class, "Chicken", 93);
        a(EntitySquid.class, "Squid", 94);
        a(EntityWolf.class, "Wolf", 95);
        a(EntityTNTPrimed.class, "PrimedTnt", 20);
        a(EntityFallingSand.class, "FallingSand", 21);
        a(EntityMinecart.class, "Minecart", 40);
        a(EntityBoat.class, "Boat", 41);
        a(EntitySnowman.class, "SnowMan", 97);
        a(EntityHerobrine.class, "Herobrine", 100); // Must match client EntityList

        // Initialize registry bootstraps
        try { net.minecraft.server.registry.SpawnGroupRegistryBootstrap.initialize(); } catch (Throwable ignored) {}
        try { net.minecraft.server.registry.JukeboxSongRegistryBootstrap.initialize(); } catch (Throwable ignored) {}
        try { net.minecraft.server.registry.RecipeTypeRegistryBootstrap.initialize(); } catch (Throwable ignored) {}
        try { net.minecraft.server.registry.LootTables.initialize(); } catch (Throwable ignored) {}
        try { net.minecraft.server.registry.StructureTypes.initialize(); } catch (Throwable ignored) {}
        try { net.minecraft.server.registry.AchievementRegistryBootstrap.initialize(); } catch (Throwable ignored) {}
        try { net.minecraft.server.registry.WorldFeatureRegistryBootstrap.initialize(); } catch (Throwable ignored) {}
    }
}
