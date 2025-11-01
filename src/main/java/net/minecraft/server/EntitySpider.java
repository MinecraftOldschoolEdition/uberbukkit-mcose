package net.minecraft.server;

// CraftBukkit start

import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.event.entity.EntityTargetEvent;
// CraftBukkit end

//uberbukkit start
import uk.betacraft.uberbukkit.UberbukkitConfig;
//uberbukkit end

public class EntitySpider extends EntityMonster {

    public EntitySpider(World world) {
        super(world);
        this.texture = "/mob/spider.png";
        this.b(1.4F, 0.9F);
        this.aE = 0.8F;
    }

    public double m() {
        return (double) this.width * 0.75D - 0.5D;
    }

    protected boolean n() {
        return UberbukkitConfig.getInstance().getBoolean("mechanics.spiders_trample_crops", false);
    }

    protected Entity findTarget() {
        boolean active = net.minecraft.server.registry.ScheduleRegistryBootstrap.isActiveFor(this.getClass(), this.world);
        if (!active) return null;
        net.minecraft.server.EntityHuman p = net.minecraft.server.registry.Sensors.findNearestPlayerNonCreative(this, 16.0D);
        if (p != null) {
            try { net.minecraft.server.registry.EntityMemory.set(this, new net.minecraft.server.util.ResourceLocation("minecraft","nearest_player"), p); } catch (Throwable ignored) {}
            boolean visible = net.minecraft.server.registry.Sensors.hasLineOfSight(this, p);
            if (visible) {
                try {
                    net.minecraft.server.registry.EntityMemory.set(this, new net.minecraft.server.util.ResourceLocation("minecraft","visible_target"), p);
                    net.minecraft.server.registry.EntityMemory.set(this, new net.minecraft.server.util.ResourceLocation("minecraft","last_seen_pos"), new double[]{p.locX, p.locY, p.locZ});
                } catch (Throwable ignored) {}
                return p;
            }
        }
        return null;
    }

    protected String g() {
        return "mob.spider";
    }

    protected String h() {
        return "mob.spider";
    }

    protected String i() {
        return "mob.spiderdeath";
    }

    protected void a(Entity entity, float f) {
        boolean passive = !net.minecraft.server.registry.ScheduleRegistryBootstrap.isActiveFor(this.getClass(), this.world);
        if (passive && this.random.nextInt(100) == 0) {
            // CraftBukkit start
            EntityTargetEvent event = new EntityTargetEvent(this.getBukkitEntity(), null, EntityTargetEvent.TargetReason.FORGOT_TARGET);
            this.world.getServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                if (event.getTarget() == null) {
                    this.target = null;
                } else {
                    this.target = ((CraftEntity) event.getTarget()).getHandle();
                }
                return;
            }
            // CraftBukkit end
        } else {
            if (f > 2.0F && f < 6.0F && this.random.nextInt(10) == 0) {
                if (this.onGround) {
                    double d0 = entity.locX - this.locX;
                    double d1 = entity.locZ - this.locZ;
                    float f2 = MathHelper.a(d0 * d0 + d1 * d1);

                    this.motX = d0 / (double) f2 * 0.5D * 0.800000011920929D + this.motX * 0.20000000298023224D;
                    this.motZ = d1 / (double) f2 * 0.5D * 0.800000011920929D + this.motZ * 0.20000000298023224D;
                    this.motY = 0.4000000059604645D;
                }
            } else {
                super.a(entity, f);
            }
        }
    }

    public void b(NBTTagCompound nbttagcompound) {
        super.b(nbttagcompound);
    }

    public void a(NBTTagCompound nbttagcompound) {
        super.a(nbttagcompound);
    }

    protected int j() {
        return Item.STRING.id;
    }

    public boolean p() {
        return UberbukkitConfig.getInstance().getBoolean("mechanics.spiders_climb_walls", true) ? this.positionChanged : super.p();
    }
}
