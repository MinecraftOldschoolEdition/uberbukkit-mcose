package org.bukkit.command.defaults;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TitleCommandTimeTest {
    @Test
    public void parses26_3TimeUnitsAsTicks() {
        assertEquals(10, TitleCommand.parseTime("10"));
        assertEquals(10, TitleCommand.parseTime("10t"));
        assertEquals(40, TitleCommand.parseTime("2s"));
        assertEquals(24000, TitleCommand.parseTime("1d"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeTimes() {
        TitleCommand.parseTime("-1");
    }
}
