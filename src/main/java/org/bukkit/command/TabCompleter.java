package org.bukkit.command;

import java.util.List;

/**
 * Represents a class which can handle tab completion for commands.
 * <p>
 * Plugin developers can implement this interface to provide custom
 * tab completions for their commands. The completions will be sent
 * to the client and displayed in the chat autocomplete popup when
 * the player presses Tab while typing the command.
 * 
 * <h2>How Tab Completion Works</h2>
 * <ol>
 *   <li>Player types a command and presses Tab</li>
 *   <li>Client sends the partial command to the server</li>
 *   <li>Server calls {@link #onTabComplete} on the command's TabCompleter</li>
 *   <li>Server sends the returned completions back to the client</li>
 *   <li>Client displays the completions in a popup menu</li>
 * </ol>
 * 
 * <h2>Setting Up Tab Completion</h2>
 * There are three ways to add tab completion to your plugin commands:
 * 
 * <h3>Option 1: Implement TabCompleter separately</h3>
 * <pre>
 * public class MyPlugin extends JavaPlugin {
 *     public void onEnable() {
 *         getCommand("mycommand").setTabCompleter(new MyTabCompleter());
 *     }
 * }
 * 
 * public class MyTabCompleter implements TabCompleter {
 *     public List&lt;String&gt; onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
 *         // Return completions based on args
 *         return TabCompletions.filter(args[0], "option1", "option2");
 *     }
 * }
 * </pre>
 * 
 * <h3>Option 2: Have your plugin implement TabCompleter</h3>
 * <pre>
 * public class MyPlugin extends JavaPlugin implements TabCompleter {
 *     public void onEnable() {
 *         getCommand("mycommand").setTabCompleter(this);
 *     }
 *     
 *     public List&lt;String&gt; onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
 *         if (args.length == 1) {
 *             return TabCompletions.filter(args[0], "create", "delete", "list");
 *         }
 *         return TabCompletions.empty();
 *     }
 * }
 * </pre>
 * 
 * <h3>Option 3: Use TabExecutor (recommended)</h3>
 * <p>
 * If your executor also handles tab completion, implement {@link TabExecutor}
 * which combines both interfaces. When set as executor, it will automatically
 * be used for tab completion without needing to call setTabCompleter().
 * </p>
 * <pre>
 * public class MyPlugin extends JavaPlugin implements TabExecutor {
 *     public void onEnable() {
 *         getCommand("mycommand").setExecutor(this);
 *         // No need for setTabCompleter - it's automatic!
 *     }
 *     
 *     public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
 *         // Handle command
 *         return true;
 *     }
 *     
 *     public List&lt;String&gt; onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
 *         // Handle tab completion
 *         return TabCompletions.players(args[0]);
 *     }
 * }
 * </pre>
 * 
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Use {@link TabCompletions} helper methods for common patterns</li>
 *   <li>Filter results by the current argument prefix for better UX</li>
 *   <li>Check permissions before returning sensitive completions</li>
 *   <li>Return an empty list (not null) when no completions are available</li>
 *   <li>Keep completion lists reasonable in size (under 100 items)</li>
 * </ul>
 * 
 * @see TabExecutor
 * @see TabCompletions
 * @see PluginCommand#setTabCompleter(TabCompleter)
 */
public interface TabCompleter {
    
    /**
     * Requests a list of possible completions for a command argument.
     * <p>
     * This method is called when a player presses Tab while typing a command
     * that belongs to this TabCompleter.
     *
     * @param sender Source of the command. For players tab-completing a
     *               command, this will be a Player instance.
     * @param command Command which was executed
     * @param alias The alias used
     * @param args The arguments passed to the command, including final
     *             partial argument to be completed and command label
     * @return A List of possible completions for the final argument,
     *         or null to default to the command's tabComplete method
     */
    List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args);
}

