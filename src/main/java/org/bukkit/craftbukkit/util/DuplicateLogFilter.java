package org.bukkit.craftbukkit.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * Keeps a broken plugin or noisy hot path from writing the same message on
 * every tick. The first few copies remain visible, then the message is allowed
 * through again when the suppression window expires.
 */
public final class DuplicateLogFilter implements Filter {

    private static final int MAX_TRACKED_MESSAGES = 2048;
    private static final int MAX_SIGNATURE_TEXT = 512;

    private final long windowMillis;
    private final int burstLimit;
    private final Map<String, Window> windows = new LinkedHashMap<String, Window>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Window> eldest) {
            return size() > MAX_TRACKED_MESSAGES;
        }
    };

    public DuplicateLogFilter(int windowSeconds, int burstLimit) {
        this.windowMillis = Math.max(0L, (long) windowSeconds * 1000L);
        this.burstLimit = Math.max(1, burstLimit);
    }

    @Override
    public synchronized boolean isLoggable(LogRecord record) {
        if (record == null || this.windowMillis == 0L) {
            return true;
        }
        if (isAuditMessage(record.getMessage())) {
            return true;
        }

        long now = record.getMillis();
        String signature = signature(record);
        Window window = this.windows.get(signature);
        if (window == null) {
            this.windows.put(signature, new Window(now));
            return true;
        }

        long elapsed = now - window.startedAt;
        if (elapsed < 0L || elapsed >= this.windowMillis) {
            window.startedAt = now;
            window.accepted = 1;
            return true;
        }

        if (window.accepted < this.burstLimit) {
            ++window.accepted;
            return true;
        }

        return false;
    }

    private static boolean isAuditMessage(String message) {
        if (message == null) {
            return false;
        }
        // Preserve player/console actions even when the same text is repeated;
        // these lines are useful for moderation and command auditing.
        return (message.startsWith("<") && message.indexOf("> ") > 1)
                || message.contains(" issued server command: ")
                || message.contains("CONSOLE:");
    }

    private static String signature(LogRecord record) {
        StringBuilder key = new StringBuilder(256);
        key.append(record.getLevel() == null ? 0 : record.getLevel().intValue()).append('|');
        appendCompact(key, record.getLoggerName());
        key.append('|');
        appendCompact(key, record.getMessage());

        Throwable thrown = record.getThrown();
        if (thrown != null) {
            key.append('|').append(thrown.getClass().getName()).append('|');
            appendCompact(key, thrown.getMessage());
        }
        return key.toString();
    }

    private static void appendCompact(StringBuilder target, String value) {
        if (value == null) {
            target.append("<null>");
            return;
        }
        if (value.length() <= MAX_SIGNATURE_TEXT) {
            target.append(value);
            return;
        }
        target.append(value, 0, MAX_SIGNATURE_TEXT)
                .append("#len=").append(value.length())
                .append("#hash=").append(value.hashCode());
    }

    private static final class Window {
        private long startedAt;
        private int accepted = 1;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
