package net.minecraft.server.registry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.server.BiomeBase;
import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;

public class StructureTypeDataLoaderTest {
    @BeforeClass
    public static void loadRegistries() {
        assertTrue(Block.STONE != null);
        assertTrue(Item.SIGN != null);
        ItemRegistryBootstrap.initialize();
        StructureTypes.initialize();
    }

    @Test
    public void dataPreservesBothDescriptorsAndOrderedLegacySpawnerEntries() throws Exception {
        assertEquals(Arrays.asList(StructureTypes.DUNGEON, StructureTypes.HEROBRINE_SHRINE),
                Arrays.asList(StructureTypes.keys().toArray(
                        new ResourceLocation[StructureTypes.size()])));
        assertFalse("structure descriptor registry must remain extensible",
                Registries.STRUCTURE_TYPE.isFrozen());

        StructureType dungeon = StructureTypes.get(StructureTypes.DUNGEON);
        assertEquals("Dungeon", dungeon.getDisplayName());
        assertEquals(new ResourceLocation("minecraft", "chests/dungeon"),
                dungeon.getLootTable());
        assertSame(dungeon.getLootTableInstance(),
                LootTables.get(dungeon.getLootTable()));
        assertArrayEquals(new String[] {"Skeleton", "Zombie", "Zombie", "Spider"},
                (String[])field(dungeon, "spawnerMobs"));
        assertArrayEquals(new int[] {1, 2, 0, 1},
                (int[])field(dungeon, "spawnerWeights"));
        assertArrayEquals(new String[] {"Skeleton", "Zombie", "Zombie", "Spider"},
                dungeon.getSpawnerMobIds());
        assertArrayEquals(new int[] {1, 2, 0, 1},
                dungeon.getSpawnerMobWeights());
        assertTrue(dungeon.getBiomes().isEmpty());

        StructureType shrine = StructureTypes.get(StructureTypes.HEROBRINE_SHRINE);
        assertEquals("Herobrine Shrine", shrine.getDisplayName());
        assertEquals(null, shrine.getLootTable());
        assertEquals(null, field(shrine, "spawnerMobs"));
        assertEquals(null, field(shrine, "spawnerWeights"));
        assertEquals(Arrays.asList(new ResourceLocation("minecraft", "desert")),
                Arrays.asList(shrine.getBiomes().toArray(
                        new ResourceLocation[shrine.getBiomes().size()])));
        assertTrue(shrine.isValidBiome(BiomeBase.DESERT));
        assertFalse(shrine.isValidBiome(BiomeBase.PLAINS));
    }

    @Test
    public void decodedDungeonConsumesTheSameSingleNextIntFourForEverySeed() {
        StructureType legacy = StructureType.builder(StructureTypes.DUNGEON)
                .displayName("Dungeon")
                .lootTable(new ResourceLocation("minecraft", "chests/dungeon"))
                .spawnerMobs(
                        new String[] {"Skeleton", "Zombie", "Zombie", "Spider"},
                        new int[] {1, 2, 0, 1})
                .build();
        StructureType decoded = StructureTypes.get(StructureTypes.DUNGEON);
        for (long seed = 0L; seed < 4096L; seed++) {
            TracingRandom legacyRandom = new TracingRandom(seed);
            TracingRandom decodedRandom = new TracingRandom(seed);
            assertEquals("mob for seed " + seed,
                    legacy.pickSpawnerMob(legacyRandom), decoded.pickSpawnerMob(decodedRandom));
            assertEquals("random calls for seed " + seed,
                    legacyRandom.trace(), decodedRandom.trace());
            assertEquals("post-pick random state for seed " + seed,
                    legacyRandom.nextLong(), decodedRandom.nextLong());
        }

        TracingRandom shrineRandom = new TracingRandom(1L);
        assertEquals("Pig", StructureTypes.get(StructureTypes.HEROBRINE_SHRINE)
                .pickSpawnerMob(shrineRandom));
        assertEquals("", shrineRandom.trace());
    }

    @Test
    public void codecRejectsMalformedOrBehaviorChangingDescriptors() {
        ResourceLocation dungeon = StructureTypes.DUNGEON;
        assertDecodeFails(dungeon, "{}");
        assertDecodeFails(dungeon, "{\"display_name\":\"\",\"spawner_mobs\":[]}");
        assertDecodeFails(dungeon, "{\"display_name\":\"Dungeon\",\"spawner_mobs\":["
                + "{\"legacy_entity_id\":\"Skeleton\",\"weight\":-1}]}");
        assertDecodeFails(dungeon, "{\"display_name\":\"Dungeon\",\"spawner_mobs\":["
                + "{\"legacy_entity_id\":\"Skeleton\",\"weight\":1.5}]}");
        assertDecodeFails(dungeon, "{\"display_name\":\"Dungeon\",\"spawner_mobs\":["
                + "{\"legacy_entity_id\":\"Skeleton\",\"weight\":0}]}");
        assertDecodeFails(dungeon, "{\"display_name\":\"Dungeon\",\"unsupported\":true}");
    }

    private static Object field(StructureType type, String name) throws Exception {
        Field field = StructureType.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(type);
    }

    private static void assertDecodeFails(ResourceLocation key, String json) {
        try {
            StructureTypeCodec.decode(key, JsonParser.parseString(json).getAsJsonObject());
            fail("Expected invalid structure data to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    private static final class TracingRandom extends Random {
        private final StringBuilder trace = new StringBuilder();

        TracingRandom(long seed) {
            super(seed);
        }

        @Override
        public int nextInt(int bound) {
            int value = super.nextInt(bound);
            trace.append(bound).append('=').append(value).append(';');
            return value;
        }

        String trace() {
            return trace.toString();
        }
    }
}
