package net.minecraft.server.registry;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import net.minecraft.server.Block;
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
    public static final int SCHEMA_VERSION = 3;
    public static final String BUILT_IN_SYNCHRONIZED_DATA =
            "badd3383912d245e1a9f07592a16676ba19bda2917159a1055a1a463adda8664";

    private RegistryDataFingerprint() {}

    public static String captureSynchronizedData() {
        PaintingVariantRegistryBootstrap.initialize();
        JukeboxSongRegistryBootstrap.initialize();
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
