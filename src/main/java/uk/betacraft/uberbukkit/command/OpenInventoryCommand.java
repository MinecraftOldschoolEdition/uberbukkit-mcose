package uk.betacraft.uberbukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OpenInventoryCommand extends Command {

    public OpenInventoryCommand(String name) {
        super(name);
        this.description = "View another player's inventory in a read-only GUI";
        this.usageMessage = "/" + name + " <playername>";
        this.setAliases(Arrays.asList("openinv"));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Player viewer = (Player) sender;
        if (args.length < 1) {
            sender.sendMessage("Usage: /" + label + " <playername>");
            return true;
        }
        String targetName = args[0];
        Player target = Bukkit.getServer().getPlayer(targetName);
        if (target == null) {
            sender.sendMessage("Can't find user " + targetName);
            return true;
        }
        // Send per-slot snapshot to the viewer (only non-empty slots) followed by the OPEN token
        org.bukkit.inventory.PlayerInventory inv = target.getInventory();
        org.bukkit.inventory.ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            org.bukkit.inventory.ItemStack it = contents[i];
            if (it != null && it.getTypeId() > 0 && it.getAmount() > 0) {
                int id = it.getTypeId();
                int cnt = it.getAmount();
                int dmg = it.getDurability();
                viewer.sendMessage("[[OPENINV_SET:" + target.getName() + "|" + i + "|" + id + "|" + cnt + "|" + dmg + "]]" );
            }
        }
        org.bukkit.inventory.ItemStack[] armor = inv.getArmorContents(); // helmet,chest,legs,boots
        // Map armor to slots 100..103 (0..3)
        for (int a = 0; a < armor.length; a++) {
            org.bukkit.inventory.ItemStack it = armor[a];
            if (it != null && it.getTypeId() > 0 && it.getAmount() > 0) {
                int id = it.getTypeId();
                int cnt = it.getAmount();
                int dmg = it.getDurability();
                int slot = 100 + a;
                viewer.sendMessage("[[OPENINV_SET:" + target.getName() + "|" + slot + "|" + id + "|" + cnt + "|" + dmg + "]]" );
            }
        }
        // Now send admin-open token (client will use the admin GUI asset)
        viewer.sendMessage("[[ADMININV:" + target.getName() + "]]" );
        return true;
    }
}


