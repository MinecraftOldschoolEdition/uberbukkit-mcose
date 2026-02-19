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
    private int spawnSearchFailures = 0;
    
    // Fog control
    private float originalFogDistance;
    private float currentFogDistance;
    private float targetFogDistance;
    private float fogDistanceAtReturnStart = 0;
    
    // Players affected by this event (within render distance of Herobrine/event center)
    private Set<EntityHuman> affectedPlayers = new HashSet<EntityHuman>();
    
    // Sound and timeout tracking
    private long lastAmbientSoundTime = 0;
    private long lastPlayerApproachTime = 0;
    private double lastPlayerDistance = Double.MAX_VALUE;
    private Random random = new Random();
    
    // Constants - should match client-side HerobrineEvent
    private static final float MIN_FOG_DISTANCE = 20.0F;
    private static final float RETREAT_FOG_DISTANCE = 8.0F;
    private static final long FOG_CLOSE_DURATION = 20000;    // 20 seconds to close fog
    private static final long HEROBRINE_SPAWN_DELAY = 10000; // Spawn halfway through fog closing
    private static final long FOG_RETURN_DURATION = 8000;
    private static final long RETREAT_FOG_CLOSE_DURATION = 2000;
    private static final long MAX_STALK_DURATION = 25000;    // 25 seconds of stalking
    private static final long APPROACH_TIMEOUT = 45000;      // End event if player doesn't approach within 45 seconds
    private static final long AMBIENT_SOUND_INTERVAL = 8000; // Play ambient sound every 8 seconds
    private static final double CLOSE_DISTANCE = 6.0;
    private static final double DESPAWN_DISTANCE = 15.0;
    private static final double AFFECTED_RADIUS = 64.0;      // Players within this distance are affected
    private static final float STALK_DISTANCE_RATIO = 0.78F; // Herobrine stalks at fog-start edge
    private static final int MAX_SPAWN_SEARCH_FAILURES = 12;
    
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
        
        if (currentState != STATE_INACTIVE) {
            return;
        }
        
        this.primaryTarget = triggerPlayer;
        // Calculate fog distance based on typical settings (server-side)
        // The client will receive HEROBRINE_FOG packets and override its local fog
        this.originalFogDistance = calculateDefaultFogDistance();
        this.currentFogDistance = this.originalFogDistance;
        this.targetFogDistance = Math.max(MIN_FOG_DISTANCE, originalFogDistance * 0.25F);
        
        
        this.eventStartTime = System.currentTimeMillis();
        this.stateStartTime = this.eventStartTime;
        this.currentState = STATE_FOG_CLOSING;
        this.herobrineSpawnedThisEvent = false;
        this.spawnSearchFailures = 0;
        this.herobrine = null;
        this.lastPlayerApproachTime = this.eventStartTime;
        this.lastPlayerDistance = Double.MAX_VALUE;
        this.lastAmbientSoundTime = 0;
        
        // Play initial creepy ambient sound for nearby players
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

            if (currentState != STATE_FOG_RETURNING) {
                handleHerobrineDamaged();
                stateElapsed = System.currentTimeMillis() - stateStartTime;
            }
            
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
            e.printStackTrace();
            forceReset();
        }
    }

    private void handleHerobrineDamaged() {
        if (herobrine == null || herobrine.dead) {
            return;
        }
        if (!herobrine.consumeDamagedFlag()) {
            return;
        }
        startRetreating();
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
        // Use slow ease-in for gradual fog onset
        this.currentFogDistance = lerp(originalFogDistance, targetFogDistance, slowEaseIn(progress));
        
        if (!herobrineSpawnedThisEvent && totalElapsed >= HEROBRINE_SPAWN_DELAY) {
            spawnHerobrine();
        }
        
        if (herobrineSpawnedThisEvent && progress >= 0.6F) {
            currentState = STATE_HEROBRINE_STALKING;
            stateStartTime = System.currentTimeMillis();
        }
    }
    
    private void spawnHerobrine() {
        if (herobrineSpawnedThisEvent || primaryTarget == null) return;
        
        float yawRad = (float) Math.toRadians(primaryTarget.yaw + 180);
        // Spawn at stalking distance - within visible range of fog
        float spawnDist = getStalkDistance();
        
        
        // Try multiple spawn locations
        double spawnX = 0, spawnZ = 0;
        int groundY = -1;
        boolean visibleSpawn = false;
        
        float[] angleOffsets = {0, 0.3f, -0.3f, 0.6f, -0.6f, 0.9f, -0.9f, 1.2f, -1.2f,
                                (float)Math.PI / 2, -(float)Math.PI / 2,
                                (float)Math.PI * 0.75f, -(float)Math.PI * 0.75f};

        for (float angleOffset : angleOffsets) {
            for (int distOffset = 0; distOffset < 4; distOffset++) {
                float testAngle = yawRad + angleOffset;
                float testDist = spawnDist + (distOffset * 3);

                double testX = primaryTarget.locX - Math.sin(testAngle) * testDist;
                double testZ = primaryTarget.locZ + Math.cos(testAngle) * testDist;
                int testY = findSolidGround((int) testX, (int) testZ);

                if (testY > 0 && Math.abs(testY - primaryTarget.locY) < 10) {
                    if (hasLineOfSightToPlayer(testX, testY + 1.0, testZ, primaryTarget)) {
                        spawnX = testX;
                        spawnZ = testZ;
                        groundY = testY;
                        visibleSpawn = true;
                        break;
                    }
                }
            }
            if (groundY > 0) {
                break;
            }
        }

        if (groundY < 0) {
            spawnSearchFailures++;

            if (spawnSearchFailures <= MAX_SPAWN_SEARCH_FAILURES) {
                return;
            }

            float[] fallbackAngles = {0, 0.35f, -0.35f, 0.75f, -0.75f, 1.15f, -1.15f,
                                      (float)Math.PI / 2, -(float)Math.PI / 2,
                                      (float)Math.PI * 0.75f, -(float)Math.PI * 0.75f, (float)Math.PI};

            for (float angleOffset : fallbackAngles) {
                for (int distOffset = -2; distOffset < 4; distOffset++) {
                    float testAngle = yawRad + angleOffset;
                    float testDist = Math.max(6.0F, spawnDist + (distOffset * 2.0F));

                    double testX = primaryTarget.locX - Math.sin(testAngle) * testDist;
                    double testZ = primaryTarget.locZ + Math.cos(testAngle) * testDist;
                    int testY = findSolidGround((int) testX, (int) testZ);

                    if (testY > 0 && Math.abs(testY - primaryTarget.locY) < 14) {
                        spawnX = testX;
                        spawnZ = testZ;
                        groundY = testY;
                        break;
                    }
                }
                if (groundY > 0) {
                    break;
                }
            }

            if (groundY < 0) {
                return;
            }
        }
        
        herobrine = new EntityHerobrine(world);
        herobrine.setPositionRotation(spawnX, groundY, spawnZ, primaryTarget.yaw, 0);
        herobrine.setAIState(EntityHerobrine.AI_STALKING);
        herobrine.setTargetPlayer(primaryTarget);
        herobrine.setCurrentFogDistance(currentFogDistance);
        herobrine.setTargetDistance(getStalkDistance());

        if (!visibleSpawn) {
            herobrine.setAIState(EntityHerobrine.AI_APPROACHING);
            herobrine.setTargetDistance(Math.max(4.0F, getStalkDistance() - 4.0F));
        }
        
        // Add to world
        boolean added = world.addEntity(herobrine);
        
        herobrineSpawnedThisEvent = true;
        spawnSearchFailures = 0;
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
        long currentTime = System.currentTimeMillis();
        float fogProgress = Math.min(1.0F, (float)totalElapsed / FOG_CLOSE_DURATION);
        // Use slow ease-in for gradual fog during stalking
        this.currentFogDistance = lerp(originalFogDistance, targetFogDistance, slowEaseIn(fogProgress));
        
        if (herobrine == null || herobrine.dead) {
            startFogReturn();
            return;
        }
        
        // Update Herobrine's target and fog distance
        herobrine.setTargetPlayer(primaryTarget);
        herobrine.setCurrentFogDistance(currentFogDistance);
        herobrine.setTargetDistance(getStalkDistance());
        
        // Check if close to any player
        double dx = herobrine.locX - primaryTarget.locX;
        double dz = herobrine.locZ - primaryTarget.locZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        // Play creepy ambient sounds periodically
        if (currentTime - lastAmbientSoundTime >= AMBIENT_SOUND_INTERVAL) {
            playCreepyAmbientSound();
            lastAmbientSoundTime = currentTime;
        }
        
        // Track if player is approaching
        if (distance < lastPlayerDistance - 1.0) {
            lastPlayerApproachTime = currentTime;
        }
        lastPlayerDistance = distance;
        
        // Timeout if player isn't approaching
        if (lastPlayerApproachTime > 0 && currentTime - lastPlayerApproachTime > APPROACH_TIMEOUT) {
            if (herobrine != null) {
                herobrine.setShouldDespawn(true);
            }
            startFogReturn();
            return;
        }
        
        if (distance < CLOSE_DISTANCE || herobrine.getAIState() == EntityHerobrine.AI_RETREATING) {
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
        
        float visibleDistance = getStalkDistance();
        
        if (distance > visibleDistance) {
            // Walk into visible area first
            currentState = STATE_HEROBRINE_APPROACHING;
            stateStartTime = System.currentTimeMillis();
            herobrine.setAIState(EntityHerobrine.AI_APPROACHING);
        } else {
            currentState = STATE_HEROBRINE_RETREATING;
            stateStartTime = System.currentTimeMillis();
            herobrine.setAIState(EntityHerobrine.AI_RETREATING);
        }
    }
    
    /**
     * Play creepy ambient sounds for all affected players
     */
    private void playCreepyAmbientSound() {
        String[] creepySounds = {
            "ambient.cave.cave",
            "mob.endermen.stare", 
            "mob.ghast.moan"
        };
        
        String sound = creepySounds[random.nextInt(creepySounds.length)];
        float pitch = 0.4F + random.nextFloat() * 0.3F;
        float volume = 0.6F + random.nextFloat() * 0.3F;
        
        double soundX, soundY, soundZ;
        if (herobrine != null && !herobrine.dead) {
            soundX = herobrine.locX;
            soundY = herobrine.locY;
            soundZ = herobrine.locZ;
        } else if (primaryTarget != null) {
            soundX = primaryTarget.locX;
            soundY = primaryTarget.locY;
            soundZ = primaryTarget.locZ;
        } else {
            return;
        }
        
        // Use world.makeSound which broadcasts to all players in range
        world.makeSound(soundX, soundY, soundZ, sound, volume, pitch);
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
        
        float targetVisibleDistance = getStalkDistance();
        
        if (distance <= targetVisibleDistance) {
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
            herobrine.die();
            herobrine = null;
            startFogReturn();
            return;
        }
        
        // Out of sight despawn
        if (!herobrine.isVisibleToAnyPlayer() && stateElapsed > 1500) {
            herobrine.die();
            herobrine = null;
            startFogReturn();
            return;
        }
        
        if (distance > currentFogDistance + DESPAWN_DISTANCE) {
            herobrine.die();
            herobrine = null;
            startFogReturn();
            return;
        }
        
        // Timeout - try escape
        if (stateElapsed > 6000 && herobrine.getAIState() != EntityHerobrine.AI_ESCAPING) {
            herobrine.startEscaping();
        }
        
        // Ultimate timeout
        if (stateElapsed > 10000) {
            herobrine.die();
            herobrine = null;
            startFogReturn();
        }
    }
    
    private void startFogReturn() {
        currentState = STATE_FOG_RETURNING;
        stateStartTime = System.currentTimeMillis();
        fogDistanceAtReturnStart = currentFogDistance;
        
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
        spawnSearchFailures = 0;
        primaryTarget = null;
        affectedPlayers.clear();
        
    }
    
    public void forceReset() {
        if (herobrine != null && !herobrine.dead) {
            herobrine.die();
        }
        herobrine = null;
        herobrineSpawnedThisEvent = false;
        spawnSearchFailures = 0;
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

    private float getStalkDistance() {
        return Math.max(6.0F, currentFogDistance * STALK_DISTANCE_RATIO);
    }

    private boolean hasLineOfSightToPlayer(double x, double y, double z, EntityHuman player) {
        double targetEyeY = player.locY + player.t();

        double dx = player.locX - x;
        double dy = targetEyeY - y;
        double dz = player.locZ - z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance < 1.0D) {
            return true;
        }

        dx /= distance;
        dy /= distance;
        dz /= distance;

        for (double d = 0.5D; d < distance - 0.5D; d += 0.5D) {
            int blockX = MathHelper.floor(x + dx * d);
            int blockY = MathHelper.floor(y + dy * d);
            int blockZ = MathHelper.floor(z + dz * d);

            int blockId = this.world.getTypeId(blockX, blockY, blockZ);
            if (blockId == 0) {
                continue;
            }

            Block block = Block.byId[blockId];
            if (block == null) {
                continue;
            }

            Material mat = block.material;
            if (mat == Material.LEAVES || mat == Material.PLANT || mat == Material.WATER ||
                mat == Material.LAVA || mat == Material.SNOW_LAYER || mat == Material.ORIENTABLE ||
                mat == Material.FIRE) {
                continue;
            }

            if (mat.isBuildable()) {
                return false;
            }
        }

        return true;
    }
    
    private static float easeInOut(float t) {
        return t < 0.5F ? 2 * t * t : 1 - (float)Math.pow(-2 * t + 2, 2) / 2;
    }
    
    /**
     * Slow ease-in function (cubic) - very gradual at start, accelerates at end.
     * Makes fog onset feel atmospheric rather than abrupt.
     */
    private static float slowEaseIn(float t) {
        return t * t * t;
    }
    
    /**
     * Calculate a default fog distance for server-side events.
     * Uses Normal render distance with vanilla fog strength (1.0).
     * Formula: farPlaneDistance * 0.8 = 128 * 0.8 = 102.4 blocks
     */
    private float calculateDefaultFogDistance() {
        float farPlaneDistance = 128.0F; // Normal render distance
        return farPlaneDistance * 0.8F;  // Vanilla fog end = 80% of far plane
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

