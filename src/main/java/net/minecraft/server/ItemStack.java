package net.minecraft.server;

import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.registry.LegacyIdBridge;
import net.minecraft.server.util.ResourceLocation;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemDamageEvent;

public final class ItemStack {

    public int count;
    public int b;
    public int id;
    public int damage; // CraftBukkit - private -> public
    /** NBT tag compound for extra item data (books, enchantments, etc.) */
    public NBTTagCompound tag;

    /** 1.21-style runtime item reference */
    private Holder<Item> itemHolder;
    /** 1.21-style component delta */
    private DataComponentPatch componentPatch = DataComponentPatch.empty();
    /** Resolved default+patch component view */
    private PatchedDataComponentMap patchedComponents = new PatchedDataComponentMap(DataComponentMap.EMPTY, DataComponentPatch.empty());
    /** Legacy-to-modern cache coherence markers */
    private int cachedLegacyId = Integer.MIN_VALUE;
    private int cachedLegacyDamage = Integer.MIN_VALUE;
    private NBTTagCompound cachedLegacyTag = null;

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
        invalidateModernState();
    }

    public ItemStack(NBTTagCompound nbttagcompound) {
        this.count = 0;
        this.b(nbttagcompound);
    }

    public ItemStack a(int i) {
        this.count -= i;
        ItemStack newStack = new ItemStack(this.id, i, this.damage);
        newStack.itemHolder = this.getItemHolder();
        newStack.componentPatch = this.getComponents().copy();
        Item item = newStack.getItem();
        DataComponentMap defaults = item == null ? DataComponentMap.EMPTY : ItemComponentDefaults.defaultsFor(item);
        newStack.patchedComponents = new PatchedDataComponentMap(defaults, newStack.componentPatch);
        newStack.projectLegacyStateFromComponents();
        newStack.normalizeLegacyInventoryItemStates();
        newStack.markModernStateFresh();
        return newStack;
    }

    public Item getItem() {
        if (this.id < 0 || this.id >= Item.byId.length) {
            return null;
        }
        return Item.byId[this.id];
    }

    public boolean placeItem(EntityHuman entityhuman, World world, int i, int j, int k, int l) {
        return this.placeItem(entityhuman, world, i, j, k, l, Double.NaN);
    }

    public boolean placeItem(EntityHuman entityhuman, World world, int i, int j, int k, int l,
                             double placementHitY) {
        Item item = this.getItem();
        boolean flag = item instanceof ItemBlock
                ? ((ItemBlock) item).placeItemWithHit(this, entityhuman, world, i, j, k, l, placementHitY)
                : item.a(this, entityhuman, world, i, j, k, l);

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
        ensureModernState();
        return ModernItemStackCodec.write(this, nbttagcompound);
    }

    public void b(NBTTagCompound nbttagcompound) {
        ItemStack decoded = ModernItemStackCodec.isModernFormat(nbttagcompound)
                ? ModernItemStackCodec.read(nbttagcompound)
                : LegacyItemStackCodec.readFromLegacyNbt(nbttagcompound);

        if (decoded == null) {
            this.id = 0;
            this.count = 0;
            this.damage = 0;
            this.tag = null;
            this.itemHolder = null;
            this.componentPatch = DataComponentPatch.empty();
            this.patchedComponents = new PatchedDataComponentMap(DataComponentMap.EMPTY, this.componentPatch);
            invalidateModernState();
            return;
        }

        this.count = decoded.count;
        this.id = decoded.id;
        this.damage = decoded.damage;
        this.tag = decoded.tag;
        this.itemHolder = decoded.itemHolder;
        this.componentPatch = decoded.componentPatch == null ? DataComponentPatch.empty() : decoded.componentPatch.copy();
        Item item = this.getItem();
        DataComponentMap defaults = item == null ? DataComponentMap.EMPTY : ItemComponentDefaults.defaultsFor(item);
        this.patchedComponents = new PatchedDataComponentMap(defaults, this.componentPatch);
        normalizeLegacyInventoryItemStates();
        markModernStateFresh();
    }

    private void normalizeLegacyInventoryItemStates() {
        // Unlit redstone torch (id 75) is a block state, not a legal inventory item.
        if (Block.REDSTONE_TORCH_OFF != null && Block.REDSTONE_TORCH_ON != null && this.id == Block.REDSTONE_TORCH_OFF.id) {
            this.id = Block.REDSTONE_TORCH_ON.id;
        }

        // Placed-state IDs can occur in old inventories and packets. Normalize
        // them to the usable inventory items before RegionCore stores the stack.
        if ((Block.SIGN_POST != null && this.id == Block.SIGN_POST.id)
                || (Block.WALL_SIGN != null && this.id == Block.WALL_SIGN.id)) {
            this.id = Item.SIGN.id;
        } else if (Block.SUGAR_CANE_BLOCK != null && this.id == Block.SUGAR_CANE_BLOCK.id) {
            this.id = Item.SUGAR_CANE.id;
        } else if (Block.WOODEN_DOOR != null && this.id == Block.WOODEN_DOOR.id) {
            this.id = Item.WOOD_DOOR.id;
        } else if (Block.IRON_DOOR_BLOCK != null && this.id == Block.IRON_DOOR_BLOCK.id) {
            this.id = Item.IRON_DOOR.id;
        } else if (Block.BED != null && this.id == Block.BED.id) {
            this.id = Item.BED.id;
        } else if (Block.CAKE_BLOCK != null && this.id == Block.CAKE_BLOCK.id) {
            this.id = Item.CAKE.id;
        } else if ((Block.DIODE_OFF != null && this.id == Block.DIODE_OFF.id)
                || (Block.DIODE_ON != null && this.id == Block.DIODE_ON.id)) {
            this.id = Item.DIODE.id;
        }
    }

    /**
     * Returns true if this item stack has an NBT tag compound.
     */
    public boolean hasTag() {
        return this.tag != null;
    }

    public boolean hasTagCompound() {
        return this.hasTag();
    }

    /**
     * Gets the NBT tag compound for this item stack.
     */
    public NBTTagCompound getTag() {
        return this.tag;
    }

    public NBTTagCompound getTagCompound() {
        return this.getTag();
    }

    /**
     * Sets the NBT tag compound for this item stack.
     */
    public void setTag(NBTTagCompound nbt) {
        this.tag = nbt;
        invalidateModernState();
    }

    public void setTagCompound(NBTTagCompound nbt) {
        this.setTag(nbt);
    }

    public int getMaxStackSize() {
        Integer componentLimit = this.getPatchedComponents().get(DataComponents.MAX_STACK_SIZE);
        return componentLimit == null ? 1 : componentLimit.intValue();
    }

    /** Returns the stack limit that applies under the target world's gamerules. */
    public int getMaxStackSize(World world) {
        boolean foodStacking = world != null && world.worldData != null
                && world.worldData.getToggleFoodStacking();
        return this.getMaxStackSize(foodStacking);
    }

    public int getMaxStackSize(boolean foodStacking) {
        if (this.hasExplicitMaxStackSizeComponent()) {
            return this.getMaxStackSize();
        }
        return foodStacking && this.getItem() instanceof ItemFood ? 4 : this.getMaxStackSize();
    }

    /** Packet decoding must accept food stacks that are valid in enabled worlds. */
    public int getNetworkMaxStackSize() {
        if (this.hasExplicitMaxStackSizeComponent()) {
            return this.getMaxStackSize();
        }
        return this.getItem() instanceof ItemFood ? Math.max(4, this.getMaxStackSize()) : this.getMaxStackSize();
    }

    private boolean hasExplicitMaxStackSizeComponent() {
        DataComponentPatch patch = this.getComponents();
        return patch.getSetValues().containsKey(DataComponents.MAX_STACK_SIZE)
                || patch.getRemovedTypes().contains(DataComponents.MAX_STACK_SIZE);
    }

    public boolean isStackable() {
        return this.getMaxStackSize() > 1 && (!this.d() || !this.f());
    }

    public boolean isStackable(World world) {
        return this.getMaxStackSize(world) > 1 && (!this.d() || !this.f());
    }

    public boolean d() {
        PatchedDataComponentMap components = this.getPatchedComponents();
        return components.get(DataComponents.MAX_DAMAGE) != null
                && components.get(DataComponents.DAMAGE) != null;
    }

    public boolean usesData() {
        return Item.byId[this.id].d();
    }

    public boolean f() {
        return this.d() && this.g() > 0;
    }

    public int g() {
        if (!this.usesDurabilityComponents()) {
            return this.damage;
        }
        Integer componentDamage = this.getPatchedComponents().get(DataComponents.DAMAGE);
        int value = componentDamage == null ? 0 : componentDamage.intValue();
        return Math.max(0, Math.min(value, this.i()));
    }

    public int getData() {
        return this.g();
    }

    public int getItemDamage() {
        return this.g();
    }

    public void b(int i) {
        ensureModernState();
        int value = this.usesDurabilityComponents()
                ? Math.max(0, Math.min(i, this.i()))
                : i;
        this.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.DAMAGE, Integer.valueOf(value))
                .build());
    }

    public void setItemDamage(int i) {
        this.b(i);
    }

    public int i() {
        Integer maximumDamage = this.getPatchedComponents().get(DataComponents.MAX_DAMAGE);
        return maximumDamage == null ? 0 : maximumDamage.intValue();
    }

    private boolean usesDurabilityComponents() {
        Item item = this.getItem();
        return item != null && item.e() > 0
                || this.getPatchedComponents().get(DataComponents.MAX_DAMAGE) != null
                || this.getComponents().getRemovedTypes().contains(DataComponents.MAX_DAMAGE);
    }

    @SuppressWarnings("deprecation")
    public void damage(int i, Entity entity) {
        if (this.d()) {
            if (entity instanceof EntityPlayer && ((EntityPlayer) entity).gameMode == 1) {
                return;
            }
            if (entity instanceof EntityPlayer) {
                PlayerItemDamageEvent event = new PlayerItemDamageEvent((Player) entity.getBukkitEntity(), new CraftItemStack(this), i);
                event.getPlayer().getServer().getPluginManager().callEvent(event);
                if (i != event.getDamage() || event.isCancelled()) event.getPlayer().updateInventory();
                if (event.isCancelled()) return;
                i = event.getDamage();
            }
            this.b(this.g() + i);
            if (this.g() >= this.i()) {
                if (entity instanceof EntityHuman) {
                    ((EntityHuman) entity).a(StatisticList.F[this.id], 1);
                }

                --this.count;
                if (this.count < 0) {
                    this.count = 0;
                }
            }
        }
    }

    public void a(EntityLiving entityliving, EntityHuman entityhuman) {
        Item item = this.getItem();
        if (item == null) {
            return;
        }

        boolean flag = item.a(this, entityliving, (EntityLiving) entityhuman);

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
        clone.itemHolder = this.getItemHolder();
        clone.componentPatch = this.getComponents().copy();
        Item item = clone.getItem();
        DataComponentMap defaults = item == null ? DataComponentMap.EMPTY : ItemComponentDefaults.defaultsFor(item);
        clone.patchedComponents = new PatchedDataComponentMap(defaults, clone.componentPatch);
        clone.projectLegacyStateFromComponents();
        clone.normalizeLegacyInventoryItemStates();
        clone.markModernStateFresh();
        return clone;
    }

    public static boolean equals(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack == null && itemstack1 == null ? true : (itemstack != null && itemstack1 != null ? itemstack.d(itemstack1) : false);
    }

    private boolean d(ItemStack itemstack) {
        return itemstack != null && this.count == itemstack.count
                && isSameItemSameComponents(this, itemstack);
    }

    /** 26.3 stack identity: logical item holder plus the complete resolved component set. */
    public static boolean isSameItemSameComponents(ItemStack first, ItemStack second) {
        return first == second || first != null && second != null
                && first.isSameItem(second)
                && (first.tag == null ? second.tag == null : first.tag.equals(second.tag))
                && first.getPatchedComponents().equals(second.getPatchedComponents());
    }

    /** Same logical item identity, independent of count and component patch. */
    public boolean isSameItem(ItemStack other) {
        if (other == null) {
            return false;
        }
        if (this == other) {
            return true;
        }

        Holder<Item> thisHolder = this.getItemHolder();
        Holder<Item> otherHolder = other.getItemHolder();
        if (thisHolder != null && otherHolder != null) {
            Item thisHeldItem = thisHolder.value();
            Item otherHeldItem = otherHolder.value();
            if (thisHeldItem != null && otherHeldItem != null) {
                return thisHeldItem == otherHeldItem;
            }
            ResourceLocation thisKey = thisHolder.key();
            ResourceLocation otherKey = otherHolder.key();
            if (thisKey != null && otherKey != null) {
                return thisKey.equals(otherKey);
            }
            int thisRuntimeId = thisHolder.runtimeId();
            int otherRuntimeId = otherHolder.runtimeId();
            if (thisRuntimeId >= 0 && otherRuntimeId >= 0) {
                return thisRuntimeId == otherRuntimeId;
            }
        }

        Item thisItem = this.getItem();
        Item otherItem = other.getItem();
        return thisItem != null && otherItem != null ? thisItem == otherItem : this.id == other.id;
    }

    /** Merge compatibility matching current container rules. */
    public boolean canStackWith(ItemStack other) {
        return isSameItemSameComponents(this, other);
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

    public Holder<Item> getItemHolder() {
        ensureModernState();
        return this.itemHolder;
    }

    public void setItemHolder(Holder<Item> holder) {
        if (holder == null) {
            this.itemHolder = null;
            invalidateModernState();
            return;
        }

        this.itemHolder = holder;
        Item heldItem = holder.value();
        if (heldItem != null) {
            this.id = ItemRegistry.getLegacyId(heldItem);
        } else if (holder.key() != null) {
            Integer bridged = LegacyIdBridge.itemIdFromKey(holder.key().toString());
            if (bridged != null) {
                this.id = bridged.intValue();
            }
        }
        normalizeLegacyInventoryItemStates();

        // Keep component state in sync with legacy fields while preserving explicit holder identity.
        rebuildModernStateFromLegacy();
        this.itemHolder = holder;
        markModernStateFresh();
    }

    public DataComponentPatch getComponents() {
        ensureModernState();
        return this.componentPatch;
    }

    public PatchedDataComponentMap getPatchedComponents() {
        ensureModernState();
        return this.patchedComponents;
    }

    public void applyComponents(DataComponentPatch patch) {
        if (patch == null || patch.isEmpty()) {
            return;
        }

        ensureModernState();
        this.componentPatch = DataComponentPatch.merge(this.componentPatch, patch);
        Item item = this.getItem();
        DataComponentMap defaults = item == null ? DataComponentMap.EMPTY : ItemComponentDefaults.defaultsFor(item);
        this.patchedComponents = new PatchedDataComponentMap(defaults, this.componentPatch);
        projectLegacyStateFromComponents();
        normalizeLegacyInventoryItemStates();
        markModernStateFresh();
    }

    public NBTTagCompound save(NBTTagCompound nbt) {
        return this.a(nbt);
    }

    public static ItemStack parse(NBTTagCompound nbt) {
        if (nbt == null) {
            return null;
        }
        return ModernItemStackCodec.isModernFormat(nbt) ? ModernItemStackCodec.read(nbt) : LegacyItemStackCodec.readFromLegacyNbt(nbt);
    }

    private void ensureModernState() {
        if (this.cachedLegacyId == this.id
                && this.cachedLegacyDamage == this.damage
                && this.cachedLegacyTag == this.tag
                && this.itemHolder != null
                && this.componentPatch != null
                && this.patchedComponents != null) {
            return;
        }
        rebuildModernStateFromLegacy();
    }

    private void rebuildModernStateFromLegacy() {
        Item item = this.getItem();
        Holder<Item> holder = item == null ? null : ItemRegistry.getHolder(item);
        if (holder == null) {
            ResourceLocation key = null;
            if (item != null) {
                key = ItemRegistry.getKey(item);
            }
            if (key == null) {
                String bridged = LegacyIdBridge.itemKeyFromId(this.id);
                if (bridged != null) {
                    try {
                        key = new ResourceLocation(bridged);
                    } catch (Throwable ignored) {}
                }
            }
            if (key == null) {
                key = new ResourceLocation("minecraft", "unregistered_item_" + this.id);
            }
            holder = Holder.direct(key, item, this.id);
        }
        this.itemHolder = holder;

        DataComponentPatch.Builder patchBuilder = DataComponentPatch.builder();
        if (this.damage != 0) {
            patchBuilder.set(DataComponents.DAMAGE, Integer.valueOf(this.damage));
        }
        if (this.tag != null) {
            patchBuilder.set(DataComponents.CUSTOM_DATA, this.tag);
            if (this.tag.hasKey("display")) {
                NBTTagCompound display = this.tag.k("display");
                if (display != null && display.hasKey("Name")) {
                    patchBuilder.set(DataComponents.CUSTOM_NAME, display.getString("Name"));
                }
            }
            if (this.tag.hasKey("ench")) {
                patchBuilder.set(DataComponents.ENCHANTMENTS, this.tag.l("ench"));
            }
            if (this.tag.hasKey("Items")) {
                patchBuilder.set(DataComponents.CONTAINER, this.tag.l("Items"));
            }
        }

        this.componentPatch = patchBuilder.build();
        DataComponentMap defaults = item == null ? DataComponentMap.EMPTY : ItemComponentDefaults.defaultsFor(item);
        this.patchedComponents = new PatchedDataComponentMap(defaults, this.componentPatch);
        markModernStateFresh();
    }

    private void projectLegacyStateFromComponents() {
        if (this.itemHolder != null && this.itemHolder.value() != null) {
            this.id = ItemRegistry.getLegacyId(this.itemHolder.value());
        } else if (this.itemHolder != null && this.itemHolder.key() != null) {
            Integer bridged = LegacyIdBridge.itemIdFromKey(this.itemHolder.key().toString());
            if (bridged != null) {
                this.id = bridged.intValue();
            }
        }

        Integer patchedDamage = this.patchedComponents.get(DataComponents.DAMAGE);
        this.damage = patchedDamage == null ? 0 : patchedDamage.intValue();

        this.tag = this.patchedComponents.get(DataComponents.CUSTOM_DATA);

        String customName = this.patchedComponents.get(DataComponents.CUSTOM_NAME);
        if (customName != null && customName.length() > 0) {
            if (this.tag == null) {
                this.tag = new NBTTagCompound();
            }
            NBTTagCompound display = this.tag.hasKey("display") ? this.tag.k("display") : new NBTTagCompound();
            display.setString("Name", customName);
            this.tag.a("display", display);
        }

        NBTTagList enchantments = this.patchedComponents.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            if (this.tag == null) {
                this.tag = new NBTTagCompound();
            }
            this.tag.a("ench", (NBTBase)enchantments);
        }

        NBTTagList container = this.patchedComponents.get(DataComponents.CONTAINER);
        if (container != null) {
            if (this.tag == null) {
                this.tag = new NBTTagCompound();
            }
            this.tag.a("Items", (NBTBase)container);
        }
    }

    private void invalidateModernState() {
        this.cachedLegacyId = Integer.MIN_VALUE;
        this.cachedLegacyDamage = Integer.MIN_VALUE;
        this.cachedLegacyTag = null;
    }

    private void markModernStateFresh() {
        this.cachedLegacyId = this.id;
        this.cachedLegacyDamage = this.damage;
        this.cachedLegacyTag = this.tag;
    }
}
