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
    private float retreatSpeed = 0.35F; // Faster when retreating
    
    // Fog and event tracking
    private float currentFogDistance = 50.0F;
    private boolean shouldDespawn = false;
    
    // Pathfinding
    private int pathfindCooldown = 0;
    private double lastPathX, lastPathZ;
    private int stuckTicks = 0;
    private int consecutiveBlockedTicks = 0;
    private int totallyStuckTicks = 0;
    private static final int STUCK_THRESHOLD_FOR_ESCAPE = 30;
    
    // Escape behavior
    private int escapeAttemptTicks = 0;
    
    // Jump state
    private boolean isJumping = false;
    private int jumpTicks = 0;
    private static final double JUMP_VELOCITY = 0.42;
    private static final double GRAVITY = 0.08;
    
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
        this.bs = 0.5F; // stepHeight
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
        
        // Debug output every 2 seconds (40 ticks)
        debugTickCounter++;
        if (debugTickCounter >= 40) {
            debugTickCounter = 0;
            System.out.println("[Herobrine AI] State=" + aiState + 
                ", Target=" + (targetPlayer != null ? targetPlayer.name : "null") +
                ", Pos=(" + (int)locX + "," + (int)locY + "," + (int)locZ + ")" +
                ", Yaw=" + (int)yaw);
        }
        
        // Pathfinding cooldown
        if (pathfindCooldown > 0) pathfindCooldown--;
        
        // Handle jumping
        if (isJumping) {
            jumpTicks++;
            if (this.onGround && jumpTicks > 3) {
                isJumping = false;
                jumpTicks = 0;
            } else if (jumpTicks > 40) {
                isJumping = false;
                jumpTicks = 0;
            }
        }
        
        // Check for nearby threatening players (any player who gets too close)
        checkForThreats();
        
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
                    break;
            }
        } else if (aiState == AI_IDLE) {
            // Even with no target, look around for players
            EntityHuman nearbyPlayer = this.world.findNearbyPlayer(this, 64.0D);
            if (nearbyPlayer != null) {
                faceEntity(nearbyPlayer);
            }
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
            System.out.println("[Herobrine] Player " + nearestThreat.name + " too close! Retreating...");
            aiState = AI_RETREATING;
        }
    }
    
    // === AI BEHAVIORS ===
    
    private void doStalkingBehavior() {
        if (targetPlayer == null) return;
        
        // Stay JUST inside the visible fog - at 70% of fog distance
        this.targetDistance = currentFogDistance * 0.70F;
        
        // Always face the player
        faceEntity(targetPlayer);
        
        // Ensure we're on solid ground
        snapToGround();
        
        // Calculate current distance to player
        double dx = this.locX - targetPlayer.locX;
        double dz = this.locZ - targetPlayer.locZ;
        double currentDist = Math.sqrt(dx * dx + dz * dz);
        
        // Direction FROM player TO us (for moving away)
        double dirAwayX = dx / Math.max(0.1, currentDist);
        double dirAwayZ = dz / Math.max(0.1, currentDist);
        
        // Calculate ideal position (at targetDistance from player, in our current direction)
        double idealX = targetPlayer.locX + dirAwayX * targetDistance;
        double idealZ = targetPlayer.locZ + dirAwayZ * targetDistance;
        
        // How far are we from ideal position?
        double toIdealX = idealX - this.locX;
        double toIdealZ = idealZ - this.locZ;
        double toIdealDist = Math.sqrt(toIdealX * toIdealX + toIdealZ * toIdealZ);
        
        // Debug every 2 seconds
        if (debugTickCounter == 0) {
            System.out.println("[Herobrine STALK] currentDist=" + String.format("%.1f", currentDist) + 
                " targetDist=" + String.format("%.1f", targetDistance) +
                " toIdealDist=" + String.format("%.1f", toIdealDist));
        }
        
        // Move towards ideal position if we're more than 1 block away from it
        if (toIdealDist > 1.0) {
            double moveX = (toIdealX / toIdealDist) * moveSpeed;
            double moveZ = (toIdealZ / toIdealDist) * moveSpeed;
            if (debugTickCounter == 0) {
                System.out.println("[Herobrine STALK] Moving towards ideal! moveX=" + String.format("%.3f", moveX) + 
                    " moveZ=" + String.format("%.3f", moveZ));
            }
            tryMove(moveX, moveZ);
        }
    }
    
    private void doApproachingBehavior() {
        if (targetPlayer == null) return;
        
        double dx = targetPlayer.locX - this.locX;
        double dz = targetPlayer.locZ - this.locZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        
        if (dist > 0.1) {
            dx /= dist;
            dz /= dist;
            
            double moveX = dx * moveSpeed;
            double moveZ = dz * moveSpeed;
            tryMove(moveX, moveZ);
            faceEntity(targetPlayer);
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
        
        if (debugTickCounter == 0) {
            System.out.println("[Herobrine RETREAT] dist=" + String.format("%.1f", dist) + 
                " stuckTicks=" + totallyStuckTicks);
        }
        
        if (dist > 0.1) {
            dx /= dist;
            dz /= dist;
            
            double moveX = dx * retreatSpeed;
            double moveZ = dz * retreatSpeed;
            
            if (debugTickCounter == 0) {
                System.out.println("[Herobrine RETREAT] Moving! moveX=" + String.format("%.3f", moveX) + 
                    " moveZ=" + String.format("%.3f", moveZ));
            }
            tryMove(moveX, moveZ);
            
            // Face walking direction (away from player)
            float awayYaw = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
            smoothRotateTo(awayYaw);
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
    }
    
    // === MOVEMENT AND PATHFINDING ===
    
    private void tryMove(double moveX, double moveZ) {
        double startX = this.locX;
        double startZ = this.locZ;
        
        int targetBlockX = MathHelper.floor(this.locX + moveX);
        int targetBlockZ = MathHelper.floor(this.locZ + moveZ);
        int currentY = MathHelper.floor(this.locY);
        
        // Try to open any doors/trapdoors in the way
        tryOpenDoorsAt(targetBlockX, currentY, targetBlockZ);
        tryOpenDoorsAt(targetBlockX, currentY + 1, targetBlockZ);
        
        // Analyze the path to decide if we need to jump
        PathAnalysis path = analyzePath(moveX, moveZ);
        
        // Decide if we need to jump
        if (!isJumping && this.onGround) {
            if (path.type == PathType.STEP_UP) {
                // Need to jump up one block
                this.motY = JUMP_VELOCITY;
                isJumping = true;
                jumpTicks = 0;
            } else if (path.type == PathType.GAP && path.gapSize <= 3) {
                // Need to jump over a gap
                this.motY = JUMP_VELOCITY * (1.0 + path.gapSize * 0.15);
                isJumping = true;
                jumpTicks = 0;
            } else if (path.type == PathType.WALL) {
                // Wall - try to go around
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
        
        // Debug: Log if movement occurred
        if (debugTickCounter == 0) {
            double actualMoveX = this.locX - prevX;
            double actualMoveZ = this.locZ - prevZ;
            System.out.println("[Herobrine] Move attempt: motX=" + String.format("%.3f", motX) + 
                " motZ=" + String.format("%.3f", motZ) + 
                " actualX=" + String.format("%.3f", actualMoveX) + 
                " actualZ=" + String.format("%.3f", actualMoveZ) +
                " onGround=" + onGround);
        }
        
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
                    isJumping = true;
                    jumpTicks = 0;
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
                        isJumping = true;
                        jumpTicks = 0;
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
                        this.motY = JUMP_VELOCITY * 1.5;
                        isJumping = true;
                        jumpTicks = 0;
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
    }
    
    public int getAIState() {
        return this.aiState;
    }
    
    public void setTargetDistance(float distance) {
        this.targetDistance = distance;
    }
    
    public boolean shouldDespawn() {
        return shouldDespawn;
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
    public boolean damageEntity(Entity entity, int i) {
        // Herobrine cannot be damaged
        return false;
    }
    
    @Override
    public boolean a(EntityHuman player) { // interact
        return false;
    }
}
