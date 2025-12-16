package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class WeatherCommand extends VanillaCommand {
    public WeatherCommand() {
        super("weather");
        this.description = "Sets the weather";
        this.usageMessage = "/weather <clear|rain|thunder> [durationSeconds]";
        this.setPermission("bukkit.command.weather");
        this.setAliases(java.util.Arrays.asList("toggledownfall"));
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;

        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }

        String mode = args[0].toLowerCase();
        int durationTicks = -1;
        if (args.length == 2) {
            try {
                int seconds = Integer.parseInt(args[1]);
                if (seconds < 0) seconds = 0;
                durationTicks = seconds * 20;
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid duration: " + args[1]);
                return false;
            }
        }

        for (World world : Bukkit.getWorlds()) {
            if (mode.equals("clear")) {
                world.setStorm(false);
                world.setThundering(false);
                if (durationTicks >= 0) {
                    world.setWeatherDuration(durationTicks);
                    world.setThunderDuration(0);
                }
            } else if (mode.equals("rain") || mode.equals("downfall")) {
                world.setStorm(true);
                world.setThundering(false);
                if (durationTicks >= 0) {
                    world.setWeatherDuration(durationTicks);
                    world.setThunderDuration(0);
                }
            } else if (mode.equals("thunder") || mode.equals("storm")) {
                world.setStorm(true);
                world.setThundering(true);
                if (durationTicks >= 0) {
                    world.setWeatherDuration(durationTicks);
                    world.setThunderDuration(durationTicks);
                }
            } else if (currentAlias.equalsIgnoreCase("toggledownfall")) {
                boolean makeStorm = !world.hasStorm();
                world.setStorm(makeStorm);
                if (!makeStorm) {
                    world.setThundering(false);
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Unknown weather type: " + args[0]);
                return false;
            }
        }

        Command.broadcastCommandMessage(sender, "Set weather to " + (currentAlias.equalsIgnoreCase("toggledownfall") ? "toggledownfall" : mode));
        return true;
    }

    @Override
    public boolean matches(String input) {
        return input.startsWith("weather ") || input.equals("weather") || input.equals("toggledownfall");
    }
    
    @Override
    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<String>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            String[] types = {"clear", "rain", "thunder"};
            for (String type : types) {
                if (type.startsWith(prefix)) {
                    completions.add(type);
                }
            }
        }
        return completions;
    }
}
