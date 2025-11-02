package uk.betacraft.uberbukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import uk.betacraft.uberbukkit.AdminRegistry;

public class FreezeCommand extends Command {
    public FreezeCommand(String name) {
        super(name);
        this.description = "Toggle freeze on a player (prevents movement and build/break)";
        this.usageMessage = "/" + name + " <playername>";
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) { sender.sendMessage("Usage: /" + label + " <playername>"); return true; }
        String target = args[0];
        boolean nowFrozen = AdminRegistry.toggleFrozen(target);
        sender.sendMessage((nowFrozen ? "§bFroze §f" : "§aUnfroze §f") + target);
        Player online = Bukkit.getServer().getPlayer(target);
        if (online != null) {
            if (nowFrozen) online.sendMessage("§cYou have been frozen by an administrator.");
            else online.sendMessage("§aYou are no longer frozen.");
        }
        return true;
    }
}


