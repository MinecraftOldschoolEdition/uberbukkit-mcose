package org.bukkit.command.defaults;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PlayerArgumentResolver;
import org.bukkit.entity.Player;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.GameType;
import net.minecraft.server.Packet70Bed;
import net.minecraft.server.WorldServer;

public class GameModeCommand extends VanillaCommand {
    public GameModeCommand() {
        super("gamemode");
        this.description = "Changes the player to a specific game mode";
        this.usageMessage = "/gamemode <player> <survival|creative|hardcore|spectator|s|c|h|sp|0|1|2|3>";
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

        entityPlayer.setGameMode(gameMode);
        entityPlayer.syncGameModeToClient();
        if (worldserver.worldData != null && worldserver.worldData.getTerrainType() == 3) {
            entityPlayer.netServerHandler.sendPacket(new Packet70Bed(2));
        }

        GameType selected = GameType.byId(gameMode);
        if (selected == GameType.HARDCORE) {
            target.sendMessage(ChatColor.DARK_RED + "Your game mode has been updated to HARDCORE mode!");
            target.sendMessage(ChatColor.RED + "Warning: Death is permanent - you will be banned if you die!");
            return "hardcore";
        }
        if (selected == GameType.SPECTATOR) {
            target.sendMessage(ChatColor.GRAY + "Your game mode has been updated to spectator mode");
            return "spectator";
        }
        target.sendMessage(ChatColor.GRAY + "Your game mode has been updated to " + selected.getName() + " mode");
        return selected.getName();
    }

    private int parseGameMode(String token) {
        return GameType.parse(token);
    }

    private void addModeSuggestions(java.util.List<String> completions, String prefix) {
        String[] modes = GameType.getCommandSuggestions();
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
