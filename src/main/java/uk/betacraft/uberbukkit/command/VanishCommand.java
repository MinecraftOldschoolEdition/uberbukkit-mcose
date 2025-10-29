package uk.betacraft.uberbukkit.command;

import net.minecraft.server.VanishAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanishCommand extends Command {
    public VanishCommand(String name) {
        super(name);
        this.description = "Toggle invisibility to other players";
        this.usageMessage = "/vanish [player]";
        this.setPermission("uberbukkit.command.vanish");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /vanish <player>");
                return true;
            }
            Player self = (Player) sender;
            boolean vanished = VanishAPI.toggle(self);
            self.sendMessage(vanished ? ChatColor.GRAY + "You have vanished." : ChatColor.GRAY + "You are now visible.");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        boolean vanished = VanishAPI.toggle(target);
        sender.sendMessage((vanished ? ChatColor.GREEN + "Vanished " : ChatColor.YELLOW + "Revealed ") + target.getName());
        if (sender != target) {
            target.sendMessage(vanished ? ChatColor.GRAY + "You have vanished." : ChatColor.GRAY + "You are now visible.");
        }
        return true;
    }
}


