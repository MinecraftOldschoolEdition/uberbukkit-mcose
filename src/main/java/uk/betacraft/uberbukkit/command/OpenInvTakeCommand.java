package uk.betacraft.uberbukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;

public class OpenInvTakeCommand extends Command {

    public OpenInvTakeCommand(String name) {
        super(name);
        this.description = "Move an item from the viewer's inventory slot to the target player's slot (swap if occupied)";
        this.usageMessage = "/" + name + " <player> <viewerSlot> <toIdx> [expId expCnt expDmg tx]";
        this.setPermission("uberbukkit.openinv");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }
        Player viewer = (Player) sender;
        // Permission check: require op or uberbukkit.openinv permission
        if (!viewer.isOp() && !viewer.hasPermission("uberbukkit.openinv")) {
            return true; // Silently fail for internal commands
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /" + label + " <player> <viewerSlot> <toIdx> [expId expCnt expDmg tx]");
            return true;
        }
        Player target = Bukkit.getServer().getPlayer(args[0]);
        if (target == null) {
            return true;
        }
        int viewerSlot, toIdx;
        try { viewerSlot = Integer.parseInt(args[1]); toIdx = Integer.parseInt(args[2]); } catch (NumberFormatException e) { return true; }

        int expId=-1, expCnt=-1, expDmg=-1; String txid=null; boolean hasValidator=false;
        if (args.length >= 7) {
            try {
                expId = Integer.parseInt(args[3]);
                expCnt = Integer.parseInt(args[4]);
                expDmg = Integer.parseInt(args[5]);
                txid   = args[6];
                hasValidator = true;
            } catch (Exception ignore) { hasValidator = false; }
        }

        net.minecraft.server.EntityPlayer v = ((CraftPlayer) viewer).getHandle();
        net.minecraft.server.EntityPlayer t = ((CraftPlayer) target).getHandle();

        // Resolve viewer inventory index into items[]: container 9..35 => items[9..35]; container 36..44 => items[0..8]
        int vItemsIndex;
        if (viewerSlot >= 9 && viewerSlot <= 35) vItemsIndex = viewerSlot;
        else if (viewerSlot >= 36 && viewerSlot <= 44) vItemsIndex = viewerSlot - 36;
        else {
            if (hasValidator) sendAck(viewer, txid, false);
            return true;
        }

        net.minecraft.server.ItemStack src = v.inventory.items[vItemsIndex];
        if (src == null) {
            if (hasValidator) sendAck(viewer, txid, false);
            // snapshots
            sendViewerSnapshot(viewer, viewer, viewerSlot, null);
            sendTargetSnapshot(viewer, target, toIdx, (toIdx < 36 ? t.inventory.items[toIdx] : (toIdx <= 39 ? t.inventory.armor[39 - toIdx] : null)));
            return true;
        }

        // Validator
        if (hasValidator) {
            int aId = src.id, aCnt = src.count, aDmg = src.getData();
            if (!(aId == expId && aCnt == expCnt && aDmg == expDmg)) {
                sendAck(viewer, txid, false);
                sendViewerSnapshot(viewer, viewer, viewerSlot, v.inventory.items[vItemsIndex]);
                sendTargetSnapshot(viewer, target, toIdx, (toIdx < 36 ? t.inventory.items[toIdx] : (toIdx <= 39 ? t.inventory.armor[39 - toIdx] : null)));
                return true;
            }
        }

        // Destination target slot
        net.minecraft.server.ItemStack dest;
        if (toIdx >= 0 && toIdx < 36) {
            dest = t.inventory.items[toIdx];
            t.inventory.items[toIdx] = src;
        } else if (toIdx >= 36 && toIdx <= 39) {
            int armor = toIdx - 36;
            if (armor < 0 || armor >= t.inventory.armor.length) {
                if (hasValidator) sendAck(viewer, txid, false);
                return true;
            }
            dest = t.inventory.armor[armor];
            t.inventory.armor[armor] = src;
        } else {
            if (hasValidator) sendAck(viewer, txid, false);
            return true;
        }

        // Place swapped item back into viewer slot
        v.inventory.items[vItemsIndex] = dest;

        t.inventory.update();
        v.inventory.update();

        // Snapshots
        sendTargetSnapshot(viewer, target, toIdx, (toIdx < 36 ? t.inventory.items[toIdx] : t.inventory.armor[39 - toIdx]));
        sendViewerSnapshot(viewer, viewer, viewerSlot, v.inventory.items[vItemsIndex]);

        if (hasValidator) sendAck(viewer, txid, true);
        return true;
    }

    private void sendTargetSnapshot(Player viewer, Player target, int slotIdx, net.minecraft.server.ItemStack it) {
        int id=0,cnt=0,dmg=0; if (it != null) { id=it.id; cnt=it.count; dmg=it.getData(); }
        int clientSlot = slotIdx;
        if (slotIdx >= 36 && slotIdx <= 39) clientSlot = 100 + (39 - slotIdx);
        viewer.sendMessage("[[OPENINV_SET:" + target.getName() + "|" + clientSlot + "|" + id + "|" + cnt + "|" + dmg + "]]" );
    }

    private void sendViewerSnapshot(Player viewer, Player v, int containerSlot, net.minecraft.server.ItemStack it) {
        // Client expects 0..35 indexing for main inventory (0..8 hotbar, 9..35 main)
        int clientSlot = (containerSlot >= 36 && containerSlot <= 44) ? (containerSlot - 36) : containerSlot;
        int id=0,cnt=0,dmg=0; if (it != null) { id=it.id; cnt=it.count; dmg=it.getData(); }
        viewer.sendMessage("[[OPENINV_SET:" + v.getName() + "|" + clientSlot + "|" + id + "|" + cnt + "|" + dmg + "]]" );
    }

    private void sendAck(Player viewer, String txid, boolean ok) {
        if (txid == null) return;
        viewer.sendMessage("[[OPENINV_TX:" + txid + "|" + (ok ? "OK" : "FAIL") + "]]" );
    }
}


