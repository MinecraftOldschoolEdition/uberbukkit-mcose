package net.minecraft.server;

public abstract class EntitySnowmanBase extends EntityCreature {
	public EntitySnowmanBase(World var1) {
		super(var1);
	}

	protected void fall(float var1) {
	}

	public void a(NBTTagCompound var1) {
		super.a(var1);
	}

	public void b(NBTTagCompound var1) {
		super.b(var1);
	}

	protected String getLivingSound() {
		return "none";
	}

	protected String getHurtSound() {
		return "none";
	}

	protected String getDeathSound() {
		return "none";
	}

	public int getTalkInterval() {
		return 120;
	}

	protected boolean canDespawn() {
		return false;
	}
}


