package net.minecraft.server;

import java.util.Random;

import uk.betacraft.uberbukkit.UberbukkitConfig;

public class BlockTNT extends Block {

    public BlockTNT(int i, int j) {
        super(i, j, Material.TNT);
    }

    public int a(int i) {
        return i == 0 ? this.textureId + 2 : (i == 1 ? this.textureId + 1 : this.textureId);
    }

    public void c(World world, int i, int j, int k) {
        super.c(world, i, j, k);
        if (world.isBlockIndirectlyPowered(i, j, k)) {
            this.postBreak(world, i, j, k, 1);
            world.setTypeId(i, j, k, 0);
        }
    }

    public void doPhysics(World world, int i, int j, int k, int l) {
        if (l > 0 && Block.byId[l].isPowerSource() && world.isBlockIndirectlyPowered(i, j, k)) {
            this.postBreak(world, i, j, k, 1);
            world.setTypeId(i, j, k, 0);
        }
    }

    public int a(Random random) {
        return 0;
    }

    public void d(World world, int i, int j, int k) {
        if (!world.isStatic) {
            EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(world, (double) ((float) i + 0.5F), (double) ((float) j + 0.5F), (double) ((float) k + 0.5F));

            entitytntprimed.fuseTicks = world.random.nextInt(entitytntprimed.fuseTicks / 4) + entitytntprimed.fuseTicks / 8;
            world.addEntity(entitytntprimed);
        }
    }

    public void postBreak(World world, int i, int j, int k, int l) {
        postBreak(world, i, j, k, l, null);
    }

    // UberBukkit - Overload to track who ignited the TNT
    public void postBreak(World world, int i, int j, int k, int l, EntityLiving igniter) {
        if (!world.isStatic) {
            if ((l & 1) == 0) {
                this.a(world, i, j, k, new ItemStack(Block.TNT.id, 1, 0));
            } else {
                EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(world, (double) ((float) i + 0.5F), (double) ((float) j + 0.5F), (double) ((float) k + 0.5F));
                entitytntprimed.source = igniter; // UberBukkit - Track the igniter
                world.addEntity(entitytntprimed);
                world.makeSound(entitytntprimed, "random.fuse", 1.0F, 1.0F);
            }
        }
    }

    public void b(World world, int i, int j, int k, EntityHuman entityhuman) {
        boolean shouldIgnite = false;
        
        // uberbukkit
        if (!UberbukkitConfig.getInstance().getBoolean("mechanics.tnt_require_lighter", true)) {
            shouldIgnite = true;
        }

        if ((entityhuman.G() != null && entityhuman.G().id == Item.FLINT_AND_STEEL.id)) {
            shouldIgnite = true;
        }

        if (shouldIgnite) {
            // UberBukkit - Directly spawn TNT with the igniter tracked
            world.setTypeId(i, j, k, 0); // Remove the TNT block
            EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(world, (double) ((float) i + 0.5F), (double) ((float) j + 0.5F), (double) ((float) k + 0.5F));
            entitytntprimed.source = entityhuman; // Track who lit the TNT
            world.addEntity(entitytntprimed);
            world.makeSound(entitytntprimed, "random.fuse", 1.0F, 1.0F);
            // MCOSE: KABOOM! achievement for igniting TNT
            entityhuman.a(AchievementList.explosion, 1);
            return; // Don't call super - we've handled it
        }

        super.b(world, i, j, k, entityhuman);
    }

    public boolean interact(World world, int i, int j, int k, EntityHuman entityhuman) {
        return super.interact(world, i, j, k, entityhuman);
    }
}
