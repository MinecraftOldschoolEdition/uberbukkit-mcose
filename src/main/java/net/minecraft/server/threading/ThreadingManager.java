package net.minecraft.server.threading;

import net.minecraft.server.*;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Central manager for all async/threaded operations.
 * Initializes and coordinates the various async systems.
 */
public class ThreadingManager {
    
    private static final Logger log = Logger.getLogger("Minecraft");
    private static ThreadingManager instance;
    
    private final ThreadingConfig config;
    private final ThreadedTaskQueue taskQueue;
    
    // Per-world async systems
    private final Map<World, AsyncChunkGenerator> chunkGenerators;
    private final Map<World, AsyncLightingEngine> lightingEngines;
    private final Map<World, AsyncEntityProcessor> entityProcessors;
    
    private boolean initialized = false;
    
    private ThreadingManager() {
        this.config = ThreadingConfig.getInstance();
        this.taskQueue = ThreadedTaskQueue.getInstance();
        this.chunkGenerators = new HashMap<>();
        this.lightingEngines = new HashMap<>();
        this.entityProcessors = new HashMap<>();
    }
    
    public static synchronized ThreadingManager getInstance() {
        if (instance == null) {
            instance = new ThreadingManager();
        }
        return instance;
    }
    
    /**
     * Initialize threading systems for the server.
     * Call this after worlds are loaded.
     */
    public void initialize(MinecraftServer server) {
        if (initialized) return;
        
        log.info("[Threading] Initializing async systems...");
        
        taskQueue.setMaxTasksPerTick(config.getMaxTasksPerTick());
        
        // Initialize per-world systems
        for (WorldServer world : server.worlds) {
            initializeWorldSystems(world);
        }
        
        initialized = true;
        log.info("[Threading] Async systems initialized successfully");
    }
    
    /**
     * Initialize async systems for a specific world.
     */
    public void initializeWorldSystems(WorldServer world) {
        String worldName = world.worldData.name;
        
        // Async chunk generation using thread-safe generators
        if (config.isAsyncChunkGenerationEnabled()) {
            int terrainType = world.worldData != null ? world.worldData.getTerrainType() : 0;
            if (AsyncChunkGenerator.isTerrainTypeSupported(terrainType)) {
                AsyncChunkGenerator chunkGen = new AsyncChunkGenerator(
                    world,
                    config.getChunkGenThreads()
                );
                chunkGenerators.put(world, chunkGen);
                log.info("[Threading] Enabled async chunk generation for world '" + worldName + "'");
            } else {
                log.warning("[Threading] Async chunk generation disabled for world '" + worldName +
                    "' because terrain type " + terrainType + " (" +
                    AsyncChunkGenerator.terrainTypeName(terrainType) +
                    ") does not have a thread-safe generator yet.");
            }
        }
        
        // Async lighting
        if (config.isAsyncLightingEnabled()) {
            AsyncLightingEngine lightingEngine = new AsyncLightingEngine(
                world,
                config.getLightingThreads()
            );
            lightingEngines.put(world, lightingEngine);
            log.info("[Threading] Enabled async lighting for world '" + worldName + "'");
        }
        
        // Async entity processing
        if (config.isAsyncEntityProcessingEnabled()) {
            AsyncEntityProcessor entityProc = new AsyncEntityProcessor(
                world,
                config.getEntityProcessingThreads()
            );
            entityProcessors.put(world, entityProc);
            log.info("[Threading] Enabled async entity processing for world '" + worldName + "'");
        }
    }
    
    /**
     * Called each server tick to process async results.
     * Call this at the end of the tick loop.
     */
    public void processTick() {
        // Process main thread task queue
        taskQueue.processMainThreadTasks();
        
        // Process lighting results
        for (AsyncLightingEngine engine : lightingEngines.values()) {
            engine.applyCompletedResults(20);
        }
        
        // Process entity results
        for (AsyncEntityProcessor processor : entityProcessors.values()) {
            processor.applyCompletedResults(50);
        }
    }
    
    /**
     * Called before world entity tick to queue async work.
     */
    public void preEntityTick(World world) {
        AsyncEntityProcessor processor = entityProcessors.get(world);
        if (processor != null) {
            processor.processQueuedTasks();
        }
    }
    
    /**
     * Get the async chunk generator for a world.
     */
    public AsyncChunkGenerator getChunkGenerator(World world) {
        return chunkGenerators.get(world);
    }
    
    /**
     * Get the async lighting engine for a world.
     */
    public AsyncLightingEngine getLightingEngine(World world) {
        return lightingEngines.get(world);
    }
    
    /**
     * Get the async entity processor for a world.
     */
    public AsyncEntityProcessor getEntityProcessor(World world) {
        return entityProcessors.get(world);
    }
    
    /**
     * Get the task queue.
     */
    public ThreadedTaskQueue getTaskQueue() {
        return taskQueue;
    }
    
    /**
     * Get the threading config.
     */
    public ThreadingConfig getConfig() {
        return config;
    }

    public ThreadingStatsSnapshot captureStats() {
        ThreadingStatsSnapshot snapshot = new ThreadingStatsSnapshot();
        snapshot.pendingMainThreadTasks = taskQueue.getPendingTaskCount();

        for (Map.Entry<World, AsyncChunkGenerator> entry : chunkGenerators.entrySet()) {
            String name = entry.getKey() instanceof WorldServer
                ? ((WorldServer) entry.getKey()).worldData.name
                : "unknown";
            AsyncChunkGenerator gen = entry.getValue();
            WorldAsyncStats stats = snapshot.worlds.get(name);
            if (stats == null) {
                stats = new WorldAsyncStats();
                stats.worldName = name;
                snapshot.worlds.put(name, stats);
            }
            stats.chunkGenActive = gen.getActiveGenerations();
            stats.chunkGenReady = gen.getReadyChunks();
            stats.chunkGenTotal = gen.getTotalChunksGenerated();
        }

        for (Map.Entry<World, AsyncLightingEngine> entry : lightingEngines.entrySet()) {
            String name = entry.getKey() instanceof WorldServer
                ? ((WorldServer) entry.getKey()).worldData.name
                : "unknown";
            AsyncLightingEngine engine = entry.getValue();
            WorldAsyncStats stats = snapshot.worlds.get(name);
            if (stats == null) {
                stats = new WorldAsyncStats();
                stats.worldName = name;
                snapshot.worlds.put(name, stats);
            }
            stats.lightingActive = engine.getActiveTasks();
            stats.lightingPending = engine.getPendingTasks();
            stats.lightingTotal = engine.getTotalUpdates();
        }

        for (Map.Entry<World, AsyncEntityProcessor> entry : entityProcessors.entrySet()) {
            String name = entry.getKey() instanceof WorldServer
                ? ((WorldServer) entry.getKey()).worldData.name
                : "unknown";
            AsyncEntityProcessor proc = entry.getValue();
            WorldAsyncStats stats = snapshot.worlds.get(name);
            if (stats == null) {
                stats = new WorldAsyncStats();
                stats.worldName = name;
                snapshot.worlds.put(name, stats);
            }
            stats.entityActive = proc.getActiveTasks();
            stats.entityPending = proc.getPendingTasks();
            stats.entityTotal = proc.getTotalProcessed();
        }

        return snapshot;
    }
    
    /**
     * Print statistics about async systems.
     */
    public void printStats() {
        log.info("[Threading] === Async Statistics ===");
        log.info("[Threading] Pending main thread tasks: " + taskQueue.getPendingTaskCount());
        
        for (Map.Entry<World, AsyncChunkGenerator> entry : chunkGenerators.entrySet()) {
            String name = entry.getKey() instanceof WorldServer ? 
                ((WorldServer) entry.getKey()).worldData.name : "unknown";
            AsyncChunkGenerator gen = entry.getValue();
            log.info("[Threading] ChunkGen[" + name + "]: " + gen.getActiveGenerations() + 
                " active, " + gen.getReadyChunks() + " ready, " + 
                gen.getTotalChunksGenerated() + " total");
        }
        
        for (Map.Entry<World, AsyncLightingEngine> entry : lightingEngines.entrySet()) {
            String name = entry.getKey() instanceof WorldServer ?
                ((WorldServer) entry.getKey()).worldData.name : "unknown";
            AsyncLightingEngine engine = entry.getValue();
            log.info("[Threading] Lighting[" + name + "]: " + engine.getActiveTasks() +
                " active, " + engine.getPendingTasks() + " pending, " +
                engine.getTotalUpdates() + " total");
        }
        
        for (Map.Entry<World, AsyncEntityProcessor> entry : entityProcessors.entrySet()) {
            String name = entry.getKey() instanceof WorldServer ?
                ((WorldServer) entry.getKey()).worldData.name : "unknown";
            AsyncEntityProcessor proc = entry.getValue();
            log.info("[Threading] Entity[" + name + "]: " + proc.getActiveTasks() +
                " active, " + proc.getPendingTasks() + " pending, " +
                proc.getTotalProcessed() + " total");
        }
    }
    
    /**
     * Shutdown all async systems.
     */
    public void shutdown() {
        log.info("[Threading] Shutting down async systems...");
        
        // Shutdown chunk generators
        for (AsyncChunkGenerator gen : chunkGenerators.values()) {
            gen.shutdown();
        }
        chunkGenerators.clear();
        
        // Shutdown lighting engines
        for (AsyncLightingEngine engine : lightingEngines.values()) {
            engine.shutdown();
        }
        lightingEngines.clear();
        
        // Shutdown entity processors
        for (AsyncEntityProcessor proc : entityProcessors.values()) {
            proc.shutdown();
        }
        entityProcessors.clear();
        
        // Clear task queue
        taskQueue.clearTasks();
        
        // Save config
        config.save();
        
        initialized = false;
        log.info("[Threading] Async systems shutdown complete");
    }
    
    /**
     * Unload systems for a specific world.
     */
    public void unloadWorld(World world) {
        AsyncChunkGenerator chunkGen = chunkGenerators.remove(world);
        if (chunkGen != null) chunkGen.shutdown();
        
        AsyncLightingEngine lighting = lightingEngines.remove(world);
        if (lighting != null) lighting.shutdown();
        
        AsyncEntityProcessor entity = entityProcessors.remove(world);
        if (entity != null) entity.shutdown();
    }

    public static class ThreadingStatsSnapshot {
        public int pendingMainThreadTasks;
        public final Map<String, WorldAsyncStats> worlds = new HashMap<String, WorldAsyncStats>();
    }

    public static class WorldAsyncStats {
        public String worldName;
        public int chunkGenActive;
        public int chunkGenReady;
        public int chunkGenTotal;
        public int lightingActive;
        public int lightingPending;
        public int lightingTotal;
        public int entityActive;
        public int entityPending;
        public int entityTotal;
    }
}
