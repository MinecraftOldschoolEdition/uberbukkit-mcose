package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.util.ResourceLocation;

/**
 * Bootstrap for the Recipe registry.
 */
public final class RecipeRegistryBootstrap {
    private static boolean initialized = false;

    private RecipeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        RecipeRegistryApi.bootstrapCraftingAndSmelting();

        RecipeRegistryApi.registerShaped(
                new ResourceLocation("minecraft", "crafting/redstone_block"),
                new ItemStack(Block.REDSTONE_BLOCK, 1),
                new Object[]{
                        "###",
                        "###",
                        "###",
                        Character.valueOf('#'),
                        Item.REDSTONE
                }
        );

        RecipeRegistryApi.registerShaped(
                new ResourceLocation("minecraft", "crafting/coal_block"),
                new ItemStack(Block.COAL_BLOCK, 1),
                new Object[]{
                        "###",
                        "###",
                        "###",
                        Character.valueOf('#'),
                        Item.COAL
                }
        );

        RecipeRegistryApi.registerShaped(
                new ResourceLocation("minecraft", "crafting/redstone_from_block"),
                new ItemStack(Item.REDSTONE, 9),
                new Object[]{"#", Character.valueOf('#'), Block.REDSTONE_BLOCK}
        );

        RecipeRegistryApi.registerShaped(
                new ResourceLocation("minecraft", "crafting/coal_from_block"),
                new ItemStack(Item.COAL, 9),
                new Object[]{"#", Character.valueOf('#'), Block.COAL_BLOCK}
        );

        RecipeRegistryApi.registerShaped(
                new ResourceLocation("minecraft", "crafting/flint_and_steel"),
                new ItemStack(Item.FLINT_AND_STEEL, 1),
                new Object[]{
                        "A ",
                        " B",
                        Character.valueOf('A'),
                        Item.IRON_INGOT,
                        Character.valueOf('B'),
                        Item.FLINT
                }
        );

        System.out.println("[RecipeRegistryBootstrap] Registered " + RecipeRegistryApi.size() + " recipes");
    }
}
