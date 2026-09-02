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
import net.minecraft.server.Material;
import net.minecraft.server.StatisticList;
import net.minecraft.server.item.component.CookingFuel;
import net.minecraft.server.registry.number.NumberProviders;
import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;

public class CookingFuelProfileParityTest {
    @Test
    public void pressurePlateBindingIsStableAcrossItsPvnTwelveMaterialBoundary()
            throws Exception {
        runProfile(11, false, 0);
        runProfile(12, true, 300);
    }

    private static void runProfile(
            int pvn,
            boolean expectedWoodMaterial,
            int expectedTicks) throws Exception {
        Path directory = Files.createTempDirectory("mcose-cooking-fuel-profile-");
        Path config = directory.resolve("uberbukkit.yml");
        String yaml = "config-version: 1\n"
                + "client:\n  allowed_protocols:\n    value: '" + pvn + "'\n";
        Files.write(config, yaml.getBytes(StandardCharsets.UTF_8));
        try {
            File javaExecutable = new File(
                    new File(System.getProperty("java.home"), "bin"),
                    System.getProperty("os.name", "").toLowerCase().contains("win")
                            ? "java.exe" : "java");
            Process process = new ProcessBuilder(
                    javaExecutable.getAbsolutePath(),
                    "-cp", System.getProperty("java.class.path"),
                    Probe.class.getName(),
                    Boolean.toString(expectedWoodMaterial),
                    Integer.toString(expectedTicks))
                    .directory(directory.toFile())
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
            assertEquals("PVN " + pvn + "\n" + text, 0, exit);
            assertTrue(text, text.contains("COOKING_FUEL_PROFILE_OK"));
        } finally {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (Exception ignored) {}
                    });
        }
    }

    public static final class Probe {
        public static void main(String[] args) {
            boolean expectedWoodMaterial = Boolean.parseBoolean(args[0]);
            int expectedTicks = Integer.parseInt(args[1]);
            StatisticList.a();
            ItemRegistryBootstrap.initialize();
            NumberProviderRegistryBootstrap.initialize();
            ItemCapabilityRegistryBootstrap.initialize();

            Item pressurePlate = Item.byId[Block.WOOD_PLATE.id];
            ResourceLocation itemKey = ItemRegistry.getKey(pressurePlate);
            CookingFuel binding = ItemCapabilityRegistryApi
                    .snapshotCookingFuelBindings().get(itemKey);
            if (binding == null
                    || !NumberProviders.COOKING_TIME_WOOD_BLOCKS.equals(
                            binding.getBurnTimeKey())) {
                throw new AssertionError(
                        "Oak pressure plate lost its canonical wood attachment");
            }
            if ((Block.WOOD_PLATE.material == Material.WOOD)
                    != expectedWoodMaterial) {
                throw new AssertionError("Unexpected pressure-plate material");
            }
            int actualTicks = ItemCapabilityRegistryApi.getFuelTicks(pressurePlate);
            if (actualTicks != expectedTicks) {
                throw new AssertionError("Pressure-plate fuel ticks "
                        + actualTicks + ", expected " + expectedTicks);
            }
            System.out.println("COOKING_FUEL_PROFILE_OK");
        }
    }
}
