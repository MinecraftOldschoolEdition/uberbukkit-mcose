package net.minecraft.server;

import net.minecraft.server.registry.BlockTags;

import net.minecraft.server.registry.PlayerCapabilityRegistryApi;

public abstract class EntityAnimal extends EntityCreature implements IAnimal {
    private static final double LEASH_FOLLOW_DISTANCE_SQ = 4.0D;
    private static final double LEASH_ELASTIC_DISTANCE_SQ = 36.0D;
    private static final double LEASH_SNAP_DISTANCE_SQ = 144.0D;

    private static final EntityDataAccessor<Integer> DATA_LEASH_STATE_ID = new EntityDataAccessor<Integer>(19, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_LEASH_HOLDER_ID = new EntityDataAccessor<String>(20, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_LEASH_FENCE_X_ID = new EntityDataAccessor<Integer>(21, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LEASH_FENCE_Y_ID = new EntityDataAccessor<Integer>(22, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LEASH_FENCE_Z_ID = new EntityDataAccessor<Integer>(23, EntityDataSerializers.INT);

    private String leashHolderName;
    private boolean leashToFence;
    private int leashFenceX;
    private int leashFenceY;
    private int leashFenceZ;

    public EntityAnimal(World world) {
        super(world);
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.getSynchedEntityData().define(DATA_LEASH_STATE_ID, Integer.valueOf(0));
        this.getSynchedEntityData().define(DATA_LEASH_HOLDER_ID, "");
        this.getSynchedEntityData().define(DATA_LEASH_FENCE_X_ID, Integer.valueOf(0));
        this.getSynchedEntityData().define(DATA_LEASH_FENCE_Y_ID, Integer.valueOf(0));
        this.getSynchedEntityData().define(DATA_LEASH_FENCE_Z_ID, Integer.valueOf(0));
    }

    protected float a(int i, int j, int k) {
        return this.world.getTypeId(i, j - 1, k) == Block.GRASS.id ? 10.0F : this.world.n(i, j, k) - 0.5F;
    }

    protected void c_() {
        if (this.isLeashed()) {
            this.ay = 0;
            this.resetLeashFallDistance();
        }

        this.updateLeashedState();
        super.c_();

        if (this.isLeashed()) {
            this.ay = 0;
            this.resetLeashFallDistance();
        }
    }

    protected boolean h_() {
        return !this.isLeashed() && super.h_();
    }

    public void b(NBTTagCompound nbttagcompound) {
        super.b(nbttagcompound);
        if (this.isLeashed()) {
            NBTTagCompound entityData = nbttagcompound.hasKey("entity_data") ? nbttagcompound.k("entity_data") : new NBTTagCompound();

            if (this.leashToFence) {
                nbttagcompound.a("LeashToFence", true);
                nbttagcompound.a("LeashFenceX", this.leashFenceX);
                nbttagcompound.a("LeashFenceY", this.leashFenceY);
                nbttagcompound.a("LeashFenceZ", this.leashFenceZ);
                entityData.a("leash_to_fence", true);
                entityData.a("leash_fence_x", this.leashFenceX);
                entityData.a("leash_fence_y", this.leashFenceY);
                entityData.a("leash_fence_z", this.leashFenceZ);
            } else {
                nbttagcompound.setString("LeashHolder", this.leashHolderName);
                entityData.setString("leash_holder", this.leashHolderName);
            }

            nbttagcompound.a("entity_data", entityData);
        }
    }

    public void a(NBTTagCompound nbttagcompound) {
        super.a(nbttagcompound);
        this.clearLeashStateFields();

        if (nbttagcompound.hasKey("entity_data")) {
            NBTTagCompound entityData = nbttagcompound.k("entity_data");
            if (entityData.m("leash_to_fence")) {
                this.setLeashFenceState(entityData.e("leash_fence_x"), entityData.e("leash_fence_y"), entityData.e("leash_fence_z"));
                this.syncLeashData();
                return;
            }

            if (entityData.hasKey("leash_holder")) {
                this.setLeashHolderState(entityData.getString("leash_holder"));
                this.syncLeashData();
                return;
            }
        }

        if (nbttagcompound.hasKey("LeashToFence") && nbttagcompound.m("LeashToFence")) {
            this.setLeashFenceState(nbttagcompound.e("LeashFenceX"), nbttagcompound.e("LeashFenceY"), nbttagcompound.e("LeashFenceZ"));
        } else if (nbttagcompound.hasKey("LeashHolder")) {
            this.setLeashHolderState(nbttagcompound.getString("LeashHolder"));
        }

        this.syncLeashData();
    }

    protected void updateLeashedState() {
        if (!this.isLeashed()) {
            return;
        }

        if (this.leashToFence) {
            this.updateFenceLeashedState();
            return;
        }

        Entity leashHolder = this.getLeashHolderEntity();
        if (!(leashHolder instanceof EntityHuman) || leashHolder.dead) {
            this.clearLeashed(true);
            return;
        }

        boolean fleeingWolf = false;
        if (this instanceof EntityWolf) {
            EntityWolf wolf = (EntityWolf) this;
            if (wolf.isSitting()) {
                wolf.setSitting(false);
            }
            fleeingWolf = wolf.isLeashFleeing();
        }

        double deltaX = leashHolder.locX - this.locX;
        double deltaY = leashHolder.locY + (double) leashHolder.t() - this.locY;
        double deltaZ = leashHolder.locZ - this.locZ;
        double distanceSq = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
        boolean holderFlying = this.isLeashHolderFlying(leashHolder);

        if (distanceSq > LEASH_SNAP_DISTANCE_SQ) {
            this.world.makeSound(this, "random.break", 1.0F, 1.0F);
            this.clearLeashed(true);
            return;
        }

        if (distanceSq > LEASH_ELASTIC_DISTANCE_SQ) {
            float distance = MathHelper.a(distanceSq);
            if (distance > 0.0F) {
                double horizontalPull = holderFlying ? 0.11D : 0.08D;
                double verticalPull = holderFlying ? 0.16D : 0.06D;
                this.motX += deltaX / (double) distance * horizontalPull;
                this.motY += deltaY / (double) distance * verticalPull;
                this.motZ += deltaZ / (double) distance * horizontalPull;
                if (holderFlying && deltaY > 1.0D) {
                    this.motY += Math.min(0.18D, deltaY * 0.02D);
                }
            }

        }

        if (distanceSq > LEASH_FOLLOW_DISTANCE_SQ && !holderFlying && !fleeingWolf) {
            this.setPathEntity(this.world.findPath(this, leashHolder, 16.0F));
        }
    }

    private void updateFenceLeashedState() {
        if (!this.isValidLeashFence(this.leashFenceX, this.leashFenceY, this.leashFenceZ)) {
            this.clearLeashed(true);
            return;
        }

        if (this instanceof EntityWolf) {
            EntityWolf wolf = (EntityWolf) this;
            if (wolf.isSitting()) {
                wolf.setSitting(false);
            }
        }

        double anchorX = (double) this.leashFenceX + 0.5D;
        double anchorY = (double) this.leashFenceY + 1.0D;
        double anchorZ = (double) this.leashFenceZ + 0.5D;
        double deltaX = anchorX - this.locX;
        double deltaY = anchorY - this.locY;
        double deltaZ = anchorZ - this.locZ;
        double distanceSq = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;

        if (distanceSq > LEASH_SNAP_DISTANCE_SQ) {
            this.world.makeSound(this, "random.break", 1.0F, 1.0F);
            this.clearLeashed(true);
            return;
        }

        if (distanceSq > LEASH_ELASTIC_DISTANCE_SQ) {
            float distance = MathHelper.a(distanceSq);
            if (distance > 0.0F) {
                this.motX += deltaX / (double) distance * 0.08D;
                this.motY += deltaY / (double) distance * 0.06D;
                this.motZ += deltaZ / (double) distance * 0.08D;
            }
        }

        if (distanceSq > LEASH_FOLLOW_DISTANCE_SQ) {
            this.setPathEntity(this.world.a(this, this.leashFenceX, this.leashFenceY, this.leashFenceZ, 16.0F));
        }
    }

    private boolean isLeashHolderFlying(Entity leashHolder) {
        return leashHolder instanceof EntityPlayer && PlayerCapabilityRegistryApi.isFlying((EntityPlayer) leashHolder);
    }

    public boolean isLeashed() {
        return this.leashToFence || this.leashHolderName != null && this.leashHolderName.length() > 0;
    }

    public boolean isLeashedTo(EntityHuman entityhuman) {
        return entityhuman != null && !this.leashToFence && this.isLeashed() && entityhuman.name.equals(this.leashHolderName);
    }

    public boolean isLeashedToFence() {
        return this.isLeashed() && this.leashToFence;
    }

    public boolean isLeashedToFence(int x, int y, int z) {
        return this.isLeashedToFence() && this.leashFenceX == x && this.leashFenceY == y && this.leashFenceZ == z;
    }

    public int getLeashFenceX() {
        return this.leashFenceX;
    }

    public int getLeashFenceY() {
        return this.leashFenceY;
    }

    public int getLeashFenceZ() {
        return this.leashFenceZ;
    }

    public Entity getLeashHolderEntity() {
        if (!this.isLeashed() || this.world == null || this.leashToFence) {
            return null;
        }

        return this.world.a(this.leashHolderName);
    }

    public void setLeashedToPlayer(EntityHuman entityhuman) {
        if (entityhuman == null) {
            return;
        }

        this.setLeashHolderState(entityhuman.name);
        this.ay = 0;
        this.resetLeashFallDistance();
        this.syncLeashData();
    }

    public void setLeashedToFence(int x, int y, int z) {
        this.setLeashFenceState(x, y, z);
        this.ay = 0;
        this.resetLeashFallDistance();
        this.syncLeashData();
    }

    public void clearLeashed(boolean dropLead) {
        boolean wasLeashed = this.isLeashed();
        if (wasLeashed) {
            this.ay = 0;
            this.resetLeashFallDistance();
        }

        if (dropLead && wasLeashed && !this.world.isStatic) {
            this.a(new ItemStack(Item.LEAD, 1), 0.0F);
        }

        this.clearLeashStateFields();
        this.syncLeashData();
    }

    public void resetLeashFallDistance() {
        this.fallDistance = 0.0F;
    }

    private void clearLeashStateFields() {
        this.leashHolderName = null;
        this.leashToFence = false;
        this.leashFenceX = 0;
        this.leashFenceY = 0;
        this.leashFenceZ = 0;
    }

    private void setLeashHolderState(String holderName) {
        this.clearLeashStateFields();
        this.leashHolderName = holderName;
    }

    private void setLeashFenceState(int x, int y, int z) {
        this.leashHolderName = null;
        this.leashToFence = true;
        this.leashFenceX = x;
        this.leashFenceY = y;
        this.leashFenceZ = z;
    }

    private void syncLeashData() {
        if (this.getSynchedEntityData() == null) {
            return;
        }

        int state = this.leashToFence ? 2 : (this.leashHolderName != null && this.leashHolderName.length() > 0 ? 1 : 0);
        this.getSynchedEntityData().set(DATA_LEASH_STATE_ID, Integer.valueOf(state));
        this.getSynchedEntityData().set(DATA_LEASH_HOLDER_ID, this.leashHolderName == null ? "" : this.leashHolderName);
        this.getSynchedEntityData().set(DATA_LEASH_FENCE_X_ID, Integer.valueOf(this.leashFenceX));
        this.getSynchedEntityData().set(DATA_LEASH_FENCE_Y_ID, Integer.valueOf(this.leashFenceY));
        this.getSynchedEntityData().set(DATA_LEASH_FENCE_Z_ID, Integer.valueOf(this.leashFenceZ));
    }

    private boolean isValidLeashFence(int x, int y, int z) {
        if (this.world == null) {
            return false;
        }

        int blockId = this.world.getTypeId(x, y, z);
        if (blockId <= 0 || blockId >= Block.byId.length) {
            return false;
        }

        return Block.byId[blockId] instanceof BlockFence;
    }

    public boolean d() {
        int i = MathHelper.floor(this.locX);
        int j = MathHelper.floor(this.boundingBox.b);
        int k = MathHelper.floor(this.locZ);

        int belowId = this.world.getTypeId(i, j - 1, k);
        Block below = belowId > 0 && belowId < Block.byId.length
                ? Block.byId[belowId] : null;
        return below != null
                && BlockTags.is(below, BlockTags.ANIMALS_SPAWNABLE_ON)
                && this.world.k(i, j, k) > 8 && super.d();
    }

    public int e() {
        return 120;
    }
}
