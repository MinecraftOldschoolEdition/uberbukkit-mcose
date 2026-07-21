package org.bukkit.craftbukkit.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Size-rotating log handler which keeps the configured filename as the active
 * file. java.util.logging.FileHandler appends ".0" to a pattern when multiple
 * generations are retained, which breaks tools expecting server.log.
 */
public final class StableNameFileHandler extends Handler {

    private final File activeFile;
    private final long limit;
    private final int count;
    private FileOutputStream output;
    private long written;
    private boolean closed;

    public StableNameFileHandler(File activeFile, long limit, int count, boolean append) throws IOException {
        if (activeFile == null) {
            throw new NullPointerException("activeFile");
        }
        this.activeFile = activeFile.getAbsoluteFile();
        this.limit = Math.max(0L, limit);
        this.count = Math.max(1, count);

        File parent = this.activeFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Unable to create log directory " + parent);
        }

        this.open(append);
    }

    @Override
    public synchronized void publish(LogRecord record) {
        if (this.closed || !this.isLoggable(record)) {
            return;
        }

        try {
            if (this.output == null) {
                this.open(true);
            }
            Formatter formatter = this.getFormatter();
            String formatted = formatter == null ? record.getMessage() + System.lineSeparator() : formatter.format(record);
            byte[] bytes = formatted.getBytes(StandardCharsets.UTF_8);

            if (this.limit > 0L && this.written > 0L && this.written + bytes.length > this.limit) {
                this.rotate();
            }

            this.output.write(bytes);
            this.output.flush();
            this.written += bytes.length;
        } catch (Exception exception) {
            this.reportError("Failed to write log record", exception, ErrorManager.WRITE_FAILURE);
        }
    }

    @Override
    public synchronized void flush() {
        if (this.output == null) {
            return;
        }
        try {
            this.output.flush();
        } catch (IOException exception) {
            this.reportError("Failed to flush log", exception, ErrorManager.FLUSH_FAILURE);
        }
    }

    @Override
    public synchronized void close() throws SecurityException {
        this.closed = true;
        try {
            this.closeOutput();
        } catch (IOException exception) {
            this.reportError("Failed to close log", exception, ErrorManager.CLOSE_FAILURE);
        }
    }

    private void rotate() throws IOException {
        this.closeOutput();
        try {
            if (this.count <= 1) {
                this.open(false);
                return;
            }

            File oldest = generation(this.count - 1);
            Files.deleteIfExists(oldest.toPath());
            for (int index = this.count - 2; index >= 1; --index) {
                File source = generation(index);
                if (source.isFile()) {
                    Files.move(source.toPath(), generation(index + 1).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (this.activeFile.isFile()) {
                Files.move(this.activeFile.toPath(), generation(1).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            this.open(false);
        } catch (IOException rotationFailure) {
            try {
                this.open(true);
            } catch (IOException reopenFailure) {
                rotationFailure.addSuppressed(reopenFailure);
            }
            throw rotationFailure;
        }
    }

    private File generation(int generation) {
        return new File(this.activeFile.getPath() + "." + generation);
    }

    private void open(boolean append) throws IOException {
        this.output = new FileOutputStream(this.activeFile, append);
        this.written = append && this.activeFile.isFile() ? this.activeFile.length() : 0L;
    }

    private void closeOutput() throws IOException {
        if (this.output == null) {
            return;
        }
        try {
            this.output.flush();
        } finally {
            this.output.close();
            this.output = null;
        }
    }
}
