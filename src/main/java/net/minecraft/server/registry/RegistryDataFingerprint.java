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
import net.minecraft.server.RecyclingManager;
import net.minecraft.server.CraftingRecipe;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.PaintingVariant;
import net.minecraft.server.ShapedRecipes;
import net.minecraft.server.ShapelessRecipes;
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
    public static final int SCHEMA_VERSION = 4;
    public static final String BUILT_IN_SYNCHRONIZED_DATA =
            "badd3383912d245e1a9f07592a16676ba19bda2917159a1055a1a463adda8664";
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
        PaintingVariantRegistryBootstrap.initialize();
        JukeboxSongRegistryBootstrap.initialize();
        BlockMiningRegistryBootstrap.initialize();
        RecipeRegistryBootstrap.initialize();

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(SCHEMA_VERSION);

            out.writeUTF("minecraft:painting_variant");
            out.writeInt(Registries.PAINTING_VARIANT.keys().size());
            for (PaintingVariant variant : Registries.PAINTING_VARIANT.values()) {
                ResourceLocation key = Registries.PAINTING_VARIANT.getKey(variant);
                out.writeUTF(key.toString());
                out.writeInt(variant.getWidth());
                out.writeInt(variant.getHeight());
                out.writeUTF(variant.getAssetId().toString());
                out.writeUTF(variant.getLegacyTitle());
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
                } else {
                    throw new IllegalStateException(
                            "Unsupported synchronized recipe type for " + key);
                }
                ItemStack output = value.b();
                out.writeUTF(stackKey(output).toString());
                out.writeInt(output.count);
                out.writeInt(output.getData());
            }

            writeBlockMining(out);
            writeRecycling(out);
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
        List<TagKey<Block>> tags = BlockMiningRegistryApi.synchronizedTagKeys();
        out.writeInt(tags.size());
        Set<ResourceLocation> commonRuleKeys =
                new LinkedHashSet<ResourceLocation>();
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
                if (i < 3) commonRuleKeys.add(member);
            }
        }

        out.writeUTF("minecraft:block_mining/rules");
        ArrayList<ResourceLocation> orderedRuleKeys =
                new ArrayList<ResourceLocation>(commonRuleKeys);
        Collections.sort(orderedRuleKeys, KEY_ORDER);
        out.writeInt(orderedRuleKeys.size());
        for (int i = 0; i < orderedRuleKeys.size(); i++) {
            ResourceLocation key = orderedRuleKeys.get(i);
            Block block = BlockRegistry.get(key);
            if (block == null || !key.equals(BlockRegistry.getKey(block))) {
                throw new IllegalStateException(
                        "Mining tag member has no canonical block: " + key);
            }
            out.writeUTF(key.toString());
            writeMiningRule(out, BlockMiningRegistryApi.get(block));
        }

        out.writeUTF("minecraft:block_mining/metadata");
        ArrayList<Block> metadataBlocks = new ArrayList<Block>();
        for (int i = 0; i < Block.byId.length; i++) {
            Block block = Block.byId[i];
            if (block != null
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
