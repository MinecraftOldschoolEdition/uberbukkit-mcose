package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldServer;
import net.minecraft.server.WorldData;

public class DifficultyCommand extends VanillaCommand {
    
    private static final String[] DIFFICULTY_NAMES = {"peaceful", "easy", "normal", "hard"};
    
    public DifficultyCommand() {
        super("difficulty");
        this.description = "Sets the difficulty level for the server";
        this.usageMessage = "/difficulty <peaceful|easy|normal|hard|0|1|2|3>";
        this.setPermission("bukkit.command.difficulty");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        MinecraftServer mcServer = ((CraftServer) Bukkit.getServer()).getServer();
        if (mcServer.worlds.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Error: No worlds loaded on the server.");
            return true;
        }

        // If no args, show current difficulty
        if (args.length == 0) {
            WorldData worldData = mcServer.worlds.get(0).worldData;
            int currentDiff = worldData.getDifficulty();
            sender.sendMessage("Current difficulty: " + ChatColor.YELLOW + DIFFICULTY_NAMES[currentDiff] + 
                              ChatColor.GRAY + " (" + currentDiff + ")");
            return true;
        }

        // Parse the difficulty argument
        String arg = args[0].toLowerCase();
        int newDifficulty = -1;
        
        // Try parsing as number first
        try {
            newDifficulty = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            // Try parsing as name
            switch (arg) {
                case "peaceful":
                case "p":
                    newDifficulty = 0;
                    break;
                case "easy":
                case "e":
                    newDifficulty = 1;
                    break;
                case "normal":
                case "n":
                    newDifficulty = 2;
                    break;
                case "hard":
                case "h":
                    newDifficulty = 3;
                    break;
            }
        }

        if (newDifficulty < 0 || newDifficulty > 3) {
            sender.sendMessage(ChatColor.RED + "Invalid difficulty. Use: peaceful, easy, normal, hard (or 0-3)");
            return false;
        }

        // Apply difficulty to all worlds
        for (WorldServer world : mcServer.worlds) {
            if (world.worldData != null) {
                world.worldData.setDifficulty(newDifficulty);
                
                // Update spawn behavior based on difficulty
                if (newDifficulty == 0) {
                    // Peaceful - disable monster spawning
                    world.spawnMonsters = 0;
                    world.setSpawnFlags(false, world.allowAnimals);
                } else {
                    // Easy/Normal/Hard - enable monster spawning
                    world.spawnMonsters = 1;
                    world.setSpawnFlags(true, world.allowAnimals);
                }
            }
        }

        String diffName = DIFFICULTY_NAMES[newDifficulty];
        sender.sendMessage(ChatColor.GREEN + "Difficulty set to " + ChatColor.YELLOW + diffName + 
                          ChatColor.GREEN + " for all worlds");
        
        // Log to console if not from console
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            Bukkit.getLogger().info(sender.getName() + " set difficulty to " + diffName);
        }

        return true;
    }

    @Override
    public boolean matches(String input) {
        if (input == null) return false;
        String lowerInput = input.toLowerCase();
        return lowerInput.startsWith("difficulty ") || lowerInput.equals("difficulty");
    }
    
    @Override
    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<String>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String diff : DIFFICULTY_NAMES) {
                if (diff.startsWith(prefix)) {
                    completions.add(diff);
                }
            }
        }
        return completions;
    }
}

