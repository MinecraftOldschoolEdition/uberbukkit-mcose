package net.minecraft.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EntityTrackerVehicleTrackingPolicyTest {
    @Test
    public void usesSnapshotVehicleTrackingRanges() {
        assertEquals(128, EntityTracker.MINECART_TRACKING_RANGE);
        assertEquals(160, EntityTracker.BOAT_TRACKING_RANGE);
    }

    @Test
    public void occupiedVehiclesAreNeverPressureThrottled() {
        EntityBoat boat = new EntityBoat(null);
        EntityMinecart minecart = new EntityMinecart(null);

        assertTrue(EntityTracker.isPressureThrottleCandidate(boat));
        assertTrue(EntityTracker.isPressureThrottleCandidate(minecart));

        boat.passenger = new TestEntity();
        minecart.passenger = new TestEntity();

        assertFalse(EntityTracker.isPressureThrottleCandidate(boat));
        assertFalse(EntityTracker.isPressureThrottleCandidate(minecart));
    }

    @Test
    public void occupiedVehicleInheritsPassengerTrackingRange() {
        EntityBoat boat = new EntityBoat(null);
        TestEntity passenger = new TestEntity();
        boat.passenger = passenger;

        EntityTrackerEntry boatEntry = new EntityTrackerEntry(boat, 80, 2, true);
        EntityTrackerEntry passengerEntry = new EntityTrackerEntry(passenger, 512, 2, false);
        EntityList trackedEntries = new EntityList();
        trackedEntries.a(boat.id, boatEntry);
        trackedEntries.a(passenger.id, passengerEntry);

        assertEquals(512, EntityTrackerEntry.effectiveTrackingRange(boatEntry.b, boat.passenger, trackedEntries));
    }

    @Test
    public void mountedRiderUsesVehiclePositionUntilAbsoluteDismountSync() {
        assertEquals(
                EntityTrackerEntry.PassengerPositionSyncMode.NORMAL,
                EntityTrackerEntry.resolvePassengerPositionSyncMode(false, false));
        assertEquals(
                EntityTrackerEntry.PassengerPositionSyncMode.RIDING_ROTATION_ONLY,
                EntityTrackerEntry.resolvePassengerPositionSyncMode(true, false));
        assertEquals(
                EntityTrackerEntry.PassengerPositionSyncMode.DISMOUNT_ABSOLUTE,
                EntityTrackerEntry.resolvePassengerPositionSyncMode(false, true));
    }

    private static final class TestEntity extends Entity {
        private TestEntity() {
            super(null);
        }

        protected void b() {
        }

        protected void a(NBTTagCompound nbt) {
        }

        protected void b(NBTTagCompound nbt) {
        }
    }
}
