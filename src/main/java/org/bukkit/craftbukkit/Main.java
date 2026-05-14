package org.bukkit.craftbukkit;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.minecraft.server.MinecraftServer;
import org.bukkit.craftbukkit.gui.ServerGUI;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    public static boolean useJline = true;
    public static boolean useGui = false;
    private static final List<String> NO_GUI_FLAGS = Arrays.asList("--nogui", "--no-gui", "-nogui", "-no-gui", "nogui");
    private static final List<String> GUI_FLAGS = Arrays.asList("--gui", "-gui", "gui");

    public static void main(String[] args) {
        // Check for GUI/no-GUI mode first (before option parsing)
        boolean launchGui = false;
        boolean forceNoGui = false;
        List<String> argList = new java.util.ArrayList<>(Arrays.asList(args));
        
        // Check for --nogui or --no-gui flag (forces console mode)
        if (containsAny(argList, NO_GUI_FLAGS)) {
            forceNoGui = true;
            // Remove nogui flags from args before passing to server
            argList.removeAll(NO_GUI_FLAGS);
            args = argList.toArray(new String[0]);
        }
        
        // Check for --gui or -gui flag
        if (!forceNoGui && containsAny(argList, GUI_FLAGS)) {
            launchGui = true;
            // Remove gui flag from args before passing to server
            argList.removeAll(GUI_FLAGS);
            args = argList.toArray(new String[0]);
        }
        
        // If no args provided (or only server args) and we detect a graphical environment, use GUI
        // unless --nogui was explicitly specified
        if (!forceNoGui && !launchGui && !java.awt.GraphicsEnvironment.isHeadless()) {
            // Default to GUI mode when running in graphical environment
            launchGui = true;
        }
        
        if (launchGui) {
            useGui = true;
            final String[] finalArgs = args;
            ServerGUI.main(finalArgs);
            return;
        }
        
        // Normal console mode
        OptionParser parser = new OptionParser() {
            {
                acceptsAll(asList("?", "help"), "Show the help");

                acceptsAll(asList("c", "config"), "Properties file to use").withRequiredArg().ofType(File.class).defaultsTo(new File("server.properties")).describedAs("Properties file");

                acceptsAll(asList("P", "plugins"), "Plugin directory to use").withRequiredArg().ofType(File.class).defaultsTo(new File("plugins")).describedAs("Plugin directory");

                acceptsAll(asList("h", "host", "server-ip"), "Host to listen on").withRequiredArg().ofType(String.class).describedAs("Hostname or IP");

                acceptsAll(asList("w", "world", "level-name"), "World directory").withRequiredArg().ofType(String.class).describedAs("World dir");

                acceptsAll(asList("world-container"), "Parent directory containing world directories").withRequiredArg().ofType(File.class).defaultsTo(new File(".")).describedAs("World container");

                acceptsAll(asList("singleplayer-layout"), "Use vanilla singleplayer dimension layout for the selected world");

                acceptsAll(asList("p", "port", "server-port"), "Port to listen on").withRequiredArg().ofType(Integer.class).describedAs("Port");

                acceptsAll(asList("o", "online-mode"), "Whether to use online authentication").withRequiredArg().ofType(Boolean.class).describedAs("Authentication");

                acceptsAll(asList("s", "size", "max-players"), "Maximum amount of players").withRequiredArg().ofType(Integer.class).describedAs("Server size");

                acceptsAll(asList("d", "date-format"), "Format of the date to display in the console (for log entries)").withRequiredArg().ofType(SimpleDateFormat.class).describedAs("Log date format");

                acceptsAll(asList("log-pattern"), "Specfies the log filename pattern").withRequiredArg().ofType(String.class).defaultsTo("server.log").describedAs("Log filename");

                acceptsAll(asList("log-limit"), "Limits the maximum size of the log file (0 = unlimited)").withRequiredArg().ofType(Integer.class).defaultsTo(0).describedAs("Max log size");

                acceptsAll(asList("log-count"), "Specified how many log files to cycle through").withRequiredArg().ofType(Integer.class).defaultsTo(1).describedAs("Log count");

                acceptsAll(asList("log-append"), "Whether to append to the log file").withRequiredArg().ofType(Boolean.class).defaultsTo(true).describedAs("Log append");

                acceptsAll(asList("b", "bukkit-settings"), "File for bukkit settings").withRequiredArg().ofType(File.class).defaultsTo(new File("bukkit.yml")).describedAs("Yml file");

                acceptsAll(asList("debug-config"), "Don't load Poseidon.yml, but generate a new one with all the default values");

                acceptsAll(asList("nojline"), "Disables jline and emulates the vanilla console");

                acceptsAll(asList("nogui"), "Run in console mode without GUI");
                
                acceptsAll(asList("gui"), "Launch with graphical user interface");

                acceptsAll(asList("v", "version"), "Show the CraftBukkit Version");
            }
        };

        OptionSet options = null;

        try {
            options = parser.parse(args);
        } catch (joptsimple.OptionException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, ex.getLocalizedMessage());
        }

        if ((options == null) || (options.has("?"))) {
            try {
                parser.printHelpOn(System.out);
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else if (options.has("v")) {
            System.out.println(CraftServer.class.getPackage().getImplementationVersion());
        } else {
            try {
                useJline = !"jline.UnsupportedTerminal".equals(System.getProperty("jline.terminal"));

                if (options.has("nojline")) {
                    System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
                    System.setProperty("user.language", "en");
                    useJline = false;
                }

                MinecraftServer.main(options);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private static List<String> asList(String... params) {
        return Arrays.asList(params);
    }

    private static boolean containsAny(List<String> values, List<String> candidates) {
        for (String candidate : candidates) {
            if (values.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
