package net.minecraft.server.threading;

import java.io.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Configuration for the multi-threading system.
 */
public class ThreadingConfig {
    
    private static final Logger log = Logger.getLogger("Minecraft");
    private static ThreadingConfig instance;
    
    private final Properties properties;
    private final File configFile;
    
    // Threading settings
    private boolean asyncChunkGeneration;
    private int chunkGenThreads;
    private boolean asyncLighting;
    private int lightingThreads;
    private boolean asyncEntityProcessing;
    private int entityProcessingThreads;
    private int maxTasksPerTick;
    
    private ThreadingConfig() {
        this.configFile = new File("threading.properties");
        this.properties = new Properties();
        loadDefaults();
        load();
    }
    
    public static synchronized ThreadingConfig getInstance() {
        if (instance == null) {
            instance = new ThreadingConfig();
        }
        return instance;
    }
    
    private void loadDefaults() {
        // Determine default thread counts based on available processors
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int defaultChunkThreads = Math.max(1, availableProcessors / 4);
        int defaultLightingThreads = Math.max(1, availableProcessors / 4);
        int defaultEntityThreads = Math.max(1, availableProcessors / 4);
        
        // Async chunk generation - uses thread-safe generators that don't access shared world state
        // Safe to enable - terrain math runs on worker threads, world updates happen on main thread
        asyncChunkGeneration = true;
        chunkGenThreads = defaultChunkThreads;
        
        // Async lighting - experimental, can cause visual glitches
        asyncLighting = false;
        lightingThreads = defaultLightingThreads;
        
        // Async entity processing - experimental, can cause AI issues
        asyncEntityProcessing = false;
        entityProcessingThreads = defaultEntityThreads;
        
        // Task processing limit per tick
        maxTasksPerTick = 100;
    }
    
    private void load() {
        if (!configFile.exists()) {
            save();
            return;
        }
        
        try (FileInputStream fis = new FileInputStream(configFile)) {
            properties.load(fis);
            
            asyncChunkGeneration = getBoolean("async.chunk-generation.enabled", asyncChunkGeneration);
            chunkGenThreads = getInt("async.chunk-generation.threads", chunkGenThreads);
            
            asyncLighting = getBoolean("async.lighting.enabled", asyncLighting);
            lightingThreads = getInt("async.lighting.threads", lightingThreads);
            
            asyncEntityProcessing = getBoolean("async.entity-processing.enabled", asyncEntityProcessing);
            entityProcessingThreads = getInt("async.entity-processing.threads", entityProcessingThreads);
            
            maxTasksPerTick = getInt("threading.max-tasks-per-tick", maxTasksPerTick);
            
            log.info("[Threading] Loaded configuration:");
            log.info("[Threading]   Async Chunk Generation: " + asyncChunkGeneration + " (" + chunkGenThreads + " threads)");
            log.info("[Threading]   Async Lighting: " + asyncLighting + " (" + lightingThreads + " threads)");
            log.info("[Threading]   Async Entity Processing: " + asyncEntityProcessing + " (" + entityProcessingThreads + " threads)");
            
        } catch (IOException e) {
            log.warning("[Threading] Failed to load config, using defaults: " + e.getMessage());
        }
    }
    
    public void save() {
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            properties.setProperty("async.chunk-generation.enabled", String.valueOf(asyncChunkGeneration));
            properties.setProperty("async.chunk-generation.threads", String.valueOf(chunkGenThreads));
            
            properties.setProperty("async.lighting.enabled", String.valueOf(asyncLighting));
            properties.setProperty("async.lighting.threads", String.valueOf(lightingThreads));
            
            properties.setProperty("async.entity-processing.enabled", String.valueOf(asyncEntityProcessing));
            properties.setProperty("async.entity-processing.threads", String.valueOf(entityProcessingThreads));
            
            properties.setProperty("threading.max-tasks-per-tick", String.valueOf(maxTasksPerTick));
            
            properties.store(fos, "Server Threading Configuration\n" +
                "WARNING: Async features are experimental!\n" +
                "Async chunk generation is generally safe.\n" +
                "Async lighting and entity processing may cause issues.");
            
        } catch (IOException e) {
            log.warning("[Threading] Failed to save config: " + e.getMessage());
        }
    }
    
    private boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
    
    private int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    // Getters
    public boolean isAsyncChunkGenerationEnabled() {
        return asyncChunkGeneration;
    }
    
    public int getChunkGenThreads() {
        return chunkGenThreads;
    }
    
    public boolean isAsyncLightingEnabled() {
        return asyncLighting;
    }
    
    public int getLightingThreads() {
        return lightingThreads;
    }
    
    public boolean isAsyncEntityProcessingEnabled() {
        return asyncEntityProcessing;
    }
    
    public int getEntityProcessingThreads() {
        return entityProcessingThreads;
    }
    
    public int getMaxTasksPerTick() {
        return maxTasksPerTick;
    }
    
    // Setters (for runtime modification)
    public void setAsyncChunkGeneration(boolean enabled) {
        this.asyncChunkGeneration = enabled;
    }
    
    public void setAsyncLighting(boolean enabled) {
        this.asyncLighting = enabled;
    }
    
    public void setAsyncEntityProcessing(boolean enabled) {
        this.asyncEntityProcessing = enabled;
    }
}

