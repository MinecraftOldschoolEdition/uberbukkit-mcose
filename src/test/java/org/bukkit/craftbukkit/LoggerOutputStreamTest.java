package org.bukkit.craftbukkit;

import org.junit.Test;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class LoggerOutputStreamTest {

    @Test
    public void removesPrintlnTerminatorAndIgnoresBlankLines() throws Exception {
        final List<String> messages = new ArrayList<String>();
        Logger logger = Logger.getLogger("LoggerOutputStreamTest-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        capture.setLevel(Level.ALL);
        logger.addHandler(capture);

        PrintStream stream = new PrintStream(new LoggerOutputStream(logger, Level.INFO), true, "UTF-8");
        stream.println("first");
        stream.println();
        stream.print("second\r\n");
        stream.flush();
        stream.close();

        assertEquals(Arrays.asList("first", "second"), messages);
    }
}
