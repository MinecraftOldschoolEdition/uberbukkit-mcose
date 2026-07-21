package org.bukkit.craftbukkit.util;

import org.junit.Test;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DuplicateLogFilterTest {

    @Test
    public void keepsInitialBurstThenSuppressesUntilWindowExpires() {
        DuplicateLogFilter filter = new DuplicateLogFilter(30, 3);

        assertTrue(filter.isLoggable(record("same message", 1000L)));
        assertTrue(filter.isLoggable(record("same message", 1001L)));
        assertTrue(filter.isLoggable(record("same message", 1002L)));
        assertFalse(filter.isLoggable(record("same message", 1003L)));
        assertTrue(filter.isLoggable(record("other message", 1004L)));
        assertTrue(filter.isLoggable(record("same message", 31000L)));
    }

    @Test
    public void neverSuppressesChatOrCommandAuditLines() {
        DuplicateLogFilter filter = new DuplicateLogFilter(30, 1);

        assertTrue(filter.isLoggable(record("<Player> repeated chat", 1000L)));
        assertTrue(filter.isLoggable(record("<Player> repeated chat", 1001L)));
        assertTrue(filter.isLoggable(record("Player issued server command: /help", 1002L)));
        assertTrue(filter.isLoggable(record("Player issued server command: /help", 1003L)));
        assertTrue(filter.isLoggable(record("CONSOLE: save-all", 1004L)));
        assertTrue(filter.isLoggable(record("CONSOLE: save-all", 1005L)));
    }

    private static LogRecord record(String message, long millis) {
        LogRecord record = new LogRecord(Level.WARNING, message);
        record.setLoggerName("Minecraft");
        record.setMillis(millis);
        return record;
    }
}
