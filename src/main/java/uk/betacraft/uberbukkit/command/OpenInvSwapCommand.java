package uk.betacraft.uberbukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;

public class OpenInvSwapCommand extends Command {

    public OpenInvSwapCommand(String name) {
        super(name);
        this.description = "Swap selected admin hotbar item with a target player's inventory slot";
        this.usageMessage = "/" + name + " <player> <slotIndex>";
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /" + label + " <player> <slotIndex>");
            return true;
        }
        Player viewer = (Player) sender;
        Player target = Bukkit.getServer().getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Can't find user " + args[0]);
            return true;
        }
        int idx;
        try { idx = Integer.parseInt(args[1]); } catch (NumberFormatException e) { sender.sendMessage("Bad slot index"); return true; }

        net.minecraft.server.EntityPlayer v = ((CraftPlayer) viewer).getHandle();
        net.minecraft.server.EntityPlayer t = ((CraftPlayer) target).getHandle();

        net.minecraft.server.ItemStack fromViewer = v.inventory.items[v.inventory.itemInHandIndex];
        net.minecraft.server.ItemStack fromTarget = null;

        if (idx >= 0 && idx < 36) {
            fromTarget = t.inventory.items[idx];
            t.inventory.items[idx] = fromViewer;
        } else if (idx >= 36 && idx <= 39) { // armor 39..36 -> helmet..boots
            int armor = 39 - idx;
            if (armor >= 0 && armor < t.inventory.armor.length) {
                fromTarget = t.inventory.armor[armor];
                t.inventory.armor[armor] = fromViewer;
            }
        } else {
            sender.sendMessage("Unsupported slot index.");
            return true;
        }

        // Place target's old item into viewer's current hotbar slot
        v.inventory.items[v.inventory.itemInHandIndex] = fromTarget;

        // Mark containers dirty so normal sync packets are sent
        t.inventory.update();
        v.inventory.update();

        // Send minimal snapshot lines back to the viewer to update the admin panel view
        int id = 0, cnt = 0, dmg = 0;
        net.minecraft.server.ItemStack ts = null;
        if (idx >= 0 && idx < 36) ts = t.inventory.items[idx];
        else if (idx >= 36 && idx <= 39) {
            int armor = 39 - idx;
            if (armor >= 0 && armor < t.inventory.armor.length) ts = t.inventory.armor[armor];
        }
        if (ts != null) { id = ts.id; cnt = ts.count; dmg = ts.getData(); }
        viewer.sendMessage("[[OPENINV_SET:" + target.getName() + "|" + idx + "|" + id + "|" + cnt + "|" + dmg + "]]" );
        return true;
    }
}


