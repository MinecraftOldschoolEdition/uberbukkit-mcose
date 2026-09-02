package net.minecraft.server.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.Material;
import net.minecraft.server.World;
import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;
import sun.misc.Unsafe;

/** Lifecycle and ownership coverage for immutable block-loot bindings. */
public class LootTablesInitializationTest {
    @Test
    public void initializedFastPathIsLockFreeByConstruction() throws Exception {
        Field initialized = LootTables.class.getDeclaredField("initialized");
        Method initialize = LootTables.class.getDeclaredMethod("initialize");

        assertTrue(Modifier.isVolatile(initialized.getModifiers()));
        assertFalse(Modifier.isSynchronized(initialize.getModifiers()));
        assertProbeSuccess("fast-path");
    }

    @Test
    public void preRegisteredPluginBlockDoesNotBecomeRequiredVanillaData()
            throws Exception {
        assertProbeSuccess("pre-registered-plugin");
    }

    @Test
    public void simultaneousInitializationPublishesOneCompleteSnapshot()
            throws Exception {
        assertProbeSuccess("concurrent");
    }

    private static void assertProbeSuccess(String mode) throws Exception {
        File javaExecutable = new File(
                new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name", "").toLowerCase()
                        .contains("win") ? "java.exe" : "java");
        Process process = new ProcessBuilder(
                javaExecutable.getAbsolutePath(),
                "-cp",
                System.getProperty("java.class.path"),
                Probe.class.getName(),
                mode)
                .redirectErrorStream(true)
                .start();
        boolean exited = process.waitFor(45L, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
        }

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        InputStream output = process.getInputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = output.read(buffer)) >= 0) {
            captured.write(buffer, 0, read);
        }
        String text = new String(
                captured.toByteArray(), StandardCharsets.UTF_8);
        assertTrue("Timed out running " + mode + ": " + text, exited);
        assertEquals(text, 0, process.exitValue());
        assertTrue(text, text.contains("LOOT_TABLE_LIFECYCLE_OK=" + mode));
    }

    public static final class Probe {
        private Probe() {}

        public static void main(String[] args) throws Exception {
            if (args.length != 1) {
                throw new AssertionError("Expected one lifecycle probe mode");
            }
            if ("pre-registered-plugin".equals(args[0])) {
                preRegisteredPluginProbe();
            } else if ("concurrent".equals(args[0])) {
                concurrentInitializationProbe();
            } else if ("fast-path".equals(args[0])) {
                initializedFastPathProbe();
            } else {
                throw new AssertionError("Unknown lifecycle probe " + args[0]);
            }
            System.out.println("LOOT_TABLE_LIFECYCLE_OK=" + args[0]);
        }

        private static void preRegisteredPluginProbe() throws Exception {
            requireLegacyRegistries();
            int freeId = findFreeBlockId();
            if (freeId < 0) {
                throw new AssertionError(
                        "No free legacy block slot for lifecycle probe");
            }

            ProbePluginBlock pluginBlock = new ProbePluginBlock(freeId);
            ResourceLocation pluginKey =
                    new ResourceLocation("test", "early_plugin_block");
            BlockRegistry.register(pluginKey, pluginBlock, freeId);
            if (VanillaRegistryKeys.blockKey(pluginBlock) != null) {
                throw new AssertionError(
                        "Plugin block was mistaken for a declared vanilla identity");
            }
            if (!pluginKey.equals(BlockRegistry.getKey(pluginBlock))) {
                throw new AssertionError(
                        "Pre-registered plugin key was not primary");
            }

            // This would require test:blocks/early_plugin_block.json if loot
            // ownership were inferred from every primary registry key.
            LootTables.initialize();
            if (LootTables.isBuiltInBlock(pluginBlock)) {
                throw new AssertionError("Plugin block entered vanilla bindings");
            }
            if (LootTables.getBlockLootTable(pluginBlock) != null) {
                throw new AssertionError(
                        "Plugin block received a vanilla loot table");
            }
            if (LootTables.builtInTableKeys().contains(
                    new ResourceLocation("test", "blocks/early_plugin_block"))) {
                throw new AssertionError(
                        "Plugin block table became required data");
            }

            World world = lightweightWorld(new Random(123L));
            pluginBlock.dropNaturally(world, 0, 64, 0, 6, 1.0F);
            if (pluginBlock.quantityCalls != 1
                    || pluginBlock.idCalls != 1
                    || pluginBlock.damageCalls != 1
                    || pluginBlock.spawnCalls != 1) {
                throw new AssertionError(
                        "Legacy virtual fallback was not invoked exactly: "
                                + pluginBlock.quantityCalls + ","
                                + pluginBlock.idCalls + ","
                                + pluginBlock.damageCalls + ","
                                + pluginBlock.spawnCalls);
            }
            if (pluginBlock.spawned == null
                    || pluginBlock.spawned.id != Item.STICK.id
                    || pluginBlock.spawned.count != 1
                    || pluginBlock.spawned.getData() != 10) {
                throw new AssertionError(
                        "Legacy virtual fallback emitted the wrong stack");
            }

            pluginBlock.resetCalls();
            pluginBlock.dropNaturally(
                    lightweightWorld(new ZeroFloatRandom()),
                    0,
                    64,
                    0,
                    6,
                    0.0F);
            if (pluginBlock.quantityCalls != 1
                    || pluginBlock.idCalls != 0
                    || pluginBlock.damageCalls != 0
                    || pluginBlock.spawnCalls != 0) {
                throw new AssertionError(
                        "Server strict '<' zero-chance fallback changed: "
                                + pluginBlock.quantityCalls + ","
                                + pluginBlock.idCalls + ","
                                + pluginBlock.damageCalls + ","
                                + pluginBlock.spawnCalls);
            }
        }

        private static void concurrentInitializationProbe() throws Exception {
            requireLegacyRegistries();
            final int threadCount = 16;
            final CountDownLatch ready = new CountDownLatch(threadCount);
            final CountDownLatch start = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(threadCount);
            final AtomicReference<Throwable> failure =
                    new AtomicReference<Throwable>();
            final AtomicReference<BlockLootTable> publishedStone =
                    new AtomicReference<BlockLootTable>();

            for (int index = 0; index < threadCount; index++) {
                Thread worker = new Thread(new Runnable() {
                    public void run() {
                        ready.countDown();
                        try {
                            start.await();
                            LootTables.initialize();
                            for (int iteration = 0;
                                    iteration < 64;
                                    iteration++) {
                                BlockLootTable stone =
                                        LootTables.getBlockLootTable(Block.STONE);
                                if (stone == null) {
                                    throw new AssertionError(
                                            "Observed an incomplete block binding");
                                }
                                BlockLootTable first = publishedStone.get();
                                if (first == null) {
                                    publishedStone.compareAndSet(null, stone);
                                    first = publishedStone.get();
                                }
                                if (stone != first) {
                                    throw new AssertionError(
                                            "Threads observed different table snapshots");
                                }
                                if (!LootTables.isBuiltInBlock(Block.STONE)
                                        || LootTables.get(LootTables.DUNGEON)
                                                == null) {
                                    throw new AssertionError(
                                            "Observed a partially published registry");
                                }
                            }
                        } catch (Throwable problem) {
                            failure.compareAndSet(null, problem);
                        } finally {
                            done.countDown();
                        }
                    }
                }, "loot-init-probe-" + index);
                worker.start();
            }

            if (!ready.await(10L, TimeUnit.SECONDS)) {
                throw new AssertionError("Workers did not reach the start gate");
            }
            start.countDown();
            if (!done.await(30L, TimeUnit.SECONDS)) {
                throw new AssertionError(
                        "Concurrent loot initialization deadlocked");
            }
            if (failure.get() != null) {
                throw new AssertionError(
                        "Concurrent loot initialization failed", failure.get());
            }
            if (publishedStone.get() == null) {
                throw new AssertionError(
                        "No complete block binding was published");
            }
        }

        private static void initializedFastPathProbe() throws Exception {
            requireLegacyRegistries();
            LootTables.initialize();
            final CountDownLatch monitorHeld = new CountDownLatch(1);
            final CountDownLatch releaseMonitor = new CountDownLatch(1);
            final CountDownLatch readCompleted = new CountDownLatch(1);
            final AtomicReference<Throwable> failure =
                    new AtomicReference<Throwable>();

            Thread monitorOwner = new Thread(new Runnable() {
                public void run() {
                    synchronized (LootTables.class) {
                        monitorHeld.countDown();
                        try {
                            releaseMonitor.await();
                        } catch (Throwable problem) {
                            failure.compareAndSet(null, problem);
                        }
                    }
                }
            }, "loot-monitor-owner");
            monitorOwner.start();
            if (!monitorHeld.await(10L, TimeUnit.SECONDS)) {
                throw new AssertionError("Could not acquire LootTables monitor");
            }

            Thread reader = new Thread(new Runnable() {
                public void run() {
                    try {
                        if (LootTables.getBlockLootTable(Block.STONE) == null) {
                            throw new AssertionError(
                                    "Missing initialized stone table");
                        }
                    } catch (Throwable problem) {
                        failure.compareAndSet(null, problem);
                    } finally {
                        readCompleted.countDown();
                    }
                }
            }, "loot-fast-path-reader");
            reader.start();

            boolean completedWithoutMonitor =
                    readCompleted.await(5L, TimeUnit.SECONDS);
            releaseMonitor.countDown();
            monitorOwner.join(10000L);
            reader.join(10000L);
            if (!completedWithoutMonitor) {
                throw new AssertionError(
                        "Initialized block lookup reacquired LootTables monitor");
            }
            if (failure.get() != null) {
                throw new AssertionError(
                        "Fast-path block lookup failed", failure.get());
            }
        }

        private static void requireLegacyRegistries() {
            if (Block.STONE == null || Item.STICK == null) {
                throw new AssertionError(
                        "Legacy block/item arrays did not initialize");
            }
            BlockRegistryBootstrap.initialize();
            ItemRegistryBootstrap.initialize();
        }

        private static int findFreeBlockId() {
            for (int id = Block.byId.length - 1; id > 0; --id) {
                if (Block.byId[id] == null) return id;
            }
            return -1;
        }

        private static World lightweightWorld(Random random) throws Exception {
            World world = (World)unsafe().allocateInstance(World.class);
            world.random = random;
            world.isStatic = false;
            return world;
        }
    }

    private static final class ProbePluginBlock extends Block {
        int quantityCalls;
        int idCalls;
        int damageCalls;
        int spawnCalls;
        ItemStack spawned;

        ProbePluginBlock(int blockId) {
            super(blockId, 0, Material.STONE);
        }

        @Override
        public int a(Random random) {
            this.quantityCalls++;
            return 1;
        }

        @Override
        public int a(int metadata, Random random) {
            this.idCalls++;
            return Item.STICK.id;
        }

        @Override
        protected int a_(int metadata) {
            this.damageCalls++;
            return metadata + 4;
        }

        @Override
        protected void a(
                World world,
                int x,
                int y,
                int z,
                ItemStack stack) {
            this.spawnCalls++;
            this.spawned = stack;
        }

        void resetCalls() {
            this.quantityCalls = 0;
            this.idCalls = 0;
            this.damageCalls = 0;
            this.spawnCalls = 0;
            this.spawned = null;
        }
    }

    private static final class ZeroFloatRandom extends Random {
        private static final long serialVersionUID = 1L;

        @Override
        public float nextFloat() {
            return 0.0F;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }
}
