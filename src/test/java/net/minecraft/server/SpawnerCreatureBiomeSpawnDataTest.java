package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.server.registry.BiomeSpawnSettingsBootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public class SpawnerCreatureBiomeSpawnDataTest {
    @BeforeClass
    public static void initializeBiomeSpawnData() {
        assertTrue(Block.STONE != null);
        BiomeSpawnSettingsBootstrap.initialize();
    }

    @Test
    public void weightedSelectionPreservesBoundariesAndConsumesOneRandomCall() {
        BiomeMeta first = new BiomeMeta(EntitySpider.class, 10, 4);
        BiomeMeta second = new BiomeMeta(EntityGhast.class, 5, 1);
        List entries = Arrays.asList(first, second);

        assertSelection(entries, 0, 15, first);
        assertSelection(entries, 9, 15, first);
        assertSelection(entries, 10, 15, second);
        assertSelection(entries, 14, 15, second);
    }

    @Test
    public void dataCountIsTheGroupStopCapWithoutAnyCountRandomness() {
        EntityGhast ghast = new EntityGhast(null);
        assertEquals(7, SpawnerCreature.successfulSpawnCap(
                new BiomeMeta(EntityGhast.class, 10, 7), ghast));
        assertEquals(1, SpawnerCreature.successfulSpawnCap(
                new BiomeMeta(EntityGhast.class, 10), ghast));
    }

    @Test
    public void flatManagerKeepsTheDataBoundPlainsSpawnLists() {
        WorldChunkManagerFlat flat = new WorldChunkManagerFlat();
        BiomeBase biome = flat.getBiome(100, -200);
        assertSame(BiomeBase.PLAINS, biome);
        assertSame(BiomeBase.PLAINS.a(EnumCreatureType.MONSTER),
                biome.a(EnumCreatureType.MONSTER));
        assertSame(BiomeBase.PLAINS.a(EnumCreatureType.CREATURE),
                biome.a(EnumCreatureType.CREATURE));
        assertSame(BiomeBase.PLAINS.a(EnumCreatureType.WATER_CREATURE),
                biome.a(EnumCreatureType.WATER_CREATURE));
        assertFalseEmpty(biome.a(EnumCreatureType.MONSTER));
        assertFalseEmpty(biome.a(EnumCreatureType.CREATURE));
    }

    @Test
    public void hellAndSkyRetainTheirSpecialOrderedListsAndCaps() {
        List hellMonsters = BiomeBase.HELL.a(EnumCreatureType.MONSTER);
        assertEquals(2, hellMonsters.size());
        assertMeta((BiomeMeta)hellMonsters.get(0), EntityGhast.class, 10, 1);
        assertMeta((BiomeMeta)hellMonsters.get(1), EntityPigZombie.class, 10, 4);
        assertTrue(BiomeBase.HELL.a(EnumCreatureType.CREATURE).isEmpty());
        assertTrue(BiomeBase.HELL.a(EnumCreatureType.WATER_CREATURE).isEmpty());

        assertTrue(BiomeBase.SKY.a(EnumCreatureType.MONSTER).isEmpty());
        List skyCreatures = BiomeBase.SKY.a(EnumCreatureType.CREATURE);
        assertEquals(1, skyCreatures.size());
        assertMeta((BiomeMeta)skyCreatures.get(0), EntityChicken.class, 10, 4);
        assertTrue(BiomeBase.SKY.a(EnumCreatureType.WATER_CREATURE).isEmpty());
    }

    @Test
    public void absentLateCustomBiomeFallsBackToConstructorLists() {
        LateCustomBiome custom = new LateCustomBiome();
        assertSame(custom.legacyMonsters(), custom.a(EnumCreatureType.MONSTER));
        assertEquals(1, custom.a(EnumCreatureType.MONSTER).size());
        BiomeMeta entry = (BiomeMeta)custom.a(EnumCreatureType.MONSTER).get(0);
        assertMeta(entry, EntityGhast.class, 7, 0);
        assertEquals(1, SpawnerCreature.successfulSpawnCap(entry, new EntityGhast(null)));
    }

    private static void assertSelection(
            List entries, int choice, int expectedBound, BiomeMeta expected) {
        BoundaryRandom random = new BoundaryRandom(choice);
        assertSame(expected, SpawnerCreature.selectSpawnEntry(entries, random));
        assertEquals(1, random.calls);
        assertEquals(expectedBound, random.bound);
    }

    private static void assertMeta(
            BiomeMeta meta, Class<?> type, int weight, int count) {
        assertSame(type, meta.a);
        assertEquals(weight, meta.b);
        assertEquals(count, meta.c);
    }

    private static void assertFalseEmpty(List list) {
        assertTrue(list != null && !list.isEmpty());
    }

    private static final class BoundaryRandom extends Random {
        private final int choice;
        private int calls;
        private int bound;

        private BoundaryRandom(int choice) {
            this.choice = choice;
        }

        public int nextInt(int bound) {
            this.calls++;
            this.bound = bound;
            if (this.choice < 0 || this.choice >= bound) {
                throw new AssertionError("Choice outside bound");
            }
            return this.choice;
        }
    }

    private static final class LateCustomBiome extends BiomeBase {
        private final List monsters;

        private LateCustomBiome() {
            this.s.clear();
            this.t.clear();
            this.u.clear();
            this.s.add(new BiomeMeta(EntityGhast.class, 7));
            this.monsters = this.s;
        }

        private List legacyMonsters() {
            return this.monsters;
        }
    }
}
