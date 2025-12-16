package org.bukkit.command;

import org.bukkit.plugin.Plugin;
import java.util.List;

/**
 * Represents a {@link Command} belonging to a plugin
 */
public final class PluginCommand extends Command {
    private final Plugin owningPlugin;
    private CommandExecutor executor;
    private TabCompleter tabCompleter;

    protected PluginCommand(String name, Plugin owner) {
        super(name);
        this.executor = owner;
        this.owningPlugin = owner;
        this.usageMessage = "";
    }

    /**
     * Executes the command, returning its success
     *
     * @param sender       Source object which is executing this command
     * @param commandLabel The alias of the command used
     * @param args         All arguments passed to the command, split via ' '
     * @return true if the command was successful, otherwise false
     */
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        boolean success = false;

        if (!owningPlugin.isEnabled()) {
            return false;
        }

        if (!testPermission(sender)) {
            return true;
        }

        try {
            success = executor.onCommand(sender, this, commandLabel, args);
        } catch (Throwable ex) {
            throw new CommandException("Unhandled exception executing command '" + commandLabel + "' in plugin " + owningPlugin.getDescription().getFullName(), ex);
        }

        if (!success && usageMessage.length() > 0) {
            for (String line : usageMessage.replace("<command>", commandLabel).split("\n")) {
                sender.sendMessage(line);
            }
        }

        return success;
    }

    /**
     * Sets the {@link CommandExecutor} to run when parsing this command
     *
     * @param executor New executor to run
     */
    public void setExecutor(CommandExecutor executor) {
        this.executor = executor;
    }

    /**
     * Gets the {@link CommandExecutor} associated with this command
     *
     * @return CommandExecutor object linked to this command
     */
    public CommandExecutor getExecutor() {
        return executor;
    }

    /**
     * Gets the owner of this PluginCommand
     *
     * @return Plugin that owns this command
     */
    public Plugin getPlugin() {
        return owningPlugin;
    }
    
    /**
     * Sets the {@link TabCompleter} to run when tab-completing this command.
     * <p>
     * If no TabCompleter is specified, the command executor will be used
     * if it implements {@link TabCompleter}.
     *
     * @param completer New tab completer to use
     */
    public void setTabCompleter(TabCompleter completer) {
        this.tabCompleter = completer;
    }
    
    /**
     * Gets the {@link TabCompleter} associated with this command.
     *
     * @return TabCompleter object linked to this command, or null if not set
     */
    public TabCompleter getTabCompleter() {
        return tabCompleter;
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * Delegates to the tab completer if one is set. Otherwise, if the
     * executor implements {@link TabCompleter}, it will be used.
     */
    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!owningPlugin.isEnabled()) {
            return java.util.Collections.emptyList();
        }
        
        // First try the dedicated tab completer
        if (tabCompleter != null) {
            List<String> completions = tabCompleter.onTabComplete(sender, this, alias, args);
            if (completions != null) {
                return completions;
            }
        }
        
        // Fall back to executor if it implements TabCompleter
        if (executor instanceof TabCompleter) {
            List<String> completions = ((TabCompleter) executor).onTabComplete(sender, this, alias, args);
            if (completions != null) {
                return completions;
            }
        }
        
        // Default: return empty list
        return java.util.Collections.emptyList();
    }
}
