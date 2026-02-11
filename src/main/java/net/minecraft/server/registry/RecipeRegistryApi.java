package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.CraftingManager;
import net.minecraft.server.CraftingRecipe;
import net.minecraft.server.FurnaceRecipes;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.ShapedRecipes;
import net.minecraft.server.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API facade for the recipe registry.
 */
public final class RecipeRegistryApi {
    private static int recipeCounter = 0;
    private static boolean bootstrapped = false;
    private static final Map<Integer, SmeltingRecipe> smeltingRecipesByInput = new HashMap<Integer, SmeltingRecipe>();

    private RecipeRegistryApi() {}

    public static boolean register(ResourceLocation key, CraftingRecipe value) {
        return RegistryApiSupport.register(Registries.RECIPE, key, value);
    }

    public static CraftingRecipe get(ResourceLocation key) {
        ensureSynchronized();
        return RegistryApiSupport.get(Registries.RECIPE, key);
    }

    public static CraftingRecipe getByIdentifier(String any) {
        ensureSynchronized();
        return RegistryApiSupport.getByIdentifier(Registries.RECIPE, any);
    }

    public static ResourceLocation getKey(CraftingRecipe value) {
        ensureSynchronized();
        return RegistryApiSupport.getKey(Registries.RECIPE, value);
    }

    public static Set<ResourceLocation> keys() {
        ensureSynchronized();
        return RegistryApiSupport.keys(Registries.RECIPE);
    }

    public static Collection<CraftingRecipe> values() {
        ensureSynchronized();
        return RegistryApiSupport.values(Registries.RECIPE);
    }

    public static int size() {
        ensureSynchronized();
        return RegistryApiSupport.size(Registries.RECIPE);
    }

    public static String normalizeInputIdentifier(String any) {
        ensureSynchronized();
        return RegistryApiSupport.normalizeInputIdentifier(Registries.RECIPE, any);
    }

    public static String canonicalizeIdentifier(String any) {
        ensureSynchronized();
        return RegistryApiSupport.canonicalizeIdentifier(Registries.RECIPE, any);
    }

    public static synchronized void bootstrapCraftingAndSmelting() {
        ensureSynchronized();
    }

    public static ResourceLocation registerShaped(ResourceLocation key, ItemStack output, Object... recipeShapeAndMappings) {
        return RecipeManager.registerShaped(key, output, recipeShapeAndMappings);
    }

    public static ResourceLocation registerShapeless(ResourceLocation key, ItemStack output, Object... inputs) {
        return RecipeManager.registerShapeless(key, output, inputs);
    }

    public static ResourceLocation registerSmelting(ResourceLocation key, int inputItemId, ItemStack output) {
        return RecipeManager.registerSmelting(key, inputItemId, output);
    }

    public static synchronized ResourceLocation generateRecipeId(CraftingRecipe recipe) {
        if (recipe == null) {
            return new ResourceLocation("minecraft", "recipe/unknown_" + (recipeCounter++));
        }

        ItemStack output = null;
        try {
            output = recipe.b();
        } catch (Throwable ignored) {}

        String type = (recipe instanceof ShapedRecipes) ? "shaped" : "shapeless";
        String outputName = output == null ? "unknown" : getItemName(output.id);
        return new ResourceLocation("minecraft", type + "/" + outputName + "_" + (recipeCounter++));
    }

    public static List<CraftingRecipe> getByOutputItemKey(String itemKey) {
        ArrayList<CraftingRecipe> out = new ArrayList<CraftingRecipe>();
        String canonicalInput = canonicalItemKey(itemKey);
        if (canonicalInput == null) {
            return out;
        }

        Collection<CraftingRecipe> all = values();
        for (CraftingRecipe recipe : all) {
            String outputKey = outputItemKey(recipe);
            if (canonicalInput.equals(outputKey)) {
                out.add(recipe);
            }
        }
        return out;
    }

    private static void registerCraftingRecipes() {
        List list = CraftingManager.getInstance().b();
        for (int i = 0; i < list.size(); i++) {
            CraftingRecipe recipe = (CraftingRecipe) list.get(i);
            if (recipe == null) {
                continue;
            }
            if (RegistryApiSupport.getKey(Registries.RECIPE, recipe) != null) {
                continue;
            }
            ResourceLocation id = generateRecipeId(recipe);
            registerWithSuffix(id, recipe);
        }
    }

    private static void registerSmeltingRecipes() {
        Map smeltingList = FurnaceRecipes.getInstance().b();
        java.util.Iterator iterator = smeltingList.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry) iterator.next();
            Integer inputIdBoxed = (Integer) entry.getKey();
            ItemStack output = (ItemStack) entry.getValue();
            if (inputIdBoxed == null || output == null) {
                continue;
            }
            int inputId = inputIdBoxed.intValue();
            SmeltingRecipe recipe = smeltingRecipesByInput.get(Integer.valueOf(inputId));
            if (recipe == null || !sameOutput(recipe.b(), output)) {
                recipe = new SmeltingRecipe(inputId, output.cloneItemStack());
                smeltingRecipesByInput.put(Integer.valueOf(inputId), recipe);
            }
            if (RegistryApiSupport.getKey(Registries.RECIPE, recipe) != null) {
                continue;
            }
            ResourceLocation id = new ResourceLocation("minecraft", "smelting/" + getItemName(inputId));
            registerWithSuffix(id, recipe);
        }
    }

    private static void ensureSynchronized() {
        RecipeTypeRegistryBootstrap.initialize();
        registerCraftingRecipes();
        registerSmeltingRecipes();
        bootstrapped = true;
    }

    private static boolean sameOutput(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.id == b.id
                && a.getData() == b.getData()
                && a.count == b.count;
    }

    private static void registerWithSuffix(ResourceLocation base, CraftingRecipe recipe) {
        if (register(base, recipe)) {
            return;
        }

        String path = base.getPath();
        for (int i = 1; i < 10000; i++) {
            ResourceLocation candidate = new ResourceLocation(base.getNamespace(), path + "_" + i);
            if (register(candidate, recipe)) {
                return;
            }
        }
    }

    private static String getItemName(int itemId) {
        if (itemId < 256) {
            Block block = Block.byId[itemId];
            if (block != null) {
                ResourceLocation key = BlockRegistry.getKey(block);
                if (key != null) {
                    return key.getPath();
                }
            }
        }

        Item item = Item.byId[itemId];
        if (item != null) {
            ResourceLocation key = ItemRegistry.getKey(item);
            if (key != null) {
                return key.getPath();
            }
        }

        return "item_" + itemId;
    }

    private static String canonicalItemKey(String any) {
        if (any == null) {
            return null;
        }

        String normalizedItem = ItemRegistry.normalizeInputIdentifier(any);
        if (normalizedItem != null) {
            Item item = ItemRegistry.get(new ResourceLocation(normalizedItem));
            ResourceLocation key = ItemRegistry.getKey(item);
            return key == null ? null : key.toString();
        }

        Block block = BlockRegistry.getByIdentifier(any);
        if (block != null) {
            ResourceLocation key = BlockRegistry.getKey(block);
            return key == null ? null : key.toString();
        }

        return null;
    }

    private static String outputItemKey(CraftingRecipe recipe) {
        if (recipe == null) {
            return null;
        }

        ItemStack output;
        try {
            output = recipe.b();
        } catch (Throwable ignored) {
            return null;
        }

        if (output == null) {
            return null;
        }

        int id = output.id;
        if (id < 256) {
            Block block = Block.byId[id];
            if (block != null) {
                ResourceLocation key = BlockRegistry.getKey(block);
                return key == null ? null : key.toString();
            }
        }

        Item item = Item.byId[id];
        if (item != null) {
            ResourceLocation key = ItemRegistry.getKey(item);
            return key == null ? null : key.toString();
        }

        return null;
    }
}
