package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.server.registry.EntityLootTable;
import net.minecraft.server.registry.EntityTypeRegistryBootstrap;
import net.minecraft.server.registry.ItemRegistryBootstrap;
import net.minecraft.server.registry.LootTables;
import net.minecraft.server.util.ResourceLocation;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.BeforeClass;
import org.junit.Test;
import uk.betacraft.uberbukkit.UberbukkitConfig;

public class EntityDeathLootTableParityTest {
    @BeforeClass
    public static void loadRegistries() {
        StatisticList.a();
        ItemRegistryBootstrap.initialize();
        EntityTypeRegistryBootstrap.initialize();
        LootTables.initialize();
    }

    @Test
    public void specializedMobsNowUseTheSharedBaseDeathDispatch() throws Exception {
        assertBaseDeclaresQ(EntitySkeleton.class);
        assertBaseDeclaresQ(EntitySheep.class);
        assertBaseDeclaresQ(EntitySquid.class);
    }

    @Test
    public void skeletonEventListKeepsArrowThenBoneCombinedStacksAndTwoCountCalls() {
        EntitySkeleton skeleton = new EntitySkeleton(null);
        for (long seed = 0L; seed < 4096L; seed++) {
            TracingRandom expectedRandom = new TracingRandom(seed);
            int arrows = expectedRandom.nextInt(3);
            int bones = expectedRandom.nextInt(3);

            TracingRandom actualRandom = new TracingRandom(seed);
            skeleton.random = actualRandom;
            List<org.bukkit.inventory.ItemStack> actual = skeleton.createDeathEventDrops();
            int expectedEntries = (arrows > 0 ? 1 : 0) + (bones > 0 ? 1 : 0);
            assertEquals("event entry count at seed " + seed, expectedEntries, actual.size());
            int index = 0;
            if (arrows > 0) {
                assertBukkitStack(actual.get(index++), Item.ARROW.id, arrows, 0);
            }
            if (bones > 0) {
                assertBukkitStack(actual.get(index), Item.BONE.id, bones, 0);
            }
            assertRandomParity(seed, expectedRandom, actualRandom);
        }
    }

    @Test
    public void clientStyleEvaluatorStillExpandsSkeletonCountsIntoSingletons() {
        EntitySkeleton skeleton = new EntitySkeleton(null);
        EntityLootTable table = table(LootTables.SKELETON);
        TracingRandom random = new TracingRandom(3L);
        List<ItemStack> drops = table.generateEntityLoot(skeleton, random);
        assertEquals(4, drops.size());
        for (int i = 0; i < drops.size(); i++) {
            assertEquals(1, drops.get(i).count);
            assertEquals(i < 2 ? Item.ARROW.id : Item.BONE.id, drops.get(i).id);
        }
        assertEquals("3=2;3=2;", random.getTrace());
    }

    @Test
    public void sheepUsesCurrentColorMetadataWithoutAnyRandomCall() {
        EntitySheep sheep = new EntitySheep(null);
        for (int color = 0; color < 16; color++) {
            sheep.setColor(color);
            sheep.setSheared(false);
            TracingRandom random = new TracingRandom(1000L + color);
            sheep.random = random;
            List<org.bukkit.inventory.ItemStack> drops = sheep.createDeathEventDrops();
            assertEquals(1, drops.size());
            assertBukkitStack(drops.get(0), Block.WOOL.id, 1, color);
            assertEquals("", random.getTrace());
        }

        sheep.setSheared(true);
        TracingRandom random = new TracingRandom(7L);
        sheep.random = random;
        assertTrue(sheep.createDeathEventDrops().isEmpty());
        assertEquals("", random.getTrace());
    }

    @Test
    public void pigConfigOverlaysTheOnFireAlternativeWithoutChangingCountRng() {
        UberbukkitConfig config = UberbukkitConfig.getInstance();
        Object oldValue = config.getProperty("mechanics.burning_pig_drop_cooked_meat");
        try {
            EntityPig pig = new EntityPig(null);
            pig.fireTicks = 20;

            config.setProperty("mechanics.burning_pig_drop_cooked_meat", Boolean.TRUE);
            assertEventUniformCount(pig, 47L, Item.GRILLED_PORK.id, 0, 2);

            config.setProperty("mechanics.burning_pig_drop_cooked_meat", Boolean.FALSE);
            assertEventUniformCount(pig, 47L, Item.PORK.id, 0, 2);
        } finally {
            config.setProperty("mechanics.burning_pig_drop_cooked_meat", oldValue);
        }
    }

    @Test
    public void largeSlimePreservesServerOnlyNextIntConsumptionWithNoDrop() {
        EntitySlime slime = new EntitySlime(null);
        slime.setSize(2);

        TracingRandom direct = new TracingRandom(91L);
        assertTrue(table(LootTables.SLIME).generateEntityLoot(slime, direct).isEmpty());
        assertEquals("", direct.getTrace());

        TracingRandom expected = new TracingRandom(91L);
        expected.nextInt(3);
        TracingRandom actual = new TracingRandom(91L);
        slime.random = actual;
        assertTrue(slime.createDeathEventDrops().isEmpty());
        assertRandomParity(91L, expected, actual);
    }

    @Test
    public void smallSlimeAndSquidKeepTheirExactCombinedCountRanges() {
        EntitySlime slime = new EntitySlime(null);
        slime.setSize(1);
        EntitySquid squid = new EntitySquid(null);
        for (long seed = 0L; seed < 1024L; seed++) {
            assertEventUniformCount(slime, seed, Item.SLIME_BALL.id, 0, 2);
            assertEventUniformCount(squid, seed, Item.INK_SACK.id, 1, 3);
        }
    }

    @Test
    public void everySingleItemEntityTableKeepsZeroThroughTwoCombinedCountParity() {
        assertGeneric(new EntityZombie(null), LootTables.ZOMBIE, Item.FEATHER.id);
        assertGeneric(new EntityCreeper(null), LootTables.CREEPER, Item.SULPHUR.id);
        assertGeneric(new EntitySpider(null), LootTables.SPIDER, Item.STRING.id);
        assertGeneric(new EntityCow(null), LootTables.COW, Item.LEATHER.id);
        assertGeneric(new EntityChicken(null), LootTables.CHICKEN, Item.FEATHER.id);
        assertGeneric(new EntityGhast(null), LootTables.GHAST, Item.SULPHUR.id);
        assertGeneric(new EntityPigZombie(null),
                LootTables.ZOMBIFIED_PIGLIN, Item.GRILLED_PORK.id);
        assertGeneric(new EntitySnowman(null), LootTables.SNOW_GOLEM, Item.SNOW_BALL.id);
    }

    @Test
    public void noTableFallbackConsumesCountRollEvenWhenDropHookReturnsZero() {
        FallbackMob mob = new FallbackMob(0);
        assertNull(LootTables.entityLootKey(mob));

        TracingRandom expected = new TracingRandom(812L);
        expected.nextInt(3);
        TracingRandom actual = new TracingRandom(812L);
        mob.random = actual;
        assertTrue(mob.createDeathEventDrops().isEmpty());
        assertRandomParity(812L, expected, actual);
    }

    @Test
    public void canonicalKeysRouteSnowGolemAndZombifiedPiglin() {
        EntitySnowman snowman = new EntitySnowman(null);
        EntityPigZombie pigZombie = new EntityPigZombie(null);
        assertEquals(LootTables.SNOW_GOLEM, LootTables.entityLootKey(snowman));
        assertEquals(LootTables.ZOMBIFIED_PIGLIN, LootTables.entityLootKey(pigZombie));
        assertSame(table(LootTables.SNOW_GOLEM), LootTables.getEntityLootTable(snowman));
        assertSame(table(LootTables.ZOMBIFIED_PIGLIN), LootTables.getEntityLootTable(pigZombie));
    }

    @Test
    public void pluginMutationsAreSpawnedAndClearingTheListCancelsAllDrops() {
        RecordingDeathFlow mutated = new RecordingDeathFlow(false);
        mutated.q();
        assertEquals(1, mutated.eventCalls);
        assertSame(mutated.initialDrops, mutated.eventInput);
        assertEquals(1, mutated.spawned.size());
        assertBukkitStack(mutated.spawned.get(0), Item.IRON_INGOT.id, 4, 2);

        RecordingDeathFlow cleared = new RecordingDeathFlow(true);
        cleared.q();
        assertEquals(1, cleared.eventCalls);
        assertTrue(cleared.spawned.isEmpty());
    }

    private static void assertBaseDeclaresQ(Class<?> type) throws Exception {
        Method method = null;
        Class<?> cursor = type;
        while (cursor != null && method == null) {
            try {
                method = cursor.getDeclaredMethod("q");
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        assertEquals(EntityLiving.class, method.getDeclaringClass());
        try {
            type.getDeclaredMethod("q");
            fail("specialized q() must be removed");
        } catch (NoSuchMethodException expected) {
        }
    }

    private static void assertEventUniformCount(
            EntityLiving entity,
            long seed,
            int itemId,
            int minimum,
            int maximum) {
        TracingRandom expectedRandom = new TracingRandom(seed);
        int expectedCount = minimum + expectedRandom.nextInt(maximum - minimum + 1);
        TracingRandom actualRandom = new TracingRandom(seed);
        entity.random = actualRandom;
        List<org.bukkit.inventory.ItemStack> actual = entity.createDeathEventDrops();
        assertEquals(expectedCount == 0 ? 0 : 1, actual.size());
        if (expectedCount > 0) {
            assertBukkitStack(actual.get(0), itemId, expectedCount, 0);
        }
        assertRandomParity(seed, expectedRandom, actualRandom);
    }

    private static void assertGeneric(
            EntityLiving entity,
            ResourceLocation expectedTable,
            int itemId) {
        assertEquals(expectedTable, LootTables.entityLootKey(entity));
        for (long seed = 0L; seed < 512L; seed++) {
            assertEventUniformCount(entity, seed, itemId, 0, 2);
        }
    }

    private static void assertBukkitStack(
            org.bukkit.inventory.ItemStack stack,
            int itemId,
            int count,
            int metadata) {
        assertEquals(itemId, stack.getTypeId());
        assertEquals(count, stack.getAmount());
        assertEquals(metadata, stack.getDurability());
    }

    private static void assertRandomParity(
            long seed,
            TracingRandom expected,
            TracingRandom actual) {
        assertEquals("random calls at seed " + seed, expected.getTrace(), actual.getTrace());
        assertEquals("post-generation random state at seed " + seed,
                expected.nextLong(), actual.nextLong());
    }

    private static EntityLootTable table(ResourceLocation key) {
        assertTrue("expected entity table for " + key,
                LootTables.get(key) instanceof EntityLootTable);
        return (EntityLootTable)LootTables.get(key);
    }

    private static final class TracingRandom extends Random {
        private final StringBuilder trace = new StringBuilder();

        TracingRandom(long seed) {
            super(seed);
        }

        public int nextInt(int bound) {
            int result = super.nextInt(bound);
            trace.append(bound).append('=').append(result).append(';');
            return result;
        }

        String getTrace() {
            return trace.toString();
        }
    }

    private static final class FallbackMob extends EntityLiving {
        private final int itemId;

        FallbackMob(int itemId) {
            super(null);
            this.itemId = itemId;
        }

        protected int j() {
            return itemId;
        }
    }

    private static final class RecordingDeathFlow extends EntityLiving {
        final List<org.bukkit.inventory.ItemStack> initialDrops =
                new ArrayList<org.bukkit.inventory.ItemStack>();
        final List<org.bukkit.inventory.ItemStack> spawned =
                new ArrayList<org.bukkit.inventory.ItemStack>();
        final boolean clear;
        List<org.bukkit.inventory.ItemStack> eventInput;
        int eventCalls;

        RecordingDeathFlow(boolean clear) {
            super(null);
            this.clear = clear;
            this.initialDrops.add(new org.bukkit.inventory.ItemStack(Item.ARROW.id, 2));
            this.initialDrops.add(new org.bukkit.inventory.ItemStack(Item.BONE.id, 1));
        }

        protected List<org.bukkit.inventory.ItemStack> createDeathEventDrops() {
            return initialDrops;
        }

        protected EntityDeathEvent callBukkitDeathEvent(
                List<org.bukkit.inventory.ItemStack> loot) {
            ++eventCalls;
            eventInput = loot;
            loot.clear();
            if (!clear) {
                loot.add(new org.bukkit.inventory.ItemStack(
                        Item.IRON_INGOT.id, 4, (short)2));
            }
            return new EntityDeathEvent(null, loot);
        }

        protected EntityItem spawnDeathDropAtEntityPosition(
                org.bukkit.inventory.ItemStack stack) {
            spawned.add(stack);
            return null;
        }
    }
}
