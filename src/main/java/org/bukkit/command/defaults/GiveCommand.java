package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
            if (net.minecraft.server.registry.RegistryKeyPolicy.looksNumeric(args[1])) {
                sender.sendMessage(ChatColor.RED + "Numeric IDs are disabled. Use an item key like iron_ingot.");
                return true;
            }

            Resolution res = resolveItemByKey(args[1]);
            if (res != null) {
                Command.broadcastCommandMessage(sender, "Giving " + player.getName() + " some " + res.variantKey);

                int amount = 1;
                if (args.length >= 3) {
                    try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ex) {}
                    if (amount < 1) amount = 1;
                    if (amount > 64) amount = 64;
                }

                short dmg = 0;
                if (res.variantKey != null) {
                    int vd = net.minecraft.server.registry.ItemRegistry.getDefaultDamage(res.variantKey);
                    if (vd >= 0) dmg = (short)vd;
                }
                player.getInventory().addItem(new ItemStack(res.itemId, amount, dmg));
            } else {
                sender.sendMessage(ChatColor.RED + "Unknown item key: " + args[1]);
            }
        } else {
            sender.sendMessage("Can't find user " + args[0]);
        }

        return true;
    }

    private static final class Resolution { final int itemId; final String variantKey; Resolution(int id, String k){this.itemId=id;this.variantKey=k;} }

    private Resolution resolveItemByKey(String token) {
        String normalized = net.minecraft.server.registry.ItemRegistry.normalizeInputIdentifier(token);
        if (normalized == null) return null;
        net.minecraft.server.Item item = net.minecraft.server.registry.ItemRegistry.get(new net.minecraft.server.util.ResourceLocation(normalized));
        if (item == null) return null;
        return new Resolution(item.id, normalized);
    }

    @Override
    public boolean matches(String input) {
        return input.startsWith("give ");
    }
    
    @Override
    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<String>();
        if (args.length == 1) {
            // Complete player names
            String prefix = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            // Complete item keys from the namespaced registry.
            String prefix = args[1].toLowerCase();
            boolean hasNamespace = prefix.indexOf(':') >= 0;
            for (net.minecraft.server.util.ResourceLocation key : net.minecraft.server.registry.ItemRegistry.displayKeys()) {
                String candidate = hasNamespace ? key.toString() : key.getPath();
                if (candidate.startsWith(prefix)) {
                    completions.add(candidate);
                }
            }
        }
        return completions;
    }
}
