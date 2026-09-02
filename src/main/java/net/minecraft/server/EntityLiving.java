package net.minecraft.server;

import net.minecraft.server.registry.BlockTags;

import com.legacyminecraft.poseidon.PoseidonConfig;
import org.bukkit.craftbukkit.TrigMath;
import org.bukkit.craftbukkit.entity.CraftEntity;

import net.minecraft.server.event.EventBus;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;

import net.minecraft.server.registry.EntityLootTable;
import net.minecraft.server.registry.LootTables;
import uk.betacraft.uberbukkit.Uberbukkit;
import uk.betacraft.uberbukkit.UberbukkitConfig;

import java.util.List;

// CraftBukkit start
// CraftBukkit end

public abstract class EntityLiving extends Entity {

    private static final String MOB_HARD_DESPAWN_RANGE_CONFIG = "world.settings.mob-despawn-range.hard";
    private static final String MOB_SOFT_DESPAWN_RANGE_CONFIG = "world.settings.mob-despawn-range.soft";
    private static final String MAX_ENTITY_COLLISIONS_CONFIG = "world.settings.max-entity-collisions";
    private static final int VANILLA_HARD_DESPAWN_RANGE = 128;
    private static final int VANILLA_SOFT_DESPAWN_RANGE = 32;
    private static final int DEFAULT_MAX_ENTITY_COLLISIONS = 8;
    private static boolean mobDespawnRangesLoaded = false;
    private static boolean maxEntityCollisionsLoaded = false;
    private static double mobHardDespawnRangeSquared = VANILLA_HARD_DESPAWN_RANGE * VANILLA_HARD_DESPAWN_RANGE;
    private static double mobSoftDespawnRangeSquared = VANILLA_SOFT_DESPAWN_RANGE * VANILLA_SOFT_DESPAWN_RANGE;
    private static int maxEntityCollisions = DEFAULT_MAX_ENTITY_COLLISIONS;

    private final CombatTracker combatTracker = new CombatTracker(this);
    public int maxNoDamageTicks = 20;
    public float I;
    public float J;
    public float K = 0.0F;
    public float L = 0.0F;
    protected float M;
    protected float N;
    protected float O;
    protected float P;
    protected boolean Q = true;
    protected String texture = "/mob/char.png";
    protected boolean S = true;
    protected float T = 0.0F;
    protected String U = null;
    protected float V = 1.0F;
    protected int W = 0;
    protected float X = 0.0F;
    public boolean Y = false;
    public float Z;
    public float aa;
    public int health = 10;
    public int ac;
    private int a;
    public int hurtTicks;
    public int ae;
    public float af = 0.0F;
    public int deathTicks = 0;
    public int attackTicks = 0;
    public float ai;
    public float aj;
    protected boolean ak = false;
    public int al = -1;
    public float am = (float) (Math.random() * 0.8999999761581421D + 0.10000000149011612D);
    public float an;
    public float ao;
    public float ap;
    protected int aq;
    protected double ar;
    protected double as;
    protected double at;
    protected double au;
    protected double av;
    float aw = 0.0F;
    public int lastDamage = 0; // CraftBukkit - protected -> public
    protected int ay = 0;
    protected float az;
    protected float aA;
    protected float aB;
    protected boolean aC = false;
    protected float aD = 0.0F;
    protected float aE = 0.7F;
    private Entity b;
    protected int aF = 0;

    public EntityLiving(World world) {
        super(world);
        this.aI = true;
        this.J = (float) (Math.random() + 1.0D) * 0.01F;
        this.setPosition(this.locX, this.locY, this.locZ);
        this.I = (float) Math.random() * 12398.0F;
        this.yaw = (float) (Math.random() * 3.1415927410125732D * 2.0D);
        this.bs = 0.5F;
    }

    protected void b() {
    }

    // hasLineOfSight
    public boolean e(Entity entity) {
        return this.world.a(Vec3D.create(this.locX, this.locY + (double) this.t(), this.locZ), Vec3D.create(entity.locX, entity.locY + (double) entity.t(), entity.locZ)) == null;
    }

    public boolean l_() {
        return !this.dead;
    }

    public boolean d_() {
        return !this.dead;
    }

    public float t() {
        return this.width * 0.85F;
    }

    public int e() {
        return 80;
    }

    public void Q() {
        String s = this.g();

        if (s != null) {
            this.world.makeSound(this, s, this.k(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        }
    }

    public void R() {
        this.Z = this.aa;
        super.R();
        this.combatTracker.recheckStatus();
        if (this.random.nextInt(1000) < this.a++) {
            this.a = -this.e();
            this.Q();
        }

        if (this.T() && this.K()) {
            // CraftBukkit start
            EntityDamageEvent event = new EntityDamageEvent(this.getBukkitEntity(), EntityDamageEvent.DamageCause.SUFFOCATION, 1);
            this.world.getServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                this.damageEntity((Entity) null, event.getDamage());
            }
            // CraftBukkit end
        }

        if (this.fireProof || this.world.isStatic) {
            this.fireTicks = 0;
        }

        int i;

        if (this.T() && this.a(Material.WATER) && !this.b_()) {
            --this.airTicks;
            if (this.airTicks == -20) {
                this.airTicks = 0;

                for (i = 0; i < 8; ++i) {
                    float f = this.random.nextFloat() - this.random.nextFloat();
                    float f1 = this.random.nextFloat() - this.random.nextFloat();
                    float f2 = this.random.nextFloat() - this.random.nextFloat();

                    this.world.a("bubble", this.locX + (double) f, this.locY + (double) f1, this.locZ + (double) f2, this.motX, this.motY, this.motZ);
                }

                // CraftBukkit start
                EntityDamageEvent event = new EntityDamageEvent(this.getBukkitEntity(), EntityDamageEvent.DamageCause.DROWNING, 2);
                this.world.getServer().getPluginManager().callEvent(event);

                if (!event.isCancelled() && event.getDamage() != 0) {
                    boolean vc = this.velocityChanged;
                    this.damageEntity((Entity) null, event.getDamage());
                    if (PoseidonConfig.getInstance().getBoolean("settings.fix-drowning-push-down.enabled", true))
                        this.velocityChanged = vc;
                }
                // CraftBukkit end
            }

            this.fireTicks = 0;
        } else {
            this.airTicks = this.maxAirTicks;
        }

        this.ai = this.aj;
        if (this.attackTicks > 0) {
            --this.attackTicks;
        }

        if (this.hurtTicks > 0) {
            --this.hurtTicks;
        }

        if (this.noDamageTicks > 0) {
            --this.noDamageTicks;
        }

        if (this.health <= 0) {
            ++this.deathTicks;
            if (this.deathTicks > 20) {
                this.X();
                this.die();

                for (i = 0; i < 20; ++i) {
                    double d0 = this.random.nextGaussian() * 0.02D;
                    double d1 = this.random.nextGaussian() * 0.02D;
                    double d2 = this.random.nextGaussian() * 0.02D;

                    this.world.a("explode", this.locX + (double) (this.random.nextFloat() * this.length * 2.0F) - (double) this.length, this.locY + (double) (this.random.nextFloat() * this.width), this.locZ + (double) (this.random.nextFloat() * this.length * 2.0F) - (double) this.length, d0, d1, d2);
                }
            }
        }

        this.P = this.O;
        this.L = this.K;
        this.lastYaw = this.yaw;
        this.lastPitch = this.pitch;
    }

    public void S() {
        for (int i = 0; i < 20; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;
            double d3 = 10.0D;

            this.world.a("explode", this.locX + (double) (this.random.nextFloat() * this.length * 2.0F) - (double) this.length - d0 * d3, this.locY + (double) (this.random.nextFloat() * this.width) - d1 * d3, this.locZ + (double) (this.random.nextFloat() * this.length * 2.0F) - (double) this.length - d2 * d3, d0, d1, d2);
        }
    }

    public void E() {
        super.E();
        this.M = this.N;
        this.N = 0.0F;
    }

    public void m_() {
        super.m_();
        this.v();
        double d0 = this.locX - this.lastX;
        double d1 = this.locZ - this.lastZ;
        float f = MathHelper.a(d0 * d0 + d1 * d1);
        float f1 = this.K;
        float f2 = 0.0F;

        this.M = this.N;
        float f3 = 0.0F;

        if (f > 0.05F) {
            f3 = 1.0F;
            f2 = f * 3.0F;
            // CraftBukkit - Math -> TrigMath
            f1 = (float) TrigMath.atan2(d1, d0) * 180.0F / 3.1415927F - 90.0F;
        }

        if (this.aa > 0.0F) {
            f1 = this.yaw;
        }

        if (!this.onGround) {
            f3 = 0.0F;
        }

        this.N += (f3 - this.N) * 0.3F;

        float f4 = MathHelper.wrapDegrees(f1 - this.K);

        this.K += f4 * 0.3F;

        float f5 = MathHelper.wrapDegrees(this.yaw - this.K);

        boolean flag = f5 < -90.0F || f5 >= 90.0F;

        if (f5 < -75.0F) {
            f5 = -75.0F;
        }

        if (f5 >= 75.0F) {
            f5 = 75.0F;
        }

        this.K = this.yaw - f5;
        if (f5 * f5 > 2500.0F) {
            this.K += f5 * 0.2F;
        }

        if (flag) {
            f2 *= -1.0F;
        }

        this.lastYaw = this.yaw - MathHelper.wrapDegrees(this.yaw - this.lastYaw);
        this.L = this.K - MathHelper.wrapDegrees(this.K - this.L);
        this.lastPitch = this.pitch - MathHelper.wrapDegrees(this.pitch - this.lastPitch);

        this.O += f2;
    }

    protected void b(float f, float f1) {
        super.b(f, f1);
    }

    // CraftBukkit start - delegate so we can handle providing a reason for health being regained
    public void b(int i) {
        b(i, RegainReason.CUSTOM);
    }

    public void b(int i, RegainReason regainReason) {
        if (this.health > 0) {
            EntityRegainHealthEvent event = new EntityRegainHealthEvent(this.getBukkitEntity(), i, regainReason);
            this.world.getServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                this.health += event.getAmount();
            }
            // CraftBukkit end
            if (this.health > 20) {
                this.health = 20;
            }

            this.noDamageTicks = this.maxNoDamageTicks / 2;
        }
    }

    public boolean damageEntity(Entity entity, int i) {
        if (this.world.isStatic) {
            return false;
        } else {
            this.ay = 0;
            if (this.health <= 0) {
                return false;
            } else {
                // UberBukkit - Track last player attacker for death messages
                if (entity instanceof EntityPlayer) {
                    this.lastPlayerAttacker = (EntityPlayer) entity;
                    this.lastPlayerAttackerTime = this.world.getTime();
                    
                    // MCOSE: Overkill achievement - deal 9 hearts (18 damage) in a single hit
                    if (i >= 18) {
                        ((EntityPlayer) entity).a(AchievementList.overkill, 1);
                    }
                }
                
                this.ao = 1.5F;
                boolean flag = true;
                int noDamageWindow = this.maxNoDamageTicks;

                // Match multiplayer melee pacing to the client hold/click cadence (5 ticks).
                if (entity instanceof EntityPlayer) {
                    noDamageWindow = Math.min(noDamageWindow, 10);
                }

                int actualDamage;
                if ((float) this.noDamageTicks > (float) noDamageWindow / 2.0F) {
                    if (i <= this.lastDamage) {
                        return false;
                    }

                    actualDamage = i - this.lastDamage;
                    int healthBefore = this.health;
                    this.c(actualDamage);
                    this.recordCombatDamage(entity, healthBefore, actualDamage);
                    this.lastDamage = i;
                    flag = false;
                } else {
                    this.lastDamage = i;
                    this.ac = this.health;
                    this.noDamageTicks = noDamageWindow;
                    actualDamage = i;
                    int healthBefore = this.health;
                    this.c(i);
                    this.recordCombatDamage(entity, healthBefore, actualDamage);
                    this.hurtTicks = this.ae = 10;
                }

                this.af = 0.0F;
                if (flag) {
                    this.world.a(this, (byte) 2);
                    this.af();
                    if (entity != null) {
                        this.airBorne = true;
                        double d0 = entity.locX - this.locX;

                        double d1;

                        for (d1 = entity.locZ - this.locZ; d0 * d0 + d1 * d1 < 1.0E-4D; d1 = (Math.random() - Math.random()) * 0.01D) {
                            d0 = (Math.random() - Math.random()) * 0.01D;
                        }

                        this.af = (float) (Math.atan2(d1, d0) * 180.0D / 3.1415927410125732D) - this.yaw;
                        this.a(entity, i, d0, d1);
                    } else {
                        this.af = (float) ((int) (Math.random() * 2.0D) * 180);
                    }
                }

                if (this.health <= 0) {
                    if (flag) {
                        this.world.makeSound(this, this.i(), this.k(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                    }

                    this.die(entity);
                } else if (flag) {
                    this.world.makeSound(this, this.h(), this.k(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                }

                return true;
            }
        }
    }

    protected void c(int i) {
        this.health -= i;
    }

    public CombatTracker getCombatTracker() {
        return this.combatTracker;
    }

    private void recordCombatDamage(Entity source, int healthBefore, int attemptedDamage) {
        int appliedDamage = healthBefore - this.health;
        if (appliedDamage > 0) {
            this.combatTracker.recordDamage(DeathDamageSource.forDamage(this, source), appliedDamage);
        } else if (attemptedDamage > 0 && !(this instanceof EntityHuman)) {
            this.combatTracker.recordDamage(DeathDamageSource.forDamage(this, source), attemptedDamage);
        }
    }

    protected float k() {
        return 1.0F;
    }

    protected String g() {
        return null;
    }

    protected String h() {
        return "random.hurt";
    }

    protected String i() {
        return "random.hurt";
    }

    public void a(Entity entity, int i, double d0, double d1) {
        float f = MathHelper.a(d0 * d0 + d1 * d1);
        float f1 = 0.4F;

        this.motX /= 2.0D;
        this.motY /= 2.0D;
        this.motZ /= 2.0D;
        this.motX -= d0 / (double) f * (double) f1;
        this.motY += 0.4000000059604645D;
        this.motZ -= d1 / (double) f * (double) f1;
        if (this.motY > 0.4000000059604645D) {
            this.motY = 0.4000000059604645D;
        }
    }

    public void die(Entity entity) {
        if (!this.world.isStatic && !(this instanceof EntityPlayer)) {
            this.world.getServer().getServer().modernScoreboardManager.recordMobKill(entity);
        }
        if (this.W >= 0 && entity != null) {
            entity.c(this, this.W);
        }

        if (entity != null) {
            entity.a(this);
        }

        if (!this.world.isStatic) {
            EventBus.global().publish(new net.minecraft.server.event.events.EntityDeathEvent(this, entity, this.world));
        }

        this.ak = true;
        if (!this.world.isStatic) {
            this.q();
        }

        this.world.a(this, (byte) 3);
    }

    protected void q() {
        // CraftBukkit start - whole method
        List<org.bukkit.inventory.ItemStack> loot = this.createDeathEventDrops();
        EntityDeathEvent event = this.callBukkitDeathEvent(loot);

        for (org.bukkit.inventory.ItemStack stack : event.getDrops()) {
            this.spawnDeathDropAtEntityPosition(stack);
        }
        // CraftBukkit end
    }

    protected EntityDeathEvent callBukkitDeathEvent(
            List<org.bukkit.inventory.ItemStack> loot) {
        CraftEntity entity = (CraftEntity) this.getBukkitEntity();
        EntityDeathEvent event = new EntityDeathEvent(entity, loot);
        this.world.getServer().getPluginManager().callEvent(event);
        return event;
    }

    /**
     * Builds the single mutable Bukkit death-event list. Entity-table counts
     * are combined back into the same one-stack-per-legacy-entry shape that
     * plugins received before the data-driven cutover.
     */
    protected List<org.bukkit.inventory.ItemStack> createDeathEventDrops() {
        EntityLootTable table = LootTables.getEntityLootTable(this);
        if (table != null) {
            return this.combineEntityLootForDeathEvent(
                    this.generateEntityDeathLoot(table));
        }

        List<org.bukkit.inventory.ItemStack> loot =
                new java.util.ArrayList<org.bukkit.inventory.ItemStack>();
        int itemId = this.j();
        // Preserve UberBukkit's existing q() quirk: every no-table living
        // entity advances nextInt(3), even when j() reports no drop.
        int count = this.random.nextInt(3);
        if (itemId > 0 && count > 0) {
            loot.add(new org.bukkit.inventory.ItemStack(itemId, count));
        }
        return loot;
    }

    /** Server-only hook for legacy RNG overlays such as non-small slimes. */
    protected List<ItemStack> generateEntityDeathLoot(EntityLootTable table) {
        return table.generateEntityLoot(this, this.random);
    }

    private List<org.bukkit.inventory.ItemStack> combineEntityLootForDeathEvent(
            List<ItemStack> generated) {
        List<org.bukkit.inventory.ItemStack> combined =
                new java.util.ArrayList<org.bukkit.inventory.ItemStack>();
        for (int i = 0; i < generated.size(); i++) {
            ItemStack stack = generated.get(i);
            if (stack == null || stack.count <= 0) continue;

            short metadata = (short)stack.getData();
            org.bukkit.inventory.ItemStack previous = combined.isEmpty()
                    ? null
                    : combined.get(combined.size() - 1);
            if (previous != null
                    && previous.getTypeId() == stack.id
                    && previous.getDurability() == metadata) {
                previous.setAmount(previous.getAmount() + stack.count);
                continue;
            }

            if (stack.id == Block.WOOL.id) {
                combined.add(new org.bukkit.inventory.ItemStack(
                        stack.id, stack.count, (short)0, Byte.valueOf((byte)metadata)));
            } else if (metadata != 0) {
                combined.add(new org.bukkit.inventory.ItemStack(
                        stack.id, stack.count, metadata));
            } else {
                combined.add(new org.bukkit.inventory.ItemStack(stack.id, stack.count));
            }
        }
        return combined;
    }

    /**
     * Preserves the Bukkit death-event list while restoring Beta 1.7.3's
     * entity-drop contract: the EntityItem starts at the dead entity's exact
     * X/Z and requested Y offset. CraftWorld#dropItemNaturally adds a second
     * random position offset and can cross into an unloaded neighboring chunk.
     */
    protected EntityItem spawnDeathDropAtEntityPosition(org.bukkit.inventory.ItemStack stack) {
        if (stack == null
                || !Uberbukkit.getProtocolHandler().canReceiveBlockItem(stack.getTypeId())) {
            return null;
        }
        return this.a(new ItemStack(stack.getTypeId(), stack.getAmount(), stack.getDurability()), 0.0F);
    }

    protected int j() {
        return 0;
    }

    protected void a(float f) {
        super.a(f);
        int i = (int) Math.ceil((double) (f - 3.0F));

        if (i > 0) {
            // CraftBukkit start
            EntityDamageEvent event = new EntityDamageEvent(this.getBukkitEntity(), EntityDamageEvent.DamageCause.FALL, i);
            this.world.getServer().getPluginManager().callEvent(event);

            if (!event.isCancelled() && event.getDamage() != 0) {
                this.damageEntity((Entity) null, event.getDamage());
            }
            // CraftBukkit end

            int j = this.world.getTypeId(MathHelper.floor(this.locX), MathHelper.floor(this.locY - 0.20000000298023224D - (double) this.height), MathHelper.floor(this.locZ));

            if (j > 0) {
                StepSound stepsound = Block.byId[j].stepSound;

                float stepVolume = stepsound.getVolume1() * 0.5F;
                float stepPitch = stepsound.getVolume2() * 0.75F;
                if (this instanceof EntityHuman) {
                    this.world.makeSound((EntityHuman) this, this.locX, this.locY - (double) this.height, this.locZ, stepsound.getName(), stepVolume, stepPitch);
                } else {
                    this.world.makeSound(this, stepsound.getName(), stepVolume, stepPitch);
                }
            }
        }
    }

    public void a(float f, float f1) {
        double d0;

        // Creative flying players should not be slowed or pushed by fluids
        boolean bypassFluids = false;
        if (this instanceof EntityHuman) {
            EntityHuman ph = (EntityHuman) this;
            if ((ph.gameMode == 1 || ph.isSpectator()) && !this.onGround) {
                bypassFluids = true;
            }
        }

        if (this.ad() && !bypassFluids) {
            d0 = this.locY;
            this.a(f, f1, 0.02F);
            this.move(this.motX, this.motY, this.motZ);
            this.motX *= 0.800000011920929D;
            this.motY *= 0.800000011920929D;
            this.motZ *= 0.800000011920929D;
            this.motY -= 0.02D;
            if (this.positionChanged && this.d(this.motX, this.motY + 0.6000000238418579D - this.locY + d0, this.motZ)) {
                this.motY = 0.30000001192092896D;
            }
        } else if (this.ae() && !bypassFluids) {
            d0 = this.locY;
            this.a(f, f1, 0.02F);
            this.move(this.motX, this.motY, this.motZ);
            this.motX *= 0.5D;
            this.motY *= 0.5D;
            this.motZ *= 0.5D;
            this.motY -= 0.02D;
            if (this.positionChanged && this.d(this.motX, this.motY + 0.6000000238418579D - this.locY + d0, this.motZ)) {
                this.motY = 0.30000001192092896D;
            }
        } else {
            float f2 = 0.91F;

            if (this.onGround) {
                f2 = 0.54600006F;
                int i = this.world.getTypeId(MathHelper.floor(this.locX), MathHelper.floor(this.boundingBox.b) - 1, MathHelper.floor(this.locZ));

                if (i > 0) {
                    f2 = Block.byId[i].frictionFactor * 0.91F;
                }
            }

            float f3 = 0.16277136F / (f2 * f2 * f2);

            this.a(f, f1, this.onGround ? 0.1F * f3 : 0.02F);
            f2 = 0.91F;
            if (this.onGround) {
                f2 = 0.54600006F;
                int j = this.world.getTypeId(MathHelper.floor(this.locX), MathHelper.floor(this.boundingBox.b) - 1, MathHelper.floor(this.locZ));

                if (j > 0) {
                    f2 = Block.byId[j].frictionFactor * 0.91F;
                }
            }

            if (this.p()) {
                float f4 = 0.15F;

                if (this.motX < (double) (-f4)) {
                    this.motX = (double) (-f4);
                }

                if (this.motX > (double) f4) {
                    this.motX = (double) f4;
                }

                if (this.motZ < (double) (-f4)) {
                    this.motZ = (double) (-f4);
                }

                if (this.motZ > (double) f4) {
                    this.motZ = (double) f4;
                }

                this.fallDistance = 0.0F;
                if (this.motY < -0.15D) {
                    this.motY = -0.15D;
                }

                if (this.isSneaking() && this.motY < 0.0D) {
                    this.motY = 0.0D;
                }

                // (Alpha parity does not add forward-based climb impulse here)
            }

            this.move(this.motX, this.motY, this.motZ);
            if (this.p()) {
                // Standard ladder climb impulse when colliding horizontally
                if (this.positionChanged) {
                    this.motY = 0.2D;
                } else {
                    // Gap support: if only head-level ladder is present and the player is pushing forward, climb
                    int ci = MathHelper.floor(this.locX);
                    int cj = MathHelper.floor(this.boundingBox.b);
                    int ck = MathHelper.floor(this.locZ);
                    boolean feetLadder = isClimbableBlock(
                            this.world.getTypeId(ci, cj, ck));
                    boolean headLadder = isClimbableBlock(
                            this.world.getTypeId(ci, cj + 1, ck));
                    if (!feetLadder && headLadder && this.aA > 0.0F) {
                        this.motY = 0.2D;
                    }
                }
            }

            this.motY -= 0.08D;
            this.motY *= 0.9800000190734863D;
            this.motX *= (double) f2;
            this.motZ *= (double) f2;
        }

        this.an = this.ao;
        d0 = this.locX - this.lastX;
        double d1 = this.locZ - this.lastZ;
        float f5 = MathHelper.a(d0 * d0 + d1 * d1) * 4.0F;

        if (f5 > 1.0F) {
            f5 = 1.0F;
        }

        this.ao += (f5 - this.ao) * 0.4F;
        this.ap += this.ao;
    }

    public boolean p() {
        int i = MathHelper.floor(this.locX);
        int j = MathHelper.floor(this.boundingBox.b);
        int k = MathHelper.floor(this.locZ);

        if (isClimbableBlock(this.world.getTypeId(i, j, k))) return true;
        if (isClimbableBlock(this.world.getTypeId(i, j + 1, k))) return true;
        return false;
    }

    private static boolean isClimbableBlock(int blockId) {
        Block block = blockId > 0 && blockId < Block.byId.length
                ? Block.byId[blockId] : null;
        return block != null && BlockTags.is(block, BlockTags.CLIMBABLE);
    }

    public void b(NBTTagCompound nbttagcompound) {
        nbttagcompound.a("Health", (short) this.health);
        nbttagcompound.a("HurtTime", (short) this.hurtTicks);
        nbttagcompound.a("DeathTime", (short) this.deathTicks);
        nbttagcompound.a("AttackTime", (short) this.attackTicks);
        nbttagcompound.a("EntityAge", this.ay);
    }

    public void a(NBTTagCompound nbttagcompound) {
        this.health = nbttagcompound.d("Health");
        if (!nbttagcompound.hasKey("Health")) {
            this.health = 10;
        }

        this.hurtTicks = nbttagcompound.d("HurtTime");
        this.deathTicks = nbttagcompound.d("DeathTime");
        this.attackTicks = nbttagcompound.d("AttackTime");
        if (nbttagcompound.hasKey("EntityAge")) {
            this.ay = nbttagcompound.e("EntityAge");
        }
    }

    public boolean T() {
        return !this.dead && this.health > 0;
    }

    public boolean b_() {
        return false;
    }

    public void v() {
        if (this.aq > 0) {
            double d0 = this.locX + (this.ar - this.locX) / (double) this.aq;
            double d1 = this.locY + (this.as - this.locY) / (double) this.aq;
            double d2 = this.locZ + (this.at - this.locZ) / (double) this.aq;

            double d3;

            for (d3 = this.au - (double) this.yaw; d3 < -180.0D; d3 += 360.0D) {
                ;
            }

            while (d3 >= 180.0D) {
                d3 -= 360.0D;
            }

            this.yaw = (float) ((double) this.yaw + d3 / (double) this.aq);
            this.pitch = (float) ((double) this.pitch + (this.av - (double) this.pitch) / (double) this.aq);
            --this.aq;
            this.setPosition(d0, d1, d2);
            this.c(this.yaw, this.pitch);
            List list = this.world.getEntities(this, this.boundingBox.shrink(0.03125D, 0.0D, 0.03125D));

            if (list.size() > 0) {
                double d4 = 0.0D;

                for (int i = 0; i < list.size(); ++i) {
                    AxisAlignedBB axisalignedbb = (AxisAlignedBB) list.get(i);

                    if (axisalignedbb.e > d4) {
                        d4 = axisalignedbb.e;
                    }
                }

                d1 += d4 - this.boundingBox.b;
                this.setPosition(d0, d1, d2);
            }
        }

        if (this.D()) {
            this.aC = false;
            this.az = 0.0F;
            this.aA = 0.0F;
            this.aB = 0.0F;
        } else if (!this.Y) {
            this.c_();
        }

        boolean flag = this.ad();
        boolean flag1 = this.ae();

        if (this.aC) {
            if (flag) {
                this.motY += 0.03999999910593033D;
            } else if (flag1) {
                this.motY += 0.03999999910593033D;
            } else if (this.onGround) {
                this.O();
            }
        }

        this.az *= 0.98F;
        this.aA *= 0.98F;
        this.aB *= 0.9F;
        this.a(this.az, this.aA);
        if (this instanceof EntityHuman && ((EntityHuman) this).isSpectator()) {
            return;
        }
        int collisionLimit = getMaxEntityCollisions();
        this.numCollisions = Math.max(0, this.numCollisions - collisionLimit);
        if (collisionLimit <= 0 || this.numCollisions >= collisionLimit) {
            return;
        }

        List list1 = this.world.b((Entity) this, this.boundingBox.b(0.20000000298023224D, 0.0D, 0.20000000298023224D));

        if (list1 != null && list1.size() > 0) {
            for (int j = 0; j < list1.size() && this.numCollisions < collisionLimit; ++j) {
                Entity entity = (Entity) list1.get(j);

                if (entity.d_()) {
                    ++entity.numCollisions;
                    ++this.numCollisions;
                    entity.collide(this);
                }
            }
        }
    }

    protected boolean D() {
        return this.health <= 0;
    }

    protected void O() {
        this.motY = 0.41999998688697815D;
        this.airBorne = true;
    }

    protected boolean h_() {
        String customName = this.getCustomName();
        return customName == null || customName.length() == 0;
    }

    protected void U() {
        EntityHuman entityhuman = this.world.findNearbyPlayer(this, -1.0D);

        if (this.h_() && entityhuman != null) {
            loadMobDespawnRanges();
            double d0 = entityhuman.locX - this.locX;
            double d1 = entityhuman.locY - this.locY;
            double d2 = entityhuman.locZ - this.locZ;
            double d3 = d0 * d0 + d1 * d1 + d2 * d2;

            if (d3 > mobHardDespawnRangeSquared) {
                this.die();
            }

            if (this.ay > 600 && this.random.nextInt(800) == 0) {
                if (d3 < mobSoftDespawnRangeSquared) {
                    this.ay = 0;
                } else {
                    this.die();
                }
            }
        }
    }

    private static void loadMobDespawnRanges() {
        if (mobDespawnRangesLoaded) {
            return;
        }

        PoseidonConfig config = PoseidonConfig.getInstance();
        int hardRange = getConfigInt(config, MOB_HARD_DESPAWN_RANGE_CONFIG, VANILLA_HARD_DESPAWN_RANGE, 1, 4096);
        int softRange = getConfigInt(config, MOB_SOFT_DESPAWN_RANGE_CONFIG, VANILLA_SOFT_DESPAWN_RANGE, 1, 4096);

        if (hardRange < softRange) {
            hardRange = softRange;
        }

        mobHardDespawnRangeSquared = squareAsDouble(hardRange);
        mobSoftDespawnRangeSquared = squareAsDouble(softRange);
        mobDespawnRangesLoaded = true;
    }

    private static int getMaxEntityCollisions() {
        if (!maxEntityCollisionsLoaded) {
            maxEntityCollisions = getConfigInt(PoseidonConfig.getInstance(), MAX_ENTITY_COLLISIONS_CONFIG, DEFAULT_MAX_ENTITY_COLLISIONS, 0, 1024);
            maxEntityCollisionsLoaded = true;
        }
        return maxEntityCollisions;
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

    private static double squareAsDouble(int value) {
        return (double) value * (double) value;
    }

    protected void c_() {
        ++this.ay;
        EntityHuman entityhuman = this.world.findNearbyPlayer(this, -1.0D);

        this.U();
        this.az = 0.0F;
        this.aA = 0.0F;
        float f = 8.0F;

        if (this.random.nextFloat() < 0.02F) {
            entityhuman = this.world.findNearbyPlayer(this, (double) f);
            if (entityhuman != null) {
                this.b = entityhuman;
                this.aF = 10 + this.random.nextInt(20);
            } else {
                this.aB = (this.random.nextFloat() - 0.5F) * 20.0F;
            }
        }

        if (this.b != null) {
            this.a(this.b, 10.0F, (float) this.u());
            if (this.aF-- <= 0 || this.b.dead || this.b.g(this) > (double) (f * f)) {
                this.b = null;
            }
        } else {
            if (this.random.nextFloat() < 0.05F) {
                this.aB = (this.random.nextFloat() - 0.5F) * 20.0F;
            }

            this.yaw += this.aB;
            this.pitch = this.aD;
        }

        boolean flag = this.ad();
        boolean flag1 = this.ae();

        if (flag || flag1) {
            this.aC = this.random.nextFloat() < 0.8F;
        }
    }

    protected int u() {
        return 40;
    }

    public void a(Entity entity, float f, float f1) {
        double d0 = entity.locX - this.locX;
        double d1 = entity.locZ - this.locZ;
        double d2;

        if (entity instanceof EntityLiving) {
            EntityLiving entityliving = (EntityLiving) entity;

            d2 = this.locY + (double) this.t() - (entityliving.locY + (double) entityliving.t());
        } else {
            d2 = (entity.boundingBox.b + entity.boundingBox.e) / 2.0D - (this.locY + (double) this.t());
        }

        double d3 = (double) MathHelper.a(d0 * d0 + d1 * d1);
        float f2 = (float) (Math.atan2(d1, d0) * 180.0D / 3.1415927410125732D) - 90.0F;
        float f3 = (float) (-(Math.atan2(d2, d3) * 180.0D / 3.1415927410125732D));

        this.pitch = -this.b(this.pitch, f3, f1);
        this.yaw = this.b(this.yaw, f2, f);
    }

    public boolean V() {
        return this.b != null;
    }

    public Entity W() {
        return this.b;
    }

    private float b(float f, float f1, float f2) {
        float f3;

        for (f3 = f1 - f; f3 < -180.0F; f3 += 360.0F) {
            ;
        }

        while (f3 >= 180.0F) {
            f3 -= 360.0F;
        }

        if (f3 > f2) {
            f3 = f2;
        }

        if (f3 < -f2) {
            f3 = -f2;
        }

        return f + f3;
    }

    public void X() {
    }

    public boolean d() {
        return this.world.containsEntity(this.boundingBox) && !this.world.hasCollision(this, this.boundingBox) && !this.world.c(this.boundingBox);
    }

    protected void Y() {
        // CraftBukkit start
        EntityDamageByBlockEvent event = new EntityDamageByBlockEvent(null, this.getBukkitEntity(), EntityDamageEvent.DamageCause.VOID, 4);
        this.world.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled() || event.getDamage() == 0) {
            return;
        }

        this.damageEntity((Entity) null, event.getDamage());
        // CraftBukkit end
    }

    public Vec3D Z() {
        return this.b(1.0F);
    }

    public Vec3D b(float f) {
        float f1;
        float f2;
        float f3;
        float f4;

        if (f == 1.0F) {
            f1 = MathHelper.cos(-this.yaw * 0.017453292F - 3.1415927F);
            f2 = MathHelper.sin(-this.yaw * 0.017453292F - 3.1415927F);
            f3 = -MathHelper.cos(-this.pitch * 0.017453292F);
            f4 = MathHelper.sin(-this.pitch * 0.017453292F);
            return Vec3D.create((double) (f2 * f3), (double) f4, (double) (f1 * f3));
        } else {
            f1 = this.lastPitch + (this.pitch - this.lastPitch) * f;
            f2 = this.lastYaw + (this.yaw - this.lastYaw) * f;
            f3 = MathHelper.cos(-f2 * 0.017453292F - 3.1415927F);
            f4 = MathHelper.sin(-f2 * 0.017453292F - 3.1415927F);
            float f5 = -MathHelper.cos(-f1 * 0.017453292F);
            float f6 = MathHelper.sin(-f1 * 0.017453292F);

            return Vec3D.create((double) (f4 * f5), (double) f6, (double) (f3 * f5));
        }
    }

    public int l() {
        return 4;
    }

    public boolean isSleeping() {
        return false;
    }
}
