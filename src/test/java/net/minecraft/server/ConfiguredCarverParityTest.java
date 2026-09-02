package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.server.Alpha.AlphaChunkProvider;
import net.minecraft.server.registry.Carvers;
import net.minecraft.server.registry.ConfiguredCarverDataBootstrap;
import net.minecraft.server.registry.ConfiguredCarverDefinition;
import net.minecraft.server.registry.ConfiguredCarvers;
import net.minecraft.server.util.ResourceLocation;
import org.junit.Test;
import sun.misc.Unsafe;

public class ConfiguredCarverParityTest {
    private static final long[] SEEDS = {
            1L, 123456789L, -7046029254386353131L
    };
    private static final int[][] COORDINATES = {
            {0, 0}, {7, -11}, {-23, 19}
    };
    private static final String[][] CAVE_HASHES = {
        {
            "bcb98c14a41d49bc6613b0d42d78c7affca3ac9eb82a5abb63cf45ea9b4cdcaa",
            "a1583ad96921fb6f3e49b4e4899cf4987e69bcf40ffe16ca6ce39f9204479de0",
            "9beba74587e3e356e4cbb21bf6dc8ff4f7006cb95716431b577771a88d0998d3"
        },
        {
            "c5e6e7dd6ef60ebacc0b4ae50a0937003bcc60ad6073d6a74baf9090e048546d",
            "7bcae5acbf7d3c9c44a560d0c69165efdfd81e400db9c57ce919918105db5c75",
            "4ba4532ce145578f68c2d756ce88cdc3bf6c1747d82f84316e2a743046ea05d8"
        },
        {
            "65845e0bdd3b42aae85ad5150cf7857c83fcbe8aeb8c50676580272a184bb1a5",
            "a90e212f95dc326ed3972860767871dba898c0df57647318f2da18fd4728c5d8",
            "0fd92cda3184b9acff9b1be17ee50e52327145779ad11ed787464c23c6976a18"
        }
    };
    private static final String[][] NETHER_HASHES = {
        {
            "2fdb563eeda76051d8249b10f9b72e693db2db243a95ac0daf7e51b8f7683145",
            "1ac3640d4661aa7c5726b60e11b1069ed9f1ff4f155a4cd1c2de291174a8de83",
            "731fdf8e5f52fd817c1e8f1d332829fcd65aa4e361bbee1719fbf00b8308d608"
        },
        {
            "44ee1498bf873f9ca64adbf2021d2b5524369db4f36bc9f87af80b5f7a9eaf05",
            "da62277e5fe84c3efb19322e39a081e957b669d0f8f8c38d27a31980c0301758",
            "e89e02a6946966d1f1005c2102c2f14bd264521285d456ebecd3770998d3c464"
        },
        {
            "e89e02a6946966d1f1005c2102c2f14bd264521285d456ebecd3770998d3c464",
            "17bc44665ddf2a78965547befb02a646c123699c4e8e79f69275b8e21c85168f",
            "2e115c72f56cac4851d8b5ebcf3320356110aa3aa870edd4c9b4d4b94d98e919"
        }
    };
    private static final long[][] CAVE_CONTINUATIONS = {
        {7771423699448403385L, 5490963962145312864L, -828098824281798367L},
        {-14838256536502214L, -5377117257131748453L, -1167002138684108358L},
        {2643159003720564851L, 48321855183618523L, 9068606407615396063L}
    };
    private static final long[][] NETHER_CONTINUATIONS = {
        {7771423699448403385L, 5490963962145312864L, -828098824281798367L},
        {-14838256536502214L, -5377117257131748453L, -1167002138684108358L},
        {2643159003720564851L, -8081399968045392951L, 9068606407615396063L}
    };

    @Test
    public void configuredCarversKeepBetaBytesAndRandomContinuation()
            throws Exception {
        for (int seedIndex = 0; seedIndex < SEEDS.length; seedIndex++) {
            World world = worldWithSeed(SEEDS[seedIndex]);
            for (int coordinateIndex = 0;
                    coordinateIndex < COORDINATES.length; coordinateIndex++) {
                int chunkX = COORDINATES[coordinateIndex][0];
                int chunkZ = COORDINATES[coordinateIndex][1];
                Capture cave = capture(
                        ConfiguredCarvers.create(ConfiguredCarverDataBootstrap.CAVE),
                        world, chunkX, chunkZ, (byte)Block.STONE.id);
                assertEquals(CAVE_HASHES[seedIndex][coordinateIndex], cave.hash);
                assertEquals(CAVE_CONTINUATIONS[seedIndex][coordinateIndex],
                        cave.continuation);

                Capture nether = capture(
                        ConfiguredCarvers.create(
                                ConfiguredCarverDataBootstrap.NETHER_CAVE),
                        world, chunkX, chunkZ, (byte)Block.NETHERRACK.id);
                assertEquals(NETHER_HASHES[seedIndex][coordinateIndex], nether.hash);
                assertEquals(NETHER_CONTINUATIONS[seedIndex][coordinateIndex],
                        nether.continuation);
            }
        }
    }

    @Test
    public void skyDefinitionUsesTheExactOverworldCarver() throws Exception {
        World world = worldWithSeed(1L);
        Capture sky = capture(
                ConfiguredCarvers.create(ConfiguredCarverDataBootstrap.SKY_CAVE),
                world, 0, 0, (byte)Block.STONE.id);
        assertEquals(CAVE_HASHES[0][0], sky.hash);
        assertEquals(CAVE_CONTINUATIONS[0][0], sky.continuation);
    }

    @Test
    public void worldProvidersResolveTheirConfiguredCarverKeys()
            throws Exception {
        World world = worldWithSeed(1L);
        assertProviderCarver(
                new ChunkProviderGenerate(world, 1L), "u",
                ConfiguredCarverDataBootstrap.CAVE);
        assertProviderCarver(
                new ChunkProviderHell(world, 1L), "s",
                ConfiguredCarverDataBootstrap.NETHER_CAVE);
        assertProviderCarver(
                new ChunkProviderSky(world, 1L), "u",
                ConfiguredCarverDataBootstrap.SKY_CAVE);
        assertProviderCarver(
                new AlphaChunkProvider(world, 1L), "caveGenerator",
                ConfiguredCarverDataBootstrap.CAVE);
    }

    @Test
    public void unknownConfiguredCarverNeverFallsBackToOverworldCaves() {
        try {
            ConfiguredCarvers.create("example:missing");
            fail("Unknown configured carvers must not silently use cave");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("example:missing"));
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void legacyCarverFactoryRetainsItsForgivingFallback() {
        assertTrue(Carvers.create("example:missing") instanceof MapGenCaves);
        assertTrue(Carvers.create((String)null) instanceof MapGenCaves);
        assertTrue(Carvers.create(ConfiguredCarverDataBootstrap.NETHER_CAVE)
                instanceof MapGenCavesHell);
    }

    private static World worldWithSeed(long seed) throws Exception {
        World world = (World)unsafe().allocateInstance(World.class);
        world.worldData = new WorldData(seed, "ConfiguredCarverParity");
        return world;
    }

    private static Capture capture(
            MapGenBase carver, World world, int chunkX, int chunkZ, byte fill)
            throws Exception {
        byte[] blocks = new byte[16 * 16 * 128];
        Arrays.fill(blocks, fill);
        carver.a(null, world, chunkX, chunkZ, blocks);
        Field randomField = MapGenBase.class.getDeclaredField("b");
        randomField.setAccessible(true);
        long continuation = ((Random)randomField.get(carver)).nextLong();
        return new Capture(sha256(blocks), continuation);
    }

    private static void assertProviderCarver(
            Object provider, String fieldName, ResourceLocation expected)
            throws Exception {
        Field carverField = provider.getClass().getDeclaredField(fieldName);
        carverField.setAccessible(true);
        MapGenBase carver = (MapGenBase)carverField.get(provider);
        Field configurationField = carver.getClass()
                .getDeclaredField("configuration");
        configurationField.setAccessible(true);
        ConfiguredCarverDefinition configuration =
                (ConfiguredCarverDefinition)configurationField.get(carver);
        assertEquals(expected, configuration.getId());
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder(digest.length * 2);
        for (byte element : digest) {
            value.append(Character.forDigit(element >>> 4 & 15, 16));
            value.append(Character.forDigit(element & 15, 16));
        }
        return value.toString();
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }

    private static final class Capture {
        final String hash;
        final long continuation;

        Capture(String hash, long continuation) {
            this.hash = hash;
            this.continuation = continuation;
        }
    }
}
