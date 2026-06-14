package org.bukkit.command.defaults;

import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class LightningCommand extends VanillaCommand {
    public LightningCommand() {
        super("lightning");
        this.description = "Strikes lightning for testing";
        this.usageMessage = "/lightning [x y z]";
        this.setPermission("bukkit.command.lightning");
        this.setAliases(Arrays.asList("strike"));
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;

        if (!(args.length == 0 || args.length == 3)) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return true;
        }

        World world;
        double x;
        double y;
        double z;
        if (sender instanceof Player) {
            Player player = (Player) sender;
            Location loc = player.getLocation();
            world = player.getWorld();
            if (args.length == 0) {
                Vector direction = loc.getDirection();
                x = loc.getX() + direction.getX() * 14.0D;
                y = loc.getY() + 1.0D + direction.getY() * 6.0D;
                z = loc.getZ() + direction.getZ() * 14.0D;
            } else {
                try {
                    x = parseCoord(args[0], loc.getX());
                    y = parseCoord(args[1], loc.getY());
                    z = parseCoord(args[2], loc.getZ());
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Coordinates must be numbers or relative values like ~4.");
                    return true;
                }
            }
        } else {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "No world is loaded.");
                return true;
            }
            Location spawn = world.getSpawnLocation();
            if (args.length == 0) {
                x = spawn.getX();
                y = spawn.getY();
                z = spawn.getZ();
            } else {
                try {
                    x = parseCoord(args[0], spawn.getX());
                    y = parseCoord(args[1], spawn.getY());
                    z = parseCoord(args[2], spawn.getZ());
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Coordinates must be numbers or relative values like ~4.");
                    return true;
                }
            }
        }

        world.strikeLightning(new Location(world, x, y, z));
        sender.sendMessage(ChatColor.YELLOW + String.format("Lightning struck at %.1f, %.1f, %.1f", x, y, z));
        return true;
    }

    private double parseCoord(String token, double base) throws NumberFormatException {
        token = token.trim();
        if (token.equals("~")) {
            return base;
        }
        if (token.startsWith("~")) {
            double offset = token.length() == 1 ? 0.0D : Double.parseDouble(token.substring(1));
            return base + offset;
        }
        return Double.parseDouble(token);
    }

    @Override
    public boolean matches(String input) {
        return input.equals("lightning") || input.startsWith("lightning ") || input.equals("strike") || input.startsWith("strike ");
    }

    @Override
    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
        return new java.util.ArrayList<String>();
    }
}
