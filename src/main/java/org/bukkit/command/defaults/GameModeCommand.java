package org.bukkit.command.defaults;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PlayerArgumentResolver;
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

        java.util.List<Player> targets = new java.util.ArrayList<Player>();
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
            targets.add((Player) sender);
        } else {
            java.util.List<Player> playerFirstTargets = PlayerArgumentResolver.resolve(sender, args[0]);
            int playerFirstMode = parseGameMode(args[1]);
            if (!playerFirstTargets.isEmpty() && playerFirstMode >= 0) {
                targets.addAll(playerFirstTargets);
                gameMode = playerFirstMode;
            } else {
                // Mode-first compatibility: /gamemode <mode> <player>
                gameMode = parseGameMode(args[0]);
                if (gameMode < 0) {
                    sender.sendMessage(ChatColor.RED + "Unknown game mode: " + args[0]);
                    return false;
                }

                targets.addAll(PlayerArgumentResolver.resolve(sender, args[1]));
                if (targets.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "Can't find player " + args[1]);
                    return false;
                }
            }
        }

        if (targets.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Please specify a player!");
            return false;
        }

        String modeName = "survival";
        for (int i = 0; i < targets.size(); i++) {
            modeName = applyGameMode(targets.get(i), gameMode);
        }

        if (targets.size() == 1) {
            Player target = targets.get(0);
            if (!sender.equals(target)) {
                Command.broadcastCommandMessage(sender, "Set " + target.getName() + "'s game mode to " + modeName + " mode");
            }
        } else {
            Command.broadcastCommandMessage(sender, "Set " + targets.size() + " players to " + modeName + " mode");
        }
        return true;
    }

    private String applyGameMode(Player target, int gameMode) {
        EntityPlayer entityPlayer = ((org.bukkit.craftbukkit.entity.CraftPlayer) target).getHandle();
        WorldServer worldserver = ((org.bukkit.craftbukkit.CraftServer)entityPlayer.world.getServer()).getServer().getWorldServer(entityPlayer.dimension);

        if (gameMode == 1) {
            entityPlayer.gameMode = 1;
            entityPlayer.updateContainer();
            entityPlayer.netServerHandler.sendPacket(new Packet70Bed(3));
            entityPlayer.netServerHandler.sendPacket(new Packet70Bed(18)); // Disable hardcore hearts
            if (worldserver.worldData != null && worldserver.worldData.getTerrainType() == 3) {
                entityPlayer.netServerHandler.sendPacket(new Packet70Bed(2));
            }
            target.sendMessage(ChatColor.GRAY + "Your game mode has been updated to creative mode");
            return "creative";
        }

        if (gameMode == 2) {
            // Hardcore mode: uses survival mechanics but player is banned on death
            entityPlayer.gameMode = 2;
            entityPlayer.updateContainer();
            entityPlayer.netServerHandler.sendPacket(new Packet70Bed(4)); // Same HUD as survival
            entityPlayer.netServerHandler.sendPacket(new Packet70Bed(17)); // Enable hardcore hearts
            if (worldserver.worldData != null && worldserver.worldData.getTerrainType() == 3) {
                entityPlayer.netServerHandler.sendPacket(new Packet70Bed(2));
            }
            target.sendMessage(ChatColor.DARK_RED + "Your game mode has been updated to HARDCORE mode!");
            target.sendMessage(ChatColor.RED + "Warning: Death is permanent - you will be banned if you die!");
            return "hardcore";
        }

        entityPlayer.gameMode = 0;
        entityPlayer.updateContainer();
        entityPlayer.netServerHandler.sendPacket(new Packet70Bed(4));
        entityPlayer.netServerHandler.sendPacket(new Packet70Bed(18)); // Disable hardcore hearts
        if (worldserver.worldData != null && worldserver.worldData.getTerrainType() == 3) {
            entityPlayer.netServerHandler.sendPacket(new Packet70Bed(2));
        }
        target.sendMessage(ChatColor.GRAY + "Your game mode has been updated to survival mode");
        return "survival";
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
        completions.addAll(PlayerArgumentResolver.suggest(prefix));
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
            addModeSuggestions(completions, prefix);
            addPlayerSuggestions(completions, prefix);
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            if (parseGameMode(args[0]) >= 0) {
                addPlayerSuggestions(completions, prefix);
            } else if (PlayerArgumentResolver.isValidPlayerArgument(sender, args[0])) {
                addModeSuggestions(completions, prefix);
            } else {
                addModeSuggestions(completions, prefix);
                addPlayerSuggestions(completions, prefix);
            }
        }
        return completions;
    }
}
