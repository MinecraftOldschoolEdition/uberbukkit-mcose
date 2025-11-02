package uk.betacraft.uberbukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OpenInvSyncCommand extends Command {

    public OpenInvSyncCommand(String name) {
        super(name);
        this.description = "Request full inventory sync for admin panel";
        this.usageMessage = "/" + name + " <playername>";
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }
        if (args.length < 1) {
            return true;
        }
        Player viewer = (Player) sender;
        Player target = Bukkit.getServer().getPlayer(args[0]);
        if (target == null) {
            return true;
        }
        // Send full inventory snapshot
        OpenInventoryCommand.sendFullInventorySnapshotStatic(viewer, target);
        return true;
    }

}

