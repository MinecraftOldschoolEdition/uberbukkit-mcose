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
            Material material = matchMaterialSmart(args[1]);

            if (material != null) {
                Command.broadcastCommandMessage(sender, "Giving " + player.getName() + " some " + material.getId() + "(" + material + ")");

                int amount = 1;

                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ex) {
                    }

                    if (amount < 1) amount = 1;
                    if (amount > 64) amount = 64;
                }

                player.getInventory().addItem(new ItemStack(material, amount));
            } else {
                sender.sendMessage("There's no item called " + args[1]);
            }
        } else {
            sender.sendMessage("Can't find user " + args[0]);
        }

        return true;
    }

    private Material matchMaterialSmart(String token) {
        // Try standard Bukkit name first
        Material m = Material.matchMaterial(token);
        if (m != null) return m;
        // Try legacy names like minecraft:<name>
        String lower = token.toLowerCase();
        if (lower.startsWith("minecraft:")) {
            String simple = lower.substring("minecraft:".length());
            m = Material.matchMaterial(simple);
            if (m != null) return m;
        }
        // Try numeric id
        try {
            int id = Integer.parseInt(token);
            for (Material mat : Material.values()) {
                if (mat.getId() == id) return mat;
            }
        } catch (NumberFormatException ignore) {}
        return null;
    }

    @Override
    public boolean matches(String input) {
        return input.startsWith("give ");
    }
}
