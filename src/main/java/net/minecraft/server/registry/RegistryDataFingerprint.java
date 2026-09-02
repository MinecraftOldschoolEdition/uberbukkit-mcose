package net.minecraft.server.registry;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.Block;
import net.minecraft.server.BlockStateKey;
import net.minecraft.server.DamageType;
import net.minecraft.server.DeathMessageRules;
import net.minecraft.server.RecyclingManager;
import net.minecraft.server.CraftingRecipe;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.ItemStackTemplate;
import net.minecraft.server.PaintingVariant;
import net.minecraft.server.FireSpreadRule;
import net.minecraft.server.ShapedRecipes;
import net.minecraft.server.ShapelessRecipes;
import net.minecraft.server.item.component.CookingFuel;
import net.minecraft.server.registry.number.NumberProvider;
import net.minecraft.server.util.ResourceLocation;

/**
 * Canonical fingerprint of the data-backed registries that must agree between
 * a multiplayer client and server.
 *
 * <p>The encoding is deliberately semantic rather than a hash of JSON bytes:
 * whitespace and object-field order do not affect compatibility, while every
 * value consumed by legacy save, wire, selection, and playback paths does.</p>
 */
public final class RegistryDataFingerprint {
    public static final int SCHEMA_VERSION = 26;
    // Updated only after an isolated server schema-26 canonical capture.
    public static final String BUILT_IN_SYNCHRONIZED_DATA =
            "1d7052e3f45a0e42f843412dc97d2b3fa84f4b60f0b4b1820cfcad334222231d";
    private static final Comparator<ResourceLocation> KEY_ORDER =
            new Comparator<ResourceLocation>() {
                public int compare(ResourceLocation left, ResourceLocation right) {
                    int path = left.getPath().compareTo(right.getPath());
                    return path != 0 ? path
                            : left.getNamespace().compareTo(right.getNamespace());
                }
            };

    private RegistryDataFingerprint() {}

    public static String captureSynchronizedData() {
        BlockRegistryBootstrap.initialize();
        ItemRegistryBootstrap.initialize();
        ItemTagRegistryBootstrap.initialize();
        AchievementRegistryBootstrap.initialize();
        PaintingVariantRegistryBootstrap.initialize();
        JukeboxSongRegistryBootstrap.initialize();
        BlockMiningRegistryBootstrap.initialize();
        FireSpreadDataBootstrap.initialize();
        RecipeRegistryBootstrap.initialize();
        NumberProviderRegistryBootstrap.initialize();
        ItemCapabilityRegistryBootstrap.initialize();
        EntityTypeRegistryBootstrap.initialize();
        BiomeSpawnSettingsBootstrap.initialize();
        DamageTypeRegistryBootstrap.initialize();
        DeathMessageRules.initialize();
        DimensionTypeDataBootstrap.initialize();
        WorldPresetDataBootstrap.initialize();
        ConfiguredCarverDataBootstrap.initialize();
        ConfiguredFeatureDataBootstrap.initialize();
        PlacedFeatureDataBootstrap.initialize();
        StructureTypes.initialize();
        StructureSetDataBootstrap.initialize();
        LootTables.initialize();

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(SCHEMA_VERSION);

            out.writeUTF("minecraft:advancement");
            LegacyAdvancementDataBootstrap.writeCanonicalData(out);

            out.writeUTF("minecraft:painting_variant");
            out.writeInt(Registries.PAINTING_VARIANT.keys().size());
            for (PaintingVariant variant : Registries.PAINTING_VARIANT.values()) {
                ResourceLocation key = Registries.PAINTING_VARIANT.getKey(variant);
                out.writeUTF(key.toString());
                out.writeInt(variant.getWidth());
                out.writeInt(variant.getHeight());
                out.writeUTF(variant.getAssetId().toString());
                out.writeBoolean(variant.hasLegacyBridge());
                String legacyTitle = variant.getLegacyTitle();
                out.writeBoolean(legacyTitle != null);
                if (legacyTitle != null) {
                    out.writeUTF(legacyTitle);
                }
            }

            out.writeUTF("minecraft:jukebox_song");
            out.writeInt(Registries.JUKEBOX_SONG.keys().size());
            for (JukeboxSong song : Registries.JUKEBOX_SONG.values()) {
                ResourceLocation key = Registries.JUKEBOX_SONG.getKey(song);
                out.writeUTF(key.toString());
                out.writeUTF(string(song.getSoundEvent()));
                out.writeUTF(string(song.getDescriptionTranslationKey()));
                out.writeUTF(string(song.getSoundPath()));
                out.writeInt(song.getLengthInSeconds());
                out.writeInt(song.getComparatorOutput());
                out.writeInt(song.getItemId());
                out.writeUTF(string(song.getLegacyRecordName()));
            }

            out.writeUTF("minecraft:recipe");
            out.writeInt(RecipeRegistryBootstrap.dataRecipeKeys().size());
            for (ResourceLocation key : RecipeRegistryBootstrap.dataRecipeKeys()) {
                CraftingRecipe value = RecipeRegistryBootstrap.dataRecipe(key);
                out.writeUTF(key.toString());
                if (value instanceof ShapedRecipes) {
                    ShapedRecipes recipe = (ShapedRecipes)value;
                    out.writeUTF("minecraft:crafting_shaped");
                    out.writeInt(recipe.getRecipeWidth());
                    out.writeInt(recipe.getRecipeHeight());
                    ItemStack[] ingredients = recipe.getRecipeIngredients();
                    out.writeInt(ingredients.length);
                    for (int i = 0; i < ingredients.length; i++) {
                        ItemStack ingredient = ingredients[i];
                        out.writeBoolean(ingredient != null);
                        if (ingredient != null) writeIngredient(out, ingredient);
                    }
                } else if (value instanceof ShapelessRecipes) {
                    ShapelessRecipes recipe = (ShapelessRecipes)value;
                    out.writeUTF("minecraft:crafting_shapeless");
                    java.util.List<ItemStack> ingredients =
                            recipe.getRecipeIngredients();
                    out.writeInt(ingredients.size());
                    for (ItemStack ingredient : ingredients) {
                        if (ingredient == null) {
                            throw new IllegalStateException(
                                    "Shapeless recipe contains null ingredient: " + key);
                        }
                        writeIngredient(out, ingredient);
                    }
                } else if (value instanceof SmeltingRecipe) {
                    SmeltingRecipe recipe = (SmeltingRecipe)value;
                    out.writeUTF("minecraft:smelting");
                    out.writeUTF(itemOrBlockKey(recipe.getInputItemId()).toString());
                    out.writeInt(recipe.getInputMetadata());
                    out.writeInt(recipe.getCookingTime());
                } else {
                    throw new IllegalStateException(
                            "Unsupported synchronized recipe type for " + key);
                }
                ItemStack output = value.b();
                out.writeUTF(stackKey(output).toString());
                out.writeInt(output.count);
                out.writeInt(output.getData());
            }

            writeCookingData(out);
            writeBlockMining(out);
            writeFireSpread(out);
            writeItemTags(out);
            writeRecycling(out);
            writeLootTables(out);
            writeBiomeSpawnSettings(out);
            writeDamageTypeData(out);
            out.writeUTF("minecraft:death_message");
            DeathMessageRules.writeCanonicalData(out);
            writeDimensionData(out);
            writeConfiguredCarverData(out);
            writeWorldgenFeatureData(out);
            writeStructureTypeData(out);
            writeStructureSetData(out);
            out.flush();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(bytes.toByteArray()));
        } catch (Throwable failure) {
            throw new IllegalStateException(
                    "Could not fingerprint synchronized registry data", failure);
        }
    }

    public static boolean isBuiltInSynchronizedData(String fingerprint) {
        return BUILT_IN_SYNCHRONIZED_DATA.equals(fingerprint);
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static ResourceLocation stackKey(ItemStack stack) {
        if (stack == null) {
            throw new IllegalArgumentException("Recipe stack cannot be null");
        }
        return itemOrBlockKey(stack.id);
    }

    private static void writeIngredient(DataOutputStream out, ItemStack stack)
            throws Exception {
        out.writeUTF(stackKey(stack).toString());
        out.writeInt(stack.getData());
    }

    private static void writeBlockMining(DataOutputStream out)
            throws Exception {
        out.writeUTF("minecraft:block_mining");
        List<TagKey<Block>> tags = BlockTags.synchronizedTags();
        out.writeInt(tags.size());
        for (int i = 0; i < tags.size(); i++) {
            TagKey<Block> tag = tags.get(i);
            out.writeUTF(tag.location().toString());
            ArrayList<ResourceLocation> members =
                    new ArrayList<ResourceLocation>(
                            BlockMiningRegistryApi.tagMemberKeys(tag));
            Collections.sort(members, KEY_ORDER);
            out.writeInt(members.size());
            for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
                ResourceLocation member = members.get(memberIndex);
                out.writeUTF(member.toString());
            }
        }

        out.writeUTF("minecraft:block_mining/rules");
        Map<ResourceLocation, BlockMiningRule> effectiveRules =
                BlockMiningRegistryApi.snapshotRules();
        ArrayList<ResourceLocation> orderedRuleKeys =
                new ArrayList<ResourceLocation>();
        for (Map.Entry<ResourceLocation, BlockMiningRule> entry
                : effectiveRules.entrySet()) {
            ResourceLocation key = entry.getKey();
            BlockMiningRule rule = entry.getValue();
            if (key == null || rule == null) {
                throw new IllegalStateException(
                        "Mining rule snapshot contains a null entry");
            }
            if (!isVanillaMiningRule(rule)
                    && BlockMiningRegistryApi.isRuleSynchronized(key)) {
                orderedRuleKeys.add(key);
            }
        }
        Collections.sort(orderedRuleKeys, KEY_ORDER);
        out.writeInt(orderedRuleKeys.size());
        for (int i = 0; i < orderedRuleKeys.size(); i++) {
            ResourceLocation key = orderedRuleKeys.get(i);
            Block block = BlockRegistry.get(key);
            if (block == null || !key.equals(BlockRegistry.getKey(block))) {
                throw new IllegalStateException(
                        "Mining rule has no canonical block: " + key);
            }
            out.writeUTF(key.toString());
            writeMiningRule(out, effectiveRules.get(key));
        }

        out.writeUTF("minecraft:block_mining/metadata");
        ArrayList<Block> metadataBlocks = new ArrayList<Block>();
        for (int i = 0; i < Block.byId.length; i++) {
            Block block = Block.byId[i];
            ResourceLocation key = block == null
                    ? null : BlockRegistry.getKey(block);
            if (key != null
                    && BlockMiningRegistryApi.isRuleSynchronized(key)
                    && !BlockMiningRegistryApi.snapshotMetadataRules(block).isEmpty()) {
                metadataBlocks.add(block);
            }
        }
        Collections.sort(metadataBlocks, new Comparator<Block>() {
            public int compare(Block left, Block right) {
                return KEY_ORDER.compare(
                        requireBlockKey(left), requireBlockKey(right));
            }
        });
        out.writeInt(metadataBlocks.size());
        for (int i = 0; i < metadataBlocks.size(); i++) {
            Block block = metadataBlocks.get(i);
            out.writeUTF(requireBlockKey(block).toString());
            Map<Integer, BlockMiningRule> metadata =
                    BlockMiningRegistryApi.snapshotMetadataRules(block);
            ArrayList<Integer> values =
                    new ArrayList<Integer>(metadata.keySet());
            Collections.sort(values);
            out.writeInt(values.size());
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                Integer value = values.get(valueIndex);
                out.writeInt(value.intValue());
                writeMiningRule(out, metadata.get(value));
            }
        }
    }

    private static boolean isVanillaMiningRule(BlockMiningRule rule) {
        return rule != null
                && rule.getPreferredTool() == MiningToolType.NONE
                && !rule.isEnforcePreferredTool()
                && Float.compare(rule.getSpeedMultiplier(), 1.0F) == 0
                && !rule.allowsDropsWithoutPreferredTool();
    }

    private static void writeItemTags(DataOutputStream out)
            throws Exception {
        out.writeUTF("minecraft:item_tags");
        List<TagKey<Item>> tags = ItemTagRegistryApi.synchronizedTagKeys();
        out.writeInt(tags.size());
        for (int i = 0; i < tags.size(); i++) {
            TagKey<Item> tag = tags.get(i);
            out.writeUTF(tag.location().toString());
            ArrayList<ResourceLocation> members =
                    new ArrayList<ResourceLocation>(
                            ItemTagRegistryApi.tagMemberKeys(tag));
            Collections.sort(members, KEY_ORDER);
            out.writeInt(members.size());
            for (int memberIndex = 0;
                    memberIndex < members.size(); memberIndex++) {
                out.writeUTF(members.get(memberIndex).toString());
            }
        }
    }

    private static void writeFireSpread(DataOutputStream out)
            throws Exception {
        out.writeUTF("minecraft:fire_spread");
        if (!FireSpreadRegistryApi.isCurrent()) {
            throw new IllegalStateException(
                    "Cannot fingerprint stale fire-spread bindings");
        }
        Map<ResourceLocation, FireSpreadRule> rules =
                FireSpreadRegistryApi.snapshotRules();
        ArrayList<ResourceLocation> keys =
                new ArrayList<ResourceLocation>(rules.keySet());
        Collections.sort(keys, KEY_ORDER);
        out.writeInt(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            ResourceLocation key = keys.get(i);
            Block block = BlockRegistry.get(key);
            FireSpreadRule rule = rules.get(key);
            if (block == null || !key.equals(BlockRegistry.getKey(block))
                    || rule == null) {
                throw new IllegalStateException(
                        "Invalid synchronized fire-spread rule for " + key);
            }
            out.writeUTF(key.toString());
            out.writeInt(rule.getIgniteOdds());
            out.writeInt(rule.getBurnOdds());
        }
    }

    private static void writeMiningRule(
            DataOutputStream out,
            BlockMiningRule rule) throws Exception {
        if (rule == null || rule.getPreferredTool() == null) {
            throw new IllegalStateException("Mining rule cannot be null");
        }
        out.writeUTF(rule.getPreferredTool().name());
        out.writeBoolean(rule.isEnforcePreferredTool());
        out.writeInt(Float.floatToIntBits(rule.getSpeedMultiplier()));
        out.writeBoolean(rule.allowsDropsWithoutPreferredTool());
    }

    private static void writeCookingData(DataOutputStream out)
            throws Exception {
        out.writeUTF("minecraft:number_provider");
        ArrayList<ResourceLocation> providerKeys =
                new ArrayList<ResourceLocation>(NumberProviderRegistryApi.keys());
        Collections.sort(providerKeys, KEY_ORDER);
        out.writeInt(providerKeys.size());
        for (int i = 0; i < providerKeys.size(); i++) {
            ResourceLocation key = providerKeys.get(i);
            NumberProvider provider = NumberProviderRegistryApi.get(key);
            if (provider == null || provider.getType() == null) {
                throw new IllegalStateException(
                        "Invalid number provider for " + key);
            }
            out.writeUTF(key.toString());
            out.writeUTF(provider.getType().toString());
            out.writeInt(Float.floatToIntBits(provider.getValue()));
        }

        out.writeUTF("minecraft:item/cooking_fuel");
        Map<ResourceLocation, CookingFuel> bindings =
                ItemCapabilityRegistryApi.snapshotCookingFuelBindings();
        ArrayList<ResourceLocation> itemKeys =
                new ArrayList<ResourceLocation>(bindings.keySet());
        Collections.sort(itemKeys, KEY_ORDER);
        out.writeInt(itemKeys.size());
        for (int i = 0; i < itemKeys.size(); i++) {
            ResourceLocation itemKey = itemKeys.get(i);
            CookingFuel fuel = bindings.get(itemKey);
            if (fuel == null) {
                throw new IllegalStateException(
                        "Invalid cooking-fuel binding for " + itemKey);
            }
            out.writeUTF(itemKey.toString());
            out.writeUTF(fuel.getBurnTimeKey().toString());
            out.writeUTF(fuel.getSpeedMultiplierKey().toString());
        }

        out.writeUTF("minecraft:item/crafting_remainder");
        Map<ResourceLocation, ItemStackTemplate> remainders =
                ItemCapabilityRegistryApi.snapshotCraftingRemainderBindings();
        ArrayList<ResourceLocation> remainderKeys =
                new ArrayList<ResourceLocation>(remainders.keySet());
        Collections.sort(remainderKeys, KEY_ORDER);
        out.writeInt(remainderKeys.size());
        for (int i = 0; i < remainderKeys.size(); i++) {
            ResourceLocation inputKey = remainderKeys.get(i);
            ItemStackTemplate template = remainders.get(inputKey);
            ResourceLocation outputKey = template == null
                    ? null : template.getItemKey();
            if (template == null || outputKey == null) {
                throw new IllegalStateException(
                        "Invalid crafting-remainder binding for " + inputKey);
            }
            out.writeUTF(inputKey.toString());
            out.writeUTF(outputKey.toString());
            out.writeInt(template.getCount());
            out.writeInt(template.getLegacyMetadata());
        }
    }

    private static void writeRecycling(DataOutputStream out)
            throws Exception {
        out.writeUTF("mcose:recycling");
        Map<ResourceLocation, RecyclingManager.Definition> definitions =
                RecyclingManager.getInstance().definitions();
        ArrayList<ResourceLocation> keys =
                new ArrayList<ResourceLocation>(definitions.keySet());
        Collections.sort(keys, KEY_ORDER);
        out.writeInt(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            ResourceLocation key = keys.get(i);
            RecyclingManager.Definition definition = definitions.get(key);
            if (definition == null || !key.equals(definition.getInputKey())) {
                throw new IllegalStateException(
                        "Invalid recycling definition for " + key);
            }
            out.writeUTF(key.toString());
            out.writeUTF(definition.getMethod().name());
            out.writeUTF(definition.getResultKey().toString());
            out.writeInt(definition.getCount());
            out.writeInt(definition.getLegacyMetadata());
            out.writeBoolean(definition.isLegacyInert());
        }
    }

    private static void writeLootTables(DataOutputStream out)
            throws Exception {
        out.writeUTF("minecraft:loot_table");
        ArrayList<ResourceLocation> tableKeys =
                new ArrayList<ResourceLocation>(LootTables.keys());
        Collections.sort(tableKeys, KEY_ORDER);
        out.writeInt(tableKeys.size());
        for (int i = 0; i < tableKeys.size(); i++) {
            ResourceLocation key = tableKeys.get(i);
            LootTable table = LootTables.get(key);
            if (table == null || !key.equals(table.getId())) {
                throw new IllegalStateException(
                        "Invalid synchronized loot table for " + key);
            }

            out.writeUTF(key.toString());
            if (table instanceof BlockLootTable) {
                out.writeUTF("minecraft:block");
                // Beta block tables consume the authoritative world.rand.
                out.writeBoolean(false);
                writeBlockLootTable(out, (BlockLootTable)table);
            } else if (table instanceof EntityLootTable) {
                out.writeUTF("minecraft:entity");
                // Entity tables require random_sequence to equal the table id.
                out.writeBoolean(true);
                out.writeUTF(table.getId().toString());
                writeEntityLootTable(out, (EntityLootTable)table);
            } else {
                out.writeUTF("minecraft:chest");
                // Chest random_sequence is optional and behaviorally inert in
                // the supported legacy evaluator, so its normalized value is absent.
                out.writeBoolean(false);
                writeChestLootTable(out, table);
            }
        }

        writeEntityLootBindings(out);
    }

    private static void writeBlockLootTable(
            DataOutputStream out,
            BlockLootTable table) throws Exception {
        BlockLootTable.Adapter adapter = table.getAdapter();
        out.writeBoolean(adapter != null);
        if (adapter != null) {
            out.writeUTF(adapter.getId().toString());
        }

        List<BlockLootTable.Pool> pools = table.getBlockPools();
        out.writeInt(pools.size());
        for (int i = 0; i < pools.size(); i++) {
            BlockLootTable.Pool pool = pools.get(i);
            if (pool == null || pool.getRolls() == null
                    || pool.getPlacement() == null || pool.getEntry() == null) {
                throw new IllegalStateException(
                        "Block loot table contains an invalid pool");
            }
            writeBlockRollProvider(out, pool.getRolls());
            writeBlockConditions(out, pool.getConditions());
            out.writeBoolean(pool.appliesDropChance());
            out.writeUTF(pool.getPlacement().getId().toString());
            writeBlockEntry(out, pool.getEntry());
        }
    }

    /** Narrow semantic-byte seam for exhaustive block-loot writer tests. */
    static byte[] blockLootSemanticBytesForTesting(BlockLootTable table) {
        if (table == null) {
            throw new IllegalArgumentException("Block loot table is required");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeBlockLootTable(out, table);
            out.flush();
            return bytes.toByteArray();
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Could not serialize block loot semantics", failure);
        }
    }

    private static void writeBlockRollProvider(
            DataOutputStream out,
            BlockLootTable.RollProvider provider) throws Exception {
        if (provider == null || provider.getType() == null) {
            throw new IllegalStateException("Invalid block loot roll provider");
        }
        out.writeUTF(provider.getType().toString());
        if (provider instanceof BlockLootTable.ConstantRollProvider) {
            out.writeInt(((BlockLootTable.ConstantRollProvider)provider).getValue());
            return;
        }
        if (provider instanceof BlockLootTable.UniformRollProvider) {
            BlockLootTable.UniformRollProvider uniform =
                    (BlockLootTable.UniformRollProvider)provider;
            out.writeInt(uniform.getMinimum());
            out.writeInt(uniform.getMaximum());
            return;
        }
        if (provider instanceof BlockLootTable.LegacyOneInRollProvider) {
            out.writeInt(((BlockLootTable.LegacyOneInRollProvider)provider)
                    .getChance());
            return;
        }
        if (provider
                instanceof BlockLootTable.LegacyShiftedClampedRollProvider) {
            BlockLootTable.LegacyShiftedClampedRollProvider shifted =
                    (BlockLootTable.LegacyShiftedClampedRollProvider)provider;
            out.writeInt(shifted.getBound());
            out.writeInt(shifted.getAdd());
            out.writeInt(shifted.getMinimum());
            return;
        }
        throw new IllegalStateException(
                "Unsupported synchronized block roll provider "
                        + provider.getClass().getName());
    }

    private static void writeBlockConditions(
            DataOutputStream out,
            List<BlockLootTable.Condition> conditions) throws Exception {
        if (conditions == null) {
            throw new IllegalStateException("Block loot conditions are required");
        }
        out.writeInt(conditions.size());
        for (int i = 0; i < conditions.size(); i++) {
            BlockLootTable.Condition condition = conditions.get(i);
            if (condition == null || condition.getType() == null) {
                throw new IllegalStateException("Invalid block loot condition");
            }
            out.writeUTF(condition.getType().toString());
            if (condition instanceof BlockLootTable.LegacyMetadataCondition) {
                BlockLootTable.LegacyMetadataCondition metadata =
                        (BlockLootTable.LegacyMetadataCondition)condition;
                writeNullableInteger(out, metadata.getMask());
                writeNullableInteger(out, metadata.getEquals());
                writeNullableInteger(out, metadata.getMinimum());
                writeNullableInteger(out, metadata.getMaximum());
            } else if (condition
                    instanceof BlockLootTable.LegacyRandomIntegerCondition) {
                BlockLootTable.LegacyRandomIntegerCondition random =
                        (BlockLootTable.LegacyRandomIntegerCondition)condition;
                out.writeInt(random.getBound());
                writeNullableInteger(out, random.getEquals());
                BlockLootTable.LessOrEqualMetadata metadata =
                        random.getLessOrEqualMetadata();
                out.writeBoolean(metadata != null);
                if (metadata != null) {
                    writeNullableInteger(out, metadata.getMask());
                    writeNullableInteger(out, metadata.getMaximum());
                }
            } else {
                throw new IllegalStateException(
                        "Unsupported synchronized block loot condition "
                                + condition.getClass().getName());
            }
        }
    }

    private static void writeBlockEntry(
            DataOutputStream out,
            BlockLootTable.Entry entry) throws Exception {
        if (entry == null || entry.getType() == null) {
            throw new IllegalStateException("Invalid block loot entry");
        }
        out.writeUTF(entry.getType().toString());
        writeBlockConditions(out, entry.getConditions());
        if (entry instanceof BlockLootTable.ItemEntry) {
            BlockLootTable.ItemEntry item = (BlockLootTable.ItemEntry)entry;
            if (item.getItemId() == null || item.getLegacyMetadata() == null) {
                throw new IllegalStateException("Invalid block item loot entry");
            }
            out.writeUTF(item.getItemId().toString());
            writeBlockMetadataProvider(out, item.getLegacyMetadata());
            return;
        }
        if (entry instanceof BlockLootTable.EmptyEntry) {
            return;
        }
        if (entry instanceof BlockLootTable.AlternativesEntry) {
            List<BlockLootTable.Entry> children =
                    ((BlockLootTable.AlternativesEntry)entry).getChildren();
            out.writeInt(children.size());
            for (int i = 0; i < children.size(); i++) {
                writeBlockEntry(out, children.get(i));
            }
            return;
        }
        throw new IllegalStateException(
                "Unsupported synchronized block loot entry "
                        + entry.getClass().getName());
    }

    private static void writeBlockMetadataProvider(
            DataOutputStream out,
            BlockLootTable.MetadataProvider provider) throws Exception {
        if (provider == null || provider.getType() == null) {
            throw new IllegalStateException("Invalid block metadata provider");
        }
        out.writeUTF(provider.getType().toString());
        if (provider instanceof BlockLootTable.ConstantMetadataProvider) {
            out.writeInt(((BlockLootTable.ConstantMetadataProvider)provider)
                    .getValue());
            return;
        }
        if (provider instanceof BlockLootTable.BlockStateMetadataProvider) {
            writeNullableInteger(out,
                    ((BlockLootTable.BlockStateMetadataProvider)provider)
                            .getMask());
            return;
        }
        if (provider
                instanceof BlockLootTable.LegacyThresholdMetadataProvider) {
            BlockLootTable.LegacyThresholdMetadataProvider threshold =
                    (BlockLootTable.LegacyThresholdMetadataProvider)provider;
            out.writeInt(threshold.getThreshold());
            out.writeInt(threshold.getBelowOrEqual());
            out.writeInt(threshold.getAbove());
            return;
        }
        throw new IllegalStateException(
                "Unsupported synchronized block metadata provider "
                        + provider.getClass().getName());
    }

    private static void writeChestLootTable(
            DataOutputStream out,
            LootTable table) throws Exception {
        List<LootPool> pools = table.getPools();
        out.writeInt(pools.size());
        for (int poolIndex = 0; poolIndex < pools.size(); poolIndex++) {
            LootPool pool = pools.get(poolIndex);
            if (pool == null) {
                throw new IllegalStateException("Loot table contains a null pool");
            }
            out.writeUTF(string(pool.getName()));
            writeIntegralProvider(out, pool.getMinRolls(), pool.getMaxRolls());

            List<LootEntry> entries = pool.getEntries();
            out.writeInt(entries.size());
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                LootEntry entry = entries.get(entryIndex);
                if (entry == null || entry.getItemId() == null) {
                    throw new IllegalStateException(
                            "Loot pool contains an invalid entry");
                }
                boolean empty = new ResourceLocation("minecraft", "air")
                        .equals(entry.getItemId());
                out.writeUTF(empty ? "minecraft:empty" : "minecraft:item");
                out.writeInt(entry.getWeight());
                if (!empty) {
                    out.writeUTF(entry.getItemId().toString());
                    writeIntegralProvider(
                            out, entry.getMinCount(), entry.getMaxCount());
                    out.writeInt(entry.getMetadata());
                }
            }
        }
    }

    private static void writeEntityLootTable(
            DataOutputStream out,
            EntityLootTable table) throws Exception {
        List<EntityLootTable.EntityPool> pools = table.getEntityPools();
        out.writeInt(pools.size());
        for (int poolIndex = 0; poolIndex < pools.size(); poolIndex++) {
            EntityLootTable.EntityPool pool = pools.get(poolIndex);
            if (pool == null || pool.getEntry() == null) {
                throw new IllegalStateException(
                        "Entity loot table contains an invalid pool");
            }
            out.writeInt(1);
            writeEntityCondition(out, pool.getCondition());
            writeEntityEntry(out, pool.getEntry());
        }
    }

    private static void writeEntityEntry(
            DataOutputStream out,
            EntityLootTable.EntityEntry entry) throws Exception {
        if (entry == null || entry.getType() == null) {
            throw new IllegalStateException("Invalid entity loot entry");
        }
        out.writeUTF(entry.getType().toString());
        writeEntityCondition(out, entry.getCondition());

        if (entry instanceof EntityLootTable.ItemEntry) {
            EntityLootTable.ItemEntry item =
                    (EntityLootTable.ItemEntry)entry;
            if (item.getItemId() == null || item.getCount() == null
                    || item.getCount().getType() == null) {
                throw new IllegalStateException("Invalid entity item loot entry");
            }
            out.writeUTF(item.getItemId().toString());
            out.writeUTF(item.getCount().getType().toString());
            out.writeInt(item.getCount().getMinimum());
            out.writeInt(item.getCount().getMaximum());
            out.writeInt(item.getLegacyMetadata());
            return;
        }
        if (entry instanceof EntityLootTable.AlternativesEntry) {
            List<EntityLootTable.EntityEntry> children =
                    ((EntityLootTable.AlternativesEntry)entry).getChildren();
            out.writeInt(children.size());
            for (int i = 0; i < children.size(); i++) {
                writeEntityEntry(out, children.get(i));
            }
            return;
        }
        throw new IllegalStateException(
                "Unsupported synchronized entity loot entry type "
                        + entry.getClass().getName());
    }

    private static void writeEntityCondition(
            DataOutputStream out,
            EntityLootTable.EntityCondition condition) throws Exception {
        if (condition == null) {
            throw new IllegalStateException("Entity loot condition cannot be null");
        }
        writeNullableBoolean(out, condition.getOnFire());
        writeNullableInteger(out, condition.getCubeSize());
        writeNullableBoolean(out, condition.getSheepSheared());
        writeNullableInteger(out, condition.getSheepColor());
    }

    private static void writeEntityLootBindings(DataOutputStream out)
            throws Exception {
        out.writeUTF("minecraft:entity_type/loot_table");
        ArrayList<ResourceLocation> entityKeys =
                new ArrayList<ResourceLocation>(
                        new LinkedHashSet<ResourceLocation>(
                                EntityTypeRegistry.primaryKeys()));
        Collections.sort(entityKeys, KEY_ORDER);
        out.writeInt(entityKeys.size());
        for (int i = 0; i < entityKeys.size(); i++) {
            ResourceLocation entityKey = entityKeys.get(i);
            out.writeUTF(entityKey.toString());
            out.writeUTF(new ResourceLocation(
                    entityKey.getNamespace(),
                    "entities/" + entityKey.getPath()).toString());
        }
    }

    private static void writeBiomeSpawnSettings(DataOutputStream out)
            throws Exception {
        out.writeUTF("minecraft:worldgen/biome");
        Map<ResourceLocation, BiomeSpawnSettings> rawTables =
                BiomeSpawnSettingsBootstrap.rawTables();
        ArrayList<ResourceLocation> tableKeys =
                new ArrayList<ResourceLocation>(rawTables.keySet());
        Collections.sort(tableKeys, KEY_ORDER);
        out.writeInt(tableKeys.size());
        for (int i = 0; i < tableKeys.size(); i++) {
            ResourceLocation key = tableKeys.get(i);
            BiomeSpawnSettings settings = rawTables.get(key);
            if (settings == null || !key.equals(settings.getId())) {
                throw new IllegalStateException(
                        "Invalid synchronized biome spawn table for " + key);
            }

            out.writeUTF(biomeTableKey(key).toString());
            out.writeUTF("overlay");
            out.writeInt(3);
            writeBiomeSpawnCategory(
                    out, BiomeSpawnSettings.MONSTER,
                    settings.getSpawns(BiomeSpawnSettings.MONSTER));
            writeBiomeSpawnCategory(
                    out, BiomeSpawnSettings.CREATURE,
                    settings.getSpawns(BiomeSpawnSettings.CREATURE));
            writeBiomeSpawnCategory(
                    out, BiomeSpawnSettings.WATER_CREATURE,
                    settings.getSpawns(BiomeSpawnSettings.WATER_CREATURE));
            // The supported 26.3 projection rejects all spawn-cost entries.
            out.writeInt(0);
        }

        writeBiomeSpawnBindings(out);
    }

    private static void writeBiomeSpawnCategory(
            DataOutputStream out,
            String category,
            List<BiomeSpawnSettings.SpawnEntry> entries) throws Exception {
        if (category == null || entries == null) {
            throw new IllegalStateException(
                    "Biome spawn category cannot be null");
        }
        out.writeUTF(category);
        out.writeInt(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            BiomeSpawnSettings.SpawnEntry entry = entries.get(i);
            if (entry == null || entry.getEntityType() == null
                    || entry.getEntityClass() == null
                    || entry.getWeight() <= 0 || entry.getCount() <= 0) {
                throw new IllegalStateException(
                        "Invalid synchronized biome spawn entry");
            }
            ResourceLocation canonical =
                    EntityTypeRegistry.getKey(entry.getEntityClass());
            if (!entry.getEntityType().equals(canonical)
                    || EntityTypeRegistry.get(canonical)
                            != entry.getEntityClass()) {
                throw new IllegalStateException(
                        "Biome spawn entry has no canonical entity key");
            }
            out.writeUTF(canonical.toString());
            out.writeInt(entry.getWeight());
            out.writeUTF("minecraft:constant");
            out.writeInt(entry.getCount());
        }
    }

    private static void writeBiomeSpawnBindings(DataOutputStream out)
            throws Exception {
        out.writeUTF("minecraft:biome/spawn_table");
        Map<ResourceLocation, ResourceLocation> bindings =
                BiomeSpawnSettingsBootstrap.primaryBindings();
        ArrayList<ResourceLocation> ordered =
                new ArrayList<ResourceLocation>(bindings.keySet());
        Collections.sort(ordered, KEY_ORDER);
        out.writeInt(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            ResourceLocation biomeKey = ordered.get(i);
            ResourceLocation tableKey = bindings.get(biomeKey);
            if (tableKey == null
                    || BiomeSpawnSettingsBootstrap.getRaw(tableKey) == null) {
                throw new IllegalStateException(
                        "Biome spawn binding has no raw table: " + biomeKey);
            }
            out.writeUTF(biomeKey.toString());
            out.writeUTF(biomeTableKey(tableKey).toString());
        }
    }

    private static ResourceLocation biomeTableKey(ResourceLocation biomeKey) {
        return new ResourceLocation(
                biomeKey.getNamespace(),
                "worldgen/biome/" + biomeKey.getPath());
    }

    private static void writeDimensionData(DataOutputStream out)
            throws Exception {
        out.writeUTF("minecraft:dimension_type");
        Map<ResourceLocation, DimensionTypeDefinition> rawDefinitions =
                DimensionTypeDataBootstrap.rawDefinitions();
        ArrayList<ResourceLocation> definitionKeys =
                new ArrayList<ResourceLocation>(rawDefinitions.keySet());
        Collections.sort(definitionKeys, KEY_ORDER);
        out.writeInt(definitionKeys.size());
        for (int i = 0; i < definitionKeys.size(); i++) {
            ResourceLocation key = definitionKeys.get(i);
            DimensionTypeDefinition definition = rawDefinitions.get(key);
            if (definition == null || !key.equals(definition.getId())) {
                throw new IllegalStateException(
                        "Invalid synchronized dimension type for " + key);
            }
            DimensionTypeDefinition.LightLevelProvider spawnLight =
                    definition.getMonsterSpawnLightLevel();
            if (spawnLight == null || spawnLight.getType() == null
                    || definition.getInfiniburnTag() == null
                    || definition.getSkybox() == null
                    || definition.getCardinalLight() == null) {
                throw new IllegalStateException(
                        "Dimension type has incomplete semantic data: " + key);
            }

            out.writeUTF(key.toString());
            out.writeBoolean(definition.hasFixedTime());
            out.writeBoolean(definition.hasSkylight());
            out.writeBoolean(definition.hasCeiling());
            out.writeBoolean(definition.hasEnderDragonFight());
            out.writeLong(Double.doubleToLongBits(
                    definition.getCoordinateScale()));
            out.writeInt(definition.getMinY());
            out.writeInt(definition.getHeight());
            out.writeInt(definition.getLogicalHeight());
            out.writeUTF(definition.getInfiniburnTag().toString());
            out.writeInt(Float.floatToIntBits(definition.getAmbientLight()));
            out.writeUTF(spawnLight.getType().toString());
            out.writeInt(spawnLight.getMinInclusive());
            out.writeInt(spawnLight.getMaxInclusive());
            out.writeInt(definition.getMonsterSpawnBlockLightLimit());
            out.writeUTF(definition.getSkybox());
            out.writeUTF(definition.getCardinalLight());
        }

        out.writeUTF("minecraft:worldgen/world_preset");
        Map<ResourceLocation, WorldPresetDefinition> rawPresets =
                WorldPresetDataBootstrap.rawPresets();
        ArrayList<ResourceLocation> presetKeys =
                new ArrayList<ResourceLocation>(rawPresets.keySet());
        Collections.sort(presetKeys, KEY_ORDER);
        out.writeInt(presetKeys.size());
        for (int i = 0; i < presetKeys.size(); i++) {
            ResourceLocation key = presetKeys.get(i);
            WorldPresetDefinition preset = rawPresets.get(key);
            if (preset == null || !key.equals(preset.getId())) {
                throw new IllegalStateException(
                        "Invalid synchronized world preset for " + key);
            }
            out.writeUTF(key.toString());

            Map<ResourceLocation, WorldPresetDefinition.DimensionStem> dimensions =
                    preset.getDimensions();
            ArrayList<ResourceLocation> stemKeys =
                    new ArrayList<ResourceLocation>(dimensions.keySet());
            Collections.sort(stemKeys, KEY_ORDER);
            out.writeInt(stemKeys.size());
            for (int stemIndex = 0; stemIndex < stemKeys.size(); stemIndex++) {
                ResourceLocation stemKey = stemKeys.get(stemIndex);
                WorldPresetDefinition.DimensionStem stem = dimensions.get(stemKey);
                if (stem == null || stem.getDimensionType() == null
                        || stem.getGeneratorType() == null) {
                    throw new IllegalStateException(
                            "World preset has invalid dimension stem: "
                                    + key + " / " + stemKey);
                }
                out.writeUTF(stemKey.toString());
                out.writeUTF(stem.getDimensionType().toString());
                out.writeUTF(stem.getGeneratorType().toString());
            }
        }
    }

    private static void writeConfiguredCarverData(DataOutputStream out)
            throws Exception {
        out.writeUTF("minecraft:worldgen/carver");
        ArrayList<ResourceLocation> keys =
                new ArrayList<ResourceLocation>(
                        ConfiguredCarverRegistryApi.keys());
        Collections.sort(keys, KEY_ORDER);
        out.writeInt(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            ResourceLocation key = keys.get(i);
            ConfiguredCarverDefinition definition =
                    ConfiguredCarverRegistryApi.get(key);
            if (definition == null || !key.equals(definition.getId())
                    || definition.getType() == null) {
                throw new IllegalStateException(
                        "Invalid synchronized configured carver for " + key);
            }

            out.writeUTF(key.toString());
            out.writeUTF(definition.getType().toString());
            out.writeInt(definition.getRange());
            out.writeUTF("minecraft:legacy_nested_random");
            out.writeInt(definition.getCountBound());
            out.writeUTF("minecraft:legacy_one_in");
            out.writeInt(definition.getStartChanceBound());

            ConfiguredCarverDefinition.HeightDistribution heightDistribution =
                    definition.getHeightDistribution();
            if (heightDistribution
                    == ConfiguredCarverDefinition.HeightDistribution
                            .LEGACY_NESTED_RANDOM) {
                out.writeUTF("minecraft:legacy_nested_random");
                out.writeInt(definition.getHeightBound());
                out.writeInt(definition.getHeightOffset());
            } else if (heightDistribution
                    == ConfiguredCarverDefinition.HeightDistribution.UNIFORM) {
                out.writeUTF("minecraft:uniform");
                out.writeInt(definition.getHeightBound());
            } else {
                throw new IllegalStateException(
                        "Configured carver has unsupported height distribution: "
                                + key);
            }

            out.writeInt(Float.floatToIntBits(
                    definition.getTunnelThicknessMultiplier()));
            out.writeInt(Float.floatToIntBits(
                    definition.getHorizontalRadiusMultiplier()));
            out.writeLong(normalizedDoubleBits(
                    definition.getRoomVerticalRadiusMultiplier()));
            out.writeLong(normalizedDoubleBits(
                    definition.getVerticalRadiusMultiplier()));
            out.writeLong(normalizedDoubleBits(definition.getFloorLevel()));
            out.writeInt(definition.getMaximumCarveY());
            out.writeBoolean(definition.hasLavaLevel());
            if (definition.hasLavaLevel()) {
                out.writeInt(definition.getLavaLevel());
            }
            writeCarverBlockSet(out, definition.getReplaceableBlocks());
            writeCarverBlockSet(out, definition.getAvoidedFluids());
        }
    }

    private static void writeCarverBlockSet(
            DataOutputStream out, List<ResourceLocation> values)
            throws Exception {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException(
                    "Synchronized identifier set cannot be empty");
        }
        LinkedHashSet<ResourceLocation> canonical =
                new LinkedHashSet<ResourceLocation>();
        for (int i = 0; i < values.size(); i++) {
            ResourceLocation supplied = values.get(i);
            Block block = supplied == null ? null : BlockRegistry.get(supplied);
            ResourceLocation canonicalKey = block == null
                    ? null : BlockRegistry.getKey(block);
            if (canonicalKey == null) {
                throw new IllegalStateException(
                        "Configured carver references an unknown block: "
                                + supplied);
            }
            canonical.add(canonicalKey);
        }
        ArrayList<ResourceLocation> ordered =
                new ArrayList<ResourceLocation>(canonical);
        Collections.sort(ordered, KEY_ORDER);
        out.writeInt(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            out.writeUTF(ordered.get(i).toString());
        }
    }

    private static long normalizedDoubleBits(double value) {
        return Double.doubleToLongBits(value == 0.0D ? 0.0D : value);
    }

    private static void writeDamageTypeData(DataOutputStream out)
            throws Exception {
        out.writeUTF("minecraft:damage_type");
        ArrayList<ResourceLocation> keys =
                new ArrayList<ResourceLocation>(DamageTypeRegistryApi.keys());
        Collections.sort(keys, KEY_ORDER);
        out.writeInt(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            ResourceLocation key = keys.get(i);
            DamageType definition = DamageTypeRegistryApi.get(key);
            if (definition == null || definition.getDeathMessageType() == null) {
                throw new IllegalStateException(
                        "Invalid synchronized damage type for " + key);
            }
            out.writeUTF(key.toString());
            out.writeUTF(definition.getDeathMessageType().getSerializedName());
        }
    }

    private static void writeWorldgenFeatureData(DataOutputStream out)
            throws Exception {
        out.writeUTF("minecraft:worldgen/feature");
        Map<ResourceLocation, ConfiguredFeatureDefinition> configured =
                ConfiguredFeatureDataBootstrap.rawFeatures();
        ArrayList<ResourceLocation> configuredKeys =
                new ArrayList<ResourceLocation>(configured.keySet());
        Collections.sort(configuredKeys, KEY_ORDER);
        out.writeInt(configuredKeys.size());
        for (int i = 0; i < configuredKeys.size(); i++) {
            ResourceLocation key = configuredKeys.get(i);
            ConfiguredFeatureDefinition definition = configured.get(key);
            if (definition == null || !key.equals(definition.getId())
                    || definition.getType() == null) {
                throw new IllegalStateException(
                        "Invalid synchronized configured feature for " + key);
            }
            out.writeUTF(key.toString());
            out.writeUTF(definition.getType().toString());
            if (ConfiguredFeatureCodec.ORE.equals(definition.getType())
                    || ConfiguredFeatureCodec.LEGACY_CLAY.equals(
                            definition.getType())) {
                if (!definition.hasOreConfiguration()) {
                    throw new IllegalStateException(
                            "Block-target feature is missing its configuration: "
                                    + key);
                }
                out.writeBoolean(true);
                out.writeUTF(definition.getState().toString());
                out.writeUTF(definition.getTargetBlock().toString());
                out.writeInt(definition.getSize());
                out.writeInt(Float.floatToIntBits(
                        definition.getDiscardChanceOnAirExposure()));
            } else if (ConfiguredFeatureCodec.MONSTER_ROOM.equals(
                    definition.getType())
                    || ConfiguredFeatureCodec.FREEZE_TOP_LAYER.equals(
                            definition.getType())) {
                if (definition.hasOreConfiguration()
                        || definition.hasSpringConfiguration()
                        || definition.hasLakeConfiguration()) {
                    throw new IllegalStateException(
                            "Unconfigured feature has unexpected configuration: "
                                    + key);
                }
                out.writeBoolean(false);
            } else if (ConfiguredFeatureCodec.SPRING_FEATURE.equals(
                    definition.getType())) {
                if (!definition.hasSpringConfiguration()
                        || definition.hasOreConfiguration()) {
                    throw new IllegalStateException(
                            "Spring feature is missing its configuration: " + key);
                }
                out.writeBoolean(true);
                BlockStateKey state = definition.getSpringState();
                out.writeUTF(state.getBlockKey().toString());
                ArrayList<String> propertyKeys =
                        new ArrayList<String>(state.getProperties().keySet());
                Collections.sort(propertyKeys);
                out.writeInt(propertyKeys.size());
                for (int propertyIndex = 0;
                        propertyIndex < propertyKeys.size(); propertyIndex++) {
                    String property = propertyKeys.get(propertyIndex);
                    out.writeUTF(property);
                    out.writeUTF(state.getProperties().get(property));
                }
                out.writeBoolean(definition.requiresBlockBelow());
                out.writeInt(definition.getRockCount());
                out.writeInt(definition.getHoleCount());
                ArrayList<ResourceLocation> validBlocks =
                        new ArrayList<ResourceLocation>(definition.getValidBlocks());
                Collections.sort(validBlocks, KEY_ORDER);
                out.writeInt(validBlocks.size());
                for (int blockIndex = 0;
                        blockIndex < validBlocks.size(); blockIndex++) {
                    out.writeUTF(validBlocks.get(blockIndex).toString());
                }
            } else if (ConfiguredFeatureCodec.LAKE.equals(
                    definition.getType())) {
                if (!definition.hasLakeConfiguration()
                        || definition.hasOreConfiguration()
                        || definition.hasSpringConfiguration()) {
                    throw new IllegalStateException(
                            "Lake feature is missing its configuration: " + key);
                }
                out.writeBoolean(true);
                out.writeUTF(definition.getLakeFluidProviderType().toString());
                writeBlockState(out, definition.getLakeFluidState());
                out.writeUTF(definition.getLakeBarrierProviderType().toString());
                writeBlockState(out, definition.getLakeBarrierState());
                out.writeUTF(definition.getCanPlaceFeaturePredicate().toString());
                out.writeUTF(definition.getCanReplaceWithAirOrFluidPredicate()
                        .toString());
                out.writeUTF(definition.getCanReplaceWithBarrierPredicate()
                        .toString());
            } else {
                throw new IllegalStateException(
                        "Unsupported synchronized configured feature type: "
                                + definition.getType());
            }
        }

        out.writeUTF("minecraft:worldgen/placed_feature");
        Map<ResourceLocation, PlacedFeatureDefinition> placed =
                PlacedFeatureDataBootstrap.rawFeatures();
        ArrayList<ResourceLocation> placedKeys =
                new ArrayList<ResourceLocation>(placed.keySet());
        Collections.sort(placedKeys, KEY_ORDER);
        out.writeInt(placedKeys.size());
        for (int i = 0; i < placedKeys.size(); i++) {
            ResourceLocation key = placedKeys.get(i);
            PlacedFeatureDefinition definition = placed.get(key);
            ConfiguredFeatureDefinition referenced = definition == null
                    ? null : configured.get(definition.getFeature());
            if (definition == null || !key.equals(definition.getId())
                    || referenced == null) {
                throw new IllegalStateException(
                        "Invalid synchronized placed feature for " + key);
            }
            out.writeUTF(key.toString());
            out.writeUTF(definition.getFeature().toString());
            boolean freezeTopLayer = ConfiguredFeatureCodec.FREEZE_TOP_LAYER.equals(
                    referenced.getType());
            if (freezeTopLayer != definition.hasOnlyBiomeFilter()) {
                throw new IllegalStateException(
                        "Placed freeze-top-layer modifier shape is invalid: " + key);
            }
            out.writeBoolean(definition.hasOnlyBiomeFilter());
            if (definition.hasOnlyBiomeFilter()) {
                if (definition.getCount() != 0 || definition.getHeight() != null
                        || definition.hasRarityFilter()
                        || definition.hasBlockPredicateFilter()
                        || definition.hasLegacyLavaLakeFilter()) {
                    throw new IllegalStateException(
                            "Biome-only placed feature has unexpected modifiers: " + key);
                }
                out.writeUTF(PlacedFeatureDefinition.BIOME.toString());
                continue;
            }
            if (definition.getHeight() == null
                    || definition.getHeight().getType() == null) {
                throw new IllegalStateException(
                        "Placed feature is missing its height provider: " + key);
            }
            out.writeInt(definition.getCount());
            out.writeUTF(definition.getHeight().getType().toString());
            out.writeInt(definition.getHeight().getMinimumInclusive());
            out.writeInt(definition.getHeight().getMaximumInclusive());
            out.writeInt(definition.getHeight().getPlateau());
            out.writeInt(definition.getHeight().getInner());
            boolean lake = ConfiguredFeatureCodec.LAKE.equals(
                    referenced.getType());
            if (lake != definition.hasRarityFilter()) {
                throw new IllegalStateException(
                        "Placed feature has an invalid rarity filter: " + key);
            }
            out.writeBoolean(definition.hasRarityFilter());
            if (definition.hasRarityFilter()) {
                out.writeUTF(PlacedFeatureDefinition.RARITY_FILTER.toString());
                out.writeInt(definition.getRarityChance());
            }
            boolean legacyClay = ConfiguredFeatureCodec.LEGACY_CLAY.equals(
                    referenced.getType());
            if (legacyClay != definition.hasBlockPredicateFilter()) {
                throw new IllegalStateException(
                        "Placed feature has an invalid origin-fluid predicate: "
                                + key);
            }
            out.writeBoolean(definition.hasBlockPredicateFilter());
            if (definition.hasBlockPredicateFilter()) {
                out.writeUTF(PlacedFeatureDefinition.BLOCK_PREDICATE_FILTER
                        .toString());
                out.writeUTF(PlacedFeatureDefinition.MATCHING_FLUIDS
                        .toString());
                out.writeUTF(definition.getRequiredOriginFluid().toString());
            }
            boolean lavaLake = lake
                    && new ResourceLocation("minecraft", "lava").equals(
                            referenced.getLakeFluidState().getBlockKey());
            if (lavaLake != definition.hasLegacyLavaLakeFilter()) {
                throw new IllegalStateException(
                        "Placed feature has an invalid legacy lava-lake filter: "
                                + key);
            }
            out.writeBoolean(definition.hasLegacyLavaLakeFilter());
            if (definition.hasLegacyLavaLakeFilter()) {
                out.writeUTF(PlacedFeatureDefinition.LEGACY_LAVA_LAKE_FILTER
                        .toString());
            }
        }
    }

    private static void writeStructureTypeData(DataOutputStream out)
            throws Exception {
        out.writeUTF("minecraft:worldgen/structure");
        ArrayList<ResourceLocation> keys =
                new ArrayList<ResourceLocation>(StructureTypes.keys());
        Collections.sort(keys, KEY_ORDER);
        out.writeInt(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            ResourceLocation key = keys.get(i);
            StructureType structure = StructureTypes.get(key);
            if (structure == null || !key.equals(structure.getId())) {
                throw new IllegalStateException(
                        "Invalid synchronized structure descriptor for " + key);
            }
            out.writeUTF(key.toString());
            if (StructureTypes.DUNGEON.equals(key)) {
                writeDungeonSpawnerData(out, key, structure);
            } else if (StructureTypes.HEROBRINE_SHRINE.equals(key)) {
                writeShrineBiomeData(out, structure);
            } else {
                throw new IllegalStateException(
                        "Unsupported synchronized structure descriptor " + key);
            }
        }
    }

    private static void writeDungeonSpawnerData(
            DataOutputStream out,
            ResourceLocation key,
            StructureType structure) throws Exception {
        String[] spawnerMobs = structure.getSpawnerMobIds();
        int[] spawnerWeights = structure.getSpawnerMobWeights();
        boolean weighted = spawnerMobs.length > 0
                && spawnerWeights != null
                && spawnerWeights.length == spawnerMobs.length;
        out.writeInt(spawnerMobs.length);
        out.writeBoolean(weighted);
        for (int mobIndex = 0; mobIndex < spawnerMobs.length; mobIndex++) {
            if (spawnerMobs[mobIndex] == null) {
                throw new IllegalStateException(
                        "Structure descriptor has a null spawner id: " + key);
            }
            out.writeUTF(spawnerMobs[mobIndex]);
            if (weighted) {
                out.writeInt(spawnerWeights[mobIndex]);
            }
        }
    }

    private static void writeShrineBiomeData(
            DataOutputStream out,
            StructureType structure) throws Exception {
        ArrayList<ResourceLocation> biomes =
                new ArrayList<ResourceLocation>(structure.getBiomes());
        Collections.sort(biomes, KEY_ORDER);
        out.writeInt(biomes.size());
        for (int biomeIndex = 0; biomeIndex < biomes.size(); biomeIndex++) {
            out.writeUTF(biomes.get(biomeIndex).toString());
        }
    }

    private static void writeStructureSetData(DataOutputStream out)
            throws Exception {
        out.writeUTF("minecraft:worldgen/structure_set");
        Map<ResourceLocation, StructureSetDefinition> definitions =
                StructureSetRegistryApi.snapshot();
        ArrayList<ResourceLocation> keys =
                new ArrayList<ResourceLocation>(definitions.keySet());
        Collections.sort(keys, KEY_ORDER);
        out.writeInt(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            ResourceLocation key = keys.get(i);
            StructureSetDefinition definition = definitions.get(key);
            if (definition == null || !key.equals(definition.getId())) {
                throw new IllegalStateException(
                        "Invalid synchronized structure set for " + key);
            }
            out.writeUTF(key.toString());

            LegacyRandomChanceStructurePlacement placement =
                    definition.getPlacement();
            if (placement == null || placement.getChance() <= 0
                    || placement.getCandidateBound() <= 0) {
                throw new IllegalStateException(
                        "Invalid synchronized placement in " + key);
            }
            out.writeUTF(LegacyRandomChanceStructurePlacement.TYPE.toString());
            out.writeInt(placement.getChance());
            out.writeLong(placement.getSalt());
            out.writeLong(placement.getChunkXMultiplier());
            out.writeLong(placement.getChunkZMultiplier());
            out.writeInt(placement.getCandidateOffset());
            out.writeInt(placement.getCandidateBound());
            out.writeInt(placement.getGenerationY());
            out.writeInt(placement.getLocateY());
        }
    }

    private static void writeBlockState(
            DataOutputStream out,
            BlockStateKey state) throws Exception {
        if (state == null || state.getBlockKey() == null) {
            throw new IllegalStateException(
                    "Synchronized block state is missing its block key");
        }
        out.writeUTF(state.getBlockKey().toString());
        ArrayList<String> propertyKeys =
                new ArrayList<String>(state.getProperties().keySet());
        Collections.sort(propertyKeys);
        out.writeInt(propertyKeys.size());
        for (int i = 0; i < propertyKeys.size(); i++) {
            String property = propertyKeys.get(i);
            out.writeUTF(property);
            out.writeUTF(state.getProperties().get(property));
        }
    }

    private static void writeIntegralProvider(
            DataOutputStream out,
            int minimum,
            int maximum) throws Exception {
        out.writeUTF(minimum == maximum
                ? "minecraft:constant" : "minecraft:uniform");
        out.writeInt(minimum);
        out.writeInt(maximum);
    }

    private static void writeNullableBoolean(
            DataOutputStream out,
            Boolean value) throws Exception {
        out.writeBoolean(value != null);
        if (value != null) out.writeBoolean(value.booleanValue());
    }

    private static void writeNullableInteger(
            DataOutputStream out,
            Integer value) throws Exception {
        out.writeBoolean(value != null);
        if (value != null) out.writeInt(value.intValue());
    }

    private static ResourceLocation requireBlockKey(Block block) {
        ResourceLocation key = BlockRegistry.getKey(block);
        if (key == null || BlockRegistry.get(key) != block) {
            throw new IllegalStateException(
                    "Block mining metadata has no canonical block key");
        }
        return key;
    }

    private static ResourceLocation itemOrBlockKey(int id) {
        if (id >= 0 && id < Block.byId.length) {
            Block block = Block.byId[id];
            ResourceLocation blockKey = block == null ? null : BlockRegistry.getKey(block);
            if (blockKey != null) return blockKey;
        }
        Item item = id < 0 || id >= Item.byId.length
                ? null : Item.byId[id];
        ResourceLocation itemKey = item == null ? null : ItemRegistry.getKey(item);
        if (itemKey == null) {
            throw new IllegalStateException(
                    "Recipe stack has no canonical registry key: " + id);
        }
        return itemKey;
    }

    private static String toHex(byte[] hash) {
        StringBuilder out = new StringBuilder(hash.length * 2);
        for (int i = 0; i < hash.length; i++) {
            int value = hash[i] & 255;
            if (value < 16) out.append('0');
            out.append(Integer.toHexString(value));
        }
        return out.toString();
    }
}
