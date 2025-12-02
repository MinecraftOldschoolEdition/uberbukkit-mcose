package net.minecraft.server.threading;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe task queue for executing tasks on the main server thread.
 * Used to safely pass results from async operations back to the main thread.
 */
public class ThreadedTaskQueue {
    
    private static final Logger log = Logger.getLogger("Minecraft");
    private static ThreadedTaskQueue instance;
    
    private final ConcurrentLinkedQueue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processingTasks = new AtomicBoolean(false);
    
    // Maximum tasks to process per tick to prevent lag spikes
    private int maxTasksPerTick = 50;
    
    private ThreadedTaskQueue() {}
    
    public static synchronized ThreadedTaskQueue getInstance() {
        if (instance == null) {
            instance = new ThreadedTaskQueue();
        }
        return instance;
    }
    
    /**
     * Queue a task to run on the main server thread.
     * Safe to call from any thread.
     */
    public void queueMainThreadTask(Runnable task) {
        if (task != null) {
            mainThreadTasks.offer(task);
        }
    }
    
    /**
     * Process queued tasks. Call this from the main server tick loop.
     * Returns the number of tasks processed.
     */
    public int processMainThreadTasks() {
        if (!processingTasks.compareAndSet(false, true)) {
            return 0; // Already processing
        }
        
        try {
            int processed = 0;
            Runnable task;
            
            while (processed < maxTasksPerTick && (task = mainThreadTasks.poll()) != null) {
                try {
                    task.run();
                    processed++;
                } catch (Exception e) {
                    log.log(Level.WARNING, "Error executing queued task", e);
                }
            }
            
            return processed;
        } finally {
            processingTasks.set(false);
        }
    }
    
    /**
     * Get the number of pending tasks.
     */
    public int getPendingTaskCount() {
        return mainThreadTasks.size();
    }
    
    /**
     * Set the maximum number of tasks to process per tick.
     */
    public void setMaxTasksPerTick(int max) {
        this.maxTasksPerTick = Math.max(1, max);
    }
    
    /**
     * Clear all pending tasks. Use with caution.
     */
    public void clearTasks() {
        mainThreadTasks.clear();
    }
}

