package net.minecraft.server.registry;

public final class Sensors {
    private Sensors() {}

    /** Find nearest non-creative player within the given range. */
    public static net.minecraft.server.EntityHuman findNearestPlayerNonCreative(net.minecraft.server.EntityLiving self, double range) {
        if (self == null || self.world == null) return null;
        net.minecraft.server.EntityHuman p = self.world.findNearbyPlayer(self, range);
        if (p == null) return null;
        if (p.gameMode == 1) return null; // ignore creative
        return p;
    }

    /** Whether self has line of sight to target using existing visibility checks. */
    public static boolean hasLineOfSight(net.minecraft.server.EntityLiving self, net.minecraft.server.Entity target) {
        if (self == null || target == null) return false;
        try { return self.e(target); } catch (Throwable ignored) { return true; }
    }

    public static boolean isDaylight(net.minecraft.server.World world) {
        if (world == null) return true;
        try { return world.d(); } catch (Throwable ignored) { return true; }
    }
}


