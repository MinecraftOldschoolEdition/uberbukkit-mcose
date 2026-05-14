package net.minecraft.server;

import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.util.ResourceLocation;

import java.util.List;

public class ItemLead extends Item {
    private static final double LEASH_ATTACH_RADIUS = 7.0D;
    private static final double LEASH_RELEASE_RADIUS = 16.0D;

    public ItemLead(int i) {
        super(i);
    }

    public boolean a(ItemStack itemstack, EntityHuman entityhuman, World world, int i, int j, int k, int l) {
        if (entityhuman == null || world == null || !this.isFenceBlock(world, i, j, k)) {
            return false;
        }

        return this.attachLeashedAnimalsToFence(entityhuman, world, i, j, k);
    }

    public boolean attachToAnimal(ItemStack itemstack, EntityAnimal entityanimal, EntityHuman entityhuman) {
        if (itemstack == null || entityanimal == null || entityhuman == null || entityanimal.isLeashed()) {
            return false;
        }

        entityanimal.setLeashedToPlayer(entityhuman);
        if (entityhuman.gameMode != 1) {
            --itemstack.count;
        }

        return true;
    }

    private boolean attachLeashedAnimalsToFence(EntityHuman entityhuman, World world, int i, int j, int k) {
        List nearbyAnimals = getNearbyAnimals(world, i, j, k, LEASH_ATTACH_RADIUS);
        boolean attachedAny = false;

        for (int l = 0; l < nearbyAnimals.size(); ++l) {
            Object entry = nearbyAnimals.get(l);
            if (!(entry instanceof EntityAnimal)) {
                continue;
            }

            EntityAnimal entityanimal = (EntityAnimal) entry;
            if (entityanimal.isLeashedTo(entityhuman)) {
                entityanimal.setLeashedToFence(i, j, k);
                attachedAny = true;
            }
        }

        return attachedAny;
    }

    public static boolean hasLeashedAnimalsToAttach(EntityHuman entityhuman, World world, int i, int j, int k) {
        if (entityhuman == null || world == null) {
            return false;
        }

        List nearbyAnimals = getNearbyAnimals(world, i, j, k, LEASH_ATTACH_RADIUS);
        for (int l = 0; l < nearbyAnimals.size(); ++l) {
            Object entry = nearbyAnimals.get(l);
            if (entry instanceof EntityAnimal && ((EntityAnimal) entry).isLeashedTo(entityhuman)) {
                return true;
            }
        }

        return false;
    }

    public static boolean releaseAnimalsFromFence(World world, int i, int j, int k) {
        if (world == null) {
            return false;
        }

        List nearbyAnimals = getNearbyAnimals(world, i, j, k, LEASH_RELEASE_RADIUS);
        boolean releasedAny = false;
        int releasedCount = 0;

        for (int l = 0; l < nearbyAnimals.size(); ++l) {
            Object entry = nearbyAnimals.get(l);
            if (!(entry instanceof EntityAnimal)) {
                continue;
            }

            EntityAnimal entityanimal = (EntityAnimal) entry;
            if (entityanimal.isLeashedToFence(i, j, k)) {
                entityanimal.clearLeashed(false);
                releasedAny = true;
                ++releasedCount;
            }
        }

        if (releasedAny && !world.isStatic) {
            for (int l = 0; l < releasedCount; ++l) {
                EntityItem entityitem = new EntityItem(world, (double) i + 0.5D, (double) j + 1.05D, (double) k + 0.5D, new ItemStack(Item.LEAD, 1));
                entityitem.pickupDelay = 10;
                world.addEntity(entityitem);
            }
        }

        return releasedAny;
    }

    public static boolean isLeadItemStack(ItemStack itemstack) {
        if (itemstack == null) {
            return false;
        }

        Item item = itemstack.getItem();
        if (item == Item.LEAD) {
            return true;
        }

        try {
            Holder<Item> holder = itemstack.getItemHolder();
            if (holder != null && holder.key() != null && "minecraft:lead".equals(holder.key().toString())) {
                return true;
            }
        } catch (Throwable ignored) {}

        try {
            ResourceLocation key = item == null ? null : ItemRegistry.getKey(item);
            return key != null && "minecraft:lead".equals(key.toString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static List getNearbyAnimals(World world, int i, int j, int k, double radius) {
        AxisAlignedBB searchBox = AxisAlignedBB.b((double) i, (double) j, (double) k, (double) (i + 1), (double) (j + 1), (double) (k + 1)).b(radius, radius, radius);
        return world.a(EntityAnimal.class, searchBox);
    }

    private boolean isFenceBlock(World world, int i, int j, int k) {
        int blockId = world.getTypeId(i, j, k);
        if (blockId <= 0 || blockId >= Block.byId.length) {
            return false;
        }

        return Block.byId[blockId] instanceof BlockFence;
    }
}
