package org.bukkit.craftbukkit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggerOutputStream extends ByteArrayOutputStream {
    private final Logger logger;
    private final Level level;

    public LoggerOutputStream(Logger logger, Level level) {
        super();
        this.logger = logger;
        this.level = level;
    }

    @Override
    public void flush() throws IOException {
        synchronized (this) {
            super.flush();
            String record = this.toString();
            super.reset();

            int end = record.length();
            while (end > 0 && (record.charAt(end - 1) == '\n' || record.charAt(end - 1) == '\r')) {
                --end;
            }
            if (end != record.length()) {
                record = record.substring(0, end);
            }

            if (record.length() > 0) {
                logger.logp(level, "", "", record);
            }
        }
    }
}
