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
import net.minecraft.server.StatisticList;
import org.junit.Test;

public class BlockMiningConfiguredTagOverrideTest {
    @Test
    public void configuredProfileReplacesBuiltInMembershipWithoutJavaFallback()
            throws Exception {
        Path root = Files.createTempDirectory("mcose-mining-tag-override-");
        Path override = root.resolve(
                "data/minecraft/tags/block/legacy_mining/pickaxe_1_75.json");
        Files.createDirectories(override.getParent());
        Files.write(override,
                ("{\"replace\":true,\"values\":[\"minecraft:stone\"]}")
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
                    Probe.class.getName())
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
            assertTrue(text, text.contains("CONFIGURED_MINING_TAG_APPLIED"));
        } finally {
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (Exception ignored) {}
                    });
        }
    }

    public static final class Probe {
        public static void main(String[] args) {
            StatisticList.a();
            RegistryBootstrap.initialize();
            assertRule(BlockMiningRegistryApi.get(Block.STONE),
                    MiningToolType.PICKAXE, 1.75F);
            assertRule(BlockMiningRegistryApi.get(Block.COBBLESTONE),
                    MiningToolType.NONE, 1.0F);
            assertRule(BlockMiningRegistryApi.get(Block.COBBLESTONE_STAIRS),
                    MiningToolType.NONE, 1.0F);
            System.out.println("CONFIGURED_MINING_TAG_APPLIED");
        }

        private static void assertRule(
                BlockMiningRule rule,
                MiningToolType tool,
                float speed) {
            if (rule.getPreferredTool() != tool
                    || rule.getSpeedMultiplier() != speed) {
                throw new AssertionError("Unexpected rule "
                        + rule.getPreferredTool() + "/" + rule.getSpeedMultiplier());
            }
        }
    }
}
