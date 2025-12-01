package net.minecraft.server;

import java.util.*;

/**
 * Manages Herobrine encounter events for multiplayer.
 * 
 * Design:
 * - One Herobrine entity per active event
 * - Herobrine focuses on one primary target at a time
 * - All players within render distance see the fog effect
 * - Herobrine retreats from ANY player who gets too close
 * - Event ends when Herobrine despawns or times out
 */
public class HerobrineEventManager {
    
    // Event states
    public static final int STATE_INACTIVE = 0;
    public static final int STATE_FOG_CLOSING = 1;
    public static final int STATE_HEROBRINE_STALKING = 2;
    public static final int STATE_HEROBRINE_APPROACHING = 3;
    public static final int STATE_HEROBRINE_RETREATING = 4;
    public static final int STATE_FOG_RETURNING = 5;
    
    // Per-world event instances
    private static Map<World, HerobrineEventManager> instances = new HashMap<World, HerobrineEventManager>();
    
    // Event state
    private World world;
    private int currentState = STATE_INACTIVE;
    private long eventStartTime;
    private long stateStartTime;
    
    // Herobrine entity and target
    private EntityHerobrine herobrine;
    private EntityHuman primaryTarget;
    private boolean herobrineSpawnedThisEvent = false;
    
    // Fog control
    private float originalFogDistance;
    private float currentFogDistance;
    private float targetFogDistance;
    private float fogDistanceAtReturnStart = 0;
    
    // Players affected by this event (within render distance of Herobrine/event center)
    private Set<EntityHuman> affectedPlayers = new HashSet<EntityHuman>();
    
    // Constants
    private static final float MIN_FOG_DISTANCE = 20.0F;
    private static final float RETREAT_FOG_DISTANCE = 5.0F;
    private static final long FOG_CLOSE_DURATION = 30000;
    private static final long HEROBRINE_SPAWN_DELAY = 15000;
    private static final long FOG_RETURN_DURATION = 8000;
    private static final long RETREAT_FOG_CLOSE_DURATION = 2000;
    private static final long MAX_STALK_DURATION = 60000;
    private static final double CLOSE_DISTANCE = 8.0;
    private static final double DESPAWN_DISTANCE = 15.0;
    private static final double AFFECTED_RADIUS = 64.0; // Players within this distance are affected
    
    private HerobrineEventManager(World world) {
        this.world = world;
    }
    
    public static HerobrineEventManager getInstance(World world) {
        HerobrineEventManager manager = instances.get(world);
        if (manager == null) {
            manager = new HerobrineEventManager(world);
            instances.put(world, manager);
        }
        return manager;
    }
    
    /**
     * Start a Herobrine event triggered by a player (e.g., lighting a shrine)
     */
    public void startEvent(EntityHuman triggerPlayer) {
        System.out.println("[Herobrine] Event triggered by " + triggerPlayer.name);
        
        if (currentState != STATE_INACTIVE) {
            System.out.println("[Herobrine] Event already in progress - ignoring");
            return;
        }
        
        this.primaryTarget = triggerPlayer;
        this.originalFogDistance = 128.0F; // Default fog distance
        this.currentFogDistance = this.originalFogDistance;
        this.targetFogDistance = MIN_FOG_DISTANCE;
        
        this.eventStartTime = System.currentTimeMillis();
        this.stateStartTime = this.eventStartTime;
        this.currentState = STATE_FOG_CLOSING;
        this.herobrineSpawnedThisEvent = false;
        this.herobrine = null;
        
        // Play ambient sound for nearby players
        world.makeSound(triggerPlayer.locX, triggerPlayer.locY, triggerPlayer.locZ, 
            "ambient.cave.cave", 0.7F, 0.5F);
        
        // Initial fog update for nearby players
        updateAffectedPlayers();
        sendFogPacketToAffected();
    }
    
    /**
     * Called every server tick to update the event
     */
    public void update() {
        if (currentState == STATE_INACTIVE) return;
        
        try {
            // Validate target
            if (primaryTarget == null || primaryTarget.dead) {
                // Try to find a new target from affected players
                if (!pickNewTarget()) {
                    endEvent();
                    return;
                }
            }
            
            long currentTime = System.currentTimeMillis();
            long stateElapsed = currentTime - stateStartTime;
            long totalElapsed = currentTime - eventStartTime;
            
            // Timeout safety
            if (totalElapsed > 180000) {
                endEvent();
                return;
            }
            
            // Update affected players list
            updateAffectedPlayers();
            
            // State machine
            switch (currentState) {
                case STATE_FOG_CLOSING:
                    updateFogClosing(stateElapsed, totalElapsed);
                    break;
                case STATE_HEROBRINE_STALKING:
                    updateHerobrineStalking(stateElapsed);
                    break;
                case STATE_HEROBRINE_APPROACHING:
                    updateHerobrineApproaching(stateElapsed);
                    break;
                case STATE_HEROBRINE_RETREATING:
                    updateHerobrineRetreating(stateElapsed);
                    break;
                case STATE_FOG_RETURNING:
                    updateFogReturning(stateElapsed);
                    break;
                default:
                    endEvent();
                    break;
            }
            
            // Send fog updates to affected players
            sendFogPacketToAffected();
            
        } catch (Exception e) {
            System.out.println("[Herobrine] Error: " + e.getMessage());
            e.printStackTrace();
            forceReset();
        }
    }
    
    /**
     * Update the list of players affected by this event
     */
    private void updateAffectedPlayers() {
        affectedPlayers.clear();
        
        // Center point is either Herobrine's location or primary target's location
        double centerX, centerZ;
        if (herobrine != null && !herobrine.dead) {
            centerX = herobrine.locX;
            centerZ = herobrine.locZ;
        } else if (primaryTarget != null) {
            centerX = primaryTarget.locX;
            centerZ = primaryTarget.locZ;
        } else {
            return;
        }
        
        for (Object obj : world.players) {
            EntityHuman player = (EntityHuman) obj;
            if (player.dead) continue;
            
            double dx = player.locX - centerX;
            double dz = player.locZ - centerZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            
            if (dist <= AFFECTED_RADIUS) {
                affectedPlayers.add(player);
            }
        }
    }
    
    /**
     * Try to pick a new primary target from affected players
     */
    private boolean pickNewTarget() {
        if (affectedPlayers.isEmpty()) {
            updateAffectedPlayers();
        }
        
        for (EntityHuman player : affectedPlayers) {
            if (!player.dead) {
                primaryTarget = player;
                if (herobrine != null) {
                    herobrine.setTargetPlayer(player);
                }
                System.out.println("[Herobrine] New target: " + player.name);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Send fog distance packet to all affected players
     */
    private void sendFogPacketToAffected() {
        for (EntityHuman player : affectedPlayers) {
            if (player instanceof EntityPlayer) {
                EntityPlayer mp = (EntityPlayer) player;
                if (mp.netServerHandler != null) {
                    // Send custom fog packet
                    Packet250CustomPayload packet = createFogPacket(currentFogDistance);
                    mp.netServerHandler.sendPacket(packet);
                }
            }
        }
    }
    
    /**
     * Create a fog distance packet
     */
    private Packet250CustomPayload createFogPacket(float fogDistance) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);
        try {
            dos.writeFloat(fogDistance);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return new Packet250CustomPayload("HEROBRINE_FOG", baos.toByteArray());
    }
    
    private void updateFogClosing(long stateElapsed, long totalElapsed) {
        float progress = Math.min(1.0F, (float)stateElapsed / FOG_CLOSE_DURATION);
        this.currentFogDistance = lerp(originalFogDistance, targetFogDistance, easeInOut(progress));
        
        if (!herobrineSpawnedThisEvent && totalElapsed >= HEROBRINE_SPAWN_DELAY) {
            spawnHerobrine();
        }
        
        if (herobrineSpawnedThisEvent && progress >= 0.6F) {
            currentState = STATE_HEROBRINE_STALKING;
            stateStartTime = System.currentTimeMillis();
            System.out.println("[Herobrine] Now stalking " + primaryTarget.name);
        }
    }
    
    private void spawnHerobrine() {
        if (herobrineSpawnedThisEvent || primaryTarget == null) return;
        
        float yawRad = (float) Math.toRadians(primaryTarget.yaw + 180);
        // Spawn closer - at 15-20 blocks, just inside the target fog distance
        // This ensures he's visible when fog is at its closest
        float spawnDist = Math.min(currentFogDistance * 0.60F, 25.0F);
        
        System.out.println("[Herobrine] Attempting spawn at distance " + spawnDist + " (fog at " + currentFogDistance + ")");
        
        // Try multiple spawn locations
        double spawnX = 0, spawnZ = 0;
        int groundY = -1;
        
        for (int attempt = 0; attempt < 8; attempt++) {
            float offsetAngle = yawRad + (float)(attempt * 0.2 - 0.7);
            float offsetDist = spawnDist + (attempt * 2);
            
            double testX = primaryTarget.locX - Math.sin(offsetAngle) * offsetDist;
            double testZ = primaryTarget.locZ + Math.cos(offsetAngle) * offsetDist;
            int testY = findSolidGround((int)testX, (int)testZ);
            
            if (testY > 0 && Math.abs(testY - primaryTarget.locY) < 10) {
                spawnX = testX;
                spawnZ = testZ;
                groundY = testY;
                System.out.println("[Herobrine] Found valid spawn location on attempt " + attempt);
                break;
            }
        }
        
        if (groundY < 0) {
            spawnX = primaryTarget.locX - Math.sin(yawRad) * spawnDist;
            spawnZ = primaryTarget.locZ + Math.cos(yawRad) * spawnDist;
            groundY = findSolidGround((int)spawnX, (int)spawnZ);
            if (groundY < 0) {
                groundY = (int)primaryTarget.locY;
                System.out.println("[Herobrine] Using fallback Y position");
            }
        }
        
        herobrine = new EntityHerobrine(world);
        herobrine.setPositionRotation(spawnX, groundY, spawnZ, primaryTarget.yaw, 0);
        herobrine.setAIState(EntityHerobrine.AI_STALKING);
        herobrine.setTargetPlayer(primaryTarget);
        herobrine.setCurrentFogDistance(currentFogDistance);
        herobrine.setTargetDistance(Math.min(currentFogDistance * 0.60F, 20.0F));
        
        // Add to world
        boolean added = world.addEntity(herobrine);
        System.out.println("[Herobrine] Entity add result: " + added + ", entityId: " + herobrine.id);
        
        herobrineSpawnedThisEvent = true;
        System.out.println("[Herobrine] Spawned at " + (int)spawnX + ", " + groundY + ", " + (int)spawnZ + 
            " (player at " + (int)primaryTarget.locX + ", " + (int)primaryTarget.locY + ", " + (int)primaryTarget.locZ + ")");
    }
    
    private int findSolidGround(int x, int z) {
        int topY = world.getHighestBlockYAt(x, z);
        
        for (int y = topY; y > 1; y--) {
            int blockId = world.getTypeId(x, y, z);
            int blockAbove = world.getTypeId(x, y + 1, z);
            int blockAbove2 = world.getTypeId(x, y + 2, z);
            
            if (blockId == 0) continue;
            
            Block block = Block.byId[blockId];
            if (block == null) continue;
            
            Material mat = block.material;
            
            // Skip non-solid
            if (mat == Material.LEAVES || mat == Material.PLANT || mat == Material.WATER ||
                mat == Material.LAVA || mat == Material.SNOW_LAYER || mat == Material.CACTUS || 
                mat == Material.FIRE) {
                continue;
            }
            
            if (!mat.isBuildable()) continue;
            
            // Check clearance
            boolean clearAbove1 = blockAbove == 0 || (Block.byId[blockAbove] != null && !Block.byId[blockAbove].material.isBuildable());
            boolean clearAbove2 = blockAbove2 == 0 || (Block.byId[blockAbove2] != null && !Block.byId[blockAbove2].material.isBuildable());
            
            if (clearAbove1 && clearAbove2) {
                return y + 1;
            }
        }
        return -1;
    }
    
    private void updateHerobrineStalking(long stateElapsed) {
        long totalElapsed = System.currentTimeMillis() - eventStartTime;
        float fogProgress = Math.min(1.0F, (float)totalElapsed / FOG_CLOSE_DURATION);
        this.currentFogDistance = lerp(originalFogDistance, targetFogDistance, easeInOut(fogProgress));
        
        if (herobrine == null || herobrine.dead) {
            startFogReturn();
            return;
        }
        
        // Update Herobrine's target and fog distance
        herobrine.setTargetPlayer(primaryTarget);
        herobrine.setCurrentFogDistance(currentFogDistance);
        herobrine.setTargetDistance(currentFogDistance * 0.70F);
        
        // Check if close to any player
        double dx = herobrine.locX - primaryTarget.locX;
        double dz = herobrine.locZ - primaryTarget.locZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        if (distance < CLOSE_DISTANCE || herobrine.getAIState() == EntityHerobrine.AI_RETREATING) {
            System.out.println("[Herobrine] Too close! Retreating...");
            startRetreating();
        }
        
        if (stateElapsed > MAX_STALK_DURATION) {
            startRetreating();
        }
    }
    
    private void startRetreating() {
        if (herobrine == null) {
            startFogReturn();
            return;
        }
        
        double dx = herobrine.locX - primaryTarget.locX;
        double dz = herobrine.locZ - primaryTarget.locZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        float visibleDistance = currentFogDistance * 0.60F;
        
        if (distance > visibleDistance) {
            // Walk into visible area first
            System.out.println("[Herobrine] Walking into visible area first...");
            currentState = STATE_HEROBRINE_APPROACHING;
            stateStartTime = System.currentTimeMillis();
            herobrine.setAIState(EntityHerobrine.AI_APPROACHING);
        } else {
            currentState = STATE_HEROBRINE_RETREATING;
            stateStartTime = System.currentTimeMillis();
            herobrine.setAIState(EntityHerobrine.AI_RETREATING);
            System.out.println("[Herobrine] Retreating into the fog...");
        }
    }
    
    private void updateHerobrineApproaching(long stateElapsed) {
        if (herobrine == null || herobrine.dead) {
            startFogReturn();
            return;
        }
        
        herobrine.setTargetPlayer(primaryTarget);
        herobrine.setCurrentFogDistance(currentFogDistance);
        
        double dx = herobrine.locX - primaryTarget.locX;
        double dz = herobrine.locZ - primaryTarget.locZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        float targetVisibleDistance = currentFogDistance * 0.50F;
        
        if (distance <= targetVisibleDistance) {
            System.out.println("[Herobrine] Now visible. Retreating...");
            currentState = STATE_HEROBRINE_RETREATING;
            stateStartTime = System.currentTimeMillis();
            herobrine.setAIState(EntityHerobrine.AI_RETREATING);
        }
        
        if (stateElapsed > 5000) {
            currentState = STATE_HEROBRINE_RETREATING;
            stateStartTime = System.currentTimeMillis();
            herobrine.setAIState(EntityHerobrine.AI_RETREATING);
        }
    }
    
    private void updateHerobrineRetreating(long stateElapsed) {
        if (herobrine == null || herobrine.dead) {
            startFogReturn();
            return;
        }
        
        // Dramatic fog close-in
        float fogCloseProgress = Math.min(1.0F, (float)stateElapsed / RETREAT_FOG_CLOSE_DURATION);
        float startFog = MIN_FOG_DISTANCE;
        this.currentFogDistance = lerp(startFog, RETREAT_FOG_DISTANCE, easeInOut(fogCloseProgress));
        
        herobrine.setTargetPlayer(primaryTarget);
        herobrine.setCurrentFogDistance(currentFogDistance);
        
        // Check if wants to despawn
        if (herobrine.shouldDespawn()) {
            System.out.println("[Herobrine] Vanished mysteriously!");
            herobrine.die();
            herobrine = null;
            startFogReturn();
            return;
        }
        
        double dx = herobrine.locX - primaryTarget.locX;
        double dz = herobrine.locZ - primaryTarget.locZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        // Despawn when far enough
        if (fogCloseProgress >= 1.0F && distance > RETREAT_FOG_DISTANCE + 2.0) {
            System.out.println("[Herobrine] Vanished into the fog...");
            herobrine.die();
            herobrine = null;
            startFogReturn();
            return;
        }
        
        // Out of sight despawn
        if (!herobrine.isVisibleToAnyPlayer() && stateElapsed > 1500) {
            System.out.println("[Herobrine] Vanished when no one was looking...");
            herobrine.die();
            herobrine = null;
            startFogReturn();
            return;
        }
        
        if (distance > currentFogDistance + DESPAWN_DISTANCE) {
            System.out.println("[Herobrine] Vanished into the fog at distance: " + distance);
            herobrine.die();
            herobrine = null;
            startFogReturn();
            return;
        }
        
        // Timeout - try escape
        if (stateElapsed > 6000 && herobrine.getAIState() != EntityHerobrine.AI_ESCAPING) {
            System.out.println("[Herobrine] Retreat taking too long - trying to escape...");
            herobrine.startEscaping();
        }
        
        // Ultimate timeout
        if (stateElapsed > 10000) {
            System.out.println("[Herobrine] Timeout - swallowed by the fog...");
            herobrine.die();
            herobrine = null;
            startFogReturn();
        }
    }
    
    private void startFogReturn() {
        currentState = STATE_FOG_RETURNING;
        stateStartTime = System.currentTimeMillis();
        fogDistanceAtReturnStart = currentFogDistance;
        System.out.println("[Herobrine] The fog lifts...");
        
        // Thunder sound
        if (primaryTarget != null) {
            world.makeSound(primaryTarget.locX, primaryTarget.locY, primaryTarget.locZ, 
                "ambient.weather.thunder", 1.0F, 0.8F);
        }
    }
    
    private void updateFogReturning(long stateElapsed) {
        float progress = Math.min(1.0F, (float)stateElapsed / FOG_RETURN_DURATION);
        this.currentFogDistance = lerp(fogDistanceAtReturnStart, originalFogDistance, easeInOut(progress));
        
        if (stateElapsed >= FOG_RETURN_DURATION) {
            endEvent();
        }
    }
    
    private void endEvent() {
        currentState = STATE_INACTIVE;
        currentFogDistance = originalFogDistance;
        
        // Clear fog for affected players
        for (EntityHuman player : affectedPlayers) {
            if (player instanceof EntityPlayer) {
                EntityPlayer mp = (EntityPlayer) player;
                if (mp.netServerHandler != null) {
                    Packet250CustomPayload packet = createFogPacket(-1.0F); // -1 = no override
                    mp.netServerHandler.sendPacket(packet);
                }
            }
        }
        
        if (herobrine != null && !herobrine.dead) {
            herobrine.die();
        }
        herobrine = null;
        herobrineSpawnedThisEvent = false;
        primaryTarget = null;
        affectedPlayers.clear();
        
        System.out.println("[Herobrine] Event ended.");
    }
    
    public void forceReset() {
        if (herobrine != null && !herobrine.dead) {
            herobrine.die();
        }
        herobrine = null;
        herobrineSpawnedThisEvent = false;
        currentState = STATE_INACTIVE;
        affectedPlayers.clear();
    }
    
    public boolean isActive() {
        return currentState != STATE_INACTIVE;
    }
    
    public int getCurrentState() {
        return currentState;
    }
    
    public float getCurrentFogDistance() {
        return currentFogDistance;
    }
    
    // Utility
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
    
    private static float easeInOut(float t) {
        return t < 0.5F ? 2 * t * t : 1 - (float)Math.pow(-2 * t + 2, 2) / 2;
    }
    
    /**
     * Called when a shrine is activated (netherrack lit on fire)
     */
    public static void onShrineActivated(World world, EntityHuman player, int x, int y, int z) {
        // Check if this is a valid shrine
        if (!WorldGenHerobrineShrine.isHerobrineShrine(world, x, y, z)) {
            return;
        }
        
        // Check if already activated
        if (WorldGenHerobrineShrine.isShrineActivated(x, y, z)) {
            return;
        }
        
        // Mark as activated
        WorldGenHerobrineShrine.activateShrine(x, y, z);
        
        // Start the event
        HerobrineEventManager manager = getInstance(world);
        manager.startEvent(player);
    }
}

