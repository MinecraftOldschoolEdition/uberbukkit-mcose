package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

public class BanCommand extends VanillaCommand {
    public BanCommand() {
        super("ban");
        this.description = "Prevents the specified player from using this server";
        this.usageMessage = "/ban <player> [reason]";
        this.setPermission("bukkit.command.ban.player");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }

        // Build the reason from remaining arguments
        String reason;
        if (args.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) sb.append(" ");
                sb.append(args[i]);
            }
            reason = sb.toString();
        } else {
            reason = "Banned by an operator.";
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(args[0]);
        offlinePlayer.setBanned(true, reason);
        Command.broadcastCommandMessage(sender, "Banning " + args[0] + ": " + reason);
        
        // If player is online, kick them with the ban reason
        Player onlinePlayer = Bukkit.getPlayerExact(args[0]);
        if (onlinePlayer != null) {
            onlinePlayer.kickPlayer("You have been banned: " + reason);
        }

        return true;
    }

    @Override
    public boolean matches(String input) {
        return input.startsWith("ban ");
    }
    
    @Override
    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<String>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}
