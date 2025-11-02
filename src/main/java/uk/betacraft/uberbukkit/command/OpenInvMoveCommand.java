package uk.betacraft.uberbukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;

public class OpenInvMoveCommand extends Command {

    public OpenInvMoveCommand(String name) {
        super(name);
        this.description = "Move an item inside a target player's inventory (A->B)";
        this.usageMessage = "/" + name + " <player> <fromIdx> <toIdx>";
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /" + label + " <player> <fromIdx> <toIdx>");
            return true;
        }
        Player viewer = (Player) sender;
        Player target = Bukkit.getServer().getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Can't find user " + args[0]);
            return true;
        }
        int from, to;
        try { from = Integer.parseInt(args[1]); to = Integer.parseInt(args[2]); } catch (NumberFormatException e) { sender.sendMessage("Bad slot index"); return true; }

        // Optional validator: expected stack at 'from' and tx id
        int expId = -1, expCnt = -1, expDmg = -1; String txid = null;
        boolean hasValidator = false;
        if (args.length >= 7) {
            try {
                expId = Integer.parseInt(args[3]);
                expCnt = Integer.parseInt(args[4]);
                expDmg = Integer.parseInt(args[5]);
                txid = args[6];
                hasValidator = true;
            } catch (Exception ignore) { hasValidator = false; }
        }

        net.minecraft.server.EntityPlayer t = ((CraftPlayer) target).getHandle();

        // Get source stack
        net.minecraft.server.ItemStack src = null;
        int srcType = 0; // 0 main/hotbar, 1 armor
        int srcIdx = from;
        if (from >= 0 && from < 36) {
            src = t.inventory.items[from];
            srcType = 0;
        } else if (from >= 36 && from <= 39) {
            int armor = from - 36;
            if (armor >= 0 && armor < t.inventory.armor.length) {
                src = t.inventory.armor[armor];
                srcType = 1;
            }
        }
        if (src == null) {
            if (hasValidator) sendAck(viewer, txid, false);
            // Also snapshot the involved slots so client can heal ghosts
            sendSnapshot(viewer, target.getName(), from, (srcType == 0 ? null : null));
            sendSnapshot(viewer, target.getName(), to, (to < 36 ? t.inventory.items[to] : (to >= 36 && to <= 39 ? t.inventory.armor[39 - to] : null)));
            return true;
        }

        // Validator check
        if (hasValidator) {
            int aid = src.id, acnt = src.count, admg = src.getData();
            if (!(aid == expId && acnt == expCnt && admg == expDmg)) {
                sendAck(viewer, txid, false);
                // Send authoritative slots
                sendSnapshot(viewer, target.getName(), from, srcType == 0 ? t.inventory.items[from] : t.inventory.armor[39 - from]);
                sendSnapshot(viewer, target.getName(), to, (to < 36 ? t.inventory.items[to] : (to >= 36 && to <= 39 ? t.inventory.armor[39 - to] : null)));
                return true;
            }
        }

        // Destination reference
        net.minecraft.server.ItemStack dst;
        if (to >= 0 && to < 36) {
            dst = t.inventory.items[to];
            t.inventory.items[to] = src;
        } else if (to >= 36 && to <= 39) {
            int armor = to - 36;
            if (armor < 0 || armor >= t.inventory.armor.length) return true;
            dst = t.inventory.armor[armor];
            t.inventory.armor[armor] = src;
        } else {
            return true;
        }

        // Clear source or put swapped item back
        if (srcType == 0) {
            t.inventory.items[srcIdx] = dst;
        } else {
            int armor = srcIdx - 36;
            if (armor >= 0 && armor < t.inventory.armor.length) t.inventory.armor[armor] = dst;
        }

        t.inventory.update();

        // Send snapshots for from/to back to the viewer
        sendSnapshot(viewer, target.getName(), from, (srcType == 0 ? t.inventory.items[from] : t.inventory.armor[39 - from]));
        sendSnapshot(viewer, target.getName(), to, (to < 36 ? t.inventory.items[to] : t.inventory.armor[39 - to]));
        if (hasValidator) sendAck(viewer, txid, true);
        return true;
    }

    private void sendSnapshot(Player viewer, String targetName, int slotIdx, net.minecraft.server.ItemStack it) {
        int id = 0, cnt = 0, dmg = 0;
        if (it != null) { id = it.id; cnt = it.count; dmg = it.getData(); }
        int clientSlot = slotIdx;
        if (slotIdx >= 36 && slotIdx <= 39) {
            clientSlot = 100 + (39 - slotIdx); // 100 helm .. 103 boots
        }
        viewer.sendMessage("[[OPENINV_SET:" + targetName + "|" + clientSlot + "|" + id + "|" + cnt + "|" + dmg + "]]" );
    }

    private void sendAck(Player viewer, String txid, boolean ok) {
        if (txid == null) return;
        viewer.sendMessage("[[OPENINV_TX:" + txid + "|" + (ok ? "OK" : "FAIL") + "]]" );
    }
}


