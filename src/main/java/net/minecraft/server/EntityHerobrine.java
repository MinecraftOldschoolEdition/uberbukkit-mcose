package net.minecraft.server;

import java.util.List;
import java.util.ArrayList;

/**
 * Herobrine - the legendary mysterious entity.
 * 
 * Server-side implementation with multi-player awareness:
 * - Focuses on one target player at a time
 * - Retreats from ANY player who gets too close
 * - Uses intelligent pathfinding with obstacle avoidance
 */
public class EntityHerobrine extends EntityLiving {
    
    // AI States
    public static final int AI_IDLE = 0;
    public static final int AI_STALKING = 1;
    public static final int AI_APPROACHING = 2;
    public static final int AI_RETREATING = 3;
    public static final int AI_ESCAPING = 4;
    
    private int aiState = AI_IDLE;
    private EntityHuman targetPlayer;
    private float targetDistance = 25.0F;
    private float moveSpeed = 0.216F; // Match player walking speed
    private float retreatSpeed = 0.281F; // Player sprint-like speed
    
    // Fog and event tracking
    private float currentFogDistance = 50.0F;
    private boolean shouldDespawn = false;
    
    // Pathfinding
    private int pathfindCooldown = 0;
    private double lastPathX, lastPathZ;
    private int stuckTicks = 0;
    private int embeddedTicks = 0;
    private int consecutiveBlockedTicks = 0;
    private int totallyStuckTicks = 0;
    private static final int STUCK_THRESHOLD_FOR_ESCAPE = 30;
    private PathEntity navigationPath;
    private int navigationRepathCooldown = 0;
    private double navTargetX = Double.NaN;
    private double navTargetY = Double.NaN;
    private double navTargetZ = Double.NaN;
    private double smoothedNavMoveX = 0.0D;
    private double smoothedNavMoveZ = 0.0D;
    private static final int NAVIGATION_REPATH_TICKS = 8;
    private static final float NAVIGATION_RANGE = 28.0F;
    private static final double NAVIGATION_SMOOTHING = 0.35D;
    
    // Stalking behavior constants - should match client and HerobrineEventManager
    private static final float STALK_DISTANCE_RATIO = 0.78F;
    private static final double STALK_DISTANCE_TOLERANCE = 0.35D;
    private static final double STALK_FAST_CATCHUP_DISTANCE = 3.0D;
    private double lastStalkPlayerX = 0.0D;
    private double lastStalkPlayerZ = 0.0D;
    private double lastStalkDistance = 0.0D;
    private boolean hasStalkSample = false;

    // Damage trigger tracking (consumed by HerobrineEventManager)
    private boolean damagedByPlayerThisTick = false;
    
    // Escape behavior
    private int escapeAttemptTicks = 0;
    
    // Jump state
    private boolean isJumping = false;
    private int jumpTicks = 0;
    private static final double JUMP_VELOCITY = 0.5; // Slightly higher than player for reliability
    private static final double GRAVITY = 0.08;
    private static final double AIR_FRICTION = 0.91; // Horizontal movement friction in air
    private double jumpTargetX, jumpTargetZ; // Where we're trying to jump to
    
    // Retreat trigger tracking - retreat from ANY player who gets close
    private EntityHuman nearestThreat = null;
    private double nearestThreatDistance = Double.MAX_VALUE;
    
    // Path types for intelligent routing
    private enum PathType {
        CLEAR,      // Can walk directly
        STEP_UP,    // Need to jump up 1 block
        STEP_DOWN,  // Need to drop down 1-3 blocks
        GAP,        // Gap in ground that needs jumping over
        WALL,       // Solid wall blocking path
        CLIFF       // Dangerous drop (too high)
    }
    
    // Path analysis result
    private static class PathAnalysis {
        PathType type;
        int gapSize = 0;
        int landingY = 0;
        
        PathAnalysis(PathType type) {
            this.type = type;
        }
    }
    
    public EntityHerobrine(World world) {
        super(world);
        this.texture = "/mob/char.png"; // Uses player model
        this.health = 100;
        this.length = 1.8F; // Same height as player
        this.width = 0.6F;
        this.bs = 1.0F; // stepHeight - can auto-step up 1 block like players
    }
    
    protected void b() {
        super.b();
    }
    
    @Override
    protected void c_() { // onLivingUpdate - called by EntityLiving.v()
        // Don't call super.c_() - it resets our rotation and does unwanted AI
        // Our AI is in m_() instead
    }
    
    // Debug tick counter
    private int debugTickCounter = 0;
    
    // Override the main entity tick - this is where ALL our AI runs
    @Override
    public void m_() {
        super.m_();
        
        // Keep from despawning
        this.ay = 0; // entityAge
        this.aF = 0; // Prevent frozen state
        
        // Debug tick counter (used for periodic logging if needed)
        debugTickCounter++;
        if (debugTickCounter >= 40) {
            debugTickCounter = 0;
        }
        
        // Pathfinding cooldown
        if (pathfindCooldown > 0) pathfindCooldown--;
        if (navigationRepathCooldown > 0) navigationRepathCooldown--;

        // Never stay embedded in blocks (trees/terrain); recover if persistent
        if (isEmbeddedInSolidBlock()) {
            embeddedTicks++;
            if (embeddedTicks > 5) {
                escapeFromEmbeddedBlock();
                embeddedTicks = 0;
            }
        } else {
            embeddedTicks = 0;
        }
        
        // Handle jumping - improved vertical movement
        if (isJumping) {
            jumpTicks++;
            
            // While airborne, maintain horizontal momentum towards target
            if (!this.onGround && jumpTicks > 1) {
                double dx = jumpTargetX - this.locX;
                double dz = jumpTargetZ - this.locZ;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0.1) {
                    // Apply gentle air control towards target
                    this.motX = (dx / dist) * moveSpeed * 0.8;
                    this.motZ = (dz / dist) * moveSpeed * 0.8;
                }
            }
            
            // Land detection - must be on ground AND have fallen (motY was negative)
            if (this.onGround && jumpTicks > 5) {
                isJumping = false;
                jumpTicks = 0;
            } else if (jumpTicks > 60) {
                // Timeout - took too long, reset jump state
                isJumping = false;
                jumpTicks = 0;
            }
        }
        
        // NOTE: Retreat is triggered by HerobrineEventManager, NOT here!
        // The entity just executes the state it's given.
        
        // AI behavior based on state
        if (targetPlayer != null && !targetPlayer.dead) {
            switch (aiState) {
                case AI_STALKING:
                    doStalkingBehavior();
                    break;
                case AI_APPROACHING:
                    doApproachingBehavior();
                    break;
                case AI_RETREATING:
                    doRetreatingBehavior();
                    break;
                case AI_ESCAPING:
                    doEscapingBehavior();
                    break;
                case AI_IDLE:
                default:
                    if (targetPlayer != null) {
                        faceEntity(targetPlayer);
                    }
                    clearNavigationPath();
                    haltHorizontalMotion();
                    break;
            }
        } else if (aiState == AI_IDLE) {
            // Even with no target, look around for players
            EntityHuman nearbyPlayer = this.world.findNearbyPlayer(this, 64.0D);
            if (nearbyPlayer != null) {
                faceEntity(nearbyPlayer);
            }
            clearNavigationPath();
            haltHorizontalMotion();
        }
        
        // Force update head rotation (server sends this to clients)
        this.aA = this.yaw; // headYaw = bodyYaw
        this.aB = this.yaw; // prevHeadYaw
    }
    
    /**
     * Check for any player who gets too close - retreat from the nearest one
     */
    private void checkForThreats() {
        nearestThreat = null;
        nearestThreatDistance = Double.MAX_VALUE;
        
        double closeDistance = 10.0; // Retreat if ANY player gets this close
        
        List<EntityHuman> players = this.world.players;
        for (Object obj : players) {
            EntityHuman player = (EntityHuman) obj;
            if (player.dead) continue;
            
            double dx = this.locX - player.locX;
            double dz = this.locZ - player.locZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            
            if (dist < closeDistance && dist < nearestThreatDistance) {
                nearestThreat = player;
                nearestThreatDistance = dist;
            }
        }
        
        // If any player is too close and we're not already retreating, start retreating
        if (nearestThreat != null && aiState != AI_RETREATING && aiState != AI_ESCAPING) {
            aiState = AI_RETREATING;
        }
    }
    
    // === AI BEHAVIORS ===
    
    private void doStalkingBehavior() {
        if (targetPlayer == null) return;
        
        // Keep externally configured target distance; only use fallback when unset
        if (this.targetDistance <= 0.0F) {
            this.targetDistance = currentFogDistance * STALK_DISTANCE_RATIO;
        }
        
        // Always face the player
        faceEntitySmooth(targetPlayer);
        
        // Ensure we're on solid ground
        snapToGround();
        
        // Check if we're currently visible to the player - if not, try to reposition
        if (!isVisibleToPlayer(targetPlayer)) {
            tryMoveToVisiblePosition(targetPlayer);
            if (!isVisibleToPlayer(targetPlayer)) {
                forceVisibleStalkingPosition(targetPlayer);
            }
        }
        
        // Calculate current distance to player (EXACTLY like client)
        double dx = this.locX - targetPlayer.locX;
        double dz = this.locZ - targetPlayer.locZ;
        double currentDist = Math.sqrt(dx * dx + dz * dz);
        double distanceError = currentDist - targetDistance;

        if (!hasStalkSample) {
            lastStalkPlayerX = targetPlayer.locX;
            lastStalkPlayerZ = targetPlayer.locZ;
            lastStalkDistance = currentDist;
            hasStalkSample = true;
            clearNavigationPath();
            haltHorizontalMotion();
            return;
        }

        double playerMoveX = targetPlayer.locX - lastStalkPlayerX;
        double playerMoveZ = targetPlayer.locZ - lastStalkPlayerZ;
        double playerMoveDist = Math.sqrt(playerMoveX * playerMoveX + playerMoveZ * playerMoveZ);
        boolean playerWalkedAway = currentDist > lastStalkDistance + 0.05D && playerMoveDist > 0.02D;

        if (!playerWalkedAway) {
            lastStalkPlayerX = targetPlayer.locX;
            lastStalkPlayerZ = targetPlayer.locZ;
            lastStalkDistance = currentDist;
            clearNavigationPath();
            haltHorizontalMotion();
            return;
        }
        
        
        // Maintain a tight distance band while stalking
        if (distanceError <= STALK_DISTANCE_TOLERANCE) {
            lastStalkPlayerX = targetPlayer.locX;
            lastStalkPlayerZ = targetPlayer.locZ;
            lastStalkDistance = currentDist;
            clearNavigationPath();
            haltHorizontalMotion();
            return; // Stand still and stare
        }
        
        // Follow player - EXACTLY like client code
        double toPlayerDist = Math.max(0.1, currentDist);
        double dirX = -dx / toPlayerDist;  // Direction FROM herobrine TO player (normalized)
        double dirZ = -dz / toPlayerDist;
        
        // Ideal position = player position MINUS (direction to player * targetDistance)
        // This places ideal behind the player from herobrine's perspective, at targetDistance
        double idealX = targetPlayer.locX - dirX * targetDistance;
        double idealZ = targetPlayer.locZ - dirZ * targetDistance;
        
        // Movement vector towards ideal position
        double toIdealX = idealX - this.locX;
        double toIdealZ = idealZ - this.locZ;
        double toIdealDist = Math.sqrt(toIdealX * toIdealX + toIdealZ * toIdealZ);
        
        
        if (toIdealDist > 0.5) {
            float followSpeed = distanceError > STALK_FAST_CATCHUP_DISTANCE ? retreatSpeed : moveSpeed;
            navigateTowards(idealX, this.locY, idealZ, followSpeed);
        } else {
            clearNavigationPath();
            haltHorizontalMotion();
        }

        lastStalkPlayerX = targetPlayer.locX;
        lastStalkPlayerZ = targetPlayer.locZ;
        lastStalkDistance = currentDist;
    }
    
    /**
     * Try to reposition for visibility - EXACTLY like client.
     * Only moves to positions that maintain correct distance from player.
     * Uses very slow movement (0.1 multiplier).
     */
    private void tryMoveToVisiblePosition(EntityHuman player) {
        if (player == null) return;
        
        double currentDist = Math.sqrt(
            (this.locX - player.locX) * (this.locX - player.locX) +
            (this.locZ - player.locZ) * (this.locZ - player.locZ)
        );
        
        // Try positions around current location
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2 * i) / 8;
            double testX = this.locX + Math.cos(angle) * 3;
            double testZ = this.locZ + Math.sin(angle) * 3;
            
            // Make sure we stay at roughly the right distance from player
            double testDist = Math.sqrt(
                (testX - player.locX) * (testX - player.locX) +
                (testZ - player.locZ) * (testZ - player.locZ)
            );
            
            // Only consider positions at roughly the same distance
            if (testDist >= targetDistance - 5 && testDist <= targetDistance + 5) {
                if (hasLineOfSightFrom(testX, this.locY + 1.6, testZ, player.locX, player.locY + 1.6, player.locZ)) {
                    // Move SLOWLY towards this visible position
                    double moveX = (testX - this.locX) * 0.1;
                    double moveZ = (testZ - this.locZ) * 0.1;
                    tryMove(moveX, moveZ);
                    return;
                }
            }
        }
        // If no suitable position found, don't move at all (no fallback!)
    }

    private void forceVisibleStalkingPosition(EntityHuman player) {
        if (player == null) return;

        double preferredAngle = Math.toRadians(player.yaw + 180.0F);
        double[] angleOffsets = {0.0, 0.35, -0.35, 0.7, -0.7, 1.05, -1.05, 1.4, -1.4, Math.PI * 0.5, -Math.PI * 0.5};
        double[] radiusOffsets = {0.0, -1.5, 1.5, -3.0, 3.0};

        for (double angleOffset : angleOffsets) {
            for (double radiusOffset : radiusOffsets) {
                double dist = Math.max(5.0, this.targetDistance + radiusOffset);
                double angle = preferredAngle + angleOffset;
                double testX = player.locX - Math.sin(angle) * dist;
                double testZ = player.locZ + Math.cos(angle) * dist;
                int testY = findGroundAt(MathHelper.floor(testX), MathHelper.floor(player.locY), MathHelper.floor(testZ));

                if (testY <= 0) continue;
                if (Math.abs(testY - player.locY) > 10.0) continue;
                if (!canStandAt(testX, testY, testZ)) continue;
                if (!hasLineOfSightFrom(testX, testY + 1.6, testZ, player.locX, player.locY + 1.6, player.locZ)) continue;

                navigateTowards(testX, testY, testZ, moveSpeed);
                return;
            }
        }
    }

    private boolean canStandAt(double x, double y, double z) {
        int blockX = MathHelper.floor(x);
        int blockY = MathHelper.floor(y);
        int blockZ = MathHelper.floor(z);
        return !isBlockSolid(blockX, blockY, blockZ)
            && !isBlockSolid(blockX, blockY + 1, blockZ)
            && isBlockSolid(blockX, blockY - 1, blockZ);
    }
    
    /**
     * Find ground level at given X,Z near the reference Y level
     */
    private int findGroundAt(int x, int refY, int z) {
        // Search around reference Y
        for (int y = refY + 3; y >= refY - 5; y--) {
            if (isBlockSolid(x, y, z) && !isBlockSolid(x, y + 1, z) && !isBlockSolid(x, y + 2, z)) {
                return y + 1;
            }
        }
        return -1;
    }
    
    /**
     * Check if there's a clear line of sight between two points.
     */
    private boolean hasLineOfSightFrom(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        if (dist < 0.5) return true;
        
        double step = 0.5;
        int steps = (int)(dist / step);
        
        dx /= dist;
        dy /= dist;
        dz /= dist;
        
        for (int i = 1; i < steps; i++) {
            double checkX = x1 + dx * step * i;
            double checkY = y1 + dy * step * i;
            double checkZ = z1 + dz * step * i;
            
            int blockX = MathHelper.floor(checkX);
            int blockY = MathHelper.floor(checkY);
            int blockZ = MathHelper.floor(checkZ);
            
            int blockId = this.world.getTypeId(blockX, blockY, blockZ);
            if (isBlockOpaque(blockId)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if a block is opaque (blocks vision).
     */
    private boolean isBlockOpaque(int blockId) {
        if (blockId == 0) return false;
        Block block = Block.byId[blockId];
        if (block == null) return false;
        
        // Transparent blocks that don't block vision
        if (blockId == Block.GLASS.id) return false;
        if (blockId == Block.LEAVES.id) return false;
        if (blockId == Block.FENCE.id) return false;
        if (blockId == Block.WATER.id || blockId == Block.STATIONARY_WATER.id) return false;
        if (blockId == Block.LONG_GRASS.id) return false;
        if (blockId == Block.TORCH.id) return false;
        
        return block.material.isBuildable();
    }
    
    private void doApproachingBehavior() {
        if (targetPlayer == null) return;
        
        // Face player smoothly
        faceEntitySmooth(targetPlayer);
        
        double dx = targetPlayer.locX - this.locX;
        double dz = targetPlayer.locZ - this.locZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        
        // Walk towards player to get into visible range (use stalk ratio)
        float visibleRange = targetDistance > 0.0F ? targetDistance : currentFogDistance * STALK_DISTANCE_RATIO;
        
        if (dist > visibleRange && dist > 0.1) {
            navigateTowards(targetPlayer.locX, targetPlayer.locY, targetPlayer.locZ, (float)(moveSpeed * 0.7));
        } else {
            // Close enough - stop and stare, then event manager will trigger retreat
            clearNavigationPath();
            haltHorizontalMotion();
        }
    }
    
    private void doRetreatingBehavior() {
        if (targetPlayer == null) return;
        
        // Check if should start escaping due to being stuck
        if (shouldStartEscaping()) {
            startEscaping();
            return;
        }
        
        // Retreat away from target player (or nearest threat)
        EntityHuman retreatFrom = nearestThreat != null ? nearestThreat : targetPlayer;
        
        double dx = this.locX - retreatFrom.locX;
        double dz = this.locZ - retreatFrom.locZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        
        
        // Walk backwards (facing player while retreating)
        faceEntitySmooth(retreatFrom);
        
        if (dist > 0.1) {
            dx /= dist;
            dz /= dist;

            double retreatTargetX = this.locX + dx * 8.0;
            double retreatTargetZ = this.locZ + dz * 8.0;
            navigateTowards(retreatTargetX, this.locY, retreatTargetZ, retreatSpeed);
        } else {
            clearNavigationPath();
            haltHorizontalMotion();
        }
    }
    
    private void doEscapingBehavior() {
        if (targetPlayer == null) return;
        
        escapeAttemptTicks++;
        
        // Check if out of sight
        boolean visible = isVisibleToPlayer(targetPlayer);
        
        if (!visible) {
            shouldDespawn = true;
            return;
        }
        
        // Try to find cover
        if (!tryMoveTowardsCover(targetPlayer)) {
            tryPerpendicularEscape(targetPlayer);
        }
        
        // Timeout
        if (escapeAttemptTicks > 100) {
            shouldDespawn = true;
        }
    }
    
    private boolean shouldStartEscaping() {
        return totallyStuckTicks >= STUCK_THRESHOLD_FOR_ESCAPE;
    }
    
    public void startEscaping() {
        aiState = AI_ESCAPING;
        escapeAttemptTicks = 0;
        stuckTicks = 0;
        totallyStuckTicks = 0;
        clearNavigationPath();
    }
    
    // === MOVEMENT AND PATHFINDING ===

    private void navigateTowards(double targetX, double targetY, double targetZ, float speed) {
        boolean needsRepath = this.navigationPath == null || this.navigationPath.b() || this.navigationRepathCooldown <= 0;

        if (!needsRepath) {
            if (Double.isNaN(this.navTargetX) || Double.isNaN(this.navTargetY) || Double.isNaN(this.navTargetZ)) {
                needsRepath = true;
            } else {
                double navDx = targetX - this.navTargetX;
                double navDy = targetY - this.navTargetY;
                double navDz = targetZ - this.navTargetZ;
                needsRepath = navDx * navDx + navDy * navDy + navDz * navDz > 4.0D;
            }
        }

        if (needsRepath) {
            this.navigationPath = this.world.a(
                this,
                MathHelper.floor(targetX),
                MathHelper.floor(targetY),
                MathHelper.floor(targetZ),
                NAVIGATION_RANGE
            );
            this.navTargetX = targetX;
            this.navTargetY = targetY;
            this.navTargetZ = targetZ;
            this.navigationRepathCooldown = NAVIGATION_REPATH_TICKS;
        }

        if (this.navigationPath != null && !this.navigationPath.b()) {
            Vec3D nextPoint = this.navigationPath.a(this);
            double entityWidth = this.length * 2.0F;

            while (nextPoint != null && nextPoint.d(this.locX, nextPoint.b, this.locZ) < entityWidth * entityWidth) {
                this.navigationPath.a();
                if (this.navigationPath.b()) {
                    nextPoint = null;
                    break;
                }
                nextPoint = this.navigationPath.a(this);
            }

            if (nextPoint != null) {
                double dx = nextPoint.a - this.locX;
                double dz = nextPoint.c - this.locZ;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0.001D) {
                    double desiredMoveX = (dx / dist) * speed;
                    double desiredMoveZ = (dz / dist) * speed;
                    smoothedNavMoveX = smoothedNavMoveX * (1.0D - NAVIGATION_SMOOTHING) + desiredMoveX * NAVIGATION_SMOOTHING;
                    smoothedNavMoveZ = smoothedNavMoveZ * (1.0D - NAVIGATION_SMOOTHING) + desiredMoveZ * NAVIGATION_SMOOTHING;
                    tryMove(smoothedNavMoveX, smoothedNavMoveZ, true, nextPoint.b);
                    return;
                }
            }
        }

        double directX = targetX - this.locX;
        double directZ = targetZ - this.locZ;
        double directDist = Math.sqrt(directX * directX + directZ * directZ);
        if (directDist > 0.001D) {
            tryMove((directX / directDist) * speed, (directZ / directDist) * speed, false, this.locY);
        }
    }

    private void clearNavigationPath() {
        this.navigationPath = null;
        this.navTargetX = Double.NaN;
        this.navTargetY = Double.NaN;
        this.navTargetZ = Double.NaN;
        this.smoothedNavMoveX = 0.0D;
        this.smoothedNavMoveZ = 0.0D;
    }

    private void haltHorizontalMotion() {
        this.motX = 0.0D;
        this.motZ = 0.0D;
        if (this.onGround && !isJumping) {
            this.motY = 0.0D;
        }
    }
    
    private void tryMove(double moveX, double moveZ) {
		tryMove(moveX, moveZ, false, this.locY);
	}

	private void tryMove(double moveX, double moveZ, boolean pathGuided, double nextPointY) {
        double startX = this.locX;
        double startZ = this.locZ;
        
        int targetBlockX = MathHelper.floor(this.locX + moveX);
        int targetBlockZ = MathHelper.floor(this.locZ + moveZ);
        int currentY = MathHelper.floor(this.locY);
        
        // Try to open any doors/trapdoors in the way
        tryOpenDoorsAt(targetBlockX, currentY, targetBlockZ);
        tryOpenDoorsAt(targetBlockX, currentY + 1, targetBlockZ);
        
        // Decide if we need to jump
        if (!isJumping && this.onGround) {
            if (pathGuided) {
                int floorY = MathHelper.floor(this.boundingBox.b + 0.5D);
                if (nextPointY > (double) floorY + 0.05D || this.positionChanged) {
                    this.motY = JUMP_VELOCITY;
                    isJumping = true;
                    jumpTicks = 0;
                }
            } else {
                PathAnalysis path = analyzePath(moveX, moveZ);
                if (path.type == PathType.STEP_UP) {
                    this.motY = JUMP_VELOCITY;
                    isJumping = true;
                    jumpTicks = 0;
                    jumpTargetX = this.locX + moveX * 3;
                    jumpTargetZ = this.locZ + moveZ * 3;
                    this.motX = moveX * 1.2;
                    this.motZ = moveZ * 1.2;
                } else if (path.type == PathType.GAP && path.gapSize <= 4) {
                    double gapBoost = 1.0 + path.gapSize * 0.2;
                    this.motY = JUMP_VELOCITY * gapBoost;
                    isJumping = true;
                    jumpTicks = 0;
                    jumpTargetX = this.locX + moveX * (path.gapSize + 2);
                    jumpTargetZ = this.locZ + moveZ * (path.gapSize + 2);
                    this.motX = moveX * (1.5 + path.gapSize * 0.3);
                    this.motZ = moveZ * (1.5 + path.gapSize * 0.3);
                } else if (path.type == PathType.WALL) {
                    consecutiveBlockedTicks++;
                    if (pathfindCooldown <= 0) {
                        pathfindCooldown = 3;
                        if (trySmartPathAround(moveX, moveZ)) {
                            consecutiveBlockedTicks = 0;
                            return;
                        }
                    }
                }
            }
        }
        
        // Set motion fields - these are used by EntityTrackerEntry for client sync
        this.motX = moveX;
        this.motZ = moveZ;
        
        // Apply gravity
        if (!this.onGround) {
            this.motY -= GRAVITY;
            if (this.motY < -3.0) this.motY = -3.0; // Terminal velocity
        } else if (!isJumping) {
            this.motY = 0;
        }
        
        // Debug: Log movement attempt
        double prevX = this.locX;
        double prevZ = this.locZ;
        
        // Use standard entity movement - this handles collision and step-up properly
        this.move(this.motX, this.motY, this.motZ);
        
        
        // Reset motionY if we landed
        if (this.onGround && this.motY <= 0) {
            this.motY = 0;
        }
        
        // === STUCK DETECTION AND RECOVERY ===
        double movedDist = Math.sqrt((this.locX - startX) * (this.locX - startX) + 
                                      (this.locZ - startZ) * (this.locZ - startZ));
        
        double totalMovedDist = Math.sqrt((this.locX - lastPathX) * (this.locX - lastPathX) + 
                                      (this.locZ - lastPathZ) * (this.locZ - lastPathZ));
        
        if (totalMovedDist < 0.05) {
            stuckTicks++;
            totallyStuckTicks++;
            if (stuckTicks > 8) {
                if (pathGuided) {
                    clearNavigationPath();
                    navigationRepathCooldown = 0;
                }
                tryUnstick();
                stuckTicks = 0;
                consecutiveBlockedTicks = 0;
            }
        } else {
            stuckTicks = 0;
            totallyStuckTicks = 0;
        }
        lastPathX = this.locX;
        lastPathZ = this.locZ;
    }
    
    /**
     * Analyze the path ahead to determine what type of movement is needed
     */
    private PathAnalysis analyzePath(double moveX, double moveZ) {
        double targetX = this.locX + moveX;
        double targetZ = this.locZ + moveZ;
        int targetBlockX = MathHelper.floor(targetX);
        int targetBlockZ = MathHelper.floor(targetZ);
        int currentY = MathHelper.floor(this.locY);
        
        // Check immediate destination - can walk there directly?
        if (canMoveTo(targetX, this.locY, targetZ)) {
            return new PathAnalysis(PathType.CLEAR);
        }
        
        // Check if we can step up (1 block higher)
        int blockAtTargetFeet = this.world.getTypeId(targetBlockX, currentY, targetBlockZ);
        int blockAtNewFeet = this.world.getTypeId(targetBlockX, currentY + 1, targetBlockZ);
        int blockAtNewHead = this.world.getTypeId(targetBlockX, currentY + 2, targetBlockZ);
        
        boolean targetFeetBlocked = blockAtTargetFeet != 0 && Block.byId[blockAtTargetFeet] != null &&
                                    Block.byId[blockAtTargetFeet].material.isBuildable() &&
                                    !isPassableBlock(blockAtTargetFeet);
        boolean newFeetClear = blockAtNewFeet == 0 || !Block.byId[blockAtNewFeet].material.isBuildable() ||
                               isPassableBlock(blockAtNewFeet);
        boolean newHeadClear = blockAtNewHead == 0 || !Block.byId[blockAtNewHead].material.isBuildable() ||
                               isPassableBlock(blockAtNewHead);
        
        if (targetFeetBlocked && newFeetClear && newHeadClear) {
            return new PathAnalysis(PathType.STEP_UP);
        }
        
        // Also check via canMoveTo for consistency
        if (canMoveTo(targetX, this.locY + 1, targetZ)) {
            return new PathAnalysis(PathType.STEP_UP);
        }
        
        // Check for small drops (1-3 blocks)
        for (int drop = 1; drop <= 3; drop++) {
            if (canMoveTo(targetX, this.locY - drop, targetZ)) {
                PathAnalysis result = new PathAnalysis(PathType.STEP_DOWN);
                result.landingY = currentY - drop;
                return result;
            }
        }
        
        // Check for gaps - look for ground beyond the immediate obstacle
        double dirLen = Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (dirLen > 0.01) {
            double dirX = moveX / dirLen;
            double dirZ = moveZ / dirLen;
            
            for (int gapCheck = 1; gapCheck <= 4; gapCheck++) {
                double checkX = this.locX + dirX * (gapCheck + 0.5);
                double checkZ = this.locZ + dirZ * (gapCheck + 0.5);
                
                // Check if there's a landing spot beyond the gap
                for (int yOffset = 1; yOffset >= -3; yOffset--) {
                    if (canMoveTo(checkX, this.locY + yOffset, checkZ)) {
                        PathAnalysis result = new PathAnalysis(PathType.GAP);
                        result.gapSize = gapCheck;
                        result.landingY = currentY + yOffset;
                        return result;
                    }
                }
            }
        }
        
        // Check for wall
        int feetBlock = this.world.getTypeId(targetBlockX, currentY, targetBlockZ);
        int headBlock = this.world.getTypeId(targetBlockX, currentY + 1, targetBlockZ);
        
        if ((feetBlock != 0 && Block.byId[feetBlock] != null && 
             Block.byId[feetBlock].material.isBuildable()) ||
            (headBlock != 0 && Block.byId[headBlock] != null && 
             Block.byId[headBlock].material.isBuildable())) {
            return new PathAnalysis(PathType.WALL);
        }
        
        // Default: treat as wall
        return new PathAnalysis(PathType.WALL);
    }
    
    /**
     * Smart pathfinding around obstacles
     */
    private boolean trySmartPathAround(double moveX, double moveZ) {
        if (isJumping) return false;
        
        double speed = Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (speed < 0.01) return false;
        
        double dirX = moveX / speed;
        double dirZ = moveZ / speed;
        
        // Try angles in order of preference (smaller deviations first)
        double[] angles = {30, -30, 45, -45, 60, -60, 75, -75, 90, -90};
        
        for (double angleDeg : angles) {
            double angleRad = Math.toRadians(angleDeg);
            double cos = Math.cos(angleRad);
            double sin = Math.sin(angleRad);
            double newDirX = dirX * cos - dirZ * sin;
            double newDirZ = dirX * sin + dirZ * cos;
            
            double testX = this.locX + newDirX * speed;
            double testZ = this.locZ + newDirZ * speed;
            
            // Analyze this alternative path
            PathAnalysis altPath = analyzePath(newDirX * speed, newDirZ * speed);
            
            if (altPath.type == PathType.CLEAR) {
                executeMove(testX, testZ);
                return true;
            }
            
            if (altPath.type == PathType.STEP_UP) {
                int targetBlockX = MathHelper.floor(testX);
                int targetBlockZ = MathHelper.floor(testZ);
                int currentY = MathHelper.floor(this.locY);
                
                if (canStepUp(targetBlockX, currentY, targetBlockZ)) {
                    this.motY = JUMP_VELOCITY;
                    this.motX = newDirX * speed * 1.2;
                    this.motZ = newDirZ * speed * 1.2;
                    isJumping = true;
                    jumpTicks = 0;
                    jumpTargetX = testX;
                    jumpTargetZ = testZ;
                    return true;
                }
            }
            
            if (altPath.type == PathType.STEP_DOWN) {
                executeMove(testX, testZ);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Execute movement using the standard entity system
     */
    private void executeMove(double newX, double newZ) {
        double moveX = newX - this.locX;
        double moveZ = newZ - this.locZ;
        
        // Set motion fields - these are used by EntityTrackerEntry to send velocity updates
        this.motX = moveX;
        this.motZ = moveZ;
        
        // Apply gravity
        if (!this.onGround) {
            this.motY -= GRAVITY;
            if (this.motY < -3.0) this.motY = -3.0;
        } else {
            this.motY = 0;
        }
        
        // Use standard entity movement
        this.move(this.motX, this.motY, this.motZ);
        
        lastPathX = this.locX;
        lastPathZ = this.locZ;
        stuckTicks = 0;
    }
    
    /**
     * Check if we can move to a position
     */
    private boolean canMoveTo(double x, double y, double z) {
        int blockX = MathHelper.floor(x);
        int blockY = MathHelper.floor(y);
        int blockZ = MathHelper.floor(z);
        
        int feetBlockId = this.world.getTypeId(blockX, blockY, blockZ);
        int headBlockId = this.world.getTypeId(blockX, blockY + 1, blockZ);
        int groundBlockId = this.world.getTypeId(blockX, blockY - 1, blockZ);
        
        // Check if feet/head are clear
        boolean feetClear = isPassableBlock(feetBlockId);
        boolean headClear = isPassableBlock(headBlockId);
        
        // Ground must be solid
        boolean hasGround = false;
        if (groundBlockId != 0 && Block.byId[groundBlockId] != null) {
            boolean isSolidGround = !isPassableBlock(groundBlockId) || 
                                    groundBlockId == Block.STATIONARY_WATER.id ||
                                    groundBlockId == Block.WATER.id;
            hasGround = isSolidGround;
        }
        
        return feetClear && headClear && hasGround;
    }
    
    /**
     * Check if we can step up at this position
     */
    private boolean canStepUp(int x, int y, int z) {
        int blockAboveHead = this.world.getTypeId(x, y + 2, z);
        return blockAboveHead == 0 || (Block.byId[blockAboveHead] != null && !Block.byId[blockAboveHead].material.isBuildable());
    }
    
    /**
     * Try to open doors and trapdoors
     */
    private void tryOpenDoorsAt(int x, int y, int z) {
        int blockId = this.world.getTypeId(x, y, z);
        
        // Open wooden doors
        if (blockId == Block.WOODEN_DOOR.id) {
            int metadata = this.world.getData(x, y, z);
            boolean isOpen = (metadata & 4) != 0;
            if (!isOpen) {
                this.world.setData(x, y, z, metadata ^ 4);
                int otherY = (metadata & 8) != 0 ? y - 1 : y + 1;
                int otherMeta = this.world.getData(x, otherY, z);
                this.world.setData(x, otherY, z, otherMeta ^ 4);
                this.world.makeSound(this.locX, this.locY, this.locZ, "random.door_open", 1.0F, 1.0F);
            }
        }
        
        // Open trapdoors
        if (blockId == Block.TRAP_DOOR.id) {
            int metadata = this.world.getData(x, y, z);
            boolean isOpen = (metadata & 4) != 0;
            if (!isOpen) {
                this.world.setData(x, y, z, metadata ^ 4);
                this.world.makeSound(this.locX, this.locY, this.locZ, "random.door_open", 1.0F, 1.0F);
            }
        }
    }
    
    /**
     * Check if a block is passable (can walk through it)
     */
    private boolean isPassableBlock(int blockId) {
        if (blockId == 0) return true;
        if (blockId == Block.STATIONARY_WATER.id || blockId == Block.WATER.id) return true;
        if (blockId == Block.LEAVES.id) return true;
        if (blockId == Block.LONG_GRASS.id) return true;
        if (blockId == Block.DEAD_BUSH.id) return true;
        if (blockId == Block.YELLOW_FLOWER.id || blockId == Block.RED_ROSE.id) return true;
        if (blockId == Block.BROWN_MUSHROOM.id || blockId == Block.RED_MUSHROOM.id) return true;
        if (blockId == Block.SNOW.id) return true;
        if (blockId == Block.WOODEN_DOOR.id || blockId == Block.IRON_DOOR_BLOCK.id) return true;
        if (blockId == Block.TRAP_DOOR.id) return true;
        if (blockId == Block.SIGN_POST.id || blockId == Block.WALL_SIGN.id) return true;
        if (blockId == Block.STONE_PLATE.id || blockId == Block.WOOD_PLATE.id) return true;
        if (blockId == Block.TORCH.id || blockId == Block.REDSTONE_TORCH_ON.id || blockId == Block.REDSTONE_TORCH_OFF.id) return true;
        return false;
    }
    
    /**
     * Try to get unstuck
     */
    private void tryUnstick() {
        if (isJumping) return;
        
        int currentBlockX = MathHelper.floor(this.locX);
        int currentBlockZ = MathHelper.floor(this.locZ);
        int currentY = MathHelper.floor(this.locY);
        
        // First, try to center on current block
        double centerX = currentBlockX + 0.5;
        double centerZ = currentBlockZ + 0.5;
        double distToCenter = Math.sqrt((this.locX - centerX) * (this.locX - centerX) + 
                                         (this.locZ - centerZ) * (this.locZ - centerZ));
        
        if (distToCenter > 0.1 && canMoveTo(centerX, this.locY, centerZ)) {
            executeMove(centerX, centerZ);
            return;
        }
        
        // Try 8 directions with increasing distance
        for (double dist = 1.0; dist <= 2.0; dist += 0.5) {
            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI * 2 * i) / 8;
                double moveX = Math.sin(angle) * dist;
                double moveZ = Math.cos(angle) * dist;
                double testX = this.locX + moveX;
                double testZ = this.locZ + moveZ;
                
                // Try at current Y
                if (canMoveTo(testX, this.locY, testZ)) {
                    executeMove(testX, testZ);
                    return;
                }
                
                // Try stepping up
                if (canMoveTo(testX, this.locY + 1, testZ)) {
                    int targetBlockX = MathHelper.floor(testX);
                    int targetBlockZ = MathHelper.floor(testZ);
                    if (canStepUp(targetBlockX, currentY, targetBlockZ)) {
                        this.motY = JUMP_VELOCITY;
                        this.motX = moveX * 0.5;
                        this.motZ = moveZ * 0.5;
                        isJumping = true;
                        jumpTicks = 0;
                        jumpTargetX = testX;
                        jumpTargetZ = testZ;
                        return;
                    }
                }
                
                // Try stepping down
                for (int drop = 1; drop <= 2; drop++) {
                    if (canMoveTo(testX, this.locY - drop, testZ)) {
                        executeMove(testX, testZ);
                        return;
                    }
                }
            }
        }
        
        // Really stuck - try a high jump
        if (!isJumping) {
            for (int i = 0; i < 4; i++) {
                double angle = (Math.PI * 2 * i) / 4;
                double jumpX = Math.sin(angle) * 1.5;
                double jumpZ = Math.cos(angle) * 1.5;
                
                int checkX = MathHelper.floor(this.locX + jumpX);
                int checkZ = MathHelper.floor(this.locZ + jumpZ);
                
                for (int yCheck = currentY + 2; yCheck >= currentY - 4; yCheck--) {
                    if (!isBlockSolid(checkX, yCheck, checkZ) &&
                        !isBlockSolid(checkX, yCheck + 1, checkZ) &&
                        isBlockSolid(checkX, yCheck - 1, checkZ)) {
                        // Strong jump to escape stuck position
                        this.motY = JUMP_VELOCITY * 1.5;
                        this.motX = jumpX * 0.5;
                        this.motZ = jumpZ * 0.5;
                        isJumping = true;
                        jumpTicks = 0;
                        jumpTargetX = this.locX + jumpX;
                        jumpTargetZ = this.locZ + jumpZ;
                        return;
                    }
                }
            }
        }
        
        // Absolute last resort: teleport to surface
        int groundY = this.world.getHighestBlockYAt(currentBlockX, currentBlockZ);
        if (groundY > 0 && groundY != currentY) {
            this.locX = currentBlockX + 0.5;
            this.locY = groundY;
            this.locZ = currentBlockZ + 0.5;
            this.setPosition(this.locX, this.locY, this.locZ);
        }
    }
    
    private void snapToGround() {
        if (!this.onGround) return;
        
        int x = MathHelper.floor(this.locX);
        int y = MathHelper.floor(this.locY);
        int z = MathHelper.floor(this.locZ);
        
        // Check if we're floating
        if (!isBlockSolid(x, y - 1, z)) {
            // Find ground below
            for (int checkY = y - 1; checkY > y - 10; checkY--) {
                if (isBlockSolid(x, checkY, z)) {
                    this.locY = checkY + 1;
                    this.setPosition(this.locX, this.locY, this.locZ);
                    break;
                }
            }
        }
    }
    
    private void faceEntity(Entity entity) {
        double dx = entity.locX - this.locX;
        double dy = entity.locY - this.locY;
        double dz = entity.locZ - this.locZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        
        float targetYaw = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float targetPitch = (float)(-(Math.atan2(dy, horizontalDist) * 180.0 / Math.PI));
        
        // Instant rotation for creepy effect - set ALL rotation fields
        this.yaw = targetYaw;
        this.lastYaw = targetYaw;
        this.pitch = targetPitch;
        this.lastPitch = targetPitch;
        
        // Head rotation - these are the key fields for head looking
        this.aA = targetYaw; // headYaw / yawHead
        this.aB = targetYaw; // prevHeadYaw / prevYawHead
        
        // Body/render rotation
        this.az = 0; // movement-related yaw
    }
    
    private void smoothRotateTo(float targetYaw) {
        float diff = targetYaw - this.yaw;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        
        float maxTurn = 10.0F;
        if (diff > maxTurn) diff = maxTurn;
        if (diff < -maxTurn) diff = -maxTurn;
        
        this.yaw += diff;
        this.aA = this.yaw;
        this.pitch = 0;
    }
    
    /**
     * Smoothly turn to face an entity (creepy slow head turn)
     */
    private void faceEntitySmooth(Entity entity) {
        double dx = entity.locX - this.locX;
        double dy = entity.locY - this.locY;
        double dz = entity.locZ - this.locZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        
        float targetYaw = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float targetPitch = (float)(-(Math.atan2(dy, horizontalDist) * 180.0 / Math.PI));
        
        // Smooth rotation - turn slowly for creepy effect
        float yawDiff = targetYaw - this.yaw;
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;
        
        float pitchDiff = targetPitch - this.pitch;
        
        // Slow turn speed for creepy effect
        float maxTurnSpeed = 5.0F;
        if (yawDiff > maxTurnSpeed) yawDiff = maxTurnSpeed;
        if (yawDiff < -maxTurnSpeed) yawDiff = -maxTurnSpeed;
        if (pitchDiff > maxTurnSpeed) pitchDiff = maxTurnSpeed;
        if (pitchDiff < -maxTurnSpeed) pitchDiff = -maxTurnSpeed;
        
        this.yaw += yawDiff;
        this.pitch += pitchDiff;
        this.lastYaw = this.yaw;
        this.lastPitch = this.pitch;
        this.aA = this.yaw;
        this.aB = this.yaw;
    }
    
    /**
     * Smooth movement with velocity interpolation
     */
    private void smoothMove(double targetMotX, double targetMotZ) {
        // Smoothly interpolate velocity for fluid movement
        double smoothFactor = 0.3;
        this.motX = this.motX * (1.0 - smoothFactor) + targetMotX * smoothFactor;
        this.motZ = this.motZ * (1.0 - smoothFactor) + targetMotZ * smoothFactor;
        
        // Apply gravity
        if (!this.onGround) {
            this.motY -= GRAVITY;
            if (this.motY < -3.0) this.motY = -3.0;
        } else if (!isJumping) {
            this.motY = 0;
        }
        
        // Execute movement
        this.move(this.motX, this.motY, this.motZ);
    }
    
    private boolean isBlockSolid(int x, int y, int z) {
        int blockId = this.world.getTypeId(x, y, z);
        if (blockId == 0) return false;
        Block block = Block.byId[blockId];
        if (block == null) return false;
        return block.material.isBuildable() && !isPassableBlock(blockId);
    }
    
    public boolean isVisibleToPlayer(EntityHuman player) {
        return this.e(player); // hasLineOfSight
    }
    
    private boolean tryMoveTowardsCover(EntityHuman player) {
        int x = MathHelper.floor(this.locX);
        int y = MathHelper.floor(this.locY);
        int z = MathHelper.floor(this.locZ);
        
        double playerDirX = this.locX - player.locX;
        double playerDirZ = this.locZ - player.locZ;
        double dist = Math.sqrt(playerDirX * playerDirX + playerDirZ * playerDirZ);
        if (dist < 0.1) return false;
        
        playerDirX /= dist;
        playerDirZ /= dist;
        
        // Check for cover in retreat direction
        for (int checkDist = 2; checkDist <= 8; checkDist++) {
            int checkX = x + (int)(playerDirX * checkDist);
            int checkZ = z + (int)(playerDirZ * checkDist);
            
            if (isBlockSolid(checkX, y, checkZ) || isBlockSolid(checkX, y + 1, checkZ)) {
                // Found cover, move towards it
                double moveX = playerDirX * retreatSpeed;
                double moveZ = playerDirZ * retreatSpeed;
                tryMove(moveX, moveZ);
                return true;
            }
        }
        
        return false;
    }
    
    private void tryPerpendicularEscape(EntityHuman player) {
        double dx = this.locX - player.locX;
        double dz = this.locZ - player.locZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.1) return;
        
        // Perpendicular direction
        double perpX = -dz / dist;
        double perpZ = dx / dist;
        
        // Alternate direction each tick
        if ((escapeAttemptTicks % 40) < 20) {
            perpX = -perpX;
            perpZ = -perpZ;
        }
        
        double moveX = perpX * retreatSpeed;
        double moveZ = perpZ * retreatSpeed;
        tryMove(moveX, moveZ);
    }
    
    // === PUBLIC API ===
    
    public void setTargetPlayer(EntityHuman player) {
        this.targetPlayer = player;
    }
    
    public EntityHuman getTargetPlayer() {
        return this.targetPlayer;
    }
    
    public void setCurrentFogDistance(float distance) {
        this.currentFogDistance = distance;
    }
    
    public float getCurrentFogDistance() {
        return this.currentFogDistance;
    }
    
    public void setAIState(int state) {
        this.aiState = state;
        if (state != AI_STALKING) {
            hasStalkSample = false;
        }
        if (state == AI_IDLE) {
            clearNavigationPath();
            haltHorizontalMotion();
        }
    }
    
    public int getAIState() {
        return this.aiState;
    }
    
    public void setTargetDistance(float distance) {
        this.targetDistance = distance;
    }

    public boolean consumeDamagedFlag() {
        if (!this.damagedByPlayerThisTick) {
            return false;
        }
        this.damagedByPlayerThisTick = false;
        return true;
    }
    
    public boolean shouldDespawn() {
        return shouldDespawn;
    }
    
    public void setShouldDespawn(boolean value) {
        shouldDespawn = value;
    }
    
    public void clearDespawnFlag() {
        shouldDespawn = false;
    }
    
    public boolean isVisibleToAnyPlayer() {
        for (Object obj : this.world.players) {
            EntityHuman player = (EntityHuman) obj;
            if (player.dead) continue;
            
            double dx = this.locX - player.locX;
            double dz = this.locZ - player.locZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            
            if (dist < 64 && isVisibleToPlayer(player)) {
                return true;
            }
        }
        return false;
    }
    
    public List<EntityHuman> getPlayersWithinDistance(double distance) {
        List<EntityHuman> nearby = new ArrayList<EntityHuman>();
        for (Object obj : this.world.players) {
            EntityHuman player = (EntityHuman) obj;
            if (player.dead) continue;
            
            double dx = this.locX - player.locX;
            double dz = this.locZ - player.locZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            
            if (dist <= distance) {
                nearby.add(player);
            }
        }
        return nearby;
    }
    
    // NBT - Herobrine doesn't persist
    @Override
    public void a(NBTTagCompound nbt) {
        super.a(nbt);
    }
    
    @Override
    public void b(NBTTagCompound nbt) {
        super.b(nbt);
    }
    
    @Override
    protected String g() { // living sound
        return null; // Silent
    }
    
    @Override
    protected String h() { // hurt sound
        return null; // Silent
    }
    
    @Override
    protected String i() { // death sound
        return null; // Silent
    }
    
    @Override
    protected int j() { // dropped item ID
        return 0; // Drops nothing
    }

    @Override
    protected void a(float f) {
        super.a(0.0F);
    }

    @Override
    public boolean K() {
        return false;
    }
    
    @Override
    public boolean damageEntity(Entity entity, int i) {
        boolean tookDamage = super.damageEntity(entity, i);
        if (tookDamage && entity instanceof EntityHuman) {
            this.targetPlayer = (EntityHuman) entity;
            this.damagedByPlayerThisTick = true;
            if (this.aiState != AI_ESCAPING) {
                this.aiState = AI_RETREATING;
            }
        }
        return tookDamage;
    }

    private void escapeFromEmbeddedBlock() {
        int x = MathHelper.floor(this.locX);
        int y = MathHelper.floor(this.locY);
        int z = MathHelper.floor(this.locZ);

        for (int radius = 1; radius <= 3; radius++) {
            for (int ox = -radius; ox <= radius; ox++) {
                for (int oz = -radius; oz <= radius; oz++) {
                    int testX = x + ox;
                    int testZ = z + oz;
                    int groundY = findGroundAt(testX, y, testZ);
                    if (groundY <= 0) continue;
                    if (!canStandAt(testX + 0.5D, groundY, testZ + 0.5D)) continue;

                    this.setPosition(testX + 0.5D, groundY, testZ + 0.5D);
                    this.motX = 0.0D;
                    this.motY = 0.0D;
                    this.motZ = 0.0D;
                    return;
                }
            }
        }
    }

    private boolean isEmbeddedInSolidBlock() {
        int x = MathHelper.floor(this.locX);
        int y = MathHelper.floor(this.locY);
        int z = MathHelper.floor(this.locZ);
        return isBlockSolid(x, y, z) || isBlockSolid(x, y + 1, z);
    }
    
    @Override
    public boolean a(EntityHuman player) { // interact
        return false;
    }
}
