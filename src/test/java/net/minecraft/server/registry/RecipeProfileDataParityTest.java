package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.Block;
import net.minecraft.server.CraftingManager;
import net.minecraft.server.CraftingRecipe;
import net.minecraft.server.FurnaceRecipes;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.StatisticList;
import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;

public class RecipeProfileDataParityTest {
    @Test
    public void allPvnThresholdsAndRepresentativeLegacyConfigsHaveNoJavaFallbacks()
            throws Exception {
        int[] pvns = {7, 8, 9, 11, 12, 14};
        for (int pvn : pvns) {
            runProfile(pvn, false, false, false, true);
        }
        runProfile(14, true, true, true, false);
    }

    private static void runProfile(
            int pvn,
            boolean oldGlowstone,
            boolean oldWool,
            boolean oldSlabs,
            boolean ladderGap) throws Exception {
        Path directory = Files.createTempDirectory("mcose-recipe-profile-");
        Path config = directory.resolve("uberbukkit.yml");
        String yaml = "config-version: 1\n"
                + "client:\n  allowed_protocols:\n    value: '" + pvn + "'\n"
                + "mechanics:\n"
                + "  glowstone_pre1_6_6: " + oldGlowstone + "\n"
                + "  wool_recipe_pre1_6_6: " + oldWool + "\n"
                + "  old_slab_recipe: " + oldSlabs + "\n"
                + "  allow_ladder_gap: " + ladderGap + "\n";
        Files.write(config, yaml.getBytes(StandardCharsets.UTF_8));
        try {
            File javaExecutable = new File(
                    new File(System.getProperty("java.home"), "bin"),
                    System.getProperty("os.name", "").toLowerCase().contains("win")
                            ? "java.exe" : "java");
            Process process = new ProcessBuilder(
                    javaExecutable.getAbsolutePath(),
                    "-cp", System.getProperty("java.class.path"),
                    Probe.class.getName())
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            InputStream output = process.getInputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = output.read(buffer)) >= 0) captured.write(buffer, 0, read);
            int exit = process.waitFor();
            String text = new String(captured.toByteArray(), StandardCharsets.UTF_8);
            assertEquals("PVN " + pvn + "\n" + text, 0, exit);
            assertTrue(text, text.contains("PROFILE_OK"));
        } finally {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (Exception ignored) {}
                    });
        }
    }

    public static final class Probe {
        public static void main(String[] ignored) {
            StatisticList.a();
            CraftingManager manager = CraftingManager.getInstance();
            int builtInCount = manager.getBuiltInRecipeSnapshot().size();
            manager.registerShapelessRecipe(
                    new ItemStack(Item.STICK), new Object[] {Item.FEATHER});
            CraftingRecipe plugin = (CraftingRecipe)manager.b().get(
                    manager.b().size() - 1);

            ItemStack genericPlugin = new ItemStack(Item.STICK, 2, 0);
            ItemStack exactPlugin = new ItemStack(Item.DIAMOND, 1, 0);
            FurnaceRecipes furnace = FurnaceRecipes.getInstance();
            furnace.registerRecipe(Item.FEATHER.id, genericPlugin);
            furnace.registerRecipe(Block.SAND.id, 7, exactPlugin);

            RecipeRegistryBootstrap.initialize();
            if (RecipeRegistryBootstrap.javaFallbackRecipeCount() != 0) {
                throw new AssertionError("Java fallbacks "
                        + RecipeRegistryBootstrap.javaFallbackRecipeIndices());
            }
            List<ResourceLocation> active =
                    RecipeRegistryBootstrap.activeCraftingRecipeKeys();
            if (active.size() != builtInCount) {
                throw new AssertionError("Active built-in count changed");
            }
            List recipes = manager.b();
            for (int i = 0; i < active.size(); i++) {
                ResourceLocation key = active.get(i);
                if (key == null || Registries.RECIPE.get(key) != recipes.get(i)) {
                    throw new AssertionError("Non-authoritative crafting index " + i);
                }
            }
            if (recipes.get(recipes.size() - 7) != plugin) {
                throw new AssertionError("STARTUP plugin moved out of pre-tail position");
            }
            if (Registries.RECIPE.getKey(plugin) == null) {
                throw new AssertionError("STARTUP plugin missing from recipe registry");
            }
            if (furnace.a(Item.FEATHER.id) != genericPlugin
                    || furnace.a(new ItemStack(Block.SAND, 1, 7)) != exactPlugin) {
                throw new AssertionError("Smelting plugin precedence changed");
            }
            boolean expectsPvn8Smelting = furnace.getBuiltInInputIds().size() == 11;
            if (expectsPvn8Smelting != (uk.betacraft.uberbukkit.Uberbukkit.getTargetPVN() >= 8)) {
                throw new AssertionError("PVN smelting gate changed");
            }
            String fingerprint = RegistryDataFingerprint.captureSynchronizedData();
            if (!RegistryDataFingerprint.BUILT_IN_SYNCHRONIZED_DATA.equals(fingerprint)) {
                throw new AssertionError("Profile changed synchronized fingerprint");
            }
            System.out.println("PROFILE_OK");
        }
    }
}
