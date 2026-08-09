package net.minecraft.server;

import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.util.ResourceLocation;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.entity.EntityTargetEvent;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

// CraftBukkit start
// CraftBukkit end

public class EntityWolf extends EntityAnimal {
    private static final int LEASH_FLEE_DURATION_TICKS = 100;
    private static final int LEASH_FLEE_REPATH_INTERVAL_TICKS = 10;
    private static final double LEASH_FLEE_TARGET_DISTANCE = 12.0D;
    private static final int OWNER_FOLLOW_REPATH_INTERVAL_TICKS = 10;
    private static final int OWNER_TELEPORT_ATTEMPTS = 10;
    private static final double OWNER_FOLLOW_START_DISTANCE_SQ = 100.0D;
    private static final double OWNER_FOLLOW_STOP_DISTANCE_SQ = 4.0D;
    private static final double OWNER_TELEPORT_DISTANCE_SQ = 144.0D;

    private static final EntityDataAccessor<Byte> DATA_WOLF_FLAGS_ID = new EntityDataAccessor<Byte>(16, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<String> DATA_WOLF_OWNER_ID = new EntityDataAccessor<String>(17, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_WOLF_HEALTH_ID = new EntityDataAccessor<Integer>(18, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_WOLF_COLLAR_COLOR_ID = new EntityDataAccessor<Byte>(24, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<String> DATA_WOLF_OWNER_UUID_ID = new EntityDataAccessor<String>(25, EntityDataSerializers.STRING);

    private boolean a = false;
    private float b;
    private float c;
    private boolean f;
    private boolean g;
    private float h;
    private float i;
    private Entity leashFleeSource;
    private int leashFleeTicks;
    private int leashFleeRepathTicks;
    private boolean followingOwner;
    private int ownerFollowRepathTicks;

    public EntityWolf(World world) {
        super(world);
        this.texture = "/mob/wolf.png";
        this.b(0.8F, 0.8F);
        this.aE = 1.1F;
        this.health = 8;
        this.setSyncedWolfHealth(this.health);
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.getSynchedEntityData().define(DATA_WOLF_FLAGS_ID, Byte.valueOf((byte)0));
        this.getSynchedEntityData().define(DATA_WOLF_OWNER_ID, "");
        this.getSynchedEntityData().define(DATA_WOLF_HEALTH_ID, Integer.valueOf(10));
        this.getSynchedEntityData().define(DATA_WOLF_COLLAR_COLOR_ID, Byte.valueOf((byte)14));
        this.getSynchedEntityData().define(DATA_WOLF_OWNER_UUID_ID, "");
    }

    protected void b() {
        super.b();
        this.datawatcher.a(16, Byte.valueOf(this.getWolfFlags()));
        this.datawatcher.a(17, this.getWolfOwnerValue());
        this.datawatcher.a(18, Integer.valueOf(this.getSyncedWolfHealth()));
        this.datawatcher.a(24, Byte.valueOf(this.getSyncedCollarColor()));
        this.datawatcher.a(25, this.getWolfOwnerUUIDValue());
    }

    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == DATA_WOLF_FLAGS_ID) {
            Byte value = this.getSynchedEntityData().get(DATA_WOLF_FLAGS_ID);
            this.datawatcher.watch(16, value == null ? Byte.valueOf((byte)0) : value);
        } else if (accessor == DATA_WOLF_OWNER_ID) {
            String value = this.getSynchedEntityData().get(DATA_WOLF_OWNER_ID);
            this.datawatcher.watch(17, value == null ? "" : value);
        } else if (accessor == DATA_WOLF_HEALTH_ID) {
            Integer value = this.getSynchedEntityData().get(DATA_WOLF_HEALTH_ID);
            this.datawatcher.watch(18, Integer.valueOf(value == null ? this.health : value.intValue()));
        } else if (accessor == DATA_WOLF_COLLAR_COLOR_ID) {
            Byte value = this.getSynchedEntityData().get(DATA_WOLF_COLLAR_COLOR_ID);
            this.datawatcher.watch(24, value == null ? Byte.valueOf((byte)14) : value);
        } else if (accessor == DATA_WOLF_OWNER_UUID_ID) {
            String value = this.getSynchedEntityData().get(DATA_WOLF_OWNER_UUID_ID);
            this.datawatcher.watch(25, value == null ? "" : value);
        }
    }

    protected boolean n() {
        return false;
    }

    public void b(NBTTagCompound nbttagcompound) {
        super.b(nbttagcompound);
        boolean persistentAngry = this.isAngry() && !this.isLeashFleeing();
        nbttagcompound.a("Angry", persistentAngry);
        nbttagcompound.a("Sitting", this.isSitting());
        String owner = this.getOwnerName();
        if (owner == null) {
            owner = "";
        }
        String ownerUuid = this.getOwnerUUID();
        if (ownerUuid == null) {
            ownerUuid = "";
        }
        nbttagcompound.setString("Owner", owner);
        nbttagcompound.setString("OwnerUUID", ownerUuid);
        nbttagcompound.a("CollarColor", (byte)this.getCollarColor());

        NBTTagCompound entityData = nbttagcompound.hasKey("entity_data") ? nbttagcompound.k("entity_data") : new NBTTagCompound();
        entityData.setString("wolf_owner", owner);
        entityData.setString("wolf_owner_uuid", ownerUuid);
        entityData.a("wolf_tamed", this.isTamed());
        entityData.a("wolf_angry", persistentAngry);
        entityData.a("wolf_sitting", this.isSitting());
        entityData.a("wolf_health", this.health);
        entityData.a("wolf_collar_color", this.getCollarColor());
        nbttagcompound.a("entity_data", entityData);
    }

    public void a(NBTTagCompound nbttagcompound) {
        super.a(nbttagcompound);
        NBTTagCompound entityData = nbttagcompound.hasKey("entity_data") ? nbttagcompound.k("entity_data") : null;
        if (hasWolfEntityData(entityData)) {
            String owner = entityData.hasKey("wolf_owner") ? entityData.getString("wolf_owner") : nbttagcompound.getString("Owner");
            if (owner == null) {
                owner = "";
            }
            String ownerUuid = entityData.hasKey("wolf_owner_uuid") ? entityData.getString("wolf_owner_uuid") : nbttagcompound.getString("OwnerUUID");
            if (ownerUuid == null) {
                ownerUuid = "";
            }
            if (ownerUuid.length() == 0 && isUuidLike(owner)) {
                ownerUuid = owner;
                owner = "";
            }

            this.setOwnerName(owner);
            this.setOwnerUUID(ownerUuid);
            boolean tamed = entityData.hasKey("wolf_tamed") ? entityData.m("wolf_tamed") : owner.length() > 0 || ownerUuid.length() > 0;
            this.setTamed(tamed);
            this.setAngry(entityData.hasKey("wolf_angry") ? entityData.m("wolf_angry") : nbttagcompound.m("Angry"));
            this.setSitting(entityData.hasKey("wolf_sitting") ? entityData.m("wolf_sitting") : nbttagcompound.m("Sitting"));
            if (entityData.hasKey("wolf_health")) {
                int wolfHealth = entityData.e("wolf_health");
                this.health = wolfHealth;
                this.setSyncedWolfHealth(wolfHealth);
            } else {
                this.setSyncedWolfHealth(this.health);
            }
            int collarColor = entityData.hasKey("wolf_collar_color")
                    ? entityData.e("wolf_collar_color")
                    : (nbttagcompound.hasKey("CollarColor") ? nbttagcompound.c("CollarColor") : 14);
            this.setCollarColor(collarColor);
        } else {
            this.setAngry(nbttagcompound.m("Angry"));
            this.setSitting(nbttagcompound.m("Sitting"));
            String s = nbttagcompound.getString("Owner");
            String ownerUuid = nbttagcompound.getString("OwnerUUID");
            if (ownerUuid == null) {
                ownerUuid = "";
            }
            if (ownerUuid.length() == 0 && isUuidLike(s)) {
                ownerUuid = s;
                s = "";
            }

            if (s.length() > 0 || ownerUuid.length() > 0) {
                this.setOwnerName(s);
                this.setOwnerUUID(ownerUuid);
                this.setTamed(true);
            }
            this.setCollarColor(nbttagcompound.hasKey("CollarColor") ? nbttagcompound.c("CollarColor") : 14);
            this.setSyncedWolfHealth(this.health);
        }
    }

    private static boolean hasWolfEntityData(NBTTagCompound entityData) {
        return entityData != null
                && (entityData.hasKey("wolf_owner")
                || entityData.hasKey("wolf_owner_uuid")
                || entityData.hasKey("wolf_tamed")
                || entityData.hasKey("wolf_angry")
                || entityData.hasKey("wolf_sitting")
                || entityData.hasKey("wolf_health")
                || entityData.hasKey("wolf_collar_color"));
    }

    protected boolean h_() {
        return !this.isTamed() && super.h_();
    }

    protected void updateLeashedState() {
        if (!this.isTamed() && this.isLeashed()) {
            if (this.isLeashedToFence()) {
                this.breakLeashFleeLead();
                return;
            }

            Entity leashHolder = this.getLeashHolderEntity();
            if (leashHolder instanceof EntityHuman && (!this.isLeashFleeing() || this.leashFleeSource != leashHolder)) {
                this.startLeashFlee((EntityHuman) leashHolder);
            }
        }

        super.updateLeashedState();
    }

    protected String g() {
        return this.isAngry() ? "mob.wolf.growl" : (this.random.nextInt(3) == 0 ? (this.isTamed() && this.getSyncedWolfHealth() < 10 ? "mob.wolf.whine" : "mob.wolf.panting") : "mob.wolf.bark");
    }

    protected String h() {
        return "mob.wolf.hurt";
    }

    protected String i() {
        return "mob.wolf.death";
    }

    protected float k() {
        return 0.4F;
    }

    protected int j() {
        return -1;
    }

    protected void c_() {
        // Clear anger if target switched to creative mode
        if (this.isAngry() && this.target instanceof EntityHuman) {
            EntityHuman targetPlayer = (EntityHuman) this.target;
            if (targetPlayer.gameMode == 1 || targetPlayer.isSpectator()) {
                this.setAngry(false);
                this.target = null;
            }
        }

        if (this.isLeashFleeing()) {
            this.updateLeashFleeState();
            if (this.isLeashFleeing()) {
                super.c_();
                return;
            }
        }

        super.c_();
        if (!this.e && this.target == null && this.isTamed() && this.vehicle == null && !this.isLeashed()) {
            EntityHuman entityhuman = this.getOwnerEntity();

            if (entityhuman != null && !entityhuman.isSpectator()) {
                this.updateOwnerFollowing(entityhuman);
            } else {
                this.stopFollowingOwner(this.followingOwner);
                if (entityhuman == null && !this.C() && !this.ad()) {
                    this.setSitting(true);
                }
            }
        } else {
            this.stopFollowingOwner(this.followingOwner && this.target == null);
        }

        if (this.target == null && !this.C() && !this.isTamed() && this.world.random.nextInt(100) == 0) {
            List list = this.world.a(EntitySheep.class, AxisAlignedBB.b(this.locX, this.locY, this.locZ, this.locX + 1.0D, this.locY + 1.0D, this.locZ + 1.0D).b(16.0D, 4.0D, 16.0D));

            if (!list.isEmpty()) {
                // CraftBukkit start
                Entity entity = (Entity) list.get(this.world.random.nextInt(list.size()));
                org.bukkit.entity.Entity bukkitTarget = entity == null ? null : entity.getBukkitEntity();

                EntityTargetEvent event = new EntityTargetEvent(this.getBukkitEntity(), bukkitTarget, EntityTargetEvent.TargetReason.RANDOM_TARGET);
                this.world.getServer().getPluginManager().callEvent(event);

                if (!event.isCancelled() || event.getTarget() != null) {
                    this.setTarget(entity);
                }
                // CraftBukkit end
            }
        }

        if (this.ad()) {
            this.setSitting(false);
        }

        if (!this.world.isStatic) {
            this.setSyncedWolfHealth(this.health);
        }
    }

    public void v() {
        super.v();
        this.a = false;
        if (this.V() && !this.C() && !this.isAngry()) {
            Entity entity = this.W();

            if (entity instanceof EntityHuman) {
                EntityHuman entityhuman = (EntityHuman) entity;
                ItemStack itemstack = entityhuman.inventory.getItemInHand();

                if (itemstack != null) {
                    if (!this.isTamed() && itemstack.id == Item.BONE.id) {
                        this.a = true;
                    } else if (this.isTamed() && Item.byId[itemstack.id] instanceof ItemFood) {
                        this.a = ((ItemFood) Item.byId[itemstack.id]).l();
                    }
                }
            }
        }

        if (!this.Y && this.f && !this.g && !this.C() && this.onGround) {
            this.g = true;
            this.h = 0.0F;
            this.i = 0.0F;
            this.world.a(this, (byte) 8);
        }
    }

    public void m_() {
        super.m_();
        this.c = this.b;
        if (this.a) {
            this.b += (1.0F - this.b) * 0.4F;
        } else {
            this.b += (0.0F - this.b) * 0.4F;
        }

        if (this.a) {
            this.aF = 10;
        }

        if (this.ac()) {
            this.f = true;
            this.g = false;
            this.h = 0.0F;
            this.i = 0.0F;
        } else if ((this.f || this.g) && this.g) {
            if (this.h == 0.0F) {
                this.world.makeSound(this, "mob.wolf.shake", this.k(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            }

            this.i = this.h;
            this.h += 0.05F;
            if (this.i >= 2.0F) {
                this.f = false;
                this.g = false;
                this.i = 0.0F;
                this.h = 0.0F;
            }

            if (this.h > 0.4F) {
                float f = (float) this.boundingBox.b;
                int i = (int) (MathHelper.sin((this.h - 0.4F) * 3.1415927F) * 7.0F);

                for (int j = 0; j < i; ++j) {
                    float f1 = (this.random.nextFloat() * 2.0F - 1.0F) * this.length * 0.5F;
                    float f2 = (this.random.nextFloat() * 2.0F - 1.0F) * this.length * 0.5F;

                    this.world.a("splash", this.locX + (double) f1, (double) (f + 0.8F), this.locZ + (double) f2, this.motX, this.motY, this.motZ);
                }
            }
        }
    }

    public float t() {
        return this.width * 0.8F;
    }

    protected int u() {
        return this.isSitting() ? 20 : super.u();
    }

    private void updateOwnerFollowing(EntityHuman owner) {
        double distanceSq = this.g(owner);
        if (this.followingOwner && !this.C()) {
            this.stopFollowingOwner(false);
        }

        if (!this.followingOwner) {
            if (!shouldStartFollowingOwner(distanceSq)) {
                return;
            }

            this.followingOwner = true;
            this.ownerFollowRepathTicks = 0;
        }

        if (!shouldKeepFollowingOwner(distanceSq)) {
            this.stopFollowingOwner(true);
            return;
        }

        if (--this.ownerFollowRepathTicks <= 0) {
            this.ownerFollowRepathTicks = OWNER_FOLLOW_REPATH_INTERVAL_TICKS;
            if (shouldTeleportToOwner(distanceSq)) {
                if (this.tryToTeleportToOwner(owner)) {
                    this.stopFollowingOwner(false);
                }
            } else {
                this.setPathEntity(this.world.findPath(this, owner, 16.0F));
            }
        }
    }

    private void stopFollowingOwner(boolean clearPath) {
        this.followingOwner = false;
        this.ownerFollowRepathTicks = 0;
        if (clearPath) {
            this.setPathEntity((PathEntity) null);
        }
    }

    private boolean tryToTeleportToOwner(EntityHuman owner) {
        int ownerX = MathHelper.floor(owner.locX);
        int ownerY = MathHelper.floor(owner.locY);
        int ownerZ = MathHelper.floor(owner.locZ);
        for (int attempt = 0; attempt < OWNER_TELEPORT_ATTEMPTS; ++attempt) {
            int offsetX = this.random.nextInt(7) - 3;
            int offsetZ = this.random.nextInt(7) - 3;
            if (isTeleportOffsetOutsideInnerSquare(offsetX, offsetZ)) {
                int offsetY = this.random.nextInt(3) - 1;
                if (this.maybeTeleportTo(ownerX + offsetX, ownerY + offsetY, ownerZ + offsetZ)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean maybeTeleportTo(int x, int y, int z) {
        if (!this.canTeleportTo(x, y, z)) {
            return false;
        }

        this.setPositionRotation((double) x + 0.5D, (double) y, (double) z + 0.5D, this.yaw, this.pitch);
        this.setPathEntity((PathEntity) null);
        return true;
    }

    private boolean canTeleportTo(int x, int y, int z) {
        boolean destinationPassable = this.isTeleportSpacePassable(x, y, z) && this.isTeleportSpacePassable(x, y + 1, z);
        int supportBlockId = this.world.getTypeId(x, y - 1, z);
        Block supportBlock = supportBlockId <= 0 ? null : Block.byId[supportBlockId];
        boolean supportWalkable = supportBlock != null && supportBlock.material.isSolid() && !supportBlock.material.isLiquid() && !isDangerousTeleportBlock(supportBlockId);
        boolean supportLeaves = supportBlockId == Block.LEAVES.id;
        boolean dangerNearby = this.hasTeleportDangerNearby(x, y, z);
        int currentX = MathHelper.floor(this.locX);
        int currentY = MathHelper.floor(this.locY);
        int currentZ = MathHelper.floor(this.locZ);
        AxisAlignedBB movedBox = this.boundingBox.c((double) (x - currentX), (double) (y - currentY), (double) (z - currentZ));
        boolean collisionFree = !this.world.hasCollision(this, movedBox);
        return isWalkableTeleportLanding(destinationPassable, supportWalkable, supportLeaves, dangerNearby, collisionFree);
    }

    private boolean isTeleportSpacePassable(int x, int y, int z) {
        int blockId = this.world.getTypeId(x, y, z);
        if (blockId == 0) {
            return true;
        }

        Block block = Block.byId[blockId];
        return block != null && !block.material.isSolid() && !block.material.isLiquid() && !isDangerousTeleportBlock(blockId);
    }

    private boolean hasTeleportDangerNearby(int x, int y, int z) {
        for (int offsetX = -1; offsetX <= 1; ++offsetX) {
            for (int offsetY = -1; offsetY <= 1; ++offsetY) {
                for (int offsetZ = -1; offsetZ <= 1; ++offsetZ) {
                    if (offsetX != 0 || offsetZ != 0) {
                        int blockId = this.world.getTypeId(x + offsetX, y + offsetY, z + offsetZ);
                        if (blockId > 0) {
                            Block block = Block.byId[blockId];
                            if (block != null && (block.material.isLiquid() || isDangerousTeleportBlock(blockId))) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean isDangerousTeleportBlock(int blockId) {
        return blockId == Block.FIRE.id || blockId == Block.CACTUS.id || blockId == Block.STATIONARY_LAVA.id || blockId == Block.LAVA.id;
    }

    static boolean shouldStartFollowingOwner(double distanceSq) {
        return distanceSq >= OWNER_FOLLOW_START_DISTANCE_SQ;
    }

    static boolean shouldKeepFollowingOwner(double distanceSq) {
        return distanceSq > OWNER_FOLLOW_STOP_DISTANCE_SQ;
    }

    static boolean shouldTeleportToOwner(double distanceSq) {
        return distanceSq >= OWNER_TELEPORT_DISTANCE_SQ;
    }

    static boolean isTeleportOffsetOutsideInnerSquare(int offsetX, int offsetZ) {
        return Math.abs(offsetX) <= 3 && Math.abs(offsetZ) <= 3 && (Math.abs(offsetX) >= 2 || Math.abs(offsetZ) >= 2);
    }

    static boolean isWalkableTeleportLanding(boolean destinationPassable, boolean supportWalkable, boolean supportLeaves, boolean dangerNearby, boolean collisionFree) {
        return destinationPassable && supportWalkable && !supportLeaves && !dangerNearby && collisionFree;
    }

    protected boolean w() {
        return this.isSitting() || this.g;
    }

    public boolean damageEntity(Entity entity, int i) {
        this.setSitting(false);
        if (entity != null && !(entity instanceof EntityHuman) && !(entity instanceof EntityArrow)) {
            i = (i + 1) / 2;
        }

        if (!super.damageEntity((Entity) entity, i)) {
            return false;
        } else {
            // Don't retaliate against creative mode players
            if (entity instanceof EntityHuman
                    && (((EntityHuman) entity).gameMode == 1 || ((EntityHuman) entity).isSpectator())) {
                return true;
            }
            
            if (!this.isTamed() && !this.isAngry()) {
                if (entity instanceof EntityHuman) {
                    // CraftBukkit start
                    org.bukkit.entity.Entity bukkitTarget = entity == null ? null : entity.getBukkitEntity();

                    EntityTargetEvent event = new EntityTargetEvent(this.getBukkitEntity(), bukkitTarget, EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY);
                    this.world.getServer().getPluginManager().callEvent(event);

                    if (!event.isCancelled()) {
                        if (event.getTarget() == null) {
                            this.target = null;
                        } else {
                            this.setAngry(true);
                            this.target = ((CraftEntity) event.getTarget()).getHandle();
                        }
                    }
                    // CraftBukkit end
                }

                if (entity instanceof EntityArrow && ((EntityArrow) entity).shooter != null) {
                    entity = ((EntityArrow) entity).shooter;
                }

                if (entity instanceof EntityLiving) {
                    List list = this.world.a(EntityWolf.class, AxisAlignedBB.b(this.locX, this.locY, this.locZ, this.locX + 1.0D, this.locY + 1.0D, this.locZ + 1.0D).b(16.0D, 4.0D, 16.0D));
                    Iterator iterator = list.iterator();

                    while (iterator.hasNext()) {
                        Entity entity1 = (Entity) iterator.next();
                        EntityWolf entitywolf = (EntityWolf) entity1;

                        if (!entitywolf.isTamed() && entitywolf.target == null) {
                            // CraftBukkit start
                            org.bukkit.entity.Entity bukkitTarget = entity == null ? null : entity.getBukkitEntity();

                            EntityTargetEvent event = new EntityTargetEvent(this.getBukkitEntity(), bukkitTarget, EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY);
                            this.world.getServer().getPluginManager().callEvent(event);

                            if (!event.isCancelled()) {
                                if (event.getTarget() == null) {
                                    this.target = null;
                                } else {
                                    entitywolf.target = (Entity) entity;
                                    if (entity instanceof EntityHuman) {
                                        entitywolf.setAngry(true);
                                    }
                                }
                            }
                            // CraftBukkit end
                        }
                    }
                }
            } else if (entity != this && entity != null) {
                if (this.isTamed() && entity instanceof EntityHuman && this.isOwnedBy((EntityHuman) entity)) {
                    return true;
                }

                this.target = (Entity) entity;
            }

            return true;
        }
    }

    protected Entity findTarget() {
        return this.isLeashFleeing() ? null : (this.isAngry() ? this.world.findNearbyPlayer(this, 16.0D) : null);
    }

    public boolean startLeashFlee(EntityHuman entityhuman) {
        if (entityhuman == null || this.isTamed()) {
            return false;
        }

        this.leashFleeSource = entityhuman;
        this.leashFleeTicks = LEASH_FLEE_DURATION_TICKS;
        this.leashFleeRepathTicks = 0;
        this.target = null;
        this.setSitting(false);
        this.setAngry(true);
        this.setPathEntity((PathEntity) null);
        this.aE = 1.3F;
        this.updateLeashFleePath();
        this.world.makeSound(this, "mob.wolf.growl", this.k(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        return true;
    }

    public boolean isLeashFleeing() {
        return this.leashFleeTicks > 0;
    }

    private void updateLeashFleeState() {
        if (this.isTamed()) {
            this.stopLeashFlee(true);
            return;
        }

        if (!(this.leashFleeSource instanceof EntityHuman) || this.leashFleeSource.dead || !this.isLeashedTo((EntityHuman) this.leashFleeSource)) {
            this.stopLeashFleeAfterBreak();
            return;
        }

        this.target = null;
        this.setSitting(false);
        this.setAngry(true);
        this.aE = 1.3F;
        if (this.leashFleeRepathTicks-- <= 0 || !this.C()) {
            this.updateLeashFleePath();
            this.leashFleeRepathTicks = LEASH_FLEE_REPATH_INTERVAL_TICKS;
        }
        this.pushAwayFromLeashSource();

        if (--this.leashFleeTicks <= 0) {
            this.breakLeashFleeLead();
        }
    }

    private void breakLeashFleeLead() {
        if (this.isLeashed()) {
            if (this.world != null) {
                this.world.makeSound(this, "random.break", 1.0F, 1.0F);
            }
            this.clearLeashed(true);
            ItemLead.syncLeashDataToClients(this);
        }

        this.stopLeashFleeAfterBreak();
    }

    private void stopLeashFleeAfterBreak() {
        Entity source = this.leashFleeSource;
        this.stopLeashFlee(false);
        if (!this.isTamed()) {
            this.setAngry(true);
            if (source instanceof EntityHuman && !source.dead) {
                this.target = source;
            }
        }
    }

    private void stopLeashFlee(boolean clearAnger) {
        this.leashFleeSource = null;
        this.leashFleeTicks = 0;
        this.leashFleeRepathTicks = 0;
        this.target = null;
        this.setPathEntity((PathEntity) null);
        this.aE = 1.1F;
        if (clearAnger) {
            this.setAngry(false);
        }
    }

    private void updateLeashFleePath() {
        if (this.leashFleeSource == null || this.world == null) {
            return;
        }

        double awayX = this.locX - this.leashFleeSource.locX;
        double awayZ = this.locZ - this.leashFleeSource.locZ;
        double awayLength = MathHelper.a(awayX * awayX + awayZ * awayZ);
        if (awayLength < 0.001D) {
            float angle = this.random.nextFloat() * 3.1415927F * 2.0F;
            awayX = MathHelper.cos(angle);
            awayZ = MathHelper.sin(angle);
            awayLength = 1.0D;
        }

        awayX /= awayLength;
        awayZ /= awayLength;
        PathEntity bestPath = null;
        double bestDistanceSq = -1.0D;
        for (int i = 0; i < 10; ++i) {
            double forward = LEASH_FLEE_TARGET_DISTANCE + (double) this.random.nextInt(8);
            double side = ((double) this.random.nextFloat() - 0.5D) * 10.0D;
            int x = MathHelper.floor(this.locX + awayX * forward - awayZ * side);
            int y = MathHelper.floor(this.boundingBox.b + (double) this.random.nextInt(5) - 2.0D);
            int z = MathHelper.floor(this.locZ + awayZ * forward + awayX * side);
            PathEntity path = this.world.a(this, x, y, z, 18.0F);
            if (path != null) {
                double dx = (double) x - this.leashFleeSource.locX;
                double dz = (double) z - this.leashFleeSource.locZ;
                double distanceSq = dx * dx + dz * dz;
                if (distanceSq > bestDistanceSq) {
                    bestPath = path;
                    bestDistanceSq = distanceSq;
                }
            }
        }

        if (bestPath != null) {
            this.setPathEntity(bestPath);
        }
    }

    private void pushAwayFromLeashSource() {
        if (this.leashFleeSource == null) {
            return;
        }

        double awayX = this.locX - this.leashFleeSource.locX;
        double awayZ = this.locZ - this.leashFleeSource.locZ;
        double distanceSq = awayX * awayX + awayZ * awayZ;
        if (distanceSq > 0.0001D) {
            double distance = (double) MathHelper.a(distanceSq);
            this.motX += awayX / distance * 0.06D;
            this.motZ += awayZ / distance * 0.06D;
            if (this.onGround && this.random.nextInt(8) == 0) {
                this.motY = 0.25D;
            }
        }
    }

    protected void a(Entity entity, float f) {
        if (f > 2.0F && f < 6.0F && this.random.nextInt(10) == 0) {
            if (this.onGround) {
                double d0 = entity.locX - this.locX;
                double d1 = entity.locZ - this.locZ;
                float f1 = MathHelper.a(d0 * d0 + d1 * d1);

                this.motX = d0 / (double) f1 * 0.5D * 0.800000011920929D + this.motX * 0.20000000298023224D;
                this.motZ = d1 / (double) f1 * 0.5D * 0.800000011920929D + this.motZ * 0.20000000298023224D;
                this.motY = 0.4000000059604645D;
            }
        } else if ((double) f < 1.5D && entity.boundingBox.e > this.boundingBox.b && entity.boundingBox.b < this.boundingBox.e) {
            this.attackTicks = 20;
            byte b0 = 2;

            if (this.isTamed()) {
                b0 = 4;
            }
            // CraftBukkit start
            org.bukkit.entity.Entity damager = this.getBukkitEntity();
            org.bukkit.entity.Entity damagee = entity == null ? null : entity.getBukkitEntity();

            EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(damager, damagee, EntityDamageEvent.DamageCause.ENTITY_ATTACK, b0);
            this.world.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return;
            }
            // CraftBukkit end

            entity.damageEntity(this, b0);
        }
    }

    public boolean a(EntityHuman entityhuman) {
        ItemStack itemstack = entityhuman.inventory.getItemInHand();

        if (!this.isTamed()) {
            if (this.isLeashed()) {
                if (!this.isLeashFleeing() && entityhuman != null) {
                    this.startLeashFlee(entityhuman);
                }
                this.setAngry(true);
                return false;
            }

            if (itemstack != null && itemstack.id == Item.BONE.id && !this.isAngry()) {
                --itemstack.count;
                if (itemstack.count <= 0) {
                    entityhuman.inventory.setItem(entityhuman.inventory.itemInHandIndex, (ItemStack) null);
                }

                if (!this.world.isStatic) {
                    // CraftBukkit - added event call and isCancelled check.
                    if (this.random.nextInt(3) == 0 && !CraftEventFactory.callEntityTameEvent(this, entityhuman).isCancelled()) {
                        // CraftBukkit end
                        this.setTamed(true);
                        this.setPathEntity((PathEntity) null);
                        this.setSitting(true);
                        this.health = 20;
                        this.setSyncedWolfHealth(this.health);
                        this.setOwnerName(entityhuman.name);
                        this.setOwnerUUID(getPlayerOwnerUUID(entityhuman));
                        this.a(true);
                        this.world.a(this, (byte) 7);
                    } else {
                        this.a(false);
                        this.world.a(this, (byte) 6);
                    }
                }

                return true;
            }
        } else {
            if (itemstack != null && Item.byId[itemstack.id] instanceof ItemFood) {
                ItemFood itemfood = (ItemFood) Item.byId[itemstack.id];

                if (itemfood.l() && this.getSyncedWolfHealth() < 20) {
                    --itemstack.count;
                    if (itemstack.count <= 0) {
                        entityhuman.inventory.setItem(entityhuman.inventory.itemInHandIndex, (ItemStack) null);
                    }

                    this.b(((ItemFood) Item.PORK).k(), RegainReason.EATING);
                    return true;
                }
            }

            if (this.isOwnedBy(entityhuman)) {
                int i = getCollarColorFromDye(itemstack);
                if (i >= 0) {
                    if (i != this.getCollarColor()) {
                        --itemstack.count;
                        if (itemstack.count <= 0) {
                            entityhuman.inventory.setItem(entityhuman.inventory.itemInHandIndex, (ItemStack) null);
                        }

                        if (!this.world.isStatic) {
                            this.setCollarColor(i);
                            if (this.world instanceof WorldServer) {
                                ((WorldServer)this.world).tracker.sendPacketToEntity(this, new Packet40EntityMetadata(this));
                            }
                        }

                        return true;
                    }

                    return true;
                }

                if (!this.world.isStatic) {
                    this.setSitting(!this.isSitting());
                    this.aC = false;
                    this.setPathEntity((PathEntity) null);
                }

                return true;
            }
        }

        return false;
    }

    private static int getCollarColorFromDye(ItemStack stack) {
        int dyeDamage = getDyeDamage(stack);
        return dyeDamage < 0 ? -1 : BlockCloth.c(dyeDamage);
    }

    private static int getDyeDamage(ItemStack stack) {
        if (stack == null) {
            return -1;
        }

        ResourceLocation key = getDyeStackKey(stack);
        if (key != null) {
            int damage = ItemRegistry.getDefaultDamage(key.toString());
            if (damage >= 0 && damage < 16) {
                return damage;
            }

            damage = getDyeDamageFromPath(key.getPath());
            if (damage >= 0) {
                return damage;
            }
        }

        Item item = stack.getItem();
        if (item instanceof ItemDye || stack.id == Item.INK_SACK.id) {
            return stack.getData() & 15;
        }

        return -1;
    }

    private static ResourceLocation getDyeStackKey(ItemStack stack) {
        Holder<Item> holder = stack.getItemHolder();
        if (holder != null && holder.key() != null) {
            return holder.key();
        }

        return ItemRegistry.getKeyForStack(stack);
    }

    private static int getDyeDamageFromPath(String path) {
        if (path == null) {
            return -1;
        }

        if ("cocoa_beans".equals(path)) {
            return 3;
        }

        if (!path.endsWith("_dye")) {
            return -1;
        }

        String color = path.substring(0, path.length() - "_dye".length());
        String[] colors = new String[]{"black", "red", "green", "brown", "blue", "purple", "cyan", "light_gray", "gray", "pink", "lime", "yellow", "light_blue", "magenta", "orange", "white"};
        for (int j = 0; j < colors.length; ++j) {
            if (colors[j].equals(color)) {
                return j;
            }
        }

        return -1;
    }

    void a(boolean flag) {
        String s = "heart";

        if (!flag) {
            s = "smoke";
        }

        for (int i = 0; i < 7; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;

            this.world.a(s, this.locX + (double) (this.random.nextFloat() * this.length * 2.0F) - (double) this.length, this.locY + 0.5D + (double) (this.random.nextFloat() * this.width), this.locZ + (double) (this.random.nextFloat() * this.length * 2.0F) - (double) this.length, d0, d1, d2);
        }
    }

    public int l() {
        return 8;
    }

    public String getOwnerName() {
        return this.getWolfOwnerValue();
    }

    public void setOwnerName(String s) {
        this.setWolfOwnerValue(s);
    }

    public String getOwnerUUID() {
        return this.getWolfOwnerUUIDValue();
    }

    public void setOwnerUUID(String s) {
        this.setWolfOwnerUUIDValue(s);
    }

    public boolean isOwnedBy(EntityHuman entityhuman) {
        if (entityhuman == null) {
            return false;
        }

        String ownerUuid = normalizeOwnerUuid(this.getOwnerUUID());
        String playerUuid = normalizeOwnerUuid(getPlayerOwnerUUID(entityhuman));
        if (ownerUuid.length() > 0 && playerUuid.length() > 0 && ownerUuid.equals(playerUuid)) {
            this.rememberOwnerIdentity(entityhuman);
            return true;
        }

        String ownerName = this.getOwnerName();
        boolean ownedByName = ownerName != null && ownerName.length() > 0 && entityhuman.name != null && entityhuman.name.equalsIgnoreCase(ownerName);
        if (ownedByName) {
            this.rememberOwnerIdentity(entityhuman);
        }

        return ownedByName;
    }

    private EntityHuman getOwnerEntity() {
        String ownerName = this.getOwnerName();
        if (ownerName != null && ownerName.length() > 0) {
            EntityHuman byName = this.world.a(ownerName);
            if (byName != null) {
                this.rememberOwnerIdentity(byName);
                return byName;
            }

            for (int i = 0; i < this.world.players.size(); ++i) {
                EntityHuman player = (EntityHuman)this.world.players.get(i);
                if (player != null && player.name != null && player.name.equalsIgnoreCase(ownerName)) {
                    this.rememberOwnerIdentity(player);
                    return player;
                }
            }
        }

        String ownerUuid = normalizeOwnerUuid(this.getOwnerUUID());
        if (ownerUuid.length() == 0) {
            return null;
        }

        for (int i = 0; i < this.world.players.size(); ++i) {
            EntityHuman player = (EntityHuman)this.world.players.get(i);
            String playerUuid = normalizeOwnerUuid(getPlayerOwnerUUID(player));
            if (playerUuid.length() > 0 && ownerUuid.equals(playerUuid)) {
                this.rememberOwnerIdentity(player);
                return player;
            }
        }

        return null;
    }

    private void rememberOwnerIdentity(EntityHuman entityhuman) {
        if (entityhuman == null) {
            return;
        }

        String ownerName = this.getOwnerName();
        if ((ownerName == null || ownerName.length() == 0) && entityhuman.name != null) {
            this.setOwnerName(entityhuman.name);
        }

        String ownerUuid = this.getOwnerUUID();
        String playerUuid = getPlayerOwnerUUID(entityhuman);
        if ((ownerUuid == null || ownerUuid.length() == 0) && playerUuid.length() > 0) {
            this.setOwnerUUID(playerUuid);
        }
    }

    public boolean isSitting() {
        return (this.getWolfFlags() & 1) != 0;
    }

    public void setSitting(boolean flag) {
        byte b0 = this.getWolfFlags();

        if (flag) {
            this.setWolfFlags((byte)(b0 | 1));
        } else {
            this.setWolfFlags((byte)(b0 & -2));
        }
    }

    public boolean isAngry() {
        return (this.getWolfFlags() & 2) != 0;
    }

    public void setAngry(boolean flag) {
        byte b0 = this.getWolfFlags();

        if (flag) {
            this.setWolfFlags((byte)(b0 | 2));
        } else {
            this.setWolfFlags((byte)(b0 & -3));
        }
    }

    public boolean isTamed() {
        return (this.getWolfFlags() & 4) != 0;
    }

    public void setTamed(boolean flag) {
        byte b0 = this.getWolfFlags();

        if (flag) {
            this.setWolfFlags((byte)(b0 | 4));
        } else {
            this.setWolfFlags((byte)(b0 & -5));
        }
    }

    public int getCollarColor() {
        return this.getSyncedCollarColor() & 15;
    }

    public void setCollarColor(int color) {
        this.setSyncedCollarColor((byte)(color & 15));
    }

    private byte getWolfFlags() {
        Byte value = this.getSynchedEntityData() == null ? null : this.getSynchedEntityData().get(DATA_WOLF_FLAGS_ID);
        if (value != null) {
            return value.byteValue();
        }
        return this.datawatcher.a(16);
    }

    private void setWolfFlags(byte flags) {
        if (this.getSynchedEntityData() != null) {
            this.getSynchedEntityData().set(DATA_WOLF_FLAGS_ID, Byte.valueOf(flags));
        } else {
            this.datawatcher.watch(16, Byte.valueOf(flags));
        }
    }

    private String getWolfOwnerValue() {
        String value = this.getSynchedEntityData() == null ? null : this.getSynchedEntityData().get(DATA_WOLF_OWNER_ID);
        if (value != null) {
            return value;
        }
        String legacy = this.datawatcher.c(17);
        return legacy == null ? "" : legacy;
    }

    private void setWolfOwnerValue(String owner) {
        String value = owner == null ? "" : owner;
        if (this.getSynchedEntityData() != null) {
            this.getSynchedEntityData().set(DATA_WOLF_OWNER_ID, value);
        } else {
            this.datawatcher.watch(17, value);
        }
    }

    private String getWolfOwnerUUIDValue() {
        String value = this.getSynchedEntityData() == null ? null : this.getSynchedEntityData().get(DATA_WOLF_OWNER_UUID_ID);
        if (value != null) {
            return value;
        }
        String legacy = this.datawatcher.c(25);
        return legacy == null ? "" : legacy;
    }

    private void setWolfOwnerUUIDValue(String ownerUuid) {
        String value = ownerUuid == null ? "" : ownerUuid.trim();
        if (this.getSynchedEntityData() != null) {
            this.getSynchedEntityData().set(DATA_WOLF_OWNER_UUID_ID, value);
        } else {
            this.datawatcher.watch(25, value);
        }
    }

    private int getSyncedWolfHealth() {
        Integer value = this.getSynchedEntityData() == null ? null : this.getSynchedEntityData().get(DATA_WOLF_HEALTH_ID);
        if (value != null) {
            return value.intValue();
        }
        return this.datawatcher.b(18);
    }

    private void setSyncedWolfHealth(int wolfHealth) {
        if (this.getSynchedEntityData() != null) {
            this.getSynchedEntityData().set(DATA_WOLF_HEALTH_ID, Integer.valueOf(wolfHealth));
        } else {
            this.datawatcher.watch(18, Integer.valueOf(wolfHealth));
        }
    }

    private byte getSyncedCollarColor() {
        Byte value = this.getSynchedEntityData() == null ? null : this.getSynchedEntityData().get(DATA_WOLF_COLLAR_COLOR_ID);
        if (value != null) {
            return value.byteValue();
        }
        return this.datawatcher.a(24);
    }

    private void setSyncedCollarColor(byte collarColor) {
        if (this.getSynchedEntityData() != null) {
            this.getSynchedEntityData().set(DATA_WOLF_COLLAR_COLOR_ID, Byte.valueOf(collarColor));
        } else {
            this.datawatcher.watch(24, Byte.valueOf(collarColor));
        }
    }

    private static String getPlayerOwnerUUID(EntityHuman entityhuman) {
        UUID uuid = null;
        if (entityhuman instanceof EntityPlayer) {
            uuid = ((EntityPlayer)entityhuman).getMojangUUID();
        }

        return uuid == null ? "" : uuid.toString();
    }

    private static boolean isUuidLike(String value) {
        return normalizeOwnerUuid(value).length() == 32;
    }

    private static String normalizeOwnerUuid(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();
        if (trimmed.length() == 0) {
            return "";
        }

        if (trimmed.length() == 32) {
            return trimmed.toLowerCase();
        }

        try {
            return UUID.fromString(trimmed).toString().replace("-", "").toLowerCase();
        } catch (IllegalArgumentException ignored) {
            return trimmed.replace("-", "").toLowerCase();
        }
    }
}
