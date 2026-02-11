package net.minecraft.server;

import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemDamageEvent;
import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.util.ResourceLocation;

public final class ItemStack {

    public int count;
    public int b;
    public int id;
    public int damage; // CraftBukkit - private -> public
    /** NBT tag compound for extra item data (books, enchantments, etc.) */
    public NBTTagCompound tag;

    public ItemStack(Block block) {
        this(block, 1);
    }

    public ItemStack(Block block, int i) {
        this(block.id, i, 0);
    }

    public ItemStack(Block block, int i, int j) {
        this(block.id, i, j);
    }

    public ItemStack(Item item) {
        this(item.id, 1, 0);
    }

    public ItemStack(Item item, int i) {
        this(item.id, i, 0);
    }

    public ItemStack(Item item, int i, int j) {
        this(item.id, i, j);
    }

    public ItemStack(int i, int j, int k) {
        this.count = 0;
        this.id = i;
        this.count = j;
        this.damage = k;
        normalizeLegacyInventoryItemStates();
    }

    public ItemStack(NBTTagCompound nbttagcompound) {
        this.count = 0;
        this.b(nbttagcompound);
    }

    public ItemStack a(int i) {
        this.count -= i;
        ItemStack newStack = new ItemStack(this.id, i, this.damage);
        // Note: NBT tag is NOT copied when splitting stacks
        // This is acceptable because items with NBT (like books) have maxStackSize=1
        return newStack;
    }

    public Item getItem() {
        return Item.byId[this.id];
    }

    public boolean placeItem(EntityHuman entityhuman, World world, int i, int j, int k, int l) {
        boolean flag = this.getItem().a(this, entityhuman, world, i, j, k, l);

        if (flag) {
            entityhuman.a(StatisticList.E[this.id], 1);
        }

        return flag;
    }

    public float a(Block block) {
        return this.getItem().a(this, block);
    }

    public ItemStack a(World world, EntityHuman entityhuman) {
        return this.getItem().a(this, world, entityhuman);
    }

    public NBTTagCompound a(NBTTagCompound nbttagcompound) {
        nbttagcompound.a("id", (short) this.id);
        nbttagcompound.a("Count", (byte) this.count);
        nbttagcompound.a("Damage", (short) this.damage);
        Item item = this.getItem();
        if (item != null) {
            ResourceLocation key = ItemRegistry.getKey(item);
            if (key != null) {
                nbttagcompound.setString("name", key.toString());
            }
        }
        if (this.tag != null) {
            nbttagcompound.a("tag", this.tag);
        }
        return nbttagcompound;
    }

    public void b(NBTTagCompound nbttagcompound) {
        this.count = nbttagcompound.c("Count");
        this.damage = nbttagcompound.d("Damage");

        if (nbttagcompound.hasKey("name")) {
            String name = nbttagcompound.getString("name");
            try {
                ResourceLocation key = new ResourceLocation(name);
                Item item = ItemRegistry.get(key);
                if (item != null) {
                    this.id = ItemRegistry.getLegacyId(item);
                    normalizeLegacyInventoryItemStates();
                    if (nbttagcompound.hasKey("tag")) {
                        this.tag = nbttagcompound.k("tag");
                    }
                    return;
                }
            } catch (Throwable ignored) {}
        }

        this.id = nbttagcompound.d("id");
        normalizeLegacyInventoryItemStates();

        if (nbttagcompound.hasKey("tag")) {
            this.tag = nbttagcompound.k("tag");
        }
    }

    private void normalizeLegacyInventoryItemStates() {
        // Unlit redstone torch (id 75) is a block state, not a legal inventory item.
        if (Block.REDSTONE_TORCH_OFF != null && Block.REDSTONE_TORCH_ON != null && this.id == Block.REDSTONE_TORCH_OFF.id) {
            this.id = Block.REDSTONE_TORCH_ON.id;
        }
    }
    
    /**
     * Returns true if this item stack has an NBT tag compound.
     */
    public boolean hasTag() {
        return this.tag != null;
    }
    
    /**
     * Gets the NBT tag compound for this item stack.
     */
    public NBTTagCompound getTag() {
        return this.tag;
    }
    
    /**
     * Sets the NBT tag compound for this item stack.
     */
    public void setTag(NBTTagCompound nbt) {
        this.tag = nbt;
    }

    public int getMaxStackSize() {
        return this.getItem().getMaxStackSize();
    }

    public boolean isStackable() {
        return this.getMaxStackSize() > 1 && (!this.d() || !this.f());
    }

    public boolean d() {
        return Item.byId[this.id].e() > 0;
    }

    public boolean usesData() {
        return Item.byId[this.id].d();
    }

    public boolean f() {
        return this.d() && this.damage > 0;
    }

    public int g() {
        return this.damage;
    }

    public int getData() {
        return this.damage;
    }

    public void b(int i) {
        this.damage = i;
    }

    public int i() {
        return Item.byId[this.id].e();
    }

    @SuppressWarnings("deprecation")
    public void damage(int i, Entity entity) {
        if (this.d()) {
            if (entity instanceof EntityPlayer) {
                PlayerItemDamageEvent event = new PlayerItemDamageEvent((Player) entity.getBukkitEntity(), new CraftItemStack(this), i);
                event.getPlayer().getServer().getPluginManager().callEvent(event);
                if (i != event.getDamage() || event.isCancelled()) event.getPlayer().updateInventory();
                if (event.isCancelled()) return;
                i = event.getDamage();
            }
            this.damage += i;
            if (this.damage > this.i()) {
                if (entity instanceof EntityHuman) {
                    ((EntityHuman) entity).a(StatisticList.F[this.id], 1);
                }

                --this.count;
                if (this.count < 0) {
                    this.count = 0;
                }

                this.damage = 0;
            }
        }
    }

    public void a(EntityLiving entityliving, EntityHuman entityhuman) {
        boolean flag = Item.byId[this.id].a(this, entityliving, (EntityLiving) entityhuman);

        if (flag) {
            entityhuman.a(StatisticList.E[this.id], 1);
        }
    }

    public void a(int i, int j, int k, int l, EntityHuman entityhuman) {
        boolean flag = Item.byId[this.id].a(this, i, j, k, l, entityhuman);

        if (flag) {
            entityhuman.a(StatisticList.E[this.id], 1);
        }
    }

    public int a(Entity entity) {
        return Item.byId[this.id].a(entity);
    }

    public boolean b(Block block) {
        return Item.byId[this.id].a(block);
    }

    public void a(EntityHuman entityhuman) {
    }

    public void a(EntityLiving entityliving) {
        Item.byId[this.id].a(this, entityliving);
    }

    public ItemStack cloneItemStack() {
        ItemStack clone = new ItemStack(this.id, this.count, this.damage);
        clone.tag = this.tag; // Shallow copy of tag reference
        return clone;
    }

    public static boolean equals(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack == null && itemstack1 == null ? true : (itemstack != null && itemstack1 != null ? itemstack.d(itemstack1) : false);
    }

    private boolean d(ItemStack itemstack) {
        return this.count != itemstack.count ? false : (this.id != itemstack.id ? false : this.damage == itemstack.damage);
    }

    public boolean doMaterialsMatch(ItemStack itemstack) {
        return this.id == itemstack.id && this.damage == itemstack.damage;
    }

    public static ItemStack b(ItemStack itemstack) {
        return itemstack == null ? null : itemstack.cloneItemStack();
    }

    public String toString() {
        return this.count + "x" + Item.byId[this.id].a() + "@" + this.damage;
    }

    public void a(World world, Entity entity, int i, boolean flag) {
        if (this.b > 0) {
            --this.b;
        }

        Item.byId[this.id].a(this, world, entity, i, flag);
    }

    public void b(World world, EntityHuman entityhuman) {
        entityhuman.a(StatisticList.D[this.id], this.count);
        Item.byId[this.id].c(this, world, entityhuman);
    }

    public boolean c(ItemStack itemstack) {
        return this.id == itemstack.id && this.count == itemstack.count && this.damage == itemstack.damage;
    }
}
