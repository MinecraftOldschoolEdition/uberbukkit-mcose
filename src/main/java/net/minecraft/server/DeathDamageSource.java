package net.minecraft.server;

import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

public final class DeathDamageSource {
    private final DeathDamageType type;
    private final Entity directEntity;
    private final Entity causingEntity;
    private final String causingEntityName;

    private DeathDamageSource(DeathDamageType type, Entity directEntity, Entity causingEntity, String causingEntityName) {
        this.type = type == null ? DeathDamageType.GENERIC : type;
        this.directEntity = directEntity;
        this.causingEntity = causingEntity;
        this.causingEntityName = causingEntityName;
    }

    public static DeathDamageSource forDamage(EntityLiving victim, Entity directEntity) {
        EntityDamageEvent event = victim == null ? null : victim.getBukkitEntity().getLastDamageCause();
        DamageCause cause = event == null ? null : event.getCause();
        Entity direct = directEntity;
        Entity causing = resolveCausingEntity(directEntity, event);
        String causingName = resolveCausingName(directEntity);

        DeathDamageType type = typeFor(cause, direct, causing, victim);

        if (type == DeathDamageType.SUICIDE) {
            direct = null;
            causing = null;
            causingName = null;
        }

        return new DeathDamageSource(type, direct, causing, causingName);
    }

    public DeathDamageType getType() {
        return this.type;
    }

    public Entity getDirectEntity() {
        return this.directEntity;
    }

    public Entity getCausingEntity() {
        return this.causingEntity;
    }

    public String getCausingEntityName() {
        return this.causingEntityName;
    }

    private static DeathDamageType typeFor(DamageCause cause, Entity direct, Entity causing, EntityLiving victim) {
        if (cause == DamageCause.SUICIDE) {
            return DeathDamageType.SUICIDE;
        }
        if (cause == DamageCause.PROJECTILE || isProjectile(direct)) {
            return projectileType(direct);
        }
        if (cause == DamageCause.ENTITY_ATTACK) {
            Entity attacker = causing != null ? causing : direct;
            return attacker instanceof EntityPlayer ? DeathDamageType.PLAYER : DeathDamageType.MOB;
        }
        if (cause == DamageCause.CONTACT) {
            return DeathDamageType.CACTUS;
        }
        if (cause == DamageCause.SUFFOCATION) {
            return DeathDamageType.IN_WALL;
        }
        if (cause == DamageCause.FALL) {
            return DeathDamageType.FALL;
        }
        if (cause == DamageCause.FIRE) {
            return DeathDamageType.IN_FIRE;
        }
        if (cause == DamageCause.FIRE_TICK) {
            return DeathDamageType.ON_FIRE;
        }
        if (cause == DamageCause.LAVA) {
            return DeathDamageType.LAVA;
        }
        if (cause == DamageCause.DROWNING) {
            return DeathDamageType.DROWN;
        }
        if (cause == DamageCause.VOID) {
            return DeathDamageType.OUT_OF_WORLD;
        }
        if (cause == DamageCause.LIGHTNING) {
            return DeathDamageType.LIGHTNING_BOLT;
        }
        if (cause == DamageCause.BED_EXPLOSION) {
            return DeathDamageType.BAD_RESPAWN_POINT;
        }
        if (cause == DamageCause.BLOCK_EXPLOSION
                || cause == DamageCause.TNT_EXPLOSION
                || cause == DamageCause.ENTITY_EXPLOSION
                || cause == DamageCause.PLUGIN_EXPLOSION) {
            return DeathDamageType.EXPLOSION;
        }
        if (direct instanceof EntityFireball) {
            return DeathDamageType.FIREBALL;
        }
        if (direct instanceof EntityArrow) {
            return DeathDamageType.ARROW;
        }
        if (direct instanceof EntityLiving) {
            return direct instanceof EntityPlayer ? DeathDamageType.PLAYER : DeathDamageType.MOB;
        }
        if (victim != null && victim.fallDistance > 0.0F && direct == null) {
            return DeathDamageType.FALL;
        }
        return direct == null ? DeathDamageType.GENERIC : DeathDamageType.GENERIC_KILL;
    }

    private static DeathDamageType projectileType(Entity direct) {
        if (direct instanceof EntityFireball) {
            return DeathDamageType.FIREBALL;
        }
        if (direct instanceof EntityArrow) {
            return DeathDamageType.ARROW;
        }
        return DeathDamageType.THROWN;
    }

    private static boolean isProjectile(Entity entity) {
        return entity instanceof EntityArrow
                || entity instanceof EntityFireball
                || entity instanceof EntitySnowball
                || entity instanceof EntityEgg
                || entity instanceof EntityFish;
    }

    private static Entity resolveCausingEntity(Entity directEntity, EntityDamageEvent event) {
        Entity shooter = resolveProjectileOwner(directEntity);
        if (shooter != null) {
            return shooter;
        }

        if (directEntity instanceof EntityTNTPrimed) {
            EntityTNTPrimed tnt = (EntityTNTPrimed) directEntity;
            return tnt.source;
        }

        if (event instanceof EntityDamageByEntityEvent) {
            org.bukkit.entity.Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
            if (damager instanceof Projectile) {
                org.bukkit.entity.LivingEntity projectileShooter = ((Projectile) damager).getShooter();
                if (projectileShooter instanceof CraftEntity) {
                    return ((CraftEntity) projectileShooter).getHandle();
                }
            } else if (damager instanceof CraftEntity) {
                return ((CraftEntity) damager).getHandle();
            }
        }

        return directEntity instanceof EntityLiving ? directEntity : null;
    }

    private static Entity resolveProjectileOwner(Entity entity) {
        if (entity instanceof EntityArrow) {
            return ((EntityArrow) entity).shooter;
        }
        if (entity instanceof EntityFireball) {
            return ((EntityFireball) entity).shooter;
        }
        if (entity instanceof EntitySnowball) {
            return ((EntitySnowball) entity).shooter;
        }
        if (entity instanceof EntityEgg) {
            return ((EntityEgg) entity).thrower;
        }
        if (entity instanceof EntityFish) {
            return ((EntityFish) entity).owner;
        }
        return null;
    }

    private static String resolveCausingName(Entity directEntity) {
        if (directEntity instanceof EntityTNTPrimed) {
            return ((EntityTNTPrimed) directEntity).sourceName;
        }
        return null;
    }
}
