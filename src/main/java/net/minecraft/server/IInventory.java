package net.minecraft.server;

public interface IInventory {

    int DEFAULT_MAX_STACK_SIZE = 99;

    int getSize();

    ItemStack getItem(int i);

    ItemStack splitStack(int i, int j);

    void setItem(int i, ItemStack itemstack);

    String getName();

    int getMaxStackSize();

    void update();

    boolean a_(EntityHuman entityhuman);

    ItemStack[] getContents(); // CraftBukkit
}
