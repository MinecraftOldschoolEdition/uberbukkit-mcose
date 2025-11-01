package net.minecraft.server.registry;

import net.minecraft.server.World;

/**
 * Simple day/night schedule controlling whether an entity is considered active/aggressive.
 */
public final class Schedule {
    private final boolean activeDuringDay;
    private final boolean activeDuringNight;

    public Schedule(boolean activeDuringDay, boolean activeDuringNight) {
        this.activeDuringDay = activeDuringDay;
        this.activeDuringNight = activeDuringNight;
    }

    public static Schedule always() { return new Schedule(true, true); }
    public static Schedule dayOnly() { return new Schedule(true, false); }
    public static Schedule nightOnly() { return new Schedule(false, true); }

    public boolean isActive(World world) {
        if (world == null || world.worldProvider == null) {
            return activeDuringDay || activeDuringNight;
        }
        // Worlds without sky (e.g., Nether) treat schedule as always active if any period is active
        // c=true means no sky (Nether has c=true)
        boolean hasSky = !world.worldProvider.c;
        if (!hasSky) return activeDuringDay || activeDuringNight;
        // Check if it's daytime: time % 24000 < 12000 is daytime (0-12000 = day, 12000-24000 = night)
        long time = world.getTime();
        boolean isDay = (time % 24000L) < 12000L;
        return isDay ? activeDuringDay : activeDuringNight;
    }
}


