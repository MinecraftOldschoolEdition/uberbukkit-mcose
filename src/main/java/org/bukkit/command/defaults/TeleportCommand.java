package org.bukkit.command.defaults;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PlayerArgumentResolver;
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
                java.util.List<org.bukkit.entity.Player> targets = new java.util.ArrayList<org.bukkit.entity.Player>();
                int idx = 0;
                if (args.length == 4) {
                    targets = PlayerArgumentResolver.resolve(sender, args[0]);
                    if (targets.isEmpty()) {
                        sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                        return true;
                    }
                    idx = 1;
                } else {
                    if (!(sender instanceof org.bukkit.entity.Player)) {
                        sender.sendMessage(ChatColor.RED + "Console must specify a player.");
                        return true;
                    }
                    targets.add((org.bukkit.entity.Player) sender);
                }

                try {
                    int x = 0;
                    int y = 0;
                    int z = 0;

                    for (int i = 0; i < targets.size(); i++) {
                        org.bukkit.entity.Player target = targets.get(i);
                        org.bukkit.World world = target.getWorld();
                        double baseX = target.getLocation().getX();
                        double baseY = target.getLocation().getY();
                        double baseZ = target.getLocation().getZ();
                        x = parseCoord(args[idx], baseX);
                        y = parseCoord(args[idx + 1], baseY);
                        z = parseCoord(args[idx + 2], baseZ);
                        target.teleport(new org.bukkit.Location(world, x + 0.5, y, z + 0.5));
                    }

                    if (targets.size() == 1) {
                        sender.sendMessage(ChatColor.YELLOW + "Teleported to " + x + ", " + y + ", " + z);
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "Teleported " + targets.size() + " players.");
                    }
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
                Player dest = PlayerArgumentResolver.resolveSingle(sender, args[0]);
                if (dest == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                    return true;
                }
                self.teleport(dest.getLocation());
                sender.sendMessage(ChatColor.YELLOW + "Teleported to " + dest.getName());
                return true;
            } else if (args.length >= 2) {
                java.util.List<Player> sources = PlayerArgumentResolver.resolve(sender, args[0]);
                if (sources.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                    return true;
                }
                Player dest = PlayerArgumentResolver.resolveSingle(sender, args[1]);
                if (dest == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                    return true;
                }
                for (int i = 0; i < sources.size(); i++) {
                    sources.get(i).teleport(dest.getLocation());
                }

                if (sources.size() == 1) {
                    sender.sendMessage(ChatColor.YELLOW + "Teleported " + sources.get(0).getName() + " to " + dest.getName());
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Teleported " + sources.size() + " players to " + dest.getName());
                }
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
            completions.addAll(PlayerArgumentResolver.suggest(prefix));
        }
        return completions;
    }
}
