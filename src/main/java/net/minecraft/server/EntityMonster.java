package net.minecraft.server;

// CraftBukkit start

import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
// CraftBukkit end

public class EntityMonster extends EntityCreature implements IMonster {

    protected int damage = 2;

    public EntityMonster(World world) {
        super(world);
        this.health = 20;
    }

    public void v() {
        float f = this.c(1.0F);

        if (f > 0.5F) {
            this.ay += 2;
        }

        super.v();
    }

    public void m_() {
        super.m_();
        if (!this.world.isStatic && this.world.spawnMonsters == 0) {
            this.die();
        }
    }

    protected Entity findTarget() {
        // Honor schedules for entity activity windows
        if (!net.minecraft.server.registry.ScheduleRegistryBootstrap.isActiveFor(this.getClass(), this.world)) {
            return null;
        }
        // Use sensor pipeline to select target
        net.minecraft.server.EntityHuman entityhuman = net.minecraft.server.registry.Sensors.findNearestPlayerNonCreative(this, 16.0D);
        if (entityhuman != null) {
            // Write memory: nearest player
            try {
                net.minecraft.server.registry.EntityMemory.set(this, new net.minecraft.server.util.ResourceLocation("minecraft","nearest_player"), entityhuman);
            } catch (Throwable ignored) {}
            boolean visible = net.minecraft.server.registry.Sensors.hasLineOfSight(this, entityhuman);
            if (visible) {
                try {
                    net.minecraft.server.registry.EntityMemory.set(this, new net.minecraft.server.util.ResourceLocation("minecraft","visible_target"), entityhuman);
                    net.minecraft.server.registry.EntityMemory.set(this, new net.minecraft.server.util.ResourceLocation("minecraft","last_seen_pos"), new double[]{entityhuman.locX, entityhuman.locY, entityhuman.locZ});
                } catch (Throwable ignored) {}
                return entityhuman;
            }
        }
        return null;
    }

    public boolean damageEntity(Entity entity, int i) {
        if (super.damageEntity(entity, i)) {
            // Remember who hurt us
            try {
                net.minecraft.server.registry.EntityMemory.set(this, new net.minecraft.server.util.ResourceLocation("minecraft","hurt_by"), entity);
            } catch (Throwable ignored) {}
            if (this.passenger != entity && this.vehicle != entity) {
                if (entity != this) {
                    // Do not retaliate against creative players - they can punch but mobs won't fight back
                    if (entity instanceof EntityHuman
                            && (((EntityHuman) entity).gameMode == 1 || ((EntityHuman) entity).isSpectator())) {
                        // Take damage but don't target the player
                        return true;
                    }
                    // CraftBukkit start
                    org.bukkit.entity.Entity bukkitTarget = entity == null ? null : entity.getBukkitEntity();

                    EntityTargetEvent event = new EntityTargetEvent(this.getBukkitEntity(), bukkitTarget, EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY);
                    this.world.getServer().getPluginManager().callEvent(event);

                    if (!event.isCancelled()) {
                        if (event.getTarget() == null) {
                            this.target = null;
                        } else {
                            this.target = ((CraftEntity) event.getTarget()).getHandle();
                        }
                    }
                    // CraftBukkit end
                }

                return true;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    protected void a(Entity entity, float f) {
        if (this.attackTicks <= 0 && f < 2.0F && entity.boundingBox.e > this.boundingBox.b && entity.boundingBox.b < this.boundingBox.e) {
            try { net.minecraft.server.registry.EntityMemory.set(this, new net.minecraft.server.util.ResourceLocation("minecraft","attack_target"), entity); } catch (Throwable ignored) {}
            this.attackTicks = 20;
            // CraftBukkit start - this is still duplicated here and EntityHuman because it's possible for lastDamage EntityMonster
            // to damage another EntityMonster, and we want to catch those events.
            // This does not fire events for slime attacks, av they're not lastDamage EntityMonster.
            if (entity instanceof EntityLiving && !(entity instanceof EntityHuman)) {
                org.bukkit.entity.Entity damagee = (entity == null) ? null : entity.getBukkitEntity();

                EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(this.getBukkitEntity(), damagee, EntityDamageEvent.DamageCause.ENTITY_ATTACK, this.damage);
                this.world.getServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    entity.damageEntity(this, event.getDamage());
                }
                return;
            }
            // CraftBukkit end

            entity.damageEntity(this, this.damage);
        }
    }

    protected float a(int i, int j, int k) {
        return 0.5F - this.world.n(i, j, k);
    }

    public void b(NBTTagCompound nbttagcompound) {
        super.b(nbttagcompound);
    }

    public void a(NBTTagCompound nbttagcompound) {
        super.a(nbttagcompound);
    }

    public boolean d() {
        int i = MathHelper.floor(this.locX);
        int j = MathHelper.floor(this.boundingBox.b);
        int k = MathHelper.floor(this.locZ);

        if (this.world.a(EnumSkyBlock.SKY, i, j, k) > this.random.nextInt(32)) {
            return false;
        } else {
            int l = this.world.getLightLevel(i, j, k);

            if (this.world.u()) {
                int i1 = this.world.f;

                this.world.f = 10;
                l = this.world.getLightLevel(i, j, k);
                this.world.f = i1;
            }

            return l <= this.random.nextInt(8) && super.d();
        }
    }
}
