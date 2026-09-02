package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.registry.Registries;
import net.minecraft.server.registry.ItemTagRegistryApi;
import net.minecraft.server.registry.ItemTags;
import net.minecraft.server.registry.RecipeRegistryApi;
import net.minecraft.server.registry.RecipeRegistryBootstrap;
import net.minecraft.server.registry.SimpleRegistry;
import net.minecraft.server.util.ResourceLocation;
import net.minecraft.server.registry.RegistryBootstrap;
import net.minecraft.server.mod.ModContext;
import net.minecraft.server.mod.ModInitializer;
import org.junit.Test;

public class RegistryBootstrapStartupLifecycleTest {
    @Test
    public void modItemsExistBeforeTheItemTagSnapshotInFreshJvm()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-mod-item-tag-startup-");
        Path tag = root.resolve(
                "data/minecraft/tags/item/wolf_food.json");
        Path modJson = root.resolve("mods/wolf-food/mod.json");
        Files.createDirectories(tag.getParent());
        Files.createDirectories(modJson.getParent());
        write(tag, "{\"replace\":true,\"values\":[\"test:mod_food\"]}");
        write(modJson, "{\"id\":\"wolf-food\",\"entrypoint\":\""
                + ModItemInitializer.class.getName() + "\"}");
        try {
            File javaExecutable = new File(
                    new File(System.getProperty("java.home"), "bin"),
                    System.getProperty("os.name", "").toLowerCase().contains("win")
                            ? "java.exe" : "java");
            Process process = new ProcessBuilder(
                    javaExecutable.getAbsolutePath(),
                    "-Dmcose.resourcesDir=" + root.toAbsolutePath(),
                    "-cp", System.getProperty("java.class.path"),
                    ModItemTagProbe.class.getName(),
                    root.toAbsolutePath().toString())
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            InputStream output = process.getInputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = output.read(buffer)) >= 0) {
                captured.write(buffer, 0, read);
            }
            int exit = process.waitFor();
            String text = new String(
                    captured.toByteArray(), StandardCharsets.UTF_8);
            assertEquals(text, 0, exit);
            assertTrue(text, text.contains("MOD_ITEM_TAG_STARTUP_OK"));
        } finally {
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (Exception ignored) {}
                    });
        }
    }

    @Test
    public void malformedConfiguredDataDoesNotCommitFlagsAndCanRetryInFreshJvm()
            throws Exception {
        ChildResult result = runProbe("retry");
        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains("FIRST_FAILURE_PROPAGATED"));
        assertTrue(result.output, result.output.contains("RETRY_SUCCEEDED"));
    }

    @Test
    public void configuredDataFailureEscapesTheStartupBoundaryInFreshJvm()
            throws Exception {
        ChildResult result = runProbe("fatal");
        assertNotEquals(result.output, 0, result.exitCode);
        assertTrue(result.output,
                result.output.contains("Failed to load data registry 'loot_table'"));
        assertTrue(result.output,
                !result.output.contains("FATAL_FAILURE_WAS_SWALLOWED"));
    }

    @Test
    public void malformedRecipeCandidateLeavesEveryLiveOwnerAndPluginUntouched()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-recipe-bootstrap-");
        Path override = root.resolve(
                "data/minecraft/recipe/shaped/wooden_pickaxe_0.json");
        Files.createDirectories(override.getParent());
        write(override, "{");
        try {
            File javaExecutable = new File(
                    new File(System.getProperty("java.home"), "bin"),
                    System.getProperty("os.name", "").toLowerCase().contains("win")
                            ? "java.exe" : "java");
            Process process = new ProcessBuilder(
                    javaExecutable.getAbsolutePath(),
                    "-Dmcose.resourcesDir=" + root.toAbsolutePath(),
                    "-cp", System.getProperty("java.class.path"),
                    RecipeProbe.class.getName(),
                    override.toAbsolutePath().toString())
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            InputStream output = process.getInputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = output.read(buffer)) >= 0) captured.write(buffer, 0, read);
            int exit = process.waitFor();
            String text = new String(captured.toByteArray(), StandardCharsets.UTF_8);
            assertEquals(text, 0, exit);
            assertTrue(text, text.contains("RECIPE_FAILURE_ATOMIC"));
            assertTrue(text, text.contains("RECIPE_RETRY_SUCCEEDED"));
        } finally {
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (Exception ignored) {}
                    });
        }
    }

    @Test
    public void generatedRecipeIdsRollbackAfterPostStagingPreflightFailure()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-recipe-id-preflight-");
        try {
            File javaExecutable = new File(
                    new File(System.getProperty("java.home"), "bin"),
                    System.getProperty("os.name", "").toLowerCase().contains("win")
                            ? "java.exe" : "java");
            Process process = new ProcessBuilder(
                    javaExecutable.getAbsolutePath(),
                    "-cp", System.getProperty("java.class.path"),
                    GeneratedIdProbe.class.getName())
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            InputStream output = process.getInputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = output.read(buffer)) >= 0) captured.write(buffer, 0, read);
            int exit = process.waitFor();
            String text = new String(captured.toByteArray(), StandardCharsets.UTF_8);
            assertEquals(text, 0, exit);
            assertTrue(text, text.contains("GENERATED_ID_PREFLIGHT_ROLLBACK_OK"));
            assertTrue(text, text.contains("POST_CUTOVER_NULL_KEY_OK"));
        } finally {
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (Exception ignored) {}
                    });
        }
    }

    @Test
    public void apiRegistrationWaitsForRecipeCutoverCriticalSection()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-recipe-lock-order-");
        try {
            File javaExecutable = new File(
                    new File(System.getProperty("java.home"), "bin"),
                    System.getProperty("os.name", "").toLowerCase().contains("win")
                            ? "java.exe" : "java");
            Process process = new ProcessBuilder(
                    javaExecutable.getAbsolutePath(),
                    "-cp", System.getProperty("java.class.path"),
                    ConcurrentRegistrationProbe.class.getName())
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            InputStream output = process.getInputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = output.read(buffer)) >= 0) captured.write(buffer, 0, read);
            int exit = process.waitFor();
            String text = new String(captured.toByteArray(), StandardCharsets.UTF_8);
            assertEquals(text, 0, exit);
            assertTrue(text, text.contains("RECIPE_CUTOVER_LOCK_ORDER_OK"));
        } finally {
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (Exception ignored) {}
                    });
        }
    }

    private static ChildResult runProbe(String mode) throws Exception {
        Path root = Files.createTempDirectory("mcose-registry-bootstrap-");
        Path override = root.resolve(
                "data/minecraft/loot_table/chests/dungeon.json");
        Files.createDirectories(override.getParent());
        write(override, "{");

        try {
            File javaExecutable = new File(
                    new File(System.getProperty("java.home"), "bin"),
                    System.getProperty("os.name", "").toLowerCase().contains("win")
                            ? "java.exe" : "java");
            Process process = new ProcessBuilder(
                    javaExecutable.getAbsolutePath(),
                    "-Dmcose.resourcesDir=" + root.toAbsolutePath(),
                    "-cp",
                    System.getProperty("java.class.path"),
                    Probe.class.getName(),
                    mode,
                    override.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            InputStream output = process.getInputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = output.read(buffer)) >= 0) {
                captured.write(buffer, 0, read);
            }
            int exitCode = process.waitFor();
            return new ChildResult(
                    exitCode,
                    new String(captured.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {}
                    });
        }
    }

    private static void write(Path path, String value) throws Exception {
        OutputStream output = new FileOutputStream(path.toFile());
        try {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
    }

    private static final class ChildResult {
        final int exitCode;
        final String output;

        ChildResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            String mode = args.length == 0 ? "" : args[0];
            Path override = args.length < 2 ? null : new File(args[1]).toPath();
            StatisticList.a();

            if ("fatal".equals(mode)) {
                MinecraftServer.initializeRegistriesForStartup();
                System.out.println("FATAL_FAILURE_WAS_SWALLOWED");
                return;
            }

            boolean failed = false;
            try {
                MinecraftServer.initializeRegistriesForStartup();
            } catch (RuntimeException expected) {
                failed = true;
                System.out.println("FIRST_FAILURE_PROPAGATED");
            }
            if (!failed) {
                throw new AssertionError("Malformed configured data did not fail startup");
            }
            if (RegistryBootstrap.isInitialized()) {
                throw new AssertionError("Central bootstrap flag committed after failure");
            }

            Files.delete(override);
            MinecraftServer.initializeRegistriesForStartup();
            if (!RegistryBootstrap.isInitialized()) {
                throw new AssertionError("Registry bootstrap did not commit after retry");
            }
            System.out.println("RETRY_SUCCEEDED");
        }
    }

    public static final class ModItemTagProbe {
        public static void main(String[] args) throws Exception {
            StatisticList.a();
            MinecraftServer.initializeModsAndRegistriesForStartup(
                    new File(args[0]));
            if (!RegistryBootstrap.isInitialized()
                    || !ItemTagRegistryApi.areTagBindingsCurrent()
                    || ModItemInitializer.item == null
                    || !ItemTags.is(ModItemInitializer.item,
                            ItemTags.WOLF_FOOD)) {
                throw new AssertionError(
                        "Mod item was not bound into the startup wolf-food tag");
            }
            System.out.println("MOD_ITEM_TAG_STARTUP_OK");
        }
    }

    public static final class ModItemInitializer implements ModInitializer {
        static Item item;

        public void initialize(ModContext context) {
            item = new ModFood(29001);
            context.registerItem("test:mod_food", item, item.id);
        }
    }

    private static final class ModFood extends ItemFood {
        ModFood(int index) {
            super(index, 3, true);
        }
    }

    public static final class RecipeProbe {
        public static void main(String[] args) throws Exception {
            Path override = new File(args[0]).toPath();
            StatisticList.a();
            CraftingManager manager = CraftingManager.getInstance();
            manager.registerShapelessRecipe(
                    new ItemStack(Item.STICK), new Object[] {Item.FEATHER});
            CraftingRecipe craftingPlugin = (CraftingRecipe)manager.b().get(
                    manager.b().size() - 1);

            FurnaceRecipes furnace = FurnaceRecipes.getInstance();
            ItemStack genericPlugin = new ItemStack(Item.STICK, 2, 0);
            ItemStack exactPlugin = new ItemStack(Item.DIAMOND, 1, 0);
            furnace.registerRecipe(Item.FEATHER.id, genericPlugin);
            furnace.registerRecipe(Block.SAND.id, 7, exactPlugin);

            List craftingBefore = manager.b();
            Map genericBefore = furnace.b();
            Map<Integer, ItemStack> exactBefore = furnace.getExactRecipeSnapshot();
            Map<ResourceLocation, CraftingRecipe> registryBefore = registrySnapshot();

            boolean failed = false;
            try {
                MinecraftServer.initializeRegistriesForStartup();
            } catch (RuntimeException expected) {
                failed = true;
            }
            if (!failed) throw new AssertionError("Malformed recipe did not fail");
            if (manager.b() != craftingBefore || furnace.b() != genericBefore) {
                throw new AssertionError("Recipe owner pointer changed after decode failure");
            }
            if (manager.b().get(manager.b().size() - 1) != craftingPlugin
                    || furnace.a(Item.FEATHER.id) != genericPlugin
                    || furnace.a(new ItemStack(Block.SAND, 1, 7)) != exactPlugin) {
                throw new AssertionError("Plugin recipe changed after decode failure");
            }
            assertIdentityMap(exactBefore, furnace.getExactRecipeSnapshot());
            assertIdentityMap(registryBefore, registrySnapshot());
            if (RegistryBootstrap.isInitialized()) {
                throw new AssertionError("Central bootstrap committed after recipe failure");
            }
            System.out.println("RECIPE_FAILURE_ATOMIC");

            Files.delete(override);
            MinecraftServer.initializeRegistriesForStartup();
            if (!RegistryBootstrap.isInitialized()
                    || RecipeRegistryBootstrap.javaFallbackRecipeCount() != 0) {
                throw new AssertionError("Corrected recipe retry did not commit");
            }
            List recipes = manager.b();
            if (recipes.get(recipes.size() - 7) != craftingPlugin
                    || furnace.a(Item.FEATHER.id) != genericPlugin
                    || furnace.a(new ItemStack(Block.SAND, 1, 7)) != exactPlugin) {
                throw new AssertionError("Plugin recipe changed after retry");
            }
            System.out.println("RECIPE_RETRY_SUCCEEDED");
        }

        private static Map<ResourceLocation, CraftingRecipe> registrySnapshot() {
            Map<ResourceLocation, CraftingRecipe> result =
                    new LinkedHashMap<ResourceLocation, CraftingRecipe>();
            for (ResourceLocation key : new ArrayList<ResourceLocation>(
                    Registries.RECIPE.keys())) {
                result.put(key, Registries.RECIPE.get(key));
            }
            return result;
        }

        private static void assertIdentityMap(Map expected, Map actual) {
            if (!expected.keySet().equals(actual.keySet())) {
                throw new AssertionError("Recipe map keys changed after failure");
            }
            for (Object key : expected.keySet()) {
                if (expected.get(key) != actual.get(key)) {
                    throw new AssertionError("Recipe map identity changed for " + key);
                }
            }
        }
    }

    public static final class GeneratedIdProbe {
        public static void main(String[] ignored) throws Exception {
            StatisticList.a();
            CraftingManager manager = CraftingManager.getInstance();
            manager.registerShapedRecipe(
                    new ItemStack(Item.WOOD_PICKAXE),
                    new Object[] {"#", Character.valueOf('#'), Block.DIRT});
            CraftingRecipe startupPlugin = (CraftingRecipe)manager.b().get(
                    manager.b().size() - 1);

            List craftingBefore = manager.b();
            Map furnaceBefore = FurnaceRecipes.getInstance().b();
            int counterBefore = generatedRecipeCounter();
            int expectedPluginCounter = Math.max(counterBefore, 163);

            Registries.RECIPE.freeze();
            boolean failed = false;
            try {
                RecipeRegistryBootstrap.initialize();
            } catch (IllegalStateException expected) {
                failed = expected.getMessage() != null
                        && expected.getMessage().contains("frozen");
            }
            if (!failed) {
                throw new AssertionError("Frozen registry did not fail post-staging preflight");
            }
            if (generatedRecipeCounter() != counterBefore) {
                throw new AssertionError("Failed staging advanced generated recipe IDs");
            }
            if (manager.b() != craftingBefore
                    || FurnaceRecipes.getInstance().b() != furnaceBefore) {
                throw new AssertionError("Failed preflight published a recipe owner");
            }
            System.out.println("GENERATED_ID_PREFLIGHT_ROLLBACK_OK");

            unfreezeRecipeRegistry();
            RecipeRegistryBootstrap.initialize();
            ResourceLocation startupKey = Registries.RECIPE.getKey(startupPlugin);
            ResourceLocation expectedStartupKey = new ResourceLocation(
                    "minecraft", "shaped/wooden_pickaxe_" + expectedPluginCounter);
            if (!expectedStartupKey.equals(startupKey)) {
                throw new AssertionError("Retry generated " + startupKey
                        + " instead of stable key " + expectedStartupKey);
            }
            if (generatedRecipeCounter() != expectedPluginCounter + 1) {
                throw new AssertionError("Committed generated recipe ID cursor is wrong");
            }

            int beforePostWorld = manager.b().size();
            ResourceLocation postWorldKey = RecipeRegistryApi.registerShaped(
                    null,
                    new ItemStack(Item.WOOD_PICKAXE),
                    new Object[] {"#", Character.valueOf('#'), Item.STICK});
            CraftingRecipe postWorld = (CraftingRecipe)manager.b().get(beforePostWorld);
            ResourceLocation expectedPostWorldKey = new ResourceLocation(
                    "minecraft", "shaped/wooden_pickaxe_"
                            + (expectedPluginCounter + 1));
            if (!expectedPostWorldKey.equals(postWorldKey)
                    || Registries.RECIPE.get(postWorldKey) != postWorld) {
                throw new AssertionError("Null-key post-cutover recipe collided: "
                        + postWorldKey);
            }
            System.out.println("POST_CUTOVER_NULL_KEY_OK");
        }

        private static int generatedRecipeCounter() throws Exception {
            Field field = RecipeRegistryApi.class.getDeclaredField("recipeCounter");
            field.setAccessible(true);
            return field.getInt(null);
        }

        private static void unfreezeRecipeRegistry() throws Exception {
            Field field = SimpleRegistry.class.getDeclaredField("frozen");
            field.setAccessible(true);
            field.setBoolean(Registries.RECIPE, false);
        }
    }

    public static final class ConcurrentRegistrationProbe {
        public static void main(String[] ignored) throws Exception {
            StatisticList.a();
            final CraftingManager manager = CraftingManager.getInstance();
            final AtomicReference<Throwable> bootstrapFailure =
                    new AtomicReference<Throwable>();
            final AtomicReference<Throwable> registrationFailure =
                    new AtomicReference<Throwable>();
            final AtomicReference<ResourceLocation> registeredKey =
                    new AtomicReference<ResourceLocation>();
            final AtomicReference<CraftingRecipe> registeredRecipe =
                    new AtomicReference<CraftingRecipe>();
            final AtomicBoolean enteredApiMonitor = new AtomicBoolean(false);
            final CountDownLatch registrationStarted = new CountDownLatch(1);

            Thread bootstrap = new Thread(new Runnable() {
                public void run() {
                    try {
                        RecipeRegistryBootstrap.initialize();
                    } catch (Throwable failure) {
                        bootstrapFailure.set(failure);
                    }
                }
            }, "recipe-cutover");
            Thread registration = new Thread(new Runnable() {
                public void run() {
                    try {
                        registrationStarted.countDown();
                        synchronized (RecipeRegistryApi.class) {
                            enteredApiMonitor.set(true);
                            int before = manager.b().size();
                            ResourceLocation key = RecipeRegistryApi.registerShaped(
                                    null,
                                    new ItemStack(Item.WOOD_PICKAXE),
                                    new Object[] {"#", Character.valueOf('#'), Item.STICK});
                            registeredKey.set(key);
                            registeredRecipe.set(
                                    (CraftingRecipe)manager.b().get(before));
                        }
                    } catch (Throwable failure) {
                        registrationFailure.set(failure);
                    }
                }
            }, "recipe-registration");

            synchronized (manager) {
                bootstrap.start();
                awaitBlocked(bootstrap, "cutover did not block on crafting owner");
                registration.start();
                if (!registrationStarted.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("registration thread did not start");
                }
                awaitBlocked(registration, "registration did not block on API facade");
                if (enteredApiMonitor.get()) {
                    throw new AssertionError(
                            "registration entered API monitor before cutover commit");
                }
            }

            bootstrap.join(10000L);
            registration.join(10000L);
            if (bootstrap.isAlive() || registration.isAlive()) {
                throw new AssertionError("recipe cutover concurrency probe timed out");
            }
            if (bootstrapFailure.get() != null) {
                throw new AssertionError("recipe cutover failed", bootstrapFailure.get());
            }
            if (registrationFailure.get() != null) {
                throw new AssertionError(
                        "post-cutover registration failed", registrationFailure.get());
            }
            ResourceLocation key = registeredKey.get();
            CraftingRecipe recipe = registeredRecipe.get();
            ResourceLocation expected = new ResourceLocation(
                    "minecraft", "shaped/wooden_pickaxe_163");
            if (!expected.equals(key) || Registries.RECIPE.get(key) != recipe
                    || manager.b().get(manager.b().size() - 1) != recipe) {
                throw new AssertionError(
                        "blocked registration was lost or mis-keyed after cutover: " + key);
            }
            System.out.println("RECIPE_CUTOVER_LOCK_ORDER_OK");
        }

        private static void awaitBlocked(Thread thread, String message)
                throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L);
            while (thread.getState() != Thread.State.BLOCKED) {
                if (!thread.isAlive()) {
                    throw new AssertionError(message + " (thread exited)");
                }
                if (System.nanoTime() >= deadline) {
                    throw new AssertionError(message + " (state "
                            + thread.getState() + ")");
                }
                Thread.sleep(1L);
            }
        }
    }
}
