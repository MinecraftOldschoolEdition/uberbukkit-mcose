package net.minecraft.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import uk.betacraft.uberbukkit.Uberbukkit;

public class FurnaceRecipes {

    private static final FurnaceRecipes a = new FurnaceRecipes();
    private volatile RecipeTables recipes = new RecipeTables(
            Collections.emptyMap(), Collections.emptyMap());
    private final Map builtInRecipes = new HashMap();

    public static final FurnaceRecipes getInstance() {
        return a;
    }

    private FurnaceRecipes() {
        this.registerBuiltInRecipe(Block.IRON_ORE.id, new ItemStack(Item.IRON_INGOT));
        this.registerBuiltInRecipe(Block.GOLD_ORE.id, new ItemStack(Item.GOLD_INGOT));
        this.registerBuiltInRecipe(Block.DIAMOND_ORE.id, new ItemStack(Item.DIAMOND));
        this.registerBuiltInRecipe(Block.SAND.id, new ItemStack(Block.GLASS));
        this.registerBuiltInRecipe(Item.PORK.id, new ItemStack(Item.GRILLED_PORK));
        this.registerBuiltInRecipe(Item.RAW_FISH.id, new ItemStack(Item.COOKED_FISH));
        this.registerBuiltInRecipe(Block.COBBLESTONE.id, new ItemStack(Block.STONE));
        this.registerBuiltInRecipe(Item.CLAY_BALL.id, new ItemStack(Item.CLAY_BRICK));

        if (Uberbukkit.getTargetPVN() >= 8) {
            this.registerBuiltInRecipe(Block.LOG.id, new ItemStack(Item.COAL, 1, 1));
            this.registerBuiltInRecipe(Block.CACTUS.id, new ItemStack(Item.INK_SACK, 1, 2));
        }

        this.registerBuiltInRecipe(Block.WET_SPONGE.id, new ItemStack(Block.SPONGE, 1, 0));
    }

    private void registerBuiltInRecipe(int inputId, ItemStack output) {
        this.registerRecipe(inputId, output);
        this.builtInRecipes.put(Integer.valueOf(inputId), output);
    }

    public synchronized void registerRecipe(int i, ItemStack itemstack) {
        RecipeTables current = this.recipes;
        Map generic = new HashMap(current.generic);
        generic.put(Integer.valueOf(i), itemstack);
        this.recipes = new RecipeTables(
                Collections.unmodifiableMap(generic), current.exact);
    }

    public synchronized void registerRecipe(int i, int j, ItemStack itemstack) {
        RecipeTables current = this.recipes;
        Map exact = new HashMap(current.exact);
        exact.put(Integer.valueOf(this.metaKey(i, j)), itemstack);
        this.recipes = new RecipeTables(
                current.generic, Collections.unmodifiableMap(exact));
    }

    public ItemStack a(int i) {
        RecipeTables current = this.recipes;
        return (ItemStack)current.generic.get(Integer.valueOf(i));
    }

    public ItemStack a(ItemStack itemstack) {
        if (itemstack == null) {
            return null;
        }

        RecipeTables current = this.recipes;
        ItemStack exact = (ItemStack)current.exact.get(
                Integer.valueOf(this.metaKey(itemstack.id, itemstack.getData())));
        if (exact != null) {
            return exact;
        }

        return (ItemStack)current.generic.get(Integer.valueOf(itemstack.id));
    }

    public Map b() {
        return this.recipes.generic;
    }

    /** Returns the legacy-profile inputs that must be supplied by recipe data. */
    public synchronized Set<Integer> getBuiltInInputIds() {
        return new HashSet<Integer>(this.builtInRecipes.keySet());
    }

    /** Inputs whose Java fallback has not been replaced by a STARTUP plugin. */
    public synchronized Set<Integer> getUnmodifiedBuiltInInputIds() {
        Set<Integer> unmodified = new HashSet<Integer>();
        for (Object raw : this.builtInRecipes.entrySet()) {
            Map.Entry entry = (Map.Entry)raw;
            Integer inputId = (Integer)entry.getKey();
            if (this.recipes.generic.get(inputId) == entry.getValue()) {
                unmodified.add(inputId);
            }
        }
        return unmodified;
    }

    /**
     * Replaces untouched Java defaults while preserving every generic recipe a
     * STARTUP plugin added or overrode, including an override of a vanilla id.
     */
    public synchronized FurnacePublication prepareBuiltInRecipePublication(
            Map<Integer, ItemStack> replacements) {
        if (replacements == null
                || !this.builtInRecipes.keySet().equals(replacements.keySet())) {
            throw new IllegalArgumentException(
                    "Smelting data must cover the exact active legacy profile");
        }

        RecipeTables current = this.recipes;
        Map next = new HashMap(current.generic);
        Set<Integer> applied = new HashSet<Integer>();
        for (Object raw : this.builtInRecipes.entrySet()) {
            Map.Entry entry = (Map.Entry)raw;
            Integer inputId = (Integer)entry.getKey();
            ItemStack original = (ItemStack)entry.getValue();
            if (next.get(inputId) == original) {
                ItemStack replacement = replacements.get(inputId);
                if (replacement == null) {
                    throw new IllegalArgumentException(
                            "Missing smelting replacement for input " + inputId);
                }
                next.put(inputId, replacement.cloneItemStack());
                applied.add(inputId);
            }
        }
        return new FurnacePublication(
                new RecipeTables(Collections.unmodifiableMap(next), current.exact),
                Collections.unmodifiableSet(applied));
    }

    /** Nonthrowing startup commit after every candidate view has preflighted. */
    public synchronized void publishPreparedRecipes(FurnacePublication publication) {
        this.recipes = publication.recipes;
    }

    /** Defensive snapshot used to prove metadata-specific plugin precedence. */
    public Map<Integer, ItemStack> getExactRecipeSnapshot() {
        return new HashMap<Integer, ItemStack>(this.recipes.exact);
    }

    private int metaKey(int i, int j) {
        return i << 8 | j & 255;
    }

    public static final class FurnacePublication {
        private final RecipeTables recipes;
        private final Set<Integer> appliedInputs;

        private FurnacePublication(
                RecipeTables recipes,
                Set<Integer> appliedInputs) {
            this.recipes = recipes;
            this.appliedInputs = appliedInputs;
        }

        public Set<Integer> getAppliedInputIds() {
            return this.appliedInputs;
        }
    }

    private static final class RecipeTables {
        private final Map generic;
        private final Map exact;

        private RecipeTables(Map generic, Map exact) {
            this.generic = generic;
            this.exact = exact;
        }
    }
}
