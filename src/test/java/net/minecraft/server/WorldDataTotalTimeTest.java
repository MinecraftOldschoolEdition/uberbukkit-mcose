package net.minecraft.server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WorldDataTotalTimeTest {
    @Test
    public void totalTimePersistsSeparatelyFromDayNightTime() {
        WorldData worldData = new WorldData(1234L, "cloud-sync-test");
        worldData.a(6_000L);
        worldData.setTotalTime(98_765L);

        WorldData reloaded = new WorldData(worldData.a());

        assertEquals(6_000L, reloaded.f());
        assertEquals(98_765L, reloaded.getTotalTime());
    }

    @Test
    public void legacyWorldsSeedTotalTimeFromDayNightTime() {
        NBTTagCompound level = new NBTTagCompound();
        level.setLong("Time", 42_000L);

        WorldData worldData = new WorldData(level);

        assertEquals(42_000L, worldData.getTotalTime());
    }
}
