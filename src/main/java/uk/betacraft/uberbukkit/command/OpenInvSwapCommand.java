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

        // Optional validator: expected viewer held and expected target slot, and tx id
        int heldId=-1, heldCnt=-1, heldDmg=-1, tgtId=-1, tgtCnt=-1, tgtDmg=-1; String txid = null; boolean hasValidator=false;
        if (args.length >= 10) {
            try {
                heldId = Integer.parseInt(args[2]);
                heldCnt = Integer.parseInt(args[3]);
                heldDmg = Integer.parseInt(args[4]);
                tgtId  = Integer.parseInt(args[5]);
                tgtCnt = Integer.parseInt(args[6]);
                tgtDmg = Integer.parseInt(args[7]);
                txid   = args[8];
                hasValidator = true;
            } catch (Exception ignore) { hasValidator = false; }
        }

        net.minecraft.server.EntityPlayer v = ((CraftPlayer) viewer).getHandle();
        net.minecraft.server.EntityPlayer t = ((CraftPlayer) target).getHandle();

        net.minecraft.server.ItemStack fromViewer = v.inventory.items[v.inventory.itemInHandIndex];
        // Validator check before applying
        if (hasValidator) {
            int aId = (fromViewer == null ? 0 : fromViewer.id);
            int aCnt = (fromViewer == null ? 0 : fromViewer.count);
            int aDmg = (fromViewer == null ? 0 : fromViewer.getData());
            int bId=0,bCnt=0,bDmg=0;
            if (idx >= 0 && idx < 36) {
                net.minecraft.server.ItemStack cur = t.inventory.items[idx];
                bId = (cur == null ? 0 : cur.id);
                bCnt = (cur == null ? 0 : cur.count);
                bDmg = (cur == null ? 0 : cur.getData());
            } else if (idx >= 36 && idx <= 39) {
                int armor = 39 - idx;
                net.minecraft.server.ItemStack cur = (armor >= 0 && armor < t.inventory.armor.length) ? t.inventory.armor[armor] : null;
                bId = (cur == null ? 0 : cur.id);
                bCnt = (cur == null ? 0 : cur.count);
                bDmg = (cur == null ? 0 : cur.getData());
            }
            if (!(aId == heldId && aCnt == heldCnt && aDmg == heldDmg && bId == tgtId && bCnt == tgtCnt && bDmg == tgtDmg)) {
                sendAck(viewer, txid, false);
                // Send authoritative current slots
                if (idx >= 0 && idx < 36) {
                    sendSnapshot(viewer, target.getName(), idx, t.inventory.items[idx]);
                } else if (idx >= 36 && idx <= 39) {
                    int armor = 39 - idx;
                    sendSnapshot(viewer, target.getName(), idx, (armor >= 0 && armor < t.inventory.armor.length) ? t.inventory.armor[armor] : null);
                }
                // Also send viewer hotbar slot snapshot (client expects 0..35 indexing)
                int baseHotbarSlot = v.inventory.itemInHandIndex; // 0..8
                sendSnapshot(viewer, viewer.getName(), baseHotbarSlot, v.inventory.items[v.inventory.itemInHandIndex]);
                return true;
            }
        }
        net.minecraft.server.ItemStack fromTarget = null;

        if (idx >= 0 && idx < 36) {
            fromTarget = t.inventory.items[idx];
            t.inventory.items[idx] = fromViewer;
        } else if (idx >= 36 && idx <= 39) { // armor 39..36 -> helmet..boots
            int armor = idx - 36; // 0=boots,1=legs,2=chest,3=helmet? (array is 0 boots .. 3 helmet)
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
        if (hasValidator) sendAck(viewer, txid, true);
        return true;
    }

    private void sendSnapshot(Player viewer, String targetName, int slotIdx, net.minecraft.server.ItemStack it) {
        int id = 0, cnt = 0, dmg = 0;
        if (it != null) { id = it.id; cnt = it.count; dmg = it.getData(); }
        int clientSlot = slotIdx;
        if (slotIdx >= 36 && slotIdx <= 39) {
            clientSlot = 100 + (39 - slotIdx); // armor to 100..103
        }
        viewer.sendMessage("[[OPENINV_SET:" + targetName + "|" + clientSlot + "|" + id + "|" + cnt + "|" + dmg + "]]" );
    }

    private void sendAck(Player viewer, String txid, boolean ok) {
        if (txid == null) return;
        viewer.sendMessage("[[OPENINV_TX:" + txid + "|" + (ok ? "OK" : "FAIL") + "]]" );
    }
}


