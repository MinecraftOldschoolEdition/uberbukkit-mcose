package org.bukkit.command.defaults;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.EntityPlayer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PlayerArgumentResolver;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Legacy player-name adaptation of the 26.3 /spectate command. */
public class SpectateCommand extends VanillaCommand {
    public SpectateCommand() {
        super("spectate");
        this.description = "Makes a spectator view another player";
        this.usageMessage = "/spectate [target] [player]";
        this.setPermission("minecraft.command.spectate");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        if (args.length > 2 || args.length == 0 && !(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Usage: " + this.usageMessage);
            return true;
        }

        Player subject;
        if (args.length == 2) {
            subject = PlayerArgumentResolver.resolveSingle(sender, args[1]);
            if (subject == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }
        } else {
            subject = (Player) sender;
        }

        EntityPlayer subjectHandle = ((CraftPlayer) subject).getHandle();
        if (!subjectHandle.isSpectator()) {
            sender.sendMessage(ChatColor.RED + subject.getName() + " is not in spectator mode.");
            return true;
        }
        if (args.length == 0) {
            subjectHandle.setSpectatorTarget(null);
            sender.sendMessage(ChatColor.GRAY + "Stopped spectating.");
            return true;
        }

        Player target = PlayerArgumentResolver.resolveSingle(sender, args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }
        if (target == subject) {
            sender.sendMessage(ChatColor.RED + "You cannot spectate yourself.");
            return true;
        }

        EntityPlayer targetHandle = ((CraftPlayer) target).getHandle();
        // Move the spectator first even in the same world. This loads/tracks the
        // target before the camera payload reaches the legacy client.
        if (!subject.teleport(target.getLocation())) {
            sender.sendMessage(ChatColor.RED + "The spectator teleport was cancelled.");
            return true;
        }
        subjectHandle.setSpectatorTarget(targetHandle);
        sender.sendMessage(ChatColor.GRAY + subject.getName() + " is now spectating " + target.getName() + ".");
        return true;
    }

    @Override
    public boolean matches(String input) {
        return input.equals("spectate") || input.startsWith("spectate ");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        return new ArrayList<String>(PlayerArgumentResolver.suggest(prefix));
    }
}
