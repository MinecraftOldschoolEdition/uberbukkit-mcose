package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeleportCommand extends VanillaCommand {
    public TeleportCommand() {
        super("tp");
        this.description = "Teleports the given player to another player";
        this.usageMessage = "/tp <player> <target>";
        this.setPermission("bukkit.command.teleport");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;

        try {
            // Support relative coordinates with '~'
            if ((args.length == 3 || args.length == 4)) {
                org.bukkit.entity.Player target;
                org.bukkit.World world;
                double baseX, baseY, baseZ;
                int idx = 0;
                if (args.length == 4) {
                    target = org.bukkit.Bukkit.getPlayerExact(args[0]);
                    if (target == null) {
                        sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                        return true;
                    }
                    world = target.getWorld();
                    baseX = target.getLocation().getX();
                    baseY = target.getLocation().getY();
                    baseZ = target.getLocation().getZ();
                    idx = 1;
                } else {
                    if (!(sender instanceof org.bukkit.entity.Player)) {
                        sender.sendMessage(ChatColor.RED + "Console must specify a player.");
                        return true;
                    }
                    target = (org.bukkit.entity.Player) sender;
                    world = target.getWorld();
                    baseX = target.getLocation().getX();
                    baseY = target.getLocation().getY();
                    baseZ = target.getLocation().getZ();
                }

                try {
                    int x = parseCoord(args[idx], baseX);
                    int y = parseCoord(args[idx + 1], baseY);
                    int z = parseCoord(args[idx + 2], baseZ);
                    target.teleport(new org.bukkit.Location(world, x + 0.5, y, z + 0.5));
                    sender.sendMessage(ChatColor.YELLOW + "Teleported to " + x + ", " + y + ", " + z);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Coordinates must be numbers (or use ~ for relative).");
                }
                return true;
            }

            // Fallback to vanilla tp behavior: tp <player> <target> or tp <target> (self)
            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
                return true;
            } else if (args.length == 1) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Console must specify two players.");
                    return true;
                }
                Player self = (Player) sender;
                Player dest = Bukkit.getPlayerExact(args[0]);
                if (dest == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                    return true;
                }
                self.teleport(dest.getLocation());
                sender.sendMessage(ChatColor.YELLOW + "Teleported to " + dest.getName());
                return true;
            } else if (args.length >= 2) {
                Player src = Bukkit.getPlayerExact(args[0]);
                Player dest = Bukkit.getPlayerExact(args[1]);
                if (src == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                    return true;
                }
                if (dest == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                    return true;
                }
                src.teleport(dest.getLocation());
                sender.sendMessage(ChatColor.YELLOW + "Teleported " + src.getName() + " to " + dest.getName());
                return true;
            }

            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return true;
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Error: " + ex.getMessage());
            ex.printStackTrace();
            return true;
        }
    }

    private int parseCoord(String token, double base) throws NumberFormatException {
        token = token.trim();
        if (token.equals("~")) {
            return (int) Math.floor(base);
        }
        if (token.startsWith("~")) {
            double offset = token.length() == 1 ? 0.0 : Double.parseDouble(token.substring(1));
            return (int) Math.floor(base + offset);
        }
        return (int) Math.floor(Double.parseDouble(token));
    }

    @Override
    public boolean matches(String input) {
        return input.startsWith("tp ");
    }
    
    @Override
    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<String>();
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        
        // Complete player names for first two arguments
        if (args.length <= 2) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}
