package uk.betacraft.uberbukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class SetWorldSpawnCommand extends Command {
    public SetWorldSpawnCommand() {
        super("setworldspawn");
        this.setAliases(Arrays.asList("sws"));
        this.setDescription("Set the world's spawn point. Supports ~ relative coordinates.");
        this.setUsage("/setworldspawn [x] [y] [z]");
        this.setPermission("minecraft.command.setworldspawn");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!testPermission(sender)) return true;

        World world;
        double baseX, baseY, baseZ;
        if (sender instanceof Player) {
            Player p = (Player) sender;
            world = p.getWorld();
            baseX = p.getLocation().getX();
            baseY = p.getLocation().getY();
            baseZ = p.getLocation().getZ();
        } else {
            world = Bukkit.getWorlds().get(0);
            baseX = world.getSpawnLocation().getX();
            baseY = world.getSpawnLocation().getY();
            baseZ = world.getSpawnLocation().getZ();
        }

        int sx, sy, sz;
        try {
            if (args.length == 3) {
                sx = parseCoord(args[0], baseX);
                sy = parseCoord(args[1], baseY);
                sz = parseCoord(args[2], baseZ);
            } else if (args.length == 0 && sender instanceof Player) {
                Player p = (Player) sender;
                sx = p.getLocation().getBlockX();
                sy = p.getLocation().getBlockY();
                sz = p.getLocation().getBlockZ();
            } else {
                sender.sendMessage("§cUsage: /setworldspawn [x] [y] [z]");
                return true;
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cCoordinates must be numbers (or use ~ for relative).");
            return true;
        }

        world.setSpawnLocation(sx, sy, sz);
        sender.sendMessage("§eWorld spawn set to " + sx + ", " + sy + ", " + sz);
        return true;
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
}
