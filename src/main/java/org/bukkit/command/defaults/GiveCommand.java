package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GiveCommand extends VanillaCommand {
    public GiveCommand() {
        super("give");
        this.description = "Gives the specified player a certain amount of items";
        this.usageMessage = "/give <player> <item> [amount]";
        this.setPermission("bukkit.command.give");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;
        if ((args.length < 2) || (args.length > 3)) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }

        Player player = Bukkit.getPlayerExact(args[0]);

        if (player != null) {
            // Resolve via namespaced registry first
            Resolution res = resolveItemIdSmart(args[1]);
            if (res != null) {
                Command.broadcastCommandMessage(sender, "Giving " + player.getName() + " some " + res.itemId + "(" + args[1] + ")");

                int amount = 1;
                if (args.length >= 3) {
                    try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ex) {}
                    if (amount < 1) amount = 1;
                    if (amount > 64) amount = 64;
                }

                short dmg = 0;
                if (res.variantKey != null) {
                    int vd = net.minecraft.server.registry.VariantDefaults.get(res.variantKey);
                    if (vd >= 0) dmg = (short)vd;
                }
                player.getInventory().addItem(new ItemStack(res.itemId, amount, dmg));
            } else {
                sender.sendMessage("There's no item called " + args[1]);
            }
        } else {
            sender.sendMessage("Can't find user " + args[0]);
        }

        return true;
    }

    private static final class Resolution { final int itemId; final String variantKey; Resolution(int id, String k){this.itemId=id;this.variantKey=k;} }

    private Resolution resolveItemIdSmart(String token) {
        // Try namespaced registry
        try {
            String keyStr = token.toLowerCase();
            if (keyStr.indexOf(':') < 0) keyStr = "minecraft:" + keyStr;
            net.minecraft.server.util.ResourceLocation rl = new net.minecraft.server.util.ResourceLocation(keyStr);
            net.minecraft.server.Item it = net.minecraft.server.registry.Registries.ITEM.get(rl);
            if (it != null) {
                return new Resolution(it.id, rl.toString());
            }
        } catch (Throwable ignored) {}

        // Fallback to Bukkit Material
        Material m = Material.matchMaterial(token);
        if (m == null) {
            String lower = token.toLowerCase();
            if (lower.startsWith("minecraft:")) {
                String simple = lower.substring("minecraft:".length());
                m = Material.matchMaterial(simple);
            }
        }
        if (m != null) return new Resolution(m.getId(), null);

        // Numeric id
        try {
            int id = Integer.parseInt(token);
            return new Resolution(id, null);
        } catch (NumberFormatException ignore) {}
        return null;
    }

    @Override
    public boolean matches(String input) {
        return input.startsWith("give ");
    }
}
