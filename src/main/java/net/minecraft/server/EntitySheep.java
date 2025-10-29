package net.minecraft.server;

import java.util.Random;

import uk.betacraft.uberbukkit.UberbukkitConfig;

public class EntitySheep extends EntityAnimal {

    public static final float[][] a = new float[][] { { 1.0F, 1.0F, 1.0F }, { 0.95F, 0.7F, 0.2F }, { 0.9F, 0.5F, 0.85F }, { 0.6F, 0.7F, 0.95F }, { 0.9F, 0.9F, 0.2F }, { 0.5F, 0.8F, 0.1F }, { 0.95F, 0.7F, 0.8F }, { 0.3F, 0.3F, 0.3F }, { 0.6F, 0.6F, 0.6F }, { 0.3F, 0.6F, 0.7F }, { 0.7F, 0.4F, 0.9F }, { 0.2F, 0.4F, 0.8F }, { 0.5F, 0.4F, 0.3F }, { 0.4F, 0.5F, 0.2F }, { 0.8F, 0.3F, 0.3F }, { 0.1F, 0.1F, 0.1F } };

    // Eating animation/regrowth timer (mirrors 1.1 behavior)
    private int eatTimer;

    public EntitySheep(World world) {
        super(world);
        this.texture = "/mob/sheep.png";
        this.b(0.9F, 1.3F);
    }

    protected void b() {
        super.b();
        this.datawatcher.a(16, new Byte((byte) 0));
    }

    public boolean damageEntity(Entity entity, int i) {
        // uberbukkit
        if (UberbukkitConfig.getInstance().getBoolean("mechanics.sheep_drop_wool_on_punch", false)) {
            if (!this.world.isStatic && !this.isSheared() && entity instanceof EntityLiving) {
                this.setSheared(true);
                int j = 1 + this.random.nextInt(3);
                for (int k = 0; k < j; ++k) {
                    EntityItem entityitem = this.a(new ItemStack(Block.WOOL.id, 1, this.getColor()), 1.0F);
                    entityitem.motY += (double) (this.random.nextFloat() * 0.05F);
                    entityitem.motX += (double) ((this.random.nextFloat() - this.random.nextFloat()) * 0.1F);
                    entityitem.motZ += (double) ((this.random.nextFloat() - this.random.nextFloat()) * 0.1F);
                }
            }
        }

        return super.damageEntity(entity, i);
    }

    protected void q() {
        // CraftBukkit start - whole method
        java.util.List<org.bukkit.inventory.ItemStack> loot = new java.util.ArrayList<org.bukkit.inventory.ItemStack>();

        if (!this.isSheared()) {
            loot.add(new org.bukkit.inventory.ItemStack(org.bukkit.Material.WOOL, 1, (short) 0, (byte) this.getColor()));
        }

        org.bukkit.World bworld = this.world.getWorld();
        org.bukkit.entity.Entity entity = this.getBukkitEntity();

        org.bukkit.event.entity.EntityDeathEvent event = new org.bukkit.event.entity.EntityDeathEvent(entity, loot);
        this.world.getServer().getPluginManager().callEvent(event);

        for (org.bukkit.inventory.ItemStack stack : event.getDrops()) {
            bworld.dropItemNaturally(entity.getLocation(), stack);
        }
        // CraftBukkit end
    }

    protected int j() {
        return Block.WOOL.id;
    }

    // Tick and animation hooks for eating grass and regrowing wool
    public void R() {
        super.R();
        if (this.eatTimer > 0) {
            --this.eatTimer;
        }
    }

    protected void O() {
        if (this.eatTimer <= 0) {
            super.O();
        }
    }

    protected void c_() {
        super.c_();
        int x;
        int y;
        int z;
        if (!this.C() && this.eatTimer <= 0 && (this.random.nextInt(1000) == 0)) {
            x = MathHelper.floor(this.locX);
            y = MathHelper.floor(this.locY);
            z = MathHelper.floor(this.locZ);
            if ((this.world.getTypeId(x, y, z) == Block.LONG_GRASS.id && this.world.getData(x, y, z) == 1) || this.world.getTypeId(x, y - 1, z) == Block.GRASS.id) {
                this.eatTimer = 40;
                // Broadcast sheep eating animation to clients
                if (!this.world.isStatic && this.world instanceof WorldServer) {
                    ((WorldServer) this.world).a(this, (byte) 10);
                }
            }
        } else if (this.eatTimer == 4) {
            x = MathHelper.floor(this.locX);
            y = MathHelper.floor(this.locY);
            z = MathHelper.floor(this.locZ);
            boolean ate = false;
            if (this.world.getTypeId(x, y, z) == Block.LONG_GRASS.id) {
                // Remove tall grass with effect
                this.world.a("blockcrack_31_" + this.world.getData(x, y, z), x + 0.5D, y + 0.5D, z + 0.5D, 0.0D, 0.0D, 0.0D);
                this.world.setTypeId(x, y, z, 0);
                ate = true;
            } else if (this.world.getTypeId(x, y - 1, z) == Block.GRASS.id) {
                // Convert grass to dirt
                this.world.a("blockcrack_2_0", x + 0.5D, y - 0.5D, z + 0.5D, 0.0D, 0.0D, 0.0D);
                this.world.setTypeId(x, y - 1, z, Block.DIRT.id);
                ate = true;
            }

            if (ate) {
                this.setSheared(false);
            }
        }
    }

    public boolean a(EntityHuman entityhuman) {
        ItemStack itemstack = entityhuman.inventory.getItemInHand();

        if (itemstack != null && itemstack.id == Item.SHEARS.id && !this.isSheared()) {
            if (!this.world.isStatic) {
                this.setSheared(true);
                int i = 2 + this.random.nextInt(3);

                for (int j = 0; j < i; ++j) {
                    EntityItem entityitem = this.a(new ItemStack(Block.WOOL.id, 1, this.getColor()), 1.0F);

                    entityitem.motY += (double) (this.random.nextFloat() * 0.05F);
                    entityitem.motX += (double) ((this.random.nextFloat() - this.random.nextFloat()) * 0.1F);
                    entityitem.motZ += (double) ((this.random.nextFloat() - this.random.nextFloat()) * 0.1F);
                }
            }

            itemstack.damage(1, entityhuman);
        }

        return false;
    }

    public void b(NBTTagCompound nbttagcompound) {
        super.b(nbttagcompound);
        nbttagcompound.a("Sheared", this.isSheared());
        nbttagcompound.a("Color", (byte) this.getColor());
    }

    public void a(NBTTagCompound nbttagcompound) {
        super.a(nbttagcompound);
        this.setSheared(nbttagcompound.m("Sheared"));
        this.setColor(nbttagcompound.c("Color"));
    }

    protected String g() {
        return "mob.sheep";
    }

    protected String h() {
        return "mob.sheep";
    }

    protected String i() {
        return "mob.sheep";
    }

    public int getColor() {
        return this.datawatcher.a(16) & 15;
    }

    public void setColor(int i) {
        byte b0 = this.datawatcher.a(16);

        this.datawatcher.watch(16, Byte.valueOf((byte) (b0 & 240 | i & 15)));
    }

    public boolean isSheared() {
        return (this.datawatcher.a(16) & 16) != 0;
    }

    public void setSheared(boolean flag) {
        byte b0 = this.datawatcher.a(16);

        if (flag) {
            this.datawatcher.watch(16, Byte.valueOf((byte) (b0 | 16)));
        } else {
            this.datawatcher.watch(16, Byte.valueOf((byte) (b0 & -17)));
        }
    }

    public static int a(Random random) {
        int i = random.nextInt(100);

        // uberbukkit
        boolean spawn_black = UberbukkitConfig.getInstance().getBoolean("mechanics.spawn_sheep_with_shades_of_black", true);
        boolean spawn_brown_pink = UberbukkitConfig.getInstance().getBoolean("mechanics.spawn_brown_and_pink_sheep", true);

        if (i < 5 && spawn_black) {
            return 15;
        } else if (i < 10 && spawn_black) {
            return 7;
        } else if (i < 15 && spawn_black) {
            return 8;
        } else if (i < 18 && spawn_brown_pink) {
            return 12;
        } else if (spawn_brown_pink && random.nextInt(500) == 0) {
            return 6;
        } else {
            return 0;
        }
    }
}
