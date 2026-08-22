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
import net.minecraft.server.StatisticList;
import net.minecraft.server.Block;
import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;

public class BlockMiningTagStartupAtomicityTest {
    @Test
    public void malformedConfiguredTagLeavesMiningOwnersUntouchedAndRetryable()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-mining-tag-rollback-");
        Path override = root.resolve(
                "data/minecraft/tags/block/mineable/pickaxe.json");
        Files.createDirectories(override.getParent());
        Files.write(override, "{".getBytes(StandardCharsets.UTF_8));
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
            String text = new String(captured.toByteArray(), StandardCharsets.UTF_8);
            assertEquals(text, 0, exit);
            assertTrue(text, text.contains("MINING_TAG_FAILURE_ATOMIC"));
            assertTrue(text, text.contains("MINING_TAG_RETRY_OK"));
            assertTrue(text, text.contains("STALE_TAG_READ_REJECTED"));
        } finally {
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (Exception ignored) {}
                    });
        }
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            Path override = new File(args[0]).toPath();
            StatisticList.a();
            if (BlockMiningRegistryApi.size() != 0
                    || BlockMiningRegistryApi.tagBindings().registryRevision() != -1L) {
                throw new AssertionError("Mining owners were not initially empty");
            }

            boolean failed = false;
            try {
                RegistryBootstrap.initialize();
            } catch (RuntimeException expected) {
                failed = true;
            }
            if (!failed) {
                throw new AssertionError("Malformed configured tag did not fail");
            }
            if (RegistryBootstrap.isInitialized()
                    || BlockMiningRegistryApi.size() != 0
                    || BlockMiningRegistryApi.tagBindings().registryRevision() != -1L) {
                throw new AssertionError(
                        "Mining owner or bootstrap flag committed after failure");
            }
            System.out.println("MINING_TAG_FAILURE_ATOMIC");

            Files.delete(override);
            RegistryBootstrap.initialize();
            if (!RegistryBootstrap.isInitialized()
                    || BlockMiningRegistryApi.size() != 112
                    || !BlockMiningRegistryApi.areTagBindingsCurrent()) {
                throw new AssertionError("Clean retry did not publish mining tags");
            }
            System.out.println("MINING_TAG_RETRY_OK");

            BlockRegistry.registerAlias(
                    new ResourceLocation("test", "late_stone_alias"), Block.STONE);
            if (BlockMiningRegistryApi.areTagBindingsCurrent()) {
                throw new AssertionError("Late block alias did not stale tag generation");
            }
            boolean staleReadRejected = false;
            try {
                BlockMiningRegistryApi.isInTag(
                        BlockTags.MINEABLE_WITH_PICKAXE, Block.STONE);
            } catch (IllegalStateException expected) {
                staleReadRejected = expected.getMessage().contains("stale");
            }
            if (!staleReadRejected) {
                throw new AssertionError("Stale block tag membership was readable");
            }
            System.out.println("STALE_TAG_READ_REJECTED");
        }
    }
}
