package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.Packet70Bed;
import net.minecraft.server.WorldServer;

public class GameModeCommand extends VanillaCommand {
    public GameModeCommand() {
        super("gamemode");
        this.description = "Changes the player to a specific game mode";
        this.usageMessage = "/gamemode <player> <survival|creative|hardcore|s|c|h|0|1|2>";
        this.setPermission("bukkit.command.gamemode");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;

        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }

        Player target;
        int gameMode;

        if (args.length == 1) {
            gameMode = parseGameMode(args[0]);
            if (gameMode < 0) {
                sender.sendMessage(ChatColor.RED + "Unknown game mode: " + args[0]);
                return false;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Please specify a player!");
                return false;
            }
            target = (Player) sender;
        } else {
            Player playerFirstTarget = Bukkit.getPlayerExact(args[0]);
            int playerFirstMode = parseGameMode(args[1]);
            if (playerFirstTarget != null && playerFirstMode >= 0) {
                target = playerFirstTarget;
                gameMode = playerFirstMode;
            } else {
                // Mode-first compatibility: /gamemode <mode> <player>
                gameMode = parseGameMode(args[0]);
                if (gameMode < 0) {
                    sender.sendMessage(ChatColor.RED + "Unknown game mode: " + args[0]);
                    return false;
                }

                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Can't find player " + args[1]);
                    return false;
                }
            }
        }

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Please specify a player!");
            return false;
        }

        EntityPlayer entityPlayer = ((org.bukkit.craftbukkit.entity.CraftPlayer) target).getHandle();
        String modeName;

        if (gameMode == 1) {
            entityPlayer.gameMode = 1;
            entityPlayer.updateContainer();
            entityPlayer.netServerHandler.sendPacket(new Packet70Bed(3));
            entityPlayer.netServerHandler.sendPacket(new Packet70Bed(18)); // Disable hardcore hearts
            WorldServer worldserver = ((org.bukkit.craftbukkit.CraftServer)entityPlayer.world.getServer()).getServer().getWorldServer(entityPlayer.dimension);
            if (worldserver.worldData != null && worldserver.worldData.getTerrainType() == 3) {
                entityPlayer.netServerHandler.sendPacket(new Packet70Bed(2));
            }
            modeName = "creative";
            target.sendMessage(ChatColor.GRAY + "Your game mode has been updated to creative mode");
        } else if (gameMode == 2) {
            // Hardcore mode: uses survival mechanics but player is banned on death
            entityPlayer.gameMode = 2;
            entityPlayer.updateContainer();
            entityPlayer.netServerHandler.sendPacket(new Packet70Bed(4)); // Same HUD as survival
            entityPlayer.netServerHandler.sendPacket(new Packet70Bed(17)); // Enable hardcore hearts
            WorldServer worldserver = ((org.bukkit.craftbukkit.CraftServer)entityPlayer.world.getServer()).getServer().getWorldServer(entityPlayer.dimension);
            if (worldserver.worldData != null && worldserver.worldData.getTerrainType() == 3) {
                entityPlayer.netServerHandler.sendPacket(new Packet70Bed(2));
            }
            modeName = "hardcore";
            target.sendMessage(ChatColor.DARK_RED + "Your game mode has been updated to HARDCORE mode!");
            target.sendMessage(ChatColor.RED + "Warning: Death is permanent - you will be banned if you die!");
        } else {
            entityPlayer.gameMode = 0;
            entityPlayer.updateContainer();
            entityPlayer.netServerHandler.sendPacket(new Packet70Bed(4));
            entityPlayer.netServerHandler.sendPacket(new Packet70Bed(18)); // Disable hardcore hearts
            WorldServer worldserver = ((org.bukkit.craftbukkit.CraftServer)entityPlayer.world.getServer()).getServer().getWorldServer(entityPlayer.dimension);
            if (worldserver.worldData != null && worldserver.worldData.getTerrainType() == 3) {
                entityPlayer.netServerHandler.sendPacket(new Packet70Bed(2));
            }
            modeName = "survival";
            target.sendMessage(ChatColor.GRAY + "Your game mode has been updated to survival mode");
        }

        if (!sender.equals(target)) {
            Command.broadcastCommandMessage(sender, "Set " + target.getName() + "'s game mode to " + modeName + " mode");
        }
        return true;
    }

    private int parseGameMode(String token) {
        String modeString = token == null ? "" : token.toLowerCase();
        if (modeString.equals("survival") || modeString.equals("s") || modeString.equals("0")) {
            return 0;
        }
        if (modeString.equals("creative") || modeString.equals("c") || modeString.equals("1")) {
            return 1;
        }
        if (modeString.equals("hardcore") || modeString.equals("h") || modeString.equals("2")) {
            return 2;
        }
        return -1;
    }

    private void addModeSuggestions(java.util.List<String> completions, String prefix) {
        String[] modes = {"survival", "creative", "hardcore", "s", "c", "h", "0", "1", "2"};
        for (String mode : modes) {
            if (mode.startsWith(prefix)) {
                completions.add(mode);
            }
        }
    }

    private void addPlayerSuggestions(java.util.List<String> completions, String prefix) {
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(prefix)) {
                completions.add(p.getName());
            }
        }
    }

    private boolean isOnlinePlayerName(String name) {
        if (name == null || name.length() == 0) {
            return false;
        }
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matches(String input) {
        return input.startsWith("gamemode ");
    }
    
    @Override
    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<String>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            addPlayerSuggestions(completions, prefix);
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            if (isOnlinePlayerName(args[0])) {
                addModeSuggestions(completions, prefix);
            } else if (parseGameMode(args[0]) >= 0) {
                addPlayerSuggestions(completions, prefix);
            } else {
                addModeSuggestions(completions, prefix);
            }
        }
        return completions;
    }
}
