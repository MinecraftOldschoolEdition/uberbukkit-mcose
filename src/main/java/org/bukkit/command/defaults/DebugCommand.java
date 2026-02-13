package org.bukkit.command.defaults;

import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;

import java.util.ArrayList;
import java.util.List;

public class DebugCommand extends VanillaCommand {

    public DebugCommand() {
        super("debug");
        this.description = "Debug helpers for runtime tuning";
        this.usageMessage = "/debug tickRate <tps|reset>";
        this.setPermission("bukkit.command.debug");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        MinecraftServer server = getMinecraftServer(sender);
        if (server == null) {
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: " + usageMessage);
            sender.sendMessage(ChatColor.GRAY + "Current tick rate: " + ChatColor.WHITE + formatTickRate(server.getDebugTickRateTps())
                + ChatColor.GRAY + " TPS (" + server.getTickIntervalMs() + "ms/tick)");
            return true;
        }

        if (!args[0].equalsIgnoreCase("tickRate")) {
            sender.sendMessage(ChatColor.RED + "Unknown debug target: " + args[0]);
            sender.sendMessage(ChatColor.YELLOW + "Usage: " + usageMessage);
            return true;
        }

        if (args.length == 1) {
            sender.sendMessage(ChatColor.GRAY + "Current tick rate: " + ChatColor.WHITE + formatTickRate(server.getDebugTickRateTps())
                + ChatColor.GRAY + " TPS (" + server.getTickIntervalMs() + "ms/tick)");
            return true;
        }

        if (args.length > 2) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }

        if (args[1].equalsIgnoreCase("reset")) {
            server.resetDebugTickRate();
            Command.broadcastCommandMessage(sender, "Reset debug tick rate to 20.00 TPS (50ms/tick)");
            return true;
        }

        float requestedRate;
        try {
            requestedRate = Float.parseFloat(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid tick rate: " + args[1]);
            sender.sendMessage(ChatColor.RED + "Tick rate must be a number between 1 and 1000, or 'reset'.");
            return true;
        }

        if (requestedRate < 1.0F || requestedRate > 1000.0F) {
            sender.sendMessage(ChatColor.RED + "Tick rate out of range. Use a value from 1 to 1000.");
            return true;
        }

        float appliedRate = server.setDebugTickRateTps(requestedRate);
        Command.broadcastCommandMessage(sender, "Set debug tick rate to " + formatTickRate(appliedRate)
            + " TPS (" + server.getTickIntervalMs() + "ms/tick)");
        return true;
    }

    @Override
    public boolean matches(String input) {
        return input != null && (input.equalsIgnoreCase("debug") || input.toLowerCase().startsWith("debug "));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        ArrayList<String> completions = new ArrayList<String>();
        if (args.length == 1) {
            if ("tickrate".startsWith(args[0].toLowerCase())) {
                completions.add("tickRate");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("tickRate")) {
            String prefix = args[1].toLowerCase();
            String[] values = new String[] { "reset", "1", "20", "40", "80", "200" };
            for (String value : values) {
                if (value.startsWith(prefix)) {
                    completions.add(value);
                }
            }
        }
        return completions;
    }

    private MinecraftServer getMinecraftServer(CommandSender sender) {
        if (!(Bukkit.getServer() instanceof CraftServer)) {
            sender.sendMessage(ChatColor.RED + "CraftServer is not available.");
            return null;
        }
        return ((CraftServer) Bukkit.getServer()).getServer();
    }

    private String formatTickRate(float tickRate) {
        return String.format(java.util.Locale.ROOT, "%.2f", tickRate);
    }
}
