package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.CraftWorld;
import net.minecraft.server.WorldGenHerobrineShrine;

public class LocateCommand extends VanillaCommand {
    public LocateCommand() {
        super("locate");
        this.description = "Locates the nearest structure";
        this.usageMessage = "/locate <structure>";
        this.setPermission("bukkit.command.locate");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            sender.sendMessage(ChatColor.GRAY + "Available structures: dungeon, herobrine_shrine");
            return false;
        }

        // Get world and player position
        World bworld;
        int playerX, playerZ;
        if (sender instanceof Player) {
            Player p = (Player) sender;
            bworld = p.getWorld();
            Location loc = p.getLocation();
            playerX = loc.getBlockX();
            playerZ = loc.getBlockZ();
        } else {
            bworld = Bukkit.getWorlds().get(0);
            playerX = bworld.getSpawnLocation().getBlockX();
            playerZ = bworld.getSpawnLocation().getBlockZ();
        }

        net.minecraft.server.WorldServer world = ((CraftWorld) bworld).getHandle();
        String structure = args[0].toLowerCase().replace("minecraft:", "");

        int[] result = null;
        String structureName = null;

        switch (structure) {
            case "herobrine_shrine":
            case "shrine":
                structureName = "Herobrine Shrine";
                sender.sendMessage(ChatColor.YELLOW + "Searching for Herobrine Shrine... (this may take a moment)");
                result = WorldGenHerobrineShrine.findNearestShrine(world, playerX, playerZ, 1000);
                break;

            case "dungeon":
                structureName = "Dungeon";
                sender.sendMessage(ChatColor.RED + "Dungeons cannot be located (they are underground). Try exploring caves!");
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown structure: " + args[0]);
                sender.sendMessage(ChatColor.GRAY + "Available structures: dungeon, herobrine_shrine");
                return true;
        }

        if (result != null) {
            int x = result[0];
            int y = result[1];
            int z = result[2];
            int distance = (int) Math.sqrt((x - playerX) * (x - playerX) + (z - playerZ) * (z - playerZ));

            sender.sendMessage(ChatColor.GREEN + "The nearest " + structureName + " is at " +
                ChatColor.AQUA + "[" + x + ", ~, " + z + "]" + ChatColor.GREEN +
                " (" + distance + " blocks away)");
            
            // Show clickable teleport suggestion for players
            if (sender instanceof Player) {
                sender.sendMessage(ChatColor.GRAY + "Tip: Use /tp " + x + " " + y + " " + z + " to teleport there");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Could not find a " + structureName + " within search radius.");
            if (structure.equals("herobrine_shrine") || structure.equals("shrine")) {
                sender.sendMessage(ChatColor.GRAY + "Herobrine Shrines only spawn in deserts and are extremely rare (1 in 750,000 chunks).");
            }
        }

        return true;
    }

    @Override
    public boolean matches(String input) {
        return input.startsWith("locate ");
    }
    
    @Override
    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<String>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            String[] structures = {"dungeon", "herobrine_shrine", "shrine"};
            for (String s : structures) {
                if (s.startsWith(prefix)) {
                    completions.add(s);
                }
            }
        }
        return completions;
    }
}
