package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;
import org.bukkit.craftbukkit.util.DuplicateLogFilter;
import org.bukkit.craftbukkit.util.ShortConsoleLogFormatter;
import org.bukkit.craftbukkit.util.StableNameFileHandler;
import org.bukkit.craftbukkit.util.TerminalConsoleHandler;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

// CraftBukkit start
// CraftBukkit end

public class ConsoleLogManager {

    public static final int DEFAULT_LOG_LIMIT = 16 * 1024 * 1024;
    public static final int DEFAULT_LOG_COUNT = 4;
    public static Logger a = Logger.getLogger("Minecraft");
    public static Logger global = Logger.getLogger(""); // CraftBukkit

    public ConsoleLogManager() {
    }

    // Track if already initialized to prevent duplicate handlers on restart
    private static boolean initialized = false;
    
    // CraftBukkit - change of method signature!
    public static void init(MinecraftServer server) {
        ConsoleLogFormatter consolelogformatter = new ConsoleLogFormatter();
        boolean verbose = PoseidonConfig.getInstance().getConfigBoolean("settings.logging.verbose.enabled", false)
                || (server.options != null && server.options.has("debug-config"));
        Level configuredLevel = verbose ? Level.FINE : Level.INFO;

        a.setUseParentHandlers(false);
        a.setLevel(configuredLevel);
        global.setLevel(configuredLevel);
        
        // Only add console handlers on first init to prevent duplicates
        if (initialized) {
            return;
        }
        initialized = true;
        
        // CraftBukkit start
        ConsoleHandler consolehandler = new TerminalConsoleHandler(server.reader);
        consolehandler.setLevel(configuredLevel);
        consolehandler.setFilter(createDuplicateFilter());
        consolehandler.setFormatter(new ShortConsoleLogFormatter(server));
        
        // Don't clear handlers - preserve any GUI handlers already attached
        // Just add our console handler
        global.addHandler(consolehandler);
        // CraftBukkit end

        a.addHandler(consolehandler);

        try {
            //Project Poseidon Start
            Handler filehandler;
            int limit = ((Integer) server.options.valueOf("log-limit")).intValue();
            int count = ((Integer) server.options.valueOf("log-count")).intValue();
            boolean append = ((Boolean) server.options.valueOf("log-append")).booleanValue();
            if ((boolean) PoseidonConfig.getInstance().getConfigOption("settings.per-day-log-file.enabled")) {
                //If latest log file is enabled, create a new log file for each day
                if ((boolean) PoseidonConfig.getInstance().getConfigOption("settings.per-day-log-file.latest-log.enabled")) {
                    String latestLogFileName = "latest";
                    File log = new File("." + File.separator + "logs" + File.separator);
                    log.getParentFile().mkdirs();
                    log.mkdirs();
                    filehandler = createFileHandler("." + File.separator + "logs" + File.separator + latestLogFileName + ".log", limit, count, append);
                } else {
                    //If latest log file is disabled, create a new log file for each day with the date as the file name
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                    String logfile = LocalDate.now().format(formatter);
                    File log = new File("." + File.separator + "logs" + File.separator);
                    log.getParentFile().mkdirs();
                    log.mkdirs();
                    filehandler = createFileHandler("." + File.separator + "logs" + File.separator + logfile + ".log", limit, count, append);
                }
            } else {
                // CraftBukkit start
                String pattern = (String) server.options.valueOf("log-pattern");
                filehandler = createFileHandler(pattern, limit, count, append);
                // CraftBukkit start
            }
            //Project Poseidon End

            filehandler.setLevel(configuredLevel);
            filehandler.setFilter(createDuplicateFilter());
            filehandler.setFormatter(consolelogformatter);
            a.addHandler(filehandler);
            global.addHandler(filehandler); // CraftBukkit
        } catch (Exception exception) {
            a.log(Level.WARNING, "Failed to log to server.log", exception);
        }
    }

    private static Handler createFileHandler(String pattern, int limit, int count, boolean append) throws Exception {
        if (limit > 0 && pattern.indexOf('%') < 0) {
            return new StableNameFileHandler(new File(pattern), limit, count, append);
        }
        return new FileHandler(pattern, limit, count, append);
    }

    private static Filter createDuplicateFilter() {
        PoseidonConfig config = PoseidonConfig.getInstance();
        if (!config.getConfigBoolean("settings.logging.duplicate-suppression.enabled", true)) {
            return null;
        }
        int windowSeconds = config.getInt("settings.logging.duplicate-suppression.window-seconds", 30);
        int burst = config.getInt("settings.logging.duplicate-suppression.burst", 3);
        return new DuplicateLogFilter(windowSeconds, burst);
    }
}
