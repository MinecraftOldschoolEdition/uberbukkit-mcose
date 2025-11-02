package uk.betacraft.uberbukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import uk.betacraft.uberbukkit.AdminRegistry;

public class MuteCommand extends Command {
    public MuteCommand(String name) {
        super(name);
        this.description = "Toggle mute on a player (blocks chat messages)";
        this.usageMessage = "/" + name + " <playername>";
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) { sender.sendMessage("Usage: /" + label + " <playername>"); return true; }
        String target = args[0];
        boolean nowMuted = AdminRegistry.toggleMuted(target);
        sender.sendMessage((nowMuted ? "§bMuted §f" : "§aUnmuted §f") + target);
        Player online = Bukkit.getServer().getPlayer(target);
        if (online != null) {
            if (nowMuted) online.sendMessage("§cYou have been muted by an administrator.");
            else online.sendMessage("§aYou are no longer muted.");
        }
        return true;
    }
}


