package net.minecraft.server;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.craftbukkit.util.LongHashtable;
import org.junit.Test;
import sun.misc.Unsafe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChunkEmissiveBlockLightRepairTest {
    @Test
    public void installedGeneratedLavaSeedsBlockLightAndSchedulesRelight() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        ChunkProviderServer provider = provider(world);
        Chunk chunk = chunkAt(0, 0);
        chunk.b[index(4, 40, 5)] = (byte) Block.STATIONARY_LAVA.id;
        provider.chunks.put(0, 0, chunk);

        provider.initializeChunkBlockLightingAfterInstall(chunk);

        assertEquals(15, chunk.g.a(4, 40, 5));
        assertTrue(chunk.o);
        assertTrue(world.hasUpdate(EnumSkyBlock.BLOCK, 0, 0, 0, 15, 127, 15));
        world.updates.clear();

        provider.initializeChunkBlockLightingAfterInstall(chunk);

        assertFalse("a correctly seeded source must not request another full relight",
            world.hasUpdate(EnumSkyBlock.BLOCK, 0, 0, 0, 15, 127, 15));
    }

    @Test
    public void installingNeighborSchedulesBothSidesOfSharedBlockLightBoundary() throws Exception {
        RecordingWorld world = RecordingWorld.create();
        ChunkProviderServer provider = provider(world);
        Chunk west = chunkAt(0, 0);
        west.b[index(15, 40, 5)] = (byte) Block.STATIONARY_LAVA.id;
        provider.chunks.put(0, 0, west);
        provider.initializeChunkBlockLightingAfterInstall(west);
        world.updates.clear();

        Chunk east = chunkAt(1, 0);
        provider.chunks.put(1, 0, east);
        provider.initializeChunkBlockLightingAfterInstall(east);

        assertEquals(15, west.g.a(15, 40, 5));
        assertTrue(world.hasUpdate(EnumSkyBlock.BLOCK, 15, 0, 0, 16, 127, 15));
    }

    private static ChunkProviderServer provider(RecordingWorld world) throws Exception {
        ChunkProviderServer provider = (ChunkProviderServer) unsafe().allocateInstance(ChunkProviderServer.class);
        provider.world = world;
        provider.chunks = new LongHashtable<Chunk>();
        provider.chunkList = new ArrayList();
        return provider;
    }

    private static Chunk chunkAt(int chunkX, int chunkZ) throws Exception {
        Chunk chunk = (Chunk) unsafe().allocateInstance(Chunk.class);
        chunk.x = chunkX;
        chunk.z = chunkZ;
        chunk.b = new byte[32768];
        chunk.g = new NibbleArray(32768);
        return chunk;
    }

    private static int index(int localX, int y, int localZ) {
        return localX << 11 | localZ << 7 | y;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class RecordingWorld extends WorldServer {
        List<int[]> updates;

        private RecordingWorld() {
            super(null, null, "unused", 0, 0L, org.bukkit.World.Environment.NORMAL, null);
        }

        static RecordingWorld create() throws Exception {
            RecordingWorld world = (RecordingWorld) unsafe().allocateInstance(RecordingWorld.class);
            world.updates = new ArrayList<int[]>();
            return world;
        }

        public void a(EnumSkyBlock lightType,
                      int minX,
                      int minY,
                      int minZ,
                      int maxX,
                      int maxY,
                      int maxZ) {
            this.updates.add(new int[] {
                lightType == EnumSkyBlock.BLOCK ? 1 : 0,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ
            });
        }

        boolean hasUpdate(EnumSkyBlock lightType,
                          int minX,
                          int minY,
                          int minZ,
                          int maxX,
                          int maxY,
                          int maxZ) {
            int type = lightType == EnumSkyBlock.BLOCK ? 1 : 0;
            for (int[] update : this.updates) {
                if (update[0] == type
                    && update[1] == minX
                    && update[2] == minY
                    && update[3] == minZ
                    && update[4] == maxX
                    && update[5] == maxY
                    && update[6] == maxZ) {
                    return true;
                }
            }
            return false;
        }
    }
}
