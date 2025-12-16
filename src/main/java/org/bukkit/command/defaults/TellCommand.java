package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
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

        Player player = Bukkit.getPlayerExact(args[0]);

        if (player == null || (sender instanceof Player && !((Player) sender).canSee(player))) {
            sender.sendMessage("There's no player by that name online.");
        } else {
            String message = "";

            for (int i = 1; i < args.length; i++) {
                if (i > 1) message += " ";
                message += args[i];
            }

            String result = ChatColor.GRAY + sender.getName() + " whispers " + message;

            if (sender instanceof ConsoleCommandSender) {
                Bukkit.getLogger().info("[" + sender.getName() + "->" + player.getName() + "] " + message);
                Bukkit.getLogger().info(result);
            }

            player.sendMessage(result);
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
            String prefix = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}
