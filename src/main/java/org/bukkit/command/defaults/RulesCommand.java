package org.bukkit.command.defaults;

import java.util.List;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.ServerRules;
import net.minecraft.server.network.ServerRulesProtocol;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Opens the client-side server-rules screen, or its permission-protected editor. */
public class RulesCommand extends VanillaCommand {
    public RulesCommand() {
        super("rules");
        this.description = "Shows this server's rules.";
        this.usageMessage = "/rules [edit]";
        this.setPermission("bukkit.command.rules");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (args.length > 1 || (args.length == 1 && !"edit".equalsIgnoreCase(args[0]))) {
            sender.sendMessage(ChatColor.RED + "Usage: " + this.usageMessage);
            return true;
        }

        boolean edit = args.length == 1;
        if (edit) {
            if (!sender.hasPermission("bukkit.command.rules.edit")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to edit server rules.");
                return true;
            }
        } else if (!testPermission(sender)) {
            return true;
        }

        if (sender instanceof Player) {
            EntityPlayer player = ((CraftPlayer) sender).getHandle();
            if (player != null && player.netServerHandler != null && player.netServerHandler.supportsServerRules()) {
                ServerRules.sendScreen(player, edit ? ServerRulesProtocol.SCREEN_EDIT : ServerRulesProtocol.SCREEN_VIEW);
                return true;
            }
            sender.sendMessage(ChatColor.RED + "This command's GUI requires a current MCOSE client.");
        }

        List<String> rules = ServerRules.getRules(((org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer()).getServer());
        sender.sendMessage(ChatColor.YELLOW + "Server Rules:");
        for (int i = 0; i < rules.size(); ++i) {
            sender.sendMessage(ChatColor.GRAY + "- " + (String) rules.get(i));
        }
        if (edit) {
            sender.sendMessage(ChatColor.GRAY + "The rules editor is available from a current MCOSE client.");
        }
        return true;
    }

    @Override
    public boolean matches(String input) {
        return input.equalsIgnoreCase("rules");
    }
}
