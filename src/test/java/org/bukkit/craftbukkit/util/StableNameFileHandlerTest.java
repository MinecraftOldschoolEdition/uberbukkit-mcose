package org.bukkit.craftbukkit.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StableNameFileHandlerTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rotatesWithoutRenamingTheActiveLogToGenerationZero() throws Exception {
        File active = new File(this.temporaryFolder.getRoot(), "server.log");
        StableNameFileHandler handler = new StableNameFileHandler(active, 10L, 3, true);
        handler.setLevel(Level.ALL);
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + "\n";
            }
        });

        handler.publish(new LogRecord(Level.INFO, "first"));
        handler.publish(new LogRecord(Level.INFO, "second"));
        handler.publish(new LogRecord(Level.INFO, "third"));
        handler.close();
        handler.publish(new LogRecord(Level.INFO, "after-close"));

        assertTrue(active.isFile());
        assertFalse(new File(active.getPath() + ".0").exists());
        assertEquals("third\n", read(active));
        assertEquals("second\n", read(new File(active.getPath() + ".1")));
        assertEquals("first\n", read(new File(active.getPath() + ".2")));
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
