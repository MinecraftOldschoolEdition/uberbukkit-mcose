package org.bukkit.command;

import java.util.List;

/**
 * A convenience interface that combines {@link CommandExecutor} and {@link TabCompleter}.
 * <p>
 * This is the <b>recommended</b> way to handle both command execution and tab completion
 * in a single class. When a class implementing TabExecutor is set as a command's executor,
 * it will automatically be used for both execution and tab completion - no need to
 * call setTabCompleter() separately.
 * 
 * <h2>Why Use TabExecutor?</h2>
 * <ul>
 *   <li>Single class handles both execution and completion</li>
 *   <li>Automatic tab completer registration when set as executor</li>
 *   <li>Cleaner code organization</li>
 *   <li>Easier to maintain as command logic is in one place</li>
 * </ul>
 * 
 * <h2>Complete Example</h2>
 * <pre>
 * // plugin.yml:
 * // commands:
 * //   warp:
 * //     description: Teleport to a warp point
 * //     usage: /warp &lt;warpname&gt;
 * //     permission: myplugin.warp
 * 
 * public class WarpCommand implements TabExecutor {
 *     private final Map&lt;String, Location&gt; warps = new HashMap&lt;&gt;();
 *     
 *     // Command execution
 *     public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
 *         if (!(sender instanceof Player)) {
 *             sender.sendMessage("Only players can use this command!");
 *             return true;
 *         }
 *         
 *         if (args.length != 1) {
 *             sender.sendMessage("Usage: /warp &lt;warpname&gt;");
 *             return true;
 *         }
 *         
 *         Location loc = warps.get(args[0].toLowerCase());
 *         if (loc == null) {
 *             sender.sendMessage("Unknown warp: " + args[0]);
 *             return true;
 *         }
 *         
 *         ((Player) sender).teleport(loc);
 *         sender.sendMessage("Teleported to " + args[0]);
 *         return true;
 *     }
 *     
 *     // Tab completion - shows available warps
 *     public List&lt;String&gt; onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
 *         if (args.length == 1) {
 *             // Filter warp names by what the player has typed
 *             return TabCompletions.filter(args[0], warps.keySet());
 *         }
 *         return TabCompletions.empty();
 *     }
 * }
 * 
 * // In your main plugin class:
 * public class MyPlugin extends JavaPlugin {
 *     public void onEnable() {
 *         // Just set the executor - tab completion is automatic!
 *         getCommand("warp").setExecutor(new WarpCommand());
 *     }
 * }
 * </pre>
 * 
 * <h2>Multi-Argument Example</h2>
 * <pre>
 * public class GameCommand implements TabExecutor {
 *     
 *     public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
 *         // Handle: /game &lt;start|stop|status&gt; [player]
 *         // ...
 *         return true;
 *     }
 *     
 *     public List&lt;String&gt; onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
 *         if (args.length == 1) {
 *             // First argument: subcommand
 *             return TabCompletions.filter(args[0], "start", "stop", "status");
 *         } else if (args.length == 2) {
 *             // Second argument: depends on first
 *             if (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop")) {
 *                 return TabCompletions.players(args[1]);
 *             }
 *         }
 *         return TabCompletions.empty();
 *     }
 * }
 * </pre>
 * 
 * @see TabCompleter
 * @see CommandExecutor
 * @see TabCompletions
 */
public interface TabExecutor extends CommandExecutor, TabCompleter {
    // This interface combines CommandExecutor and TabCompleter.
    // Implement both onCommand() and onTabComplete() methods.
}

