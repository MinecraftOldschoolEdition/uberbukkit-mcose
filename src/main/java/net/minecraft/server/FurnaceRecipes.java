package net.minecraft.server;

import java.util.HashMap;
import java.util.Map;

import uk.betacraft.uberbukkit.Uberbukkit;

public class FurnaceRecipes {

    private static final FurnaceRecipes a = new FurnaceRecipes();
    private Map b = new HashMap();
    private Map c = new HashMap();

    public static final FurnaceRecipes getInstance() {
        return a;
    }

    private FurnaceRecipes() {
        this.registerRecipe(Block.IRON_ORE.id, new ItemStack(Item.IRON_INGOT));
        this.registerRecipe(Block.GOLD_ORE.id, new ItemStack(Item.GOLD_INGOT));
        this.registerRecipe(Block.DIAMOND_ORE.id, new ItemStack(Item.DIAMOND));
        this.registerRecipe(Block.SAND.id, new ItemStack(Block.GLASS));
        this.registerRecipe(Item.PORK.id, new ItemStack(Item.GRILLED_PORK));
        this.registerRecipe(Item.RAW_FISH.id, new ItemStack(Item.COOKED_FISH));
        this.registerRecipe(Block.COBBLESTONE.id, new ItemStack(Block.STONE));
        this.registerRecipe(Item.CLAY_BALL.id, new ItemStack(Item.CLAY_BRICK));

        if (Uberbukkit.getTargetPVN() >= 8) {
            this.registerRecipe(Block.LOG.id, new ItemStack(Item.COAL, 1, 1));
            this.registerRecipe(Block.CACTUS.id, new ItemStack(Item.INK_SACK, 1, 2));
        }

        this.registerRecipe(Block.WET_SPONGE.id, new ItemStack(Block.SPONGE, 1, 0));
    }

    public void registerRecipe(int i, ItemStack itemstack) {
        this.b.put(Integer.valueOf(i), itemstack);
    }

    public void registerRecipe(int i, int j, ItemStack itemstack) {
        this.c.put(Integer.valueOf(this.metaKey(i, j)), itemstack);
    }

    public ItemStack a(int i) {
        return (ItemStack) this.b.get(Integer.valueOf(i));
    }

    public ItemStack a(ItemStack itemstack) {
        if (itemstack == null) {
            return null;
        }

        ItemStack exact = (ItemStack) this.c.get(Integer.valueOf(this.metaKey(itemstack.id, itemstack.getData())));
        if (exact != null) {
            return exact;
        }

        return this.a(itemstack.getItem().id);
    }

    public Map b() {
        return this.b;
    }

    private int metaKey(int i, int j) {
        return i << 8 | j & 255;
    }
}
