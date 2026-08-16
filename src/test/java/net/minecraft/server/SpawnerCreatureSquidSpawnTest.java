package net.minecraft.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import org.junit.Test;
import sun.misc.Unsafe;

public class SpawnerCreatureSquidSpawnTest {
    @Test
    public void fourBlockDeepPoolInSeaLevelBandAllowsSquidSpawn() throws Exception {
        RecordingWorld world = RecordingWorld.create(60, 63);

        assertTrue(SpawnerCreature.canSquidSpawnAtLocation(world, 8, 61, 8));
        assertTrue(SpawnerCreature.canSquidSpawnAtLocation(world, 8, 62, 8));
    }

    @Test
    public void shallowPoolRejectsSquidSpawn() throws Exception {
        RecordingWorld world = RecordingWorld.create(61, 63);

        assertFalse(SpawnerCreature.canSquidSpawnAtLocation(world, 8, 62, 8));
    }

    @Test
    public void poolOutsideSeaLevelBandRejectsSquidSpawn() throws Exception {
        RecordingWorld highPool = RecordingWorld.create(64, 67);
        RecordingWorld lowPool = RecordingWorld.create(45, 49);

        assertFalse(SpawnerCreature.canSquidSpawnAtLocation(highPool, 8, 65, 8));
        assertFalse(SpawnerCreature.canSquidSpawnAtLocation(lowPool, 8, 48, 8));
    }

    @Test
    public void waterMustSurroundTheSquidVertically() throws Exception {
        RecordingWorld world = RecordingWorld.create(60, 63);

        assertFalse(SpawnerCreature.canSquidSpawnAtLocation(world, 8, 60, 8));
        assertFalse(SpawnerCreature.canSquidSpawnAtLocation(world, 8, 63, 8));
    }

    private static final class RecordingWorld extends World {
        private int minWaterY;
        private int maxWaterY;

        private RecordingWorld() {
            super(null, "unused", 0L, null, null, org.bukkit.World.Environment.NORMAL);
        }

        static RecordingWorld create(int minWaterY, int maxWaterY) throws Exception {
            RecordingWorld world = (RecordingWorld)unsafe().allocateInstance(RecordingWorld.class);
            world.minWaterY = minWaterY;
            world.maxWaterY = maxWaterY;
            return world;
        }

        @Override
        public Material getMaterialIfLoaded(int x, int y, int z) {
            return y >= this.minWaterY && y <= this.maxWaterY ? Material.WATER : Material.AIR;
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }
}
