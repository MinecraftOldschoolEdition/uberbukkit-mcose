package net.minecraft.server.threading;

import net.minecraft.server.Entity;
import net.minecraft.server.EntityCreature;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.World;
import net.minecraft.server.WorldServer;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asynchronous entity processing system.
 * Handles entity AI, pathfinding, and non-critical updates on worker threads.
 * 
 * IMPORTANT: Entity position updates, collision, and player interactions
 * MUST still happen on the main thread for safety.
 */
public class AsyncEntityProcessor {
    
    private static final Logger log = Logger.getLogger("Minecraft");
    
    private final World world;
    private final ExecutorService executor;
    private final ConcurrentLinkedQueue<EntityTask> pendingTasks;
    private final ConcurrentLinkedQueue<EntityResult> completedResults;
    private final AtomicInteger activeTasks;
    
    private volatile boolean shutdown = false;
    private final int batchSize;
    
    // Statistics
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    
    public AsyncEntityProcessor(World world, int threadCount) {
        this.world = world;
        this.batchSize = 50; // Process entities in batches
        this.pendingTasks = new ConcurrentLinkedQueue<>();
        this.completedResults = new ConcurrentLinkedQueue<>();
        this.activeTasks = new AtomicInteger(0);
        
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger threadNum = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "EntityProc-" + threadNum.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
        };
        
        this.executor = Executors.newFixedThreadPool(threadCount, factory);
        log.info("[AsyncEntity] Started " + threadCount + " entity processing threads");
    }
    
    /**
     * Queue entity AI calculations for async processing.
     * Safe operations: target finding, path calculation, decision making.
     */
    public void queueEntityAI(EntityLiving entity) {
        if (shutdown || entity == null || entity.dead) return;
        
        EntityTask task = new EntityTask(entity, EntityTaskType.AI_UPDATE);
        pendingTasks.offer(task);
    }
    
    /**
     * Queue pathfinding calculation for an entity.
     * Note: Currently simplified - just queues an AI update instead.
     */
    public void queuePathfinding(EntityLiving entity, double targetX, double targetY, double targetZ) {
        if (shutdown || entity == null || entity.dead) return;
        
        // Simplified: queue AI update which will handle targeting
        EntityTask task = new EntityTask(entity, EntityTaskType.AI_UPDATE);
        pendingTasks.offer(task);
    }
    
    /**
     * Process queued entity tasks on worker threads.
     * Call this at the start of the entity tick phase.
     */
    public void processQueuedTasks() {
        if (shutdown || pendingTasks.isEmpty()) return;
        
        // Gather tasks into batches
        List<EntityTask> batch = new ArrayList<>(batchSize);
        EntityTask task;
        while (batch.size() < batchSize && (task = pendingTasks.poll()) != null) {
            batch.add(task);
        }
        
        if (batch.isEmpty()) return;
        
        activeTasks.incrementAndGet();
        executor.submit(() -> {
            try {
                for (EntityTask t : batch) {
                    processTask(t);
                }
            } finally {
                activeTasks.decrementAndGet();
            }
        });
    }
    
    /**
     * Process a single entity task.
     */
    private void processTask(EntityTask task) {
        try {
            if (task.entity.dead) return;
            
            EntityResult result = null;
            
            switch (task.type) {
                case AI_UPDATE:
                    result = processAIUpdate(task.entity);
                    break;
                case PATHFINDING:
                    // Pathfinding now handled through AI_UPDATE
                    result = processAIUpdate(task.entity);
                    break;
            }
            
            if (result != null) {
                completedResults.offer(result);
            }
            totalProcessed.incrementAndGet();
        } catch (Exception e) {
            log.log(Level.WARNING, "[AsyncEntity] Error processing entity task", e);
        }
    }
    
    /**
     * Process AI update for an entity.
     * This calculates what the entity SHOULD do, but doesn't apply it.
     */
    private EntityResult processAIUpdate(EntityLiving entity) {
        // For mobs, we can pre-calculate target selection
        if (entity instanceof EntityCreature) {
            EntityCreature creature = (EntityCreature) entity;
            
            // Find potential targets (read-only operation)
            Entity closestTarget = findClosestTarget(creature);
            
            if (closestTarget != null) {
                return new EntityResult(entity, EntityResultType.SET_TARGET, closestTarget.id);
            }
        }
        
        return null;
    }
    
    /**
     * Find the closest valid target for a creature.
     * This is a read-only operation safe for async.
     */
    private Entity findClosestTarget(EntityCreature creature) {
        // Basic target finding - can be expanded
        double searchRange = 16.0;
        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        
        // Note: This reads from the entity list which may have concurrent modifications
        // In practice, this is mostly safe for read-only target finding
        try {
            // Use world.a(Class, AxisAlignedBB) to get entities by type
            List<?> entities = world.a(
                EntityHuman.class,
                creature.boundingBox.b(searchRange, 4.0, searchRange)
            );
            
            for (Object obj : entities) {
                Entity entity = (Entity) obj;
                if (entity.dead) continue;
                
                // Calculate distance manually
                double dx = creature.locX - entity.locX;
                double dy = creature.locY - entity.locY;
                double dz = creature.locZ - entity.locZ;
                double dist = dx * dx + dy * dy + dz * dz;
                
                if (dist < closestDist) {
                    closest = entity;
                    closestDist = dist;
                }
            }
        } catch (ConcurrentModificationException e) {
            // Ignore - will retry next tick
        }
        
        return closest;
    }
    
    /**
     * Apply completed results to entities on the main thread.
     * Call this after entity ticking.
     */
    public int applyCompletedResults(int maxPerTick) {
        int applied = 0;
        EntityResult result;
        
        while (applied < maxPerTick && (result = completedResults.poll()) != null) {
            try {
                applyResult(result);
                applied++;
            } catch (Exception e) {
                log.log(Level.WARNING, "[AsyncEntity] Error applying entity result", e);
            }
        }
        
        return applied;
    }
    
    /**
     * Apply a single result to an entity.
     */
    private void applyResult(EntityResult result) {
        if (result.entity.dead) return;
        
        switch (result.type) {
            case SET_TARGET:
                if (result.entity instanceof EntityCreature) {
                    EntityCreature creature = (EntityCreature) result.entity;
                    // Find target entity by iterating through entities
                    Entity target = findEntityById(result.targetId);
                    if (target != null && !target.dead) {
                        creature.target = target;
                    }
                }
                break;
                
            case PATH_NEEDED:
                // PATH_NEEDED is currently a no-op - pathfinding requires target entity
                // and should be triggered after SET_TARGET
                break;
        }
    }
    
    /**
     * Find an entity by its ID.
     */
    private Entity findEntityById(int id) {
        if (world instanceof WorldServer) {
            return ((WorldServer) world).getEntity(id);
        }
        return null;
    }
    
    /**
     * Get the number of pending tasks.
     */
    public int getPendingTasks() {
        return pendingTasks.size();
    }
    
    /**
     * Get the number of active worker tasks.
     */
    public int getActiveTasks() {
        return activeTasks.get();
    }
    
    /**
     * Get total entities processed.
     */
    public int getTotalProcessed() {
        return totalProcessed.get();
    }
    
    /**
     * Shutdown the entity processor.
     */
    public void shutdown() {
        shutdown = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        pendingTasks.clear();
        completedResults.clear();
        log.info("[AsyncEntity] Shutdown. Processed " + totalProcessed.get() + " entity tasks.");
    }
    
    // Task types
    private enum EntityTaskType {
        AI_UPDATE,
        PATHFINDING
    }
    
    // Result types
    private enum EntityResultType {
        SET_TARGET,
        PATH_NEEDED
    }
    
    /**
     * Task representing work to do for an entity.
     */
    private static class EntityTask {
        final EntityLiving entity;
        final EntityTaskType type;
        
        EntityTask(EntityLiving entity, EntityTaskType type) {
            this.entity = entity;
            this.type = type;
        }
    }
    
    /**
     * Result of async entity processing.
     */
    private static class EntityResult {
        final EntityLiving entity;
        final EntityResultType type;
        final int targetId;
        
        EntityResult(EntityLiving entity, EntityResultType type, int targetId) {
            this.entity = entity;
            this.type = type;
            this.targetId = targetId;
        }
    }
}

