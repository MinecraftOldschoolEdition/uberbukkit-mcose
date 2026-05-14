package net.minecraft.server;

public class ItemNameTag extends Item {
	public static final int MAX_NAME_LENGTH = 32;
	private static final String NAME_KEY = "Name";

	public ItemNameTag(int i) {
		super(i);
		this.maxStackSize = 1;
	}

	public boolean a(ItemStack itemstack, EntityLiving entityliving, EntityLiving user) {
		if(!(user instanceof EntityHuman)) {
			return false;
		}
		return applyToEntity(itemstack, (EntityHuman)user, entityliving);
	}

	public static boolean isNameTagItemStack(ItemStack itemstack) {
		return itemstack != null && itemstack.getItem() == Item.NAME_TAG;
	}

	public static boolean hasStoredName(ItemStack itemstack) {
		return getStoredName(itemstack).length() > 0;
	}

	public static String getStoredName(ItemStack itemstack) {
		if(itemstack == null || !itemstack.hasTag()) {
			return "";
		}

		NBTTagCompound tag = itemstack.getTag();
		return tag != null && tag.hasKey(NAME_KEY) ? sanitizeName(tag.getString(NAME_KEY)) : "";
	}

	public static void setStoredName(ItemStack itemstack, String name) {
		if(itemstack == null) {
			return;
		}

		String sanitized = sanitizeName(name);
		NBTTagCompound tag = itemstack.getTag();
		if(tag == null) {
			tag = new NBTTagCompound();
			itemstack.setTag(tag);
		}

		if(sanitized.length() == 0) {
			tag.remove(NAME_KEY);
			if(tag.c().isEmpty()) {
				itemstack.setTag((NBTTagCompound)null);
			}
		} else {
			tag.setString(NAME_KEY, sanitized);
			itemstack.setTag(tag);
		}
	}

	public static String sanitizeName(String name) {
		if(name == null || name.length() == 0) {
			return "";
		}

		StringBuilder filtered = new StringBuilder(name.length());
		for(int i = 0; i < name.length(); ++i) {
			char c = name.charAt(i);
			if(FontAllowedCharacters.isAllowedCharacter(c)) {
				filtered.append(c);
			}
		}

		String out = filtered.toString().trim();
		return out.length() > MAX_NAME_LENGTH ? out.substring(0, MAX_NAME_LENGTH) : out;
	}

	public static boolean applyToEntity(ItemStack itemstack, EntityHuman player, EntityLiving entityliving) {
		if(!isNameTagItemStack(itemstack) || player == null || entityliving == null || entityliving instanceof EntityHuman) {
			return false;
		}

		String name = getStoredName(itemstack);
		if(name.length() == 0) {
			return false;
		}

		entityliving.setCustomName(name);
		syncNameToClients(entityliving);
		if(player.gameMode != 1) {
			--itemstack.count;
		}
		return true;
	}

	private static void syncNameToClients(EntityLiving entityliving) {
		if(entityliving == null || entityliving.world == null || entityliving.world.isStatic || !(entityliving.world instanceof WorldServer)) {
			return;
		}

		((WorldServer)entityliving.world).tracker.sendPacketToEntity(entityliving, new Packet40EntityMetadata(entityliving));
	}
}
