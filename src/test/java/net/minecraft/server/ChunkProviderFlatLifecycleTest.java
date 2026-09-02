package net.minecraft.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

public class ChunkProviderFlatLifecycleTest {
    @BeforeClass
    public static void initializeLegacyRegistries() {
        StatisticList.a();
    }

    @Test
    public void flatGeneratorDoesNotRetainASecondCopyOfOwnedChunks() throws Exception {
        WorldServer world = (WorldServer) unsafe().allocateInstance(WorldServer.class);
        putObjectField(world, "worldProvider", new WorldProviderNormal());
        world.chunkProvider = new NoLoadedChunks();
        ChunkProviderFlat source = new ChunkProviderFlat(world, 1L, false);

        Chunk first = source.getOrCreateChunk(37, -19);
        Chunk second = source.getOrCreateChunk(37, -19);

        assertTrue(source.isChunkLoaded(37, -19));
        assertNotSame(first, second);
        assertNotSame(first.b, second.b);
        assertNotSame(first.e, second.e);
        assertNotSame(first.f, second.f);
        assertNotSame(first.g, second.g);
        assertArrayEquals(first.b, second.b);
        assertArrayEquals(first.heightMap, second.heightMap);
    }

    private static void putObjectField(Object target, String fieldName, Object value) throws Exception {
        Field field = World.class.getDeclaredField(fieldName);
        unsafe().putObject(target, unsafe().objectFieldOffset(field), value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class NoLoadedChunks implements IChunkProvider {
        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return false;
        }

        public Chunk getOrCreateChunk(int chunkX, int chunkZ) {
            return null;
        }

        public Chunk getChunkAt(int chunkX, int chunkZ) {
            return null;
        }

        public void getChunkAt(IChunkProvider provider, int chunkX, int chunkZ) {
        }

        public boolean saveChunks(boolean saveAll, IProgressUpdate progress) {
            return true;
        }

        public boolean unloadChunks() {
            return false;
        }

        public boolean canSave() {
            return true;
        }
    }
}
