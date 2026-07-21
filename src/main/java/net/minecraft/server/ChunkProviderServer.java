package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;
import net.minecraft.server.threading.AsyncChunkGenerator;
import net.minecraft.server.threading.ChunkGenerationData;
import net.minecraft.server.threading.ThreadingManager;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.util.LongHashset;
import org.bukkit.craftbukkit.util.LongHashtable;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.generator.BlockPopulator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// CraftBukkit start
// CraftBukkit end

public class ChunkProviderServer implements IChunkProvider {

    // CraftBukkit start
    public LongHashset unloadQueue = new LongHashset();
    public Chunk emptyChunk;
    public IChunkProvider chunkProvider; // CraftBukkit
    private IChunkLoader e;
    public boolean forceChunkLoad = false;
    public LongHashtable<Chunk> chunks = new LongHashtable<Chunk>();
    public List chunkList = new ArrayList();
    public WorldServer world;
    private int nextChunkSaveIndex = 0;
    private final Map<Long, ChunkSaveFailure> chunkSaveFailures = new HashMap<Long, ChunkSaveFailure>();
    private int lastChunkSaveFailureLogTick = Integer.MIN_VALUE;
    private int suppressedChunkSaveFailureLogs = 0;
    private static final int SAVE_RETRY_BASE_TICKS = 20;
    private static final int SAVE_RETRY_MAX_TICKS = 1200;
    // CraftBukkit end

    public ChunkProviderServer(WorldServer worldserver, IChunkLoader ichunkloader, IChunkProvider ichunkprovider) {
        this.emptyChunk = new EmptyChunk(worldserver, new byte['\u8000'], 0, 0);
        this.world = worldserver;
        this.e = ichunkloader;
        this.chunkProvider = ichunkprovider;
    }

    public boolean isChunkLoaded(int i, int j) {
        return this.chunks.containsKey(i, j); // CraftBukkit
    }

    Chunk getChunkAtIfLoadedMainThreadNoCache(int i, int j) {
        return (Chunk) this.chunks.get(i, j);
    }

    public void queueUnload(int i, int j) {
        ChunkCoordinates chunkcoordinates = this.world.getSpawn();
        int k = i * 16 + 8 - chunkcoordinates.x;
        int l = j * 16 + 8 - chunkcoordinates.z;
        short short1 = 128;

        if (k < -short1 || k > short1 || l < -short1 || l > short1 || !(this.world.keepSpawnInMemory)) { // CraftBukkit - added 'this.world.keepSpawnInMemory'
            this.unloadQueue.add(i, j); // CraftBukkit
        }
    }

    public Chunk getChunkAt(int i, int j) {
        // CraftBukkit start
        this.unloadQueue.remove(i, j);
        Chunk chunk = (Chunk) this.chunks.get(i, j);
        boolean newChunk = false;
        // CraftBukkit end

        if (chunk == null) {
            chunk = this.loadChunk(i, j);
            if (chunk != null) {
                ServerProfiler.getInstance().recordChunkLoaded();
            }
            if (chunk == null) {
                if (this.chunkProvider == null) {
                    chunk = this.emptyChunk;
                } else {
                    // Try async generation first (uses thread-safe generators)
                    AsyncChunkGenerator asyncGen = ThreadingManager.getInstance().getChunkGenerator(this.world);
                    long genStart = System.nanoTime();
                    if (asyncGen != null) {
                        // Check if async generation already completed
                        ChunkGenerationData asyncData = asyncGen.pollCompletedChunk(i, j);
                        if (asyncData != null) {
                            long applyStart = System.nanoTime();
                            // Create chunk from async-generated data using the byte[] constructor
                            chunk = new Chunk(this.world, asyncData.blocks, i, j);
                            asyncGen.applyDataToChunk(chunk, asyncData);
                            ServerProfiler.getInstance().recordChunkIo(
                                "async-apply",
                                (System.nanoTime() - applyStart) / 1_000_000.0D,
                                this.world.worldData.name
                            );
                        } else if (!asyncGen.isChunkPending(i, j)) {
                            // Request async generation for future chunks nearby
                            // But generate this one synchronously since player needs it now
                            asyncGen.requestChunkAsync(i, j);
                            long syncStart = System.nanoTime();
                            chunk = this.chunkProvider.getOrCreateChunk(i, j);
                            ServerProfiler.getInstance().recordChunkIo(
                                "generate-sync",
                                (System.nanoTime() - syncStart) / 1_000_000.0D,
                                this.world.worldData.name
                            );
                        } else {
                            // Chunk is pending - generate synchronously to avoid blocking
                            long syncStart = System.nanoTime();
                            chunk = this.chunkProvider.getOrCreateChunk(i, j);
                            ServerProfiler.getInstance().recordChunkIo(
                                "generate-sync",
                                (System.nanoTime() - syncStart) / 1_000_000.0D,
                                this.world.worldData.name
                            );
                        }
                    } else {
                        // Fallback to sync generation
                        long syncStart = System.nanoTime();
                        chunk = this.chunkProvider.getOrCreateChunk(i, j);
                        ServerProfiler.getInstance().recordChunkIo(
                            "generate-sync",
                            (System.nanoTime() - syncStart) / 1_000_000.0D,
                            this.world.worldData.name
                        );
                    }
                    long genEnd = System.nanoTime();
                    double genTimeMs = (genEnd - genStart) / 1_000_000.0;
                    ServerProfiler.getInstance().recordChunkGenerated(genTimeMs);
                }
                newChunk = true; // CraftBukkit
            }

            this.chunks.put(i, j, chunk); // CraftBukkit
            this.chunkList.add(chunk);
            if (chunk != null) {
                chunk.loadNOP();
                chunk.addEntities();
            }

            // CraftBukkit start
            org.bukkit.Server server = this.world.getServer();
            if (server != null) {
                /*
                 * If it's a new world, the first few chunks are generated inside
                 * the World constructor. We can't reliably alter that, so we have
                 * no way of creating a CraftWorld/CraftServer at that point.
                 */
                server.getPluginManager().callEvent(new ChunkLoadEvent(chunk.bukkitChunk, newChunk));
            }
            // CraftBukkit end

            if (!chunk.done && this.isChunkLoaded(i + 1, j + 1) && this.isChunkLoaded(i, j + 1) && this.isChunkLoaded(i + 1, j)) {
                this.getChunkAt(this, i, j);
            }

            if (this.isChunkLoaded(i - 1, j) && !this.getOrCreateChunk(i - 1, j).done && this.isChunkLoaded(i - 1, j + 1) && this.isChunkLoaded(i, j + 1) && this.isChunkLoaded(i - 1, j)) {
                this.getChunkAt(this, i - 1, j);
            }

            if (this.isChunkLoaded(i, j - 1) && !this.getOrCreateChunk(i, j - 1).done && this.isChunkLoaded(i + 1, j - 1) && this.isChunkLoaded(i, j - 1) && this.isChunkLoaded(i + 1, j)) {
                this.getChunkAt(this, i, j - 1);
            }

            if (this.isChunkLoaded(i - 1, j - 1) && !this.getOrCreateChunk(i - 1, j - 1).done && this.isChunkLoaded(i - 1, j - 1) && this.isChunkLoaded(i, j - 1) && this.isChunkLoaded(i - 1, j)) {
                this.getChunkAt(this, i - 1, j - 1);
            }
        }

        return chunk;
    }

    public Chunk getOrCreateChunk(int i, int j) {
        // CraftBukkit start
        Chunk chunk = (Chunk) this.chunks.get(i, j);

        //Poseidon chunk regenerate
        try {
            chunk = chunk == null ? (!this.world.isLoading && !this.forceChunkLoad ? this.emptyChunk : this.getChunkAt(i, j)) : chunk;
        } catch (Exception e) {
            if (PoseidonConfig.getInstance().getConfigBoolean("emergency.debug.regenerate-corrupt-chunks.enable")) {
                System.err.println("Poseidon could not load chunk (" + i + "," + j + "). Emergency corrupt-chunk handling is enabled; returning the empty quarantine chunk.");
                e.printStackTrace();
                return this.emptyChunk;
            } else {
                throw e instanceof ChunkLoadFailureException
                        ? (ChunkLoadFailureException) e
                        : new ChunkLoadFailureException(i, j, e);
            }
        }


        if (chunk == this.emptyChunk) return chunk;
        if (i != chunk.x || j != chunk.z) {
            MinecraftServer.log.info("Chunk (" + chunk.x + ", " + chunk.z + ") stored at  (" + i + ", " + j + ")");
            MinecraftServer.log.info(chunk.getClass().getName());
            Throwable ex = new Throwable();
            ex.fillInStackTrace();
            ex.printStackTrace();
        }
        return chunk;
        // CraftBukkit end
    }

    public Chunk loadChunk(int i, int j) { // CraftBukkit - private -> public
        if (this.e == null) {
            return null;
        } else {
            long start = System.nanoTime();
            try {
                Chunk chunk = this.e.a(this.world, i, j);

                if (chunk != null) {
                    chunk.r = this.world.getTime();
                }

                ServerProfiler.getInstance().recordChunkIo("load", (System.nanoTime() - start) / 1_000_000.0D, this.world.worldData.name);

                return chunk;
            } catch (Exception exception) {
                ServerProfiler.getInstance().recordChunkIo("load", (System.nanoTime() - start) / 1_000_000.0D, this.world.worldData.name);
                throw exception instanceof ChunkLoadFailureException
                        ? (ChunkLoadFailureException) exception
                        : new ChunkLoadFailureException(i, j, exception);
            }
        }
    }

    static final class ChunkLoadFailureException extends RuntimeException {
        final int chunkX;
        final int chunkZ;

        ChunkLoadFailureException(int chunkX, int chunkZ, Throwable cause) {
            super("Failed to load existing chunk [" + chunkX + "," + chunkZ + "]; refusing to regenerate over recoverable data", cause);
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }

    public void saveChunkNOP(Chunk chunk) { // CraftBukkit - private -> public
        if (this.e != null) {
            long start = System.nanoTime();
            try {
                this.e.b(this.world, chunk);
                ServerProfiler.getInstance().recordChunkIo("save-nop", (System.nanoTime() - start) / 1_000_000.0D, this.world.worldData.name);
            } catch (Exception exception) {
                ServerProfiler.getInstance().recordChunkIo("save-nop", (System.nanoTime() - start) / 1_000_000.0D, this.world.worldData.name);
                exception.printStackTrace();
            }
        }
    }

    public void saveChunk(Chunk chunk) { // CraftBukkit - private -> public
        this.saveChunkSafely(chunk, true);
    }

    private boolean saveChunkSafely(Chunk chunk) {
        return this.saveChunkSafely(chunk, false);
    }

    private boolean saveChunkSafely(Chunk chunk, boolean force) {
        if (this.e == null) {
            return false;
        }
        if (!force && !this.isChunkSaveRetryReady(chunk)) {
            return false;
        }

        long start = System.nanoTime();
        try {
            chunk.r = this.world.getTime();
            this.e.a(this.world, chunk);
            this.world.markPendingBlockTicksSavedForChunk(chunk.x, chunk.z);
            ServerProfiler.getInstance().recordChunkIo("save", (System.nanoTime() - start) / 1_000_000.0D, this.world.worldData.name);
            this.chunkSaveFailures.remove(Long.valueOf(chunkKey(chunk.x, chunk.z)));
            return true;
        } catch (Exception ioexception) { // CraftBukkit - IOException -> Exception
            chunk.o = true;
            ServerProfiler.getInstance().recordChunkIo("save", (System.nanoTime() - start) / 1_000_000.0D, this.world.worldData.name);
            this.recordChunkSaveFailure(chunk, ioexception);
            return false;
        }
    }

    private boolean isChunkSaveRetryReady(Chunk chunk) {
        ChunkSaveFailure failure = this.chunkSaveFailures.get(Long.valueOf(chunkKey(chunk.x, chunk.z)));
        return failure == null || MinecraftServer.currentTick >= failure.nextAttemptTick;
    }

    private void recordChunkSaveFailure(Chunk chunk, Exception failureCause) {
        Long key = Long.valueOf(chunkKey(chunk.x, chunk.z));
        ChunkSaveFailure failure = this.chunkSaveFailures.get(key);
        if (failure == null) {
            failure = new ChunkSaveFailure();
            this.chunkSaveFailures.put(key, failure);
        }

        ++failure.attempts;
        int shift = Math.min(10, failure.attempts - 1);
        int delay = Math.min(SAVE_RETRY_MAX_TICKS, SAVE_RETRY_BASE_TICKS << shift);
        failure.nextAttemptTick = MinecraftServer.currentTick + delay;

        int elapsed = MinecraftServer.currentTick - this.lastChunkSaveFailureLogTick;
        if (elapsed < 0 || elapsed >= 20) {
            System.err.println("Failed to save chunk [" + chunk.x + "," + chunk.z + "]; retrying in " + delay
                    + " ticks (" + failureCause.getClass().getSimpleName() + ": " + String.valueOf(failureCause.getMessage()) + ")"
                    + (this.suppressedChunkSaveFailureLogs > 0 ? " (" + this.suppressedChunkSaveFailureLogs + " similar messages suppressed)" : ""));
            this.lastChunkSaveFailureLogTick = MinecraftServer.currentTick;
            this.suppressedChunkSaveFailureLogs = 0;
        } else {
            ++this.suppressedChunkSaveFailureLogs;
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 4294967295L) | (((long) chunkZ & 4294967295L) << 32);
    }

    public void getChunkAt(IChunkProvider ichunkprovider, int i, int j) {
        Chunk chunk = this.getOrCreateChunk(i, j);

        if (!chunk.done) {
            chunk.done = true;
            if (this.chunkProvider != null) {
                this.chunkProvider.getChunkAt(ichunkprovider, i, j);

                // CraftBukkit start
                BlockSand.instaFall = true;
                Random random = new Random();
                random.setSeed(world.getSeed());
                long xRand = random.nextLong() / 2L * 2L + 1L;
                long zRand = random.nextLong() / 2L * 2L + 1L;
                random.setSeed((long) i * xRand + (long) j * zRand ^ world.getSeed());

                org.bukkit.World world = this.world.getWorld();
                if (world != null) {
                    for (BlockPopulator populator : world.getPopulators()) {
                        populator.populate(world, random, chunk.bukkitChunk);
                    }
                }
                BlockSand.instaFall = false;
                this.world.getServer().getPluginManager().callEvent(new ChunkPopulateEvent(chunk.bukkitChunk));
                // CraftBukkit end

                chunk.f();
            }
        }
    }

    public boolean saveChunks(boolean flag, IProgressUpdate iprogressupdate) {
        int i = 0;

        if (flag) {
            for (int j = 0; j < this.chunkList.size(); ++j) {
                Chunk chunk = (Chunk) this.chunkList.get(j);

                if (!chunk.p) {
                    this.saveChunkNOP(chunk);
                }

                if (chunk.a(true)) {
                    if (this.saveChunkSafely(chunk, true)) {
                        chunk.o = false;
                    }
                }
            }

            if (this.e == null) {
                return true;
            }

            this.e.b();
            return true;
        }

        int chunkCount = this.chunkList.size();

        if (chunkCount == 0) {
            this.nextChunkSaveIndex = 0;
            return true;
        }

        for (int checked = 0; checked < chunkCount; ++checked) {
            if (this.nextChunkSaveIndex >= this.chunkList.size()) {
                this.nextChunkSaveIndex = 0;
            }

            Chunk chunk = (Chunk) this.chunkList.get(this.nextChunkSaveIndex++);

            if (chunk.a(false)) {
                if (this.saveChunkSafely(chunk)) {
                    chunk.o = false;
                    ++i;
                }

                if (i == 24) {
                    return false;
                }
            }
        }

        return true;
    }

    public boolean unloadChunks() {
        if (!this.world.canSave) {
            // CraftBukkit start
            org.bukkit.Server server = this.world.getServer();
            ArrayList failedUnloads = new ArrayList();
            for (int i = 0; i < 50 && !this.unloadQueue.isEmpty(); i++) {
                long chunkcoordinates = this.unloadQueue.popFirst();
                Chunk chunk = this.chunks.get(chunkcoordinates);
                if (chunk == null) continue;

                ChunkUnloadEvent event = new ChunkUnloadEvent(chunk.bukkitChunk);
                if (!this.isChunkSaveRetryReady(chunk)) {
                    failedUnloads.add(Long.valueOf(chunkcoordinates));
                    continue;
                }
                server.getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
//                    this.world.getWorld().preserveChunk((CraftChunk) chunk.bukkitChunk);

                    if (!this.saveChunkSafely(chunk)) {
                        failedUnloads.add(Long.valueOf(chunkcoordinates));
                        continue;
                    }
                    this.saveChunkNOP(chunk);
                    chunk.removeEntities();
                    // this.unloadQueue.remove(integer);
                    this.chunks.remove(chunkcoordinates); // CraftBukkit
                    this.chunkList.remove(chunk);
                    this.chunkSaveFailures.remove(Long.valueOf(chunkcoordinates));
                    ServerProfiler.getInstance().recordChunkUnloaded();
                }
            }

            for (int i = 0; i < failedUnloads.size(); ++i) {
                this.unloadQueue.add(((Long) failedUnloads.get(i)).longValue());
            }
            // CraftBukkit end

            if (this.e != null) {
                this.e.a();
            }
        }

        return this.chunkProvider.unloadChunks();
    }

    public boolean canSave() {
        return !this.world.canSave;
    }

    private static final class ChunkSaveFailure {
        int attempts;
        int nextAttemptTick;
    }
}
