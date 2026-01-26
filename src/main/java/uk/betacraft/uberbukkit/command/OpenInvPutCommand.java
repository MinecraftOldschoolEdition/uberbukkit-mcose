package uk.betacraft.uberbukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;

public class OpenInvPutCommand extends Command {

    public OpenInvPutCommand(String name) {
        super(name);
        this.description = "Move an item from target player slot into viewer's specific inventory slot (swap if occupied)";
        this.usageMessage = "/" + name + " <player> <fromIdx> <viewerSlot> [expId expCnt expDmg tx]";
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
            sender.sendMessage("Usage: /" + label + " <player> <fromIdx> <viewerSlot> [expId expCnt expDmg tx]");
            return true;
        }
        Player target = Bukkit.getServer().getPlayer(args[0]);
        if (target == null) {
            return true;
        }
        int fromIdx, viewerSlot;
        try { fromIdx = Integer.parseInt(args[1]); viewerSlot = Integer.parseInt(args[2]); } catch (NumberFormatException e) { return true; }

        int expId=-1, expCnt=-1, expDmg=-1; String txid = null; boolean hasValidator=false;
        if (args.length >= 7) {
            try {
                expId = Integer.parseInt(args[3]);
                expCnt = Integer.parseInt(args[4]);
                expDmg = Integer.parseInt(args[5]);
                txid = args[6];
                hasValidator = true;
            } catch (Exception ignore) { hasValidator = false; }
        }

        net.minecraft.server.EntityPlayer v = ((CraftPlayer) viewer).getHandle();
        net.minecraft.server.EntityPlayer t = ((CraftPlayer) target).getHandle();

        // Resolve source from target inventory
        net.minecraft.server.ItemStack src = null;
        boolean fromArmor = false;
        if (fromIdx >= 0 && fromIdx < 36) {
            src = t.inventory.items[fromIdx];
        } else if (fromIdx >= 36 && fromIdx <= 39) {
            int armor = 39 - fromIdx;
            if (armor >= 0 && armor < t.inventory.armor.length) {
                src = t.inventory.armor[armor];
                fromArmor = true;
            }
        }

        if (src == null) {
            if (hasValidator) sendAck(viewer, txid, false);
            sendSnapshot(viewer, target.getName(), fromIdx, null);
            sendSnapshot(viewer, viewer.getName(), viewerSlot, getViewerSlot(v, viewerSlot));
            return true;
        }

        // Validator check for expected source
        if (hasValidator) {
            int aId = src.id, aCnt = src.count, aDmg = src.getData();
            if (!(aId == expId && aCnt == expCnt && aDmg == expDmg)) {
                sendAck(viewer, txid, false);
                sendSnapshot(viewer, target.getName(), fromIdx, fromArmor ? t.inventory.armor[39 - fromIdx] : t.inventory.items[fromIdx]);
                sendSnapshot(viewer, viewer.getName(), viewerSlot, getViewerSlot(v, viewerSlot));
                return true;
            }
        }

        // Destination pointer in viewer's inventory by ContainerPlayer slot index (9..35 main, 36..44 hotbar)
        int vItemsIndex = -1;
        if (viewerSlot >= 9 && viewerSlot <= 35) {
            vItemsIndex = viewerSlot; // direct mapping 9..35
        } else if (viewerSlot >= 36 && viewerSlot <= 44) {
            vItemsIndex = viewerSlot - 36; // hotbar 0..8
        } else {
            // Unsupported destination; fail and snapshot
            if (hasValidator) sendAck(viewer, txid, false);
            sendSnapshot(viewer, viewer.getName(), viewerSlot, getViewerSlot(v, viewerSlot));
            return true;
        }

        net.minecraft.server.ItemStack dest = v.inventory.items[vItemsIndex];

        // Perform swap
        if (fromIdx >= 0 && fromIdx < 36) {
            t.inventory.items[fromIdx] = dest;
        } else if (fromIdx >= 36 && fromIdx <= 39) {
            int armor = fromIdx - 36;
            if (armor >= 0 && armor < t.inventory.armor.length) t.inventory.armor[armor] = dest;
        }
        v.inventory.items[vItemsIndex] = src;

        // Update
        t.inventory.update();
        v.inventory.update();

        // Snapshots back to viewer
        sendSnapshot(viewer, target.getName(), fromIdx, fromIdx < 36 ? t.inventory.items[fromIdx] : t.inventory.armor[39 - fromIdx]);
        int sendViewerSlot = (viewerSlot >= 36 && viewerSlot <= 44) ? (viewerSlot - 36) : viewerSlot; // map to 0..35 for client
        sendSnapshot(viewer, viewer.getName(), sendViewerSlot, getViewerSlot(v, viewerSlot));
        if (hasValidator) sendAck(viewer, txid, true);
        return true;
    }

    private net.minecraft.server.ItemStack getViewerSlot(net.minecraft.server.EntityPlayer v, int viewerSlot) {
        if (viewerSlot >= 9 && viewerSlot <= 35) return v.inventory.items[viewerSlot];
        if (viewerSlot >= 36 && viewerSlot <= 44) return v.inventory.items[viewerSlot - 36];
        return null;
    }

    private void sendSnapshot(Player viewer, String targetName, int slotIdx, net.minecraft.server.ItemStack it) {
        int id = 0, cnt = 0, dmg = 0;
        if (it != null) { id = it.id; cnt = it.count; dmg = it.getData(); }
        int clientSlot = slotIdx;
        if (slotIdx >= 36 && slotIdx <= 39) {
            clientSlot = 100 + (39 - slotIdx);
        }
        viewer.sendMessage("[[OPENINV_SET:" + targetName + "|" + clientSlot + "|" + id + "|" + cnt + "|" + dmg + "]]" );
    }

    private void sendAck(Player viewer, String txid, boolean ok) {
        if (txid == null) return;
        viewer.sendMessage("[[OPENINV_TX:" + txid + "|" + (ok ? "OK" : "FAIL") + "]]" );
    }
}


