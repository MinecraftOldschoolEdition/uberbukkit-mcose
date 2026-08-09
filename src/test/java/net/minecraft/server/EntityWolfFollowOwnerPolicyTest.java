package net.minecraft.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EntityWolfFollowOwnerPolicyTest {
    @Test
    public void snapshotFollowDistancesArePreserved() {
        assertFalse(EntityWolf.shouldStartFollowingOwner(99.999D));
        assertTrue(EntityWolf.shouldStartFollowingOwner(100.0D));
        assertFalse(EntityWolf.shouldKeepFollowingOwner(4.0D));
        assertTrue(EntityWolf.shouldKeepFollowingOwner(4.001D));
        assertFalse(EntityWolf.shouldTeleportToOwner(143.999D));
        assertTrue(EntityWolf.shouldTeleportToOwner(144.0D));
    }

    @Test
    public void teleportOffsetsStayInSnapshotOuterRing() {
        assertFalse(EntityWolf.isTeleportOffsetOutsideInnerSquare(0, 0));
        assertFalse(EntityWolf.isTeleportOffsetOutsideInnerSquare(1, -1));
        assertTrue(EntityWolf.isTeleportOffsetOutsideInnerSquare(2, 0));
        assertTrue(EntityWolf.isTeleportOffsetOutsideInnerSquare(-3, 3));
        assertFalse(EntityWolf.isTeleportOffsetOutsideInnerSquare(4, 0));
    }

    @Test
    public void teleportRequiresWalkableCollisionFreeGroundAwayFromWaterAndDanger() {
        assertTrue(EntityWolf.isWalkableTeleportLanding(true, true, false, false, true));
        assertFalse(EntityWolf.isWalkableTeleportLanding(false, true, false, false, true));
        assertFalse(EntityWolf.isWalkableTeleportLanding(true, false, false, false, true));
        assertFalse(EntityWolf.isWalkableTeleportLanding(true, true, true, false, true));
        assertFalse(EntityWolf.isWalkableTeleportLanding(true, true, false, true, true));
        assertFalse(EntityWolf.isWalkableTeleportLanding(true, true, false, false, false));
    }
}
