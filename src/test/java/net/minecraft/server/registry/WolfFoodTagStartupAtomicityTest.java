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
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.StatisticList;
import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;

public class WolfFoodTagStartupAtomicityTest {
    @Test
    public void invalidConfiguredTagLeavesOwnerUntouchedAndRetryable()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-wolf-food-rollback-");
        Path override = root.resolve(
                "data/minecraft/tags/item/wolf_food.json");
        Files.createDirectories(override.getParent());
        Files.write(override, (
                "{\"replace\":true,\"values\":[\"minecraft:bone\"]}")
                .getBytes(StandardCharsets.UTF_8));
        try {
            File javaExecutable = new File(
                    new File(System.getProperty("java.home"), "bin"),
                    System.getProperty("os.name", "").toLowerCase().contains("win")
                            ? "java.exe" : "java");
            Process process = new ProcessBuilder(
                    javaExecutable.getAbsolutePath(),
                    "-Dmcose.resourcesDir=" + root.toAbsolutePath(),
                    "-cp", System.getProperty("java.class.path"),
                    Probe.class.getName(),
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
            int exit = process.waitFor();
            String text = new String(
                    captured.toByteArray(), StandardCharsets.UTF_8);
            assertEquals(text, 0, exit);
            assertTrue(text, text.contains("WOLF_FOOD_FAILURE_ATOMIC"));
            assertTrue(text, text.contains("WOLF_FOOD_RETRY_OK"));
            assertTrue(text, text.contains("DUPLICATE_ITEM_REVISION_STABLE"));
            assertTrue(text, text.contains(
                    "HOT_TAG_READ_DID_NOT_RESCAN_LEGACY_ARRAY"));
            assertTrue(text, text.contains("STALE_ITEM_TAG_READ_REJECTED"));
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

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            Path override = new File(args[0]).toPath();
            assert Block.STONE != null;
            StatisticList.a();
            if (ItemTagRegistryApi.tagBindings().registryRevision() != -1L) {
                throw new AssertionError(
                        "Item-tag owner was not initially empty");
            }

            boolean failed = false;
            try {
                RegistryBootstrap.initialize();
            } catch (RuntimeException expected) {
                failed = true;
            }
            if (!failed) {
                throw new AssertionError(
                        "Invalid configured wolf-food tag did not fail");
            }
            if (RegistryBootstrap.isInitialized()
                    || ItemTagRegistryApi.tagBindings().registryRevision() != -1L) {
                throw new AssertionError(
                        "Item-tag owner or bootstrap flag committed after failure");
            }
            System.out.println("WOLF_FOOD_FAILURE_ATOMIC");

            Files.delete(override);
            RegistryBootstrap.initialize();
            if (!RegistryBootstrap.isInitialized()
                    || !ItemTagRegistryApi.areTagBindingsCurrent()
                    || !ItemTags.is(Item.PORK, ItemTags.WOLF_FOOD)
                    || !ItemTags.is(Item.GRILLED_PORK, ItemTags.WOLF_FOOD)) {
                throw new AssertionError(
                        "Clean retry did not publish wolf-food tag");
            }
            System.out.println("WOLF_FOOD_RETRY_OK");

            long beforeDuplicate = ItemRegistry.registrationRevision();
            ItemRegistry.register(
                    new ResourceLocation("minecraft", "porkchop"),
                    Item.PORK, Item.PORK.id);
            if (ItemRegistry.registrationRevision() != beforeDuplicate) {
                throw new AssertionError(
                        "Duplicate canonical registration advanced revision");
            }
            System.out.println("DUPLICATE_ITEM_REVISION_STABLE");

            Item late = new LateItem(29000);
            if (!ItemTags.is(Item.PORK, ItemTags.WOLF_FOOD)
                    || !ItemTagRegistryApi.areTagBindingsCurrent()) {
                throw new AssertionError(
                        "Gameplay tag read rescanned an untracked legacy item");
            }
            System.out.println("HOT_TAG_READ_DID_NOT_RESCAN_LEGACY_ARRAY");

            ItemRegistry.register(
                    new ResourceLocation("test", "late_item"), late, late.id);
            if (ItemTagRegistryApi.areTagBindingsCurrent()) {
                throw new AssertionError(
                        "Late canonical item did not stale tag generation");
            }
            boolean staleReadRejected = false;
            try {
                ItemTags.is(Item.PORK, ItemTags.WOLF_FOOD);
            } catch (IllegalStateException expected) {
                staleReadRejected = expected.getMessage().contains("stale");
            }
            if (!staleReadRejected) {
                throw new AssertionError(
                        "Stale item-tag membership was readable");
            }
            System.out.println("STALE_ITEM_TAG_READ_REJECTED");
        }

        private static final class LateItem extends Item {
            LateItem(int index) {
                super(index);
            }
        }
    }
}
