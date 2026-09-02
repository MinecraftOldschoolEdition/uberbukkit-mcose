package net.minecraft.server.registry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Random;
import net.minecraft.server.BiomeBase;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class StructureSetDataParityTest {
    private static final ResourceLocation SHRINE =
            new ResourceLocation("minecraft", "herobrine_shrine");

    @BeforeClass
    public static void initializeData() {
        assertTrue(Block.STONE != null);
        assertTrue(Item.SIGN != null);
        BiomeRegistryBootstrap.initialize();
        StructureSetDataBootstrap.initialize();
    }

    @After
    public void restoreBuiltInGeneration() {
        StructureSetDataBootstrap.reload(
                RegistryDataLoader.createLayeredProvider(null));
    }

    @Test
    public void builtInDataOwnsTheExactLegacyPlacementPolicy() {
        Map<ResourceLocation, StructureSetDefinition> definitions =
                StructureSetDataBootstrap.loadForTests(
                        RegistryDataLoader.createLayeredProvider(null));
        assertEquals(1, definitions.size());
        StructureSetDefinition shrine = definitions.get(SHRINE);
        assertEquals(SHRINE, shrine.getId());
        assertEquals(1, shrine.getStructures().size());
        assertEquals(SHRINE, shrine.getStructures().get(0).getStructure());
        assertEquals(1, shrine.getStructures().get(0).getWeight());

        LegacyRandomChanceStructurePlacement placement = shrine.getPlacement();
        assertEquals(750000, placement.getChance());
        assertEquals(777777777L, placement.getSalt());
        assertEquals(341873128712L, placement.getChunkXMultiplier());
        assertEquals(132897987541L, placement.getChunkZMultiplier());
        assertEquals(8, placement.getCandidateOffset());
        assertEquals(16, placement.getCandidateBound());
        assertEquals(64, placement.getGenerationY());
        assertEquals(72, placement.getLocateY());
    }

    @Test
    public void dataBackedSamplerMatchesTheRemovedFormulaAcrossSeedChunkMatrix() {
        LegacyRandomChanceStructurePlacement placement =
                StructureSetDataBootstrap.herobrineShrine().getPlacement();
        long[] worldSeeds = {
                0L, 1L, -1L, 987654321L, Long.MIN_VALUE, Long.MAX_VALUE
        };
        int[][] chunks = {
                {0, 0}, {1, -1}, {-1, 1}, {137, -991},
                {-994073, 0}, {-404132, 0}, {-826456, 0},
                {-945591, 0}, {-132377, 0}, {Integer.MAX_VALUE, -17},
                {Integer.MIN_VALUE, Integer.MAX_VALUE}
        };

        int successes = 0;
        for (int seedIndex = 0; seedIndex < worldSeeds.length; seedIndex++) {
            for (int chunkIndex = 0; chunkIndex < chunks.length; chunkIndex++) {
                long worldSeed = worldSeeds[seedIndex];
                int chunkX = chunks[chunkIndex][0];
                int chunkZ = chunks[chunkIndex][1];
                OracleCandidate expected = legacyOracle(
                        worldSeed, chunkX, chunkZ);
                LegacyRandomChanceStructurePlacement.Candidate actual =
                        placement.sample(worldSeed, chunkX, chunkZ);
                if (expected == null) {
                    assertNull(actual);
                } else {
                    successes++;
                    assertEquals(expected.blockX, actual.getBlockX());
                    assertEquals(expected.blockZ, actual.getBlockZ());
                    assertEquals(64, actual.getGenerationY());
                    assertEquals(72, actual.getLocateY());
                    assertArrayEquals(
                            new int[] {expected.blockX, 72, expected.blockZ},
                            actual.toLocatePosition());
                    assertEquals(expected.random.nextLong(),
                            actual.getRandom().nextLong());
                }
            }
        }
        assertTrue("matrix must exercise successful placement", successes > 0);
    }

    @Test
    public void placementAttemptNeverAdvancesPopulationRandom() {
        LegacyRandomChanceStructurePlacement placement =
                StructureSetDataBootstrap.herobrineShrine().getPlacement();
        Random actualPopulation = new Random(123456789L);
        Random expectedPopulation = new Random(123456789L);
        assertEquals(expectedPopulation.nextLong(), actualPopulation.nextLong());
        placement.sample(0L, -994073, 0);
        assertEquals(expectedPopulation.nextLong(), actualPopulation.nextLong());
        placement.sample(0L, 0, 0);
        assertEquals(expectedPopulation.nextLong(), actualPopulation.nextLong());
    }

    @Test
    public void structureBiomePolicyAndInfdevBypassRemainExact() {
        assertEquals(1, StructureTypes.get(SHRINE).getBiomes().size());
        assertTrue(StructureTypes.get(SHRINE).getBiomes().contains(
                new ResourceLocation("minecraft", "desert")));
        assertTrue(StructureSetDataBootstrap.isHerobrineShrineAllowed(
                0, BiomeBase.DESERT));
        assertFalse(StructureSetDataBootstrap.isHerobrineShrineAllowed(
                0, BiomeBase.PLAINS));
        assertTrue(StructureSetDataBootstrap.isHerobrineShrineAllowed(
                7, BiomeBase.PLAINS));
        assertTrue(StructureSetDataBootstrap.isHerobrineShrineAllowed(
                7, null));
    }

    @Test
    public void malformedReloadRollsBackThePublishedGeneration()
            throws Exception {
        Map<ResourceLocation, StructureSetDefinition> before =
                StructureSetRegistryApi.snapshot();
        long generation = StructureSetRegistryApi.generation();
        Path root = createOverride(
                builtInJson().replace("\"chance\": 750000", "\"chance\": 0"));
        try {
            StructureSetDataBootstrap.reload(
                    RegistryDataLoader.createLayeredProvider(root.toFile()));
            fail("Expected malformed structure-set reload to fail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("worldgen/structure_set"));
        } finally {
            deleteTree(root);
        }

        assertSame(before, StructureSetRegistryApi.snapshot());
        assertEquals(generation, StructureSetRegistryApi.generation());
        assertEquals(750000,
                StructureSetDataBootstrap.herobrineShrine()
                        .getPlacement().getChance());
    }

    @Test
    public void validReloadAtomicallyReplacesPlacementParameters()
            throws Exception {
        Map<ResourceLocation, StructureSetDefinition> before =
                StructureSetRegistryApi.snapshot();
        long generation = StructureSetRegistryApi.generation();
        Path root = createOverride(
                builtInJson()
                        .replace("\"chance\": 750000", "\"chance\": 1")
                        .replace("\"generation_y\": 64", "\"generation_y\": 63")
                        .replace("\"locate_y\": 72", "\"locate_y\": 73"));
        try {
            StructureSetDataBootstrap.reload(
                    RegistryDataLoader.createLayeredProvider(root.toFile()));
            assertFalse(before == StructureSetRegistryApi.snapshot());
            assertEquals(generation + 1L, StructureSetRegistryApi.generation());
            LegacyRandomChanceStructurePlacement placement =
                    StructureSetDataBootstrap.herobrineShrine().getPlacement();
            assertEquals(1, placement.getChance());
            LegacyRandomChanceStructurePlacement.Candidate candidate =
                    placement.sample(17L, 2, -3);
            assertEquals(63, candidate.getGenerationY());
            assertEquals(73, candidate.getLocateY());
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void codecRejectsUnsupportedAndNonIntegralPlacementData() {
        assertCodecFails(builtInJson().replace(
                "\"locate_y\": 72", "\"locate_y\": 72.5"));
        assertCodecFails(builtInJson().replace(
                "\"locate_y\": 72", "\"locate_y\": 72,\n"
                        + "    \"unknown\": true"));
        assertCodecFails(builtInJson().replace(
                "\"weight\": 1", "\"weight\": 0"));
    }

    @Test
    public void generationAndLocateCallSitesUseTheSharedPlacementModule()
            throws Exception {
        Path sourceRoot = projectRoot().resolve(
                "src/main/java/net/minecraft/server");
        String overworld = source(sourceRoot.resolve("ChunkProviderGenerate.java"));
        assertTrue(overworld.contains(
                "StructureSetDataBootstrap.isHerobrineShrineAllowed"));
        assertTrue(overworld.contains(
                "StructureSetDataBootstrap.herobrineShrine()"));
        assertFalse(overworld.contains("nextInt(750000)"));

        String infdev = source(sourceRoot.resolve(
                "Infdev/InfdevChunkProvider.java"));
        assertTrue(infdev.contains(
                "StructureSetDataBootstrap.isHerobrineShrineAllowed(7, null)"));
        assertTrue(infdev.contains(
                "StructureSetDataBootstrap.herobrineShrine()"));
        assertFalse(infdev.contains("nextInt(750000)"));

        String locate = source(sourceRoot.resolve(
                "WorldGenHerobrineShrine.java"));
        assertTrue(locate.contains(
                "StructureSetDataBootstrap.isHerobrineShrineAllowed"));
        assertTrue(locate.contains(
                "StructureSetDataBootstrap.herobrineShrine()"));
        assertTrue(locate.contains("shrine.toLocatePosition()"));
        assertFalse(locate.contains("nextInt(750000)"));
    }

    private static void assertCodecFails(String json) {
        try {
            StructureSetCodec.decode(
                    SHRINE, JsonParser.parseString(json).getAsJsonObject());
            fail("Malformed structure set was accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    private static OracleCandidate legacyOracle(
            long worldSeed, int chunkX, int chunkZ) {
        long shrineSeed = (long)chunkX * 341873128712L
                + (long)chunkZ * 132897987541L
                + worldSeed + 777777777L;
        Random random = new Random(shrineSeed);
        if (random.nextInt(750000) != 0) return null;
        int blockX = chunkX * 16 + random.nextInt(16) + 8;
        int blockZ = chunkZ * 16 + random.nextInt(16) + 8;
        return new OracleCandidate(blockX, blockZ, random);
    }

    private static String builtInJson() {
        return "{\n"
                + "  \"structures\": [\n"
                + "    {\n"
                + "      \"structure\": \"minecraft:herobrine_shrine\",\n"
                + "      \"weight\": 1\n"
                + "    }\n"
                + "  ],\n"
                + "  \"placement\": {\n"
                + "    \"type\": \"minecraft:legacy_random_chance\",\n"
                + "    \"chance\": 750000,\n"
                + "    \"salt\": 777777777,\n"
                + "    \"chunk_x_multiplier\": 341873128712,\n"
                + "    \"chunk_z_multiplier\": 132897987541,\n"
                + "    \"candidate_offset\": {\n"
                + "      \"min\": 8,\n"
                + "      \"bound\": 16\n"
                + "    },\n"
                + "    \"generation_y\": 64,\n"
                + "    \"locate_y\": 72\n"
                + "  }\n"
                + "}\n";
    }

    private static String source(Path file) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static Path createOverride(String json) throws Exception {
        Path root = Files.createTempDirectory("mcose-structure-set-");
        Path target = root.resolve(
                "data/minecraft/worldgen/structure_set/herobrine_shrine.json");
        Files.createDirectories(target.getParent());
        Files.write(target, json.getBytes(StandardCharsets.UTF_8));
        return root;
    }

    private static Path projectRoot() {
        Path root = findProjectRoot(java.nio.file.Paths.get("")
                .toAbsolutePath().normalize());
        if (root != null) return root;
        String launchDirectory = System.getenv("PWD");
        if (launchDirectory != null && launchDirectory.length() > 0) {
            root = findProjectRoot(java.nio.file.Paths.get(launchDirectory)
                    .toAbsolutePath().normalize());
            if (root != null) return root;
        }
        throw new IllegalStateException("Could not locate server source root");
    }

    private static Path findProjectRoot(Path start) {
        Path cursor = start;
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("src/main/java"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    private static void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
    }

    private static final class OracleCandidate {
        final int blockX;
        final int blockZ;
        final Random random;

        OracleCandidate(int blockX, int blockZ, Random random) {
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.random = random;
        }
    }
}
