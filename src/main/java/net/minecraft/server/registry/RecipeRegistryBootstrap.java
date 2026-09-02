package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.CraftingManager;
import net.minecraft.server.CraftingRecipe;
import net.minecraft.server.FurnaceRecipes;
import net.minecraft.server.ItemStack;
import net.minecraft.server.RecyclingManager;
import net.minecraft.server.ShapedRecipes;
import net.minecraft.server.ShapelessRecipes;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/**
 * Atomically cuts the legacy startup recipe tables over to server-data values.
 *
 * <p>The Java constructors remain a short-lived compatibility scaffold so old
 * PVN/config profiles, static statistic initialization, and STARTUP plugins see
 * the same ordering they always did. Classpath data identifies those built-ins;
 * the layered provider supplies their authoritative values. Plugins appended
 * after the sorted built-in prefix, or overriding a furnace input, are retained
 * by identity. The six legacy additions remain the final unsorted tail.
 * As with the legacy startup lifecycle, asynchronous plugin access while this
 * main-thread cutover is actively committing is unsupported; engine/world
 * readers begin only after central registry bootstrap completes.</p>
 */
public final class RecipeRegistryBootstrap {
    private static final ResourceLocation LEGACY_BASELINE =
            new ResourceLocation("minecraft", "legacy_baseline");
    private static final ResourceLocation LEGACY_SMELTING =
            new ResourceLocation("minecraft", "legacy_smelting");
    private static final ResourceLocation LEGACY_ADDITIONS =
            new ResourceLocation("minecraft", "legacy_additions");
    private static final ResourceLocation SERVER_LEGACY_VARIANTS =
            new ResourceLocation("minecraft", "server_legacy_variants");

    private static boolean initialized;
    private static List<ResourceLocation> dataRecipeKeys =
            Collections.<ResourceLocation>emptyList();
    private static Map<ResourceLocation, CraftingRecipe> synchronizedRecipes =
            Collections.<ResourceLocation, CraftingRecipe>emptyMap();
    private static int javaFallbackRecipeCount = -1;
    private static List<Integer> javaFallbackRecipeIndices =
            Collections.<Integer>emptyList();
    private static List<ResourceLocation> activeCraftingRecipeKeys =
            Collections.<ResourceLocation>emptyList();

    private RecipeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;

        // Initialize legacy owners before decoding, but capture their active
        // STARTUP boundary only inside the publication lock below.
        RecipeTypeRegistryBootstrap.initialize();
        CraftingManager manager = CraftingManager.getInstance();
        FurnaceRecipes furnace = FurnaceRecipes.getInstance();
        RecyclingManager recycling = RecyclingManager.getInstance();

        Map<ResourceLocation, RecyclingCodec.Entry> decodedRecycling =
                RegistryDataLoader.loadAll(
                        "recycling",
                        new RegistryDataLoader.Decoder<RecyclingCodec.Entry>() {
                            public RecyclingCodec.Entry decode(
                                    ResourceLocation key,
                                    JsonObject json) {
                                return RecyclingCodec.decode(key, json);
                            }
                        });
        Map<ResourceLocation, RecyclingManager.Definition> stagedRecycling =
                stageRecyclingDefinitions(decodedRecycling);

        RegistryDataLoader.LayeredResourceProvider classpath =
                RegistryDataLoader.createLayeredProvider(null);
        List<ResourceLocation> oracleKeys = RegistryDataLoader.loadRequiredTag(
                "recipe", LEGACY_BASELINE, classpath);
        Map<ResourceLocation, CraftingRecipe> oracleBaseline = loadRecipes(
                oracleKeys, classpath, false);
        List<ResourceLocation> oracleVariantKeys = RegistryDataLoader.loadRequiredTag(
                "recipe", SERVER_LEGACY_VARIANTS, classpath);
        Map<ResourceLocation, CraftingRecipe> oracleVariants = loadRecipes(
                oracleVariantKeys, classpath, false);
        List<ResourceLocation> matchingOracleKeys =
                new ArrayList<ResourceLocation>(oracleKeys);
        matchingOracleKeys.addAll(oracleVariantKeys);
        Map<ResourceLocation, CraftingRecipe> matchingOracle =
                new LinkedHashMap<ResourceLocation, CraftingRecipe>(oracleBaseline);
        matchingOracle.putAll(oracleVariants);

        List<ResourceLocation> baselineKeys = RegistryDataLoader.loadRequiredTag(
                "recipe", LEGACY_BASELINE);
        Map<ResourceLocation, CraftingRecipe> baseline = loadRecipes(
                baselineKeys, null, false);
        List<ResourceLocation> variantKeys = RegistryDataLoader.loadRequiredTag(
                "recipe", SERVER_LEGACY_VARIANTS);
        Map<ResourceLocation, CraftingRecipe> variants = loadRecipes(
                variantKeys, null, false);
        Map<ResourceLocation, CraftingRecipe> configuredCrafting =
                new LinkedHashMap<ResourceLocation, CraftingRecipe>(baseline);
        configuredCrafting.putAll(variants);

        List<ResourceLocation> smeltingKeys = RegistryDataLoader.loadRequiredTag(
                "recipe", LEGACY_SMELTING);
        Map<ResourceLocation, CraftingRecipe> smelting = loadRecipes(
                smeltingKeys, null, true);

        List<ResourceLocation> additionKeys = RegistryDataLoader.loadRequiredTag(
                "recipe", LEGACY_ADDITIONS);
        Map<ResourceLocation, CraftingRecipe> additions = loadRecipes(
                additionKeys, null, false);

        LinkedHashMap<ResourceLocation, CraftingRecipe> allData =
                combineData(baselineKeys, baseline, smeltingKeys, smelting,
                        additionKeys, additions);
        Map<ResourceLocation, CraftingRecipe> preparedSynchronizedRecipes =
                Collections.unmodifiableMap(
                        new LinkedHashMap<ResourceLocation, CraftingRecipe>(allData));
        List<ResourceLocation> preparedDataRecipeKeys =
                Collections.unmodifiableList(
                        new ArrayList<ResourceLocation>(allData.keySet()));

        // Global recipe lock order is API facade -> crafting owner -> furnace
        // owner -> recycling owner. Registration through the facade follows
        // the same first three, while recycling publication is guarded from
        // token preparation through the one reference assignment.
        synchronized (RecipeRegistryApi.class) {
            synchronized (manager) {
                synchronized (furnace) {
                    synchronized (recycling) {
                    RecyclingManager.PreparedState recyclingPublication =
                            recycling.prepareDataPublication(stagedRecycling);
                    List javaBuiltIns = manager.getBuiltInRecipeSnapshot();
                    List currentCrafting = new ArrayList(manager.b());
                    if (currentCrafting.size() < javaBuiltIns.size()) {
                        throw new IllegalStateException(
                                "Crafting table lost its built-in prefix");
                    }

                    ActiveCrafting activeCrafting = selectActiveCrafting(
                            javaBuiltIns, matchingOracleKeys, matchingOracle,
                            configuredCrafting);
                    ActiveSmelting activeSmelting = selectActiveSmelting(
                            smeltingKeys, smelting);
                    RecipeRegistryApi.PreparedRecipeIdAllocation generatedRecipeIds =
                            RecipeRegistryApi.prepareGeneratedRecipeIds(
                                    baselineKeys.size());
                    LinkedHashMap<ResourceLocation, CraftingRecipe> stagedRegistry =
                            stageRegistry(currentCrafting, javaBuiltIns.size(),
                                    activeCrafting, activeSmelting, additionKeys,
                                    additions, generatedRecipeIds);

                    CraftingManager.RecipePublication craftingPublication =
                            manager.prepareDataRecipePublication(
                                    activeCrafting.recipes,
                                    new ArrayList<CraftingRecipe>(additions.values()));
                    FurnaceRecipes.FurnacePublication furnacePublication =
                            furnace.prepareBuiltInRecipePublication(
                                    activeSmelting.replacements);
                    Map<Integer, SmeltingRecipe> preparedSmeltingCache =
                            RecipeRegistryApi.prepareAuthoritativeSmeltingRecipes(
                                    activeSmelting.runtimeRecipes);
                    List<Integer> preparedFallbackIndices =
                            Collections.unmodifiableList(
                                    new ArrayList<Integer>(
                                            activeCrafting.fallbackIndices));
                    List<ResourceLocation> preparedActiveCraftingKeys =
                            Collections.unmodifiableList(
                                    new ArrayList<ResourceLocation>(
                                            activeCrafting.keys));

                    if (!furnacePublication.getAppliedInputIds().equals(
                            activeSmelting.dataOwnedInputs)) {
                        throw new IllegalStateException(
                                "STARTUP smelting table changed during recipe preflight");
                    }
                    if (!RecipeRegistryApi.canCommitGeneratedRecipeIds(
                            generatedRecipeIds)) {
                        throw new IllegalStateException(
                                "Generated recipe ID state changed during recipe preflight");
                    }
                    if (!RecipeRegistryApi.canReplaceAllAtomic(stagedRegistry)) {
                        throw new IllegalStateException(
                                "Authoritative recipe registry failed publication preflight");
                    }
                    if (!recycling.canPublishPreparedState(recyclingPublication)) {
                        throw new IllegalStateException(
                                "Recycling state changed during recipe preflight");
                    }

                    // This is the first live mutation. All four monitors stay
                    // held; every remaining commit is a prepared reference or
                    // primitive assignment.
                    if (!RecipeRegistryApi.replaceAllAtomic(stagedRegistry)) {
                        throw new IllegalStateException(
                                "Authoritative recipe registry could not be published atomically");
                    }
                    recycling.publishPreparedState(recyclingPublication);
                    manager.publishPreparedDataRecipes(craftingPublication);
                    furnace.publishPreparedRecipes(furnacePublication);
                    RecipeRegistryApi.commitAuthoritativeSmeltingRecipes(
                            preparedSmeltingCache);
                    RecipeRegistryApi.commitGeneratedRecipeIds(generatedRecipeIds);
                    synchronizedRecipes = preparedSynchronizedRecipes;
                    dataRecipeKeys = preparedDataRecipeKeys;
                    javaFallbackRecipeCount = activeCrafting.fallbackCount;
                    javaFallbackRecipeIndices = preparedFallbackIndices;
                    activeCraftingRecipeKeys = preparedActiveCraftingKeys;
                    initialized = true;
                    }
                }
            }
        }
        System.out.println("[RecipeRegistryBootstrap] Registered "
                + RecipeRegistryApi.size() + " active recipes from "
                + dataRecipeKeys.size() + " synchronized data entries and "
                + recycling.definitions().size() + " recycling definitions");
    }

    /** Ordered baseline, smelting, then legacy-addition data keys. */
    public static synchronized List<ResourceLocation> dataRecipeKeys() {
        initialize();
        return dataRecipeKeys;
    }

    /** Returns the decoded synchronized-data value, active in this PVN or not. */
    public static synchronized CraftingRecipe dataRecipe(ResourceLocation key) {
        initialize();
        return synchronizedRecipes.get(key);
    }

    public static synchronized int javaFallbackRecipeCount() {
        initialize();
        return javaFallbackRecipeCount;
    }

    public static synchronized List<Integer> javaFallbackRecipeIndices() {
        initialize();
        return javaFallbackRecipeIndices;
    }

    public static synchronized List<ResourceLocation> activeCraftingRecipeKeys() {
        initialize();
        return activeCraftingRecipeKeys;
    }

    private static Map<ResourceLocation, RecyclingManager.Definition>
            stageRecyclingDefinitions(
                    Map<ResourceLocation, RecyclingCodec.Entry> decoded) {
        LinkedHashMap<ResourceLocation, RecyclingManager.Definition> active =
                new LinkedHashMap<ResourceLocation, RecyclingManager.Definition>();
        LinkedHashMap<Integer, ResourceLocation> logicalInputs =
                new LinkedHashMap<Integer, ResourceLocation>();
        for (Map.Entry<ResourceLocation, RecyclingCodec.Entry> entry
                : decoded.entrySet()) {
            ResourceLocation key = entry.getKey();
            RecyclingCodec.Entry value = entry.getValue();
            if (value == null || !key.equals(value.getInputKey())) {
                throw new IllegalArgumentException(
                        "Recycling decoder returned a mismatched input for " + key);
            }
            Integer legacyId = Integer.valueOf(value.getInputItemId());
            ResourceLocation previous = logicalInputs.put(legacyId, key);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate logical recycling inputs " + previous + " and "
                                + key + " use legacy ID " + legacyId);
            }
            if (!value.isTombstone()) {
                active.put(key, value.getDefinition());
            }
        }
        return Collections.unmodifiableMap(active);
    }

    private static Map<ResourceLocation, CraftingRecipe> loadRecipes(
            List<ResourceLocation> keys,
            RegistryDataLoader.ResourceProvider provider,
            final boolean requireSmelting) {
        RegistryDataLoader.Decoder<CraftingRecipe> decoder =
                new RegistryDataLoader.Decoder<CraftingRecipe>() {
                    public CraftingRecipe decode(ResourceLocation key, JsonObject json) {
                        CraftingRecipe recipe = RecipeCodec.decode(key, json);
                        if (requireSmelting && !(recipe instanceof SmeltingRecipe)) {
                            throw new IllegalArgumentException(
                                    "smelting tag contains a non-smelting recipe");
                        }
                        if (!requireSmelting
                                && !(recipe instanceof ShapedRecipes)
                                && !(recipe instanceof ShapelessRecipes)) {
                            throw new IllegalArgumentException(
                                    "crafting tag contains a non-crafting recipe");
                        }
                        return recipe;
                    }
                };
        return provider == null
                ? RegistryDataLoader.loadRequired("recipe", keys, decoder)
                : RegistryDataLoader.loadRequired("recipe", keys, provider, decoder);
    }

    private static LinkedHashMap<ResourceLocation, CraftingRecipe> combineData(
            List<ResourceLocation> baselineKeys,
            Map<ResourceLocation, CraftingRecipe> baseline,
            List<ResourceLocation> smeltingKeys,
            Map<ResourceLocation, CraftingRecipe> smelting,
            List<ResourceLocation> additionKeys,
            Map<ResourceLocation, CraftingRecipe> additions) {
        LinkedHashMap<ResourceLocation, CraftingRecipe> combined =
                new LinkedHashMap<ResourceLocation, CraftingRecipe>();
        appendExact(combined, baselineKeys, baseline, "baseline");
        appendExact(combined, smeltingKeys, smelting, "smelting");
        appendExact(combined, additionKeys, additions, "legacy additions");
        return combined;
    }

    private static void appendExact(
            LinkedHashMap<ResourceLocation, CraftingRecipe> target,
            List<ResourceLocation> keys,
            Map<ResourceLocation, CraftingRecipe> values,
            String description) {
        for (ResourceLocation key : keys) {
            CraftingRecipe value = values.get(key);
            if (value == null || target.put(key, value) != null) {
                throw new IllegalStateException(
                        "Duplicate or missing " + description + " recipe " + key);
            }
        }
    }

    private static ActiveCrafting selectActiveCrafting(
            List javaBuiltIns,
            List<ResourceLocation> oracleKeys,
            Map<ResourceLocation, CraftingRecipe> oracle,
            Map<ResourceLocation, CraftingRecipe> configured) {
        List<CraftingRecipe> replacements =
                new ArrayList<CraftingRecipe>(javaBuiltIns.size());
        List<ResourceLocation> activeKeys =
                new ArrayList<ResourceLocation>(javaBuiltIns.size());
        Set<ResourceLocation> consumed = new HashSet<ResourceLocation>();
        int fallbackCount = 0;
        List<Integer> fallbackIndices = new ArrayList<Integer>();

        for (int i = 0; i < javaBuiltIns.size(); i++) {
            CraftingRecipe javaRecipe = (CraftingRecipe)javaBuiltIns.get(i);
            ResourceLocation matched = null;
            for (ResourceLocation key : oracleKeys) {
                if (!consumed.contains(key) && sameRecipe(javaRecipe, oracle.get(key))) {
                    matched = key;
                    break;
                }
            }
            if (matched == null) {
                // A legacy PVN/config variant not represented by the modern
                // 163-entry corpus stays byte-for-byte Java-authored.
                replacements.add(javaRecipe);
                activeKeys.add(null);
                fallbackCount++;
                fallbackIndices.add(Integer.valueOf(i));
                continue;
            }
            CraftingRecipe replacement = configured.get(matched);
            if (replacement == null) {
                throw new IllegalStateException(
                        "Configured baseline removed active built-in recipe " + matched);
            }
            consumed.add(matched);
            replacements.add(replacement);
            activeKeys.add(matched);
        }
        return new ActiveCrafting(
                replacements, activeKeys, fallbackCount, fallbackIndices);
    }

    private static ActiveSmelting selectActiveSmelting(
            List<ResourceLocation> smeltingKeys,
            Map<ResourceLocation, CraftingRecipe> decoded) {
        Map<Integer, SmeltingRecipe> byInput =
                new LinkedHashMap<Integer, SmeltingRecipe>();
        Map<Integer, ResourceLocation> keysByInput =
                new LinkedHashMap<Integer, ResourceLocation>();
        for (ResourceLocation key : smeltingKeys) {
            SmeltingRecipe recipe = (SmeltingRecipe)decoded.get(key);
            if (recipe.getInputMetadata() != -1) {
                throw new IllegalArgumentException(
                        "Legacy generic smelting recipe must use metadata -1: " + key);
            }
            Integer input = Integer.valueOf(recipe.getInputItemId());
            if (byInput.put(input, recipe) != null) {
                throw new IllegalArgumentException(
                        "Duplicate generic smelting input " + input);
            }
            keysByInput.put(input, key);
        }

        FurnaceRecipes furnace = FurnaceRecipes.getInstance();
        Set<Integer> activeInputs = furnace.getBuiltInInputIds();
        Set<Integer> dataOwnedInputs = furnace.getUnmodifiedBuiltInInputIds();
        Map<Integer, ItemStack> replacements =
                new LinkedHashMap<Integer, ItemStack>();
        for (Integer input : activeInputs) {
            SmeltingRecipe recipe = byInput.get(input);
            if (recipe == null) {
                throw new IllegalStateException(
                        "Smelting data does not cover active input " + input);
            }
            replacements.put(input, recipe.getSmeltingResult());
        }

        Map<Integer, SmeltingRecipe> runtimeRecipes =
                new LinkedHashMap<Integer, SmeltingRecipe>();
        for (Integer input : dataOwnedInputs) {
            runtimeRecipes.put(input, byInput.get(input));
        }
        List<Integer> runtimeInputs = new ArrayList<Integer>();
        for (Object raw : furnace.b().keySet()) {
            runtimeInputs.add((Integer)raw);
        }
        Collections.sort(runtimeInputs);
        for (Integer input : runtimeInputs) {
            if (runtimeRecipes.containsKey(input)) continue;
            ItemStack output = (ItemStack)furnace.b().get(input);
            runtimeRecipes.put(input,
                    RecipeRegistryApi.smeltingRecipeForCurrentOutput(
                            input.intValue(), output));
        }
        return new ActiveSmelting(replacements, dataOwnedInputs,
                runtimeRecipes, keysByInput);
    }

    private static LinkedHashMap<ResourceLocation, CraftingRecipe> stageRegistry(
            List currentCrafting,
            int builtInCount,
            ActiveCrafting activeCrafting,
            ActiveSmelting activeSmelting,
            List<ResourceLocation> additionKeys,
            Map<ResourceLocation, CraftingRecipe> additions,
            RecipeRegistryApi.PreparedRecipeIdAllocation generatedRecipeIds) {
        LinkedHashMap<ResourceLocation, CraftingRecipe> staged =
                new LinkedHashMap<ResourceLocation, CraftingRecipe>();

        for (int i = 0; i < activeCrafting.recipes.size(); i++) {
            CraftingRecipe recipe = activeCrafting.recipes.get(i);
            ResourceLocation key = activeCrafting.keys.get(i);
            if (key == null) {
                key = existingOrGeneratedKey(
                        (CraftingRecipe)currentCrafting.get(i), generatedRecipeIds);
            }
            putUnique(staged, key, recipe);
        }
        for (int i = builtInCount; i < currentCrafting.size(); i++) {
            CraftingRecipe pluginRecipe = (CraftingRecipe)currentCrafting.get(i);
            putUnique(staged,
                    existingOrGeneratedKey(pluginRecipe, generatedRecipeIds),
                    pluginRecipe);
        }

        for (Map.Entry<Integer, SmeltingRecipe> entry
                : activeSmelting.runtimeRecipes.entrySet()) {
            Integer input = entry.getKey();
            SmeltingRecipe recipe = entry.getValue();
            ResourceLocation key = activeSmelting.dataOwnedInputs.contains(input)
                    ? activeSmelting.keysByInput.get(input)
                    : existingOrGeneratedKey(recipe, generatedRecipeIds);
            putUnique(staged, key, recipe);
        }

        // Preserve registry-only plugin extensions that do not appear in the
        // legacy manager/furnace owners. Authoritative data retains its key;
        // a colliding extension receives a deterministic suffix.
        List<ResourceLocation> existingRegistryKeys =
                new ArrayList<ResourceLocation>(Registries.RECIPE.keys());
        for (ResourceLocation key : existingRegistryKeys) {
            CraftingRecipe extension = Registries.RECIPE.get(key);
            if (extension != null && !staged.containsValue(extension)) {
                putUnique(staged, key, extension);
            }
        }

        for (ResourceLocation key : additionKeys) {
            putUnique(staged, key, additions.get(key));
        }
        return staged;
    }

    private static ResourceLocation existingOrGeneratedKey(
            CraftingRecipe recipe,
            RecipeRegistryApi.PreparedRecipeIdAllocation generatedRecipeIds) {
        ResourceLocation key = Registries.RECIPE.getKey(recipe);
        if (key != null) return key;
        if (recipe instanceof SmeltingRecipe) {
            return new ResourceLocation("minecraft", "smelting/item_"
                    + ((SmeltingRecipe)recipe).getInputItemId());
        }
        return RecipeRegistryApi.previewGeneratedRecipeId(generatedRecipeIds, recipe);
    }

    private static void putUnique(
            LinkedHashMap<ResourceLocation, CraftingRecipe> staged,
            ResourceLocation preferred,
            CraftingRecipe recipe) {
        if (preferred == null || recipe == null) {
            throw new IllegalArgumentException("Recipe key and value cannot be null");
        }
        ResourceLocation key = preferred;
        CraftingRecipe existing = staged.get(key);
        if (existing == recipe) return;
        for (int suffix = 1; existing != null; suffix++) {
            if (suffix >= 10000) {
                throw new IllegalStateException("Could not allocate recipe key for " + preferred);
            }
            key = new ResourceLocation(preferred.getNamespace(),
                    preferred.getPath() + "_" + suffix);
            existing = staged.get(key);
            if (existing == recipe) return;
        }
        staged.put(key, recipe);
    }

    private static boolean sameRecipe(CraftingRecipe left, CraftingRecipe right) {
        if (left == null || right == null || left.getClass() != right.getClass()) {
            return false;
        }
        if (!sameStack(left.b(), right.b(), true)) return false;
        if (left instanceof ShapedRecipes) {
            ShapedRecipes a = (ShapedRecipes)left;
            ShapedRecipes b = (ShapedRecipes)right;
            return a.getRecipeWidth() == b.getRecipeWidth()
                    && a.getRecipeHeight() == b.getRecipeHeight()
                    && sameIngredients(a.getRecipeIngredients(), b.getRecipeIngredients());
        }
        if (left instanceof ShapelessRecipes) {
            List<ItemStack> a = ((ShapelessRecipes)left).getRecipeIngredients();
            List<ItemStack> b = ((ShapelessRecipes)right).getRecipeIngredients();
            return sameIngredients(a.toArray(new ItemStack[a.size()]),
                    b.toArray(new ItemStack[b.size()]));
        }
        return false;
    }

    private static boolean sameIngredients(ItemStack[] left, ItemStack[] right) {
        if (left.length != right.length) return false;
        for (int i = 0; i < left.length; i++) {
            if (!sameStack(left[i], right[i], false)) return false;
        }
        return true;
    }

    private static boolean sameStack(ItemStack left, ItemStack right, boolean count) {
        if (left == null || right == null) return left == right;
        return left.id == right.id
                && left.getData() == right.getData()
                && (!count || left.count == right.count);
    }

    private static final class ActiveCrafting {
        final List<CraftingRecipe> recipes;
        final List<ResourceLocation> keys;
        final int fallbackCount;
        final List<Integer> fallbackIndices;

        ActiveCrafting(
                List<CraftingRecipe> recipes,
                List<ResourceLocation> keys,
                int fallbackCount,
                List<Integer> fallbackIndices) {
            this.recipes = recipes;
            this.keys = keys;
            this.fallbackCount = fallbackCount;
            this.fallbackIndices = fallbackIndices;
        }
    }

    private static final class ActiveSmelting {
        final Map<Integer, ItemStack> replacements;
        final Set<Integer> dataOwnedInputs;
        final Map<Integer, SmeltingRecipe> runtimeRecipes;
        final Map<Integer, ResourceLocation> keysByInput;

        ActiveSmelting(
                Map<Integer, ItemStack> replacements,
                Set<Integer> dataOwnedInputs,
                Map<Integer, SmeltingRecipe> runtimeRecipes,
                Map<Integer, ResourceLocation> keysByInput) {
            this.replacements = replacements;
            this.dataOwnedInputs = dataOwnedInputs;
            this.runtimeRecipes = runtimeRecipes;
            this.keysByInput = keysByInput;
        }
    }
}
