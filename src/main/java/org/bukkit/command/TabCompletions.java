package org.bukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Utility class providing common tab completion patterns for plugin developers.
 * <p>
 * This class contains static helper methods that can be used to easily generate
 * common completion lists like player names, boolean values, numbers, etc.
 * Using these helpers ensures consistent behavior and reduces boilerplate code.
 * 
 * <h2>Quick Reference</h2>
 * <table border="1">
 *   <tr><th>Method</th><th>Description</th><th>Example</th></tr>
 *   <tr><td>{@link #empty()}</td><td>Empty list</td><td>No completions</td></tr>
 *   <tr><td>{@link #players(String)}</td><td>Online players</td><td>Player names matching prefix</td></tr>
 *   <tr><td>{@link #allPlayers()}</td><td>All online players</td><td>All player names</td></tr>
 *   <tr><td>{@link #booleans(String)}</td><td>true/false</td><td>Boolean values matching prefix</td></tr>
 *   <tr><td>{@link #filter(String, String...)}</td><td>Filter options</td><td>Custom options matching prefix</td></tr>
 *   <tr><td>{@link #integers(String, int, int)}</td><td>Number range</td><td>Numbers matching prefix</td></tr>
 *   <tr><td>{@link #combine(List[])}</td><td>Merge lists</td><td>Combine multiple completion sources</td></tr>
 * </table>
 * 
 * <h2>Complete Example</h2>
 * <pre>
 * public List&lt;String&gt; onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
 *     switch (args.length) {
 *         case 1:
 *             // First arg: subcommand
 *             return TabCompletions.filter(args[0], "create", "delete", "list", "info");
 *             
 *         case 2:
 *             // Second arg: depends on subcommand
 *             if (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("info")) {
 *                 return TabCompletions.filter(args[1], getMyItemNames());
 *             } else if (args[0].equalsIgnoreCase("create")) {
 *                 return TabCompletions.players(args[1]);
 *             }
 *             break;
 *             
 *         case 3:
 *             // Third arg: a number between 1 and 10
 *             if (args[0].equalsIgnoreCase("create")) {
 *                 return TabCompletions.integers(args[2], 1, 10);
 *             }
 *             break;
 *     }
 *     return TabCompletions.empty();
 * }
 * </pre>
 * 
 * <h2>Combining with Custom Logic</h2>
 * <pre>
 * public List&lt;String&gt; onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
 *     if (args.length == 1) {
 *         // Combine players with special values
 *         return TabCompletions.combine(
 *             TabCompletions.players(args[0]),
 *             TabCompletions.filter(args[0], "@a", "@p", "@r")  // Selectors
 *         );
 *     }
 *     return TabCompletions.empty();
 * }
 * </pre>
 * 
 * @see TabCompleter
 * @see TabExecutor
 */
public final class TabCompletions {
    
    private TabCompletions() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Returns an empty completion list.
     *
     * @return An empty list
     */
    public static List<String> empty() {
        return new ArrayList<String>();
    }
    
    /**
     * Returns a list of online player names matching the given prefix.
     *
     * @param prefix The prefix to match (case-insensitive)
     * @return List of matching player names
     */
    public static List<String> players(String prefix) {
        List<String> completions = new ArrayList<String>();
        String lowerPrefix = prefix.toLowerCase();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(lowerPrefix)) {
                completions.add(p.getName());
            }
        }
        return completions;
    }
    
    /**
     * Returns a list of all online player names.
     *
     * @return List of all online player names
     */
    public static List<String> allPlayers() {
        List<String> completions = new ArrayList<String>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            completions.add(p.getName());
        }
        return completions;
    }
    
    /**
     * Returns "true" and "false" options matching the given prefix.
     *
     * @param prefix The prefix to match
     * @return List of matching boolean strings
     */
    public static List<String> booleans(String prefix) {
        return filter(prefix, "true", "false");
    }
    
    /**
     * Filters the given options to only those matching the prefix.
     *
     * @param prefix The prefix to match (case-insensitive)
     * @param options The options to filter
     * @return List of matching options
     */
    public static List<String> filter(String prefix, String... options) {
        return filter(prefix, Arrays.asList(options));
    }
    
    /**
     * Filters the given options to only those matching the prefix.
     *
     * @param prefix The prefix to match (case-insensitive)
     * @param options The options to filter
     * @return List of matching options
     */
    public static List<String> filter(String prefix, Collection<String> options) {
        List<String> completions = new ArrayList<String>();
        String lowerPrefix = prefix.toLowerCase();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lowerPrefix)) {
                completions.add(option);
            }
        }
        return completions;
    }
    
    /**
     * Returns a list of integers from min to max matching the prefix.
     *
     * @param prefix The prefix to match
     * @param min Minimum value (inclusive)
     * @param max Maximum value (inclusive)
     * @return List of matching integer strings
     */
    public static List<String> integers(String prefix, int min, int max) {
        List<String> completions = new ArrayList<String>();
        for (int i = min; i <= max; i++) {
            String s = String.valueOf(i);
            if (s.startsWith(prefix)) {
                completions.add(s);
            }
        }
        return completions;
    }
    
    /**
     * Combines multiple completion lists into one.
     *
     * @param lists The lists to combine
     * @return A combined list of all completions
     */
    @SafeVarargs
    public static List<String> combine(List<String>... lists) {
        List<String> result = new ArrayList<String>();
        for (List<String> list : lists) {
            if (list != null) {
                result.addAll(list);
            }
        }
        return result;
    }
}

