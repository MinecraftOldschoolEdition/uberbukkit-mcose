package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import net.minecraft.server.WorldData;
import net.minecraft.server.MinecraftServer;

public class GameruleCommand extends VanillaCommand {
    public GameruleCommand() {
        super("gamerule");
        this.description = "Sets or queries a game rule.";
        this.usageMessage = "/gamerule <rule name> [true|false]";
        this.setPermission("bukkit.command.gamerule");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }

        MinecraftServer mcServer = ((CraftServer) Bukkit.getServer()).getServer();
        if (mcServer.worlds.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Error: No worlds loaded on the server.");
            return true;
        }
        WorldData worldData = mcServer.worlds.get(0).worldData;
        if (worldData == null) {
            sender.sendMessage(ChatColor.RED + "Error: Could not retrieve world data for the primary world.");
            return true;
        }

        String ruleName = args[0].toLowerCase();

        if (args.length == 1) {
            if (ruleName.equals("dodaynightcycle")) {
                sender.sendMessage(args[0] + " = " + worldData.getDoDayNightCycle());
            } else if (ruleName.equals("tntexplodes")) {
                sender.sendMessage(args[0] + " = " + worldData.getTntexplodes());
            } else if (ruleName.equals("mobgriefing")) {
                sender.sendMessage(args[0] + " = " + worldData.getMobGriefing());
            } else {
                sender.sendMessage(ChatColor.RED + "Unknown game rule: " + args[0]);
                return false;
            }
        } else if (args.length == 2) {
            String valueStr = args[1].toLowerCase();
            boolean value;
            if (valueStr.equals("true")) {
                value = true;
            } else if (valueStr.equals("false")) {
                value = false;
            } else {
                sender.sendMessage(ChatColor.RED + "Invalid value for game rule. Use 'true' or 'false'.");
                return false;
            }

            if (ruleName.equals("dodaynightcycle")) {
                worldData.setDoDayNightCycle(value);
                sender.sendMessage("Game rule " + args[0] + " has been set to " + value);
                if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                    Bukkit.getLogger().info("User " + sender.getName() + " set game rule " + args[0] + " to " + value);
                }
            } else if (ruleName.equals("tntexplodes")) {
                worldData.setTntexplodes(value);
                sender.sendMessage("Game rule " + args[0] + " has been set to " + value);
                if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                    Bukkit.getLogger().info("User " + sender.getName() + " set game rule " + args[0] + " to " + value);
                }
            } else if (ruleName.equals("mobgriefing")) {
                worldData.setMobGriefing(value);
                sender.sendMessage("Game rule " + args[0] + " has been set to " + value);
                if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                    Bukkit.getLogger().info("User " + sender.getName() + " set game rule " + args[0] + " to " + value);
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Unknown game rule: " + args[0]);
                return false;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }

        return true;
    }

    @Override
    public boolean matches(String input) {
        if (input == null) return false;
        String lowerInput = input.toLowerCase();
        return lowerInput.startsWith("gamerule ") || lowerInput.equals("gamerule");
    }
}


