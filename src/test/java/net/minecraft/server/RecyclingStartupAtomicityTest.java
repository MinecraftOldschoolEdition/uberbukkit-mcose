package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.server.registry.RecipeRegistryBootstrap;
import net.minecraft.server.registry.RegistryBootstrap;
import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;

public class RecyclingStartupAtomicityTest {
    @Test
    public void malformedConfiguredDefinitionRollsBackEveryOwnerAndCanRetry()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-recycling-rollback-");
        Path override = root.resolve(
                "data/minecraft/recycling/wooden_pickaxe.json");
        Files.createDirectories(override.getParent());
        Files.write(override, "{".getBytes(StandardCharsets.UTF_8));
        try {
            ChildResult result = runProbe(
                    root, AtomicityProbe.class, override.toAbsolutePath().toString());
            assertEquals(result.output, 0, result.exitCode);
            assertTrue(result.output,
                    result.output.contains("RECYCLING_FAILURE_ATOMIC"));
            assertTrue(result.output,
                    result.output.contains("RECYCLING_RETRY_OK"));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void startupPluginQueryDoesNotForceCutoverAndRecyclingKeepsPriority()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-recycling-startup-");
        try {
            ChildResult result = runProbe(root, StartupBoundaryProbe.class);
            assertEquals(result.output, 0, result.exitCode);
            assertTrue(result.output,
                    result.output.contains("RECYCLING_STARTUP_BOUNDARY_OK"));
            assertTrue(result.output,
                    result.output.contains("RECYCLING_PRIORITY_OK"));
            assertTrue(result.output,
                    result.output.contains("RECYCLING_FURNACE_PRIORITY_EVENT_OK"));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void configuredAddOverrideAndTombstonePublishAsEffectiveSnapshot()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-recycling-configured-");
        Path directory = root.resolve("data/minecraft/recycling");
        Files.createDirectories(directory);
        Files.write(directory.resolve("wooden_pickaxe.json"), active(
                "craft", "minecraft:string", 1).getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve("stone_pickaxe.json"),
                "{\"type\":\"mcose:recycling\",\"enabled\":false}"
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve("chainmail_helmet.json"), active(
                "smelt", "minecraft:iron_ingot", 5)
                        .getBytes(StandardCharsets.UTF_8));
        try {
            ChildResult result = runProbe(root, ConfiguredProbe.class);
            assertEquals(result.output, 0, result.exitCode);
            assertTrue(result.output,
                    result.output.contains("RECYCLING_CONFIGURED_DATA_OK"));
        } finally {
            deleteTree(root);
        }
    }

    private static String active(String method, String result, int count) {
        return "{\"type\":\"mcose:recycling\",\"method\":\"" + method
                + "\",\"result\":{\"id\":\"" + result
                + "\",\"count\":" + count + ",\"legacy_metadata\":0}}";
    }

    private static ChildResult runProbe(
            Path root,
            Class<?> probe,
            String... arguments) throws Exception {
        File javaExecutable = new File(
                new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name", "").toLowerCase().contains("win")
                        ? "java.exe" : "java");
        ArrayList<String> command = new ArrayList<String>();
        command.add(javaExecutable.getAbsolutePath());
        command.add("-Dmcose.resourcesDir=" + root.toAbsolutePath());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(probe.getName());
        for (int i = 0; i < arguments.length; i++) command.add(arguments[i]);
        Process process = new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        InputStream output = process.getInputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = output.read(buffer)) >= 0) captured.write(buffer, 0, read);
        int exit = process.waitFor();
        return new ChildResult(exit,
                new String(captured.toByteArray(), StandardCharsets.UTF_8));
    }

    private static void deleteTree(Path root) throws Exception {
        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) {}
                });
    }

    private static InventoryCrafting inventory(ItemStack... stacks) {
        InventoryCrafting inventory = new InventoryCrafting(new Container() {
            public boolean b(EntityHuman player) {
                return true;
            }
        }, stacks.length, 1);
        for (int i = 0; i < stacks.length; i++) inventory.setItem(i, stacks[i]);
        return inventory;
    }

    private static final class ChildResult {
        final int exitCode;
        final String output;

        ChildResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    public static final class AtomicityProbe {
        public static void main(String[] args) throws Exception {
            Path malformed = new File(args[0]).toPath();
            StatisticList.a();
            RecyclingManager recycling = RecyclingManager.getInstance();
            Map<ResourceLocation, RecyclingManager.Definition> recyclingBefore =
                    recycling.definitions();
            if (recyclingBefore.size() != 45) {
                throw new AssertionError("Legacy oracle was not available at STARTUP");
            }

            CraftingManager manager = CraftingManager.getInstance();
            manager.registerShapelessRecipe(
                    new ItemStack(Item.DIAMOND), new Object[] {Item.FEATHER});
            CraftingRecipe craftingPlugin = (CraftingRecipe)manager.b().get(
                    manager.b().size() - 1);
            FurnaceRecipes furnace = FurnaceRecipes.getInstance();
            ItemStack furnacePlugin = new ItemStack(Item.STICK, 2, 0);
            furnace.registerRecipe(Item.FEATHER.id, furnacePlugin);
            List craftingBefore = manager.b();
            Map furnaceBefore = furnace.b();

            boolean failed = false;
            try {
                RegistryBootstrap.initialize();
            } catch (RuntimeException expected) {
                failed = true;
            }
            if (!failed) throw new AssertionError("Malformed recycling data did not fail");
            if (RegistryBootstrap.isInitialized()
                    || recycling.definitions() != recyclingBefore
                    || manager.b() != craftingBefore
                    || furnace.b() != furnaceBefore
                    || manager.b().get(manager.b().size() - 1) != craftingPlugin
                    || furnace.a(Item.FEATHER.id) != furnacePlugin) {
                throw new AssertionError("Failed recycling cutover mutated a live owner");
            }
            System.out.println("RECYCLING_FAILURE_ATOMIC");

            Files.delete(malformed);
            RegistryBootstrap.initialize();
            if (!RegistryBootstrap.isInitialized()
                    || recycling.definitions() == recyclingBefore
                    || recycling.definitions().size() != 45
                    || !manager.b().contains(craftingPlugin)
                    || furnace.a(Item.FEATHER.id) != furnacePlugin) {
                throw new AssertionError("Recycling retry did not commit cleanly");
            }
            System.out.println("RECYCLING_RETRY_OK");
        }
    }

    public static final class StartupBoundaryProbe {
        public static void main(String[] ignored) throws Exception {
            StatisticList.a();
            CraftingManager manager = CraftingManager.getInstance();
            manager.registerShapelessRecipe(
                    new ItemStack(Item.DIAMOND), new Object[] {Item.WOOD_PICKAXE});
            CraftingRecipe plugin = (CraftingRecipe)manager.b().get(
                    manager.b().size() - 1);
            FurnaceRecipes furnaceRecipes = FurnaceRecipes.getInstance();
            ItemStack furnacePlugin = new ItemStack(Item.DIAMOND, 1, 0);
            furnaceRecipes.registerRecipe(Item.IRON_PICKAXE.id, furnacePlugin);

            ItemStack before = manager.craft(inventory(
                    new ItemStack(Item.WOOD_PICKAXE, 8, 0)));
            if (before == null || before.id != Block.WOOD.id || before.count != 3) {
                throw new AssertionError("STARTUP recycling oracle did not win");
            }
            Field initialized = RecipeRegistryBootstrap.class
                    .getDeclaredField("initialized");
            initialized.setAccessible(true);
            if (initialized.getBoolean(null)) {
                throw new AssertionError("Recycling lookup forced recipe cutover");
            }
            System.out.println("RECYCLING_STARTUP_BOUNDARY_OK");

            RegistryBootstrap.initialize();
            ItemStack after = manager.craft(inventory(
                    new ItemStack(Item.WOOD_PICKAXE, 8, 0)));
            if (!manager.b().contains(plugin) || after == null
                    || after.id != Block.WOOD.id || after.count != 3) {
                throw new AssertionError(
                        "Data recycling lost priority or STARTUP plugin recipe");
            }
            System.out.println("RECYCLING_PRIORITY_OK");

            RecordingProbeFurnace furnace = new RecordingProbeFurnace();
            furnace.setItem(0, new ItemStack(Item.IRON_PICKAXE, 12, 0));
            furnace.burn();
            if (furnaceRecipes.a(Item.IRON_PICKAXE.id) != furnacePlugin
                    || furnace.proposed == null
                    || furnace.proposed.id != Item.IRON_INGOT.id
                    || furnace.proposed.count != 3
                    || furnace.getItem(2) == null
                    || furnace.getItem(2).id != Item.IRON_INGOT.id
                    || furnace.getItem(0) != null) {
                throw new AssertionError(
                        "Furnace recycling did not precede plugin smelting/event path");
            }
            System.out.println("RECYCLING_FURNACE_PRIORITY_EVENT_OK");
        }
    }

    private static final class RecordingProbeFurnace extends TileEntityFurnace {
        ItemStack proposed;

        protected ItemStack fireFurnaceSmeltEvent(
                ItemStack source,
                ItemStack proposedResult) {
            this.proposed = proposedResult.cloneItemStack();
            return proposedResult.cloneItemStack();
        }
    }

    public static final class ConfiguredProbe {
        public static void main(String[] ignored) {
            StatisticList.a();
            RegistryBootstrap.initialize();
            RecyclingManager manager = RecyclingManager.getInstance();
            Map<ResourceLocation, RecyclingManager.Definition> definitions =
                    manager.definitions();
            ResourceLocation wooden =
                    new ResourceLocation("minecraft", "wooden_pickaxe");
            ResourceLocation stone =
                    new ResourceLocation("minecraft", "stone_pickaxe");
            ResourceLocation chain =
                    new ResourceLocation("minecraft", "chainmail_helmet");
            RecyclingManager.Definition override = definitions.get(wooden);
            if (definitions.size() != 45
                    || override == null || override.getCount() != 1
                    || !new ResourceLocation("minecraft", "string").equals(
                            override.getResultKey())
                    || definitions.containsKey(stone)
                    || !definitions.containsKey(chain)
                    || manager.canRecycle(Item.STONE_PICKAXE.id)
                    || !manager.canSmeltRecycle(Item.CHAINMAIL_HELMET.id)) {
                throw new AssertionError("Configured recycling snapshot is wrong");
            }
            ItemStack result = manager.getSmeltRecycleResult(
                    new ItemStack(Item.CHAINMAIL_HELMET, 4, 0));
            if (result == null || result.id != Item.IRON_INGOT.id
                    || result.count != 5 || result.getData() != 0) {
                throw new AssertionError("Configured recycling addition did not run");
            }
            System.out.println("RECYCLING_CONFIGURED_DATA_OK");
        }
    }
}
