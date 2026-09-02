package net.minecraft.server;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.server.registry.WorldPresetDataBootstrap;
import net.minecraft.server.registry.WorldPresetDefinition;
import net.minecraft.server.registry.WorldPresetGeneratorRouting;
import net.minecraft.server.util.ResourceLocation;
import org.bukkit.craftbukkit.generator.CustomChunkGenerator;
import org.bukkit.craftbukkit.generator.NetherChunkGenerator;
import org.bukkit.craftbukkit.generator.SkyLandsChunkGenerator;
import org.bukkit.generator.ChunkGenerator;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

public class WorldPresetServerRoutingTest {
    @BeforeClass
    public static void initializePresetData() {
        WorldPresetDataBootstrap.initialize();
    }

    @Test
    public void worldServerUsesExactLegacyConstructorsAndWrappers() throws Exception {
        assertProvider(0, new WorldProviderNormal(), ChunkProviderGenerate.class);
        assertProvider(1, new WorldProviderNormal(),
                net.minecraft.server.Alpha.AlphaChunkProvider.class);
        assertProvider(5, new WorldProviderNormal(),
                net.minecraft.server.Alpha.AlphaChunkProvider.class);
        assertProvider(2, new WorldProviderNormal(), ChunkProviderFlat.class);
        assertProvider(3, new WorldProviderNormal(), ChunkProviderSky.class);
        assertProvider(6, new WorldProviderNormal(),
                net.minecraft.server.Classic.ChunkProviderClassic.class);
        assertProvider(7, new WorldProviderNormal(),
                net.minecraft.server.Infdev.InfdevChunkProvider.class);
        assertProvider(999, new WorldProviderNormal(), ChunkProviderGenerate.class);

        assertProvider(0, new WorldProviderHell(), NetherChunkGenerator.class);
        assertProvider(3, new WorldProviderHell(), ChunkProviderNetherSky.class);
        assertProvider(6, new WorldProviderHell(),
                net.minecraft.server.Classic.ChunkProviderHellClassic.class);
        assertProvider(999, new WorldProviderHell(), NetherChunkGenerator.class);

        assertProvider(0, new WorldProviderSky(), SkyLandsChunkGenerator.class);
        assertProvider(3, new WorldProviderSky(), SkyLandsChunkGenerator.class);
    }

    @Test
    public void providerFactoriesUseTheSameSymbolicGraphAndLegacyClasses()
            throws Exception {
        assertDirectProvider(0, new WorldProviderNormal(), ChunkProviderGenerate.class);
        assertDirectProvider(1, new WorldProviderNormal(),
                net.minecraft.server.Alpha.AlphaChunkProvider.class);
        assertDirectProvider(5, new WorldProviderNormal(),
                net.minecraft.server.Alpha.AlphaChunkProvider.class);
        assertDirectProvider(2, new WorldProviderNormal(), ChunkProviderFlat.class);
        assertDirectProvider(3, new WorldProviderNormal(), ChunkProviderSky.class);
        assertDirectProvider(6, new WorldProviderNormal(),
                net.minecraft.server.Classic.ChunkProviderClassic.class);
        assertDirectProvider(7, new WorldProviderNormal(),
                net.minecraft.server.Infdev.InfdevChunkProvider.class);
        assertDirectProvider(999, new WorldProviderNormal(), ChunkProviderGenerate.class);

        assertDirectProvider(0, new WorldProviderHell(), ChunkProviderHell.class);
        assertDirectProvider(3, new WorldProviderHell(), ChunkProviderNetherSky.class);
        assertDirectProvider(6, new WorldProviderHell(),
                net.minecraft.server.Classic.ChunkProviderHellClassic.class);
        assertDirectProvider(999, new WorldProviderHell(), ChunkProviderHell.class);

        assertDirectProvider(0, new WorldProviderSky(), ChunkProviderSky.class);
        assertDirectProvider(3, new WorldProviderSky(), ChunkProviderSky.class);
    }

    @Test
    public void bukkitGeneratorPrecedesAllPresetLookup() throws Exception {
        Field rawField = WorldPresetDataBootstrap.class.getDeclaredField("rawPresets");
        rawField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<ResourceLocation, WorldPresetDefinition> original =
                (Map<ResourceLocation, WorldPresetDefinition>)rawField.get(null);
        synchronized (WorldPresetDataBootstrap.class) {
            try {
                rawField.set(null,
                        Collections.<ResourceLocation, WorldPresetDefinition>emptyMap());
                try {
                    WorldPresetGeneratorRouting.generatorKey(
                            0, WorldPresetGeneratorRouting.OVERWORLD);
                    fail("Poisoned preset view did not reject built-in routing");
                } catch (IllegalStateException expected) {
                    assertTrue(expected.getMessage().contains("Missing bound world preset"));
                }

                WorldServer world = allocateWorld(0, new WorldProviderNormal());
                world.generator = new EmptyBukkitGenerator();
                IChunkProvider result = world.b();
                assertTrue(result instanceof ChunkProviderServer);
                assertTrue(((ChunkProviderServer)result).chunkProvider
                        instanceof CustomChunkGenerator);
            } finally {
                rawField.set(null, original);
            }
        }
    }

    private static void assertProvider(
            int terrainType, WorldProvider provider, Class<?> expected) throws Exception {
        WorldServer world = allocateWorld(terrainType, provider);
        IChunkProvider result = world.b();
        assertTrue(result instanceof ChunkProviderServer);
        assertSame(expected, ((ChunkProviderServer)result).chunkProvider.getClass());
    }

    private static void assertDirectProvider(
            int terrainType, WorldProvider provider, Class<?> expected) throws Exception {
        allocateWorld(terrainType, provider);
        assertSame(expected, provider.getChunkProvider().getClass());
    }

    private static WorldServer allocateWorld(
            int terrainType, WorldProvider provider) throws Exception {
        WorldServer world = (WorldServer)unsafe().allocateInstance(WorldServer.class);
        world.worldData = new WorldData(12345L, "routing-test");
        world.worldData.setTerrainType(terrainType);
        world.worldProvider = provider;
        provider.a = world;
        putObjectField(world, World.class, "w", new EmptyDataManager());
        return world;
    }

    private static void putObjectField(
            Object target, Class<?> owner, String fieldName, Object value) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        unsafe().putObject(target, unsafe().objectFieldOffset(field), value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }

    private static final class EmptyBukkitGenerator extends ChunkGenerator {
        public byte[] generate(
                org.bukkit.World world, Random random, int chunkX, int chunkZ) {
            return new byte[32768];
        }
    }

    private static final class EmptyDataManager implements IDataManager {
        public WorldData c() { return null; }
        public void b() {}
        public IChunkLoader a(WorldProvider provider) { return null; }
        public void a(WorldData data, List players) {}
        public void a(WorldData data) {}
        public PlayerFileData d() { return null; }
        public void e() {}
        public File b(String name) { return null; }
        public UUID getUUID() { return new UUID(0L, 0L); }
    }
}
