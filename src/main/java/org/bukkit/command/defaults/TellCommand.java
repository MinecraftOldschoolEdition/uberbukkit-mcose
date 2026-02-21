package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PlayerArgumentResolver;
import org.bukkit.entity.Player;

public class TellCommand extends VanillaCommand {
    public TellCommand() {
        super("tell");
        this.description = "Sends a private message to the given player";
        this.usageMessage = "/tell <player> <message>";
        this.setPermission("bukkit.command.tell");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }

        java.util.List<Player> targets = PlayerArgumentResolver.resolve(sender, args[0]);
        if (sender instanceof Player) {
            Player source = (Player) sender;
            java.util.ArrayList<Player> visibleTargets = new java.util.ArrayList<Player>();
            for (int i = 0; i < targets.size(); i++) {
                Player candidate = targets.get(i);
                if (source.canSee(candidate)) {
                    visibleTargets.add(candidate);
                }
            }
            targets = visibleTargets;
        }

        if (targets.isEmpty()) {
            sender.sendMessage("There's no player by that name online.");
        } else {
            String message = "";

            for (int i = 1; i < args.length; i++) {
                if (i > 1) message += " ";
                message += args[i];
            }

            String result = ChatColor.GRAY + sender.getName() + " whispers " + message;

            for (int i = 0; i < targets.size(); i++) {
                Player target = targets.get(i);
                if (sender instanceof ConsoleCommandSender) {
                    Bukkit.getLogger().info("[" + sender.getName() + "->" + target.getName() + "] " + message);
                }
                target.sendMessage(result);
            }
        }

        return true;
    }

    @Override
    public boolean matches(String input) {
        return input.startsWith("tell ");
    }
    
    @Override
    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<String>();
        if (args.length == 1) {
            completions.addAll(PlayerArgumentResolver.suggest(args[0].toLowerCase()));
        }
        return completions;
    }
}
