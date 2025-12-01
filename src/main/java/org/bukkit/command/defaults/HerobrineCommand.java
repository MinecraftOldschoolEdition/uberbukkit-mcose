package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.CraftWorld;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.HerobrineEventManager;

public class HerobrineCommand extends VanillaCommand {
    public HerobrineCommand() {
        super("herobrine");
        this.description = "Triggers the Herobrine encounter event";
        this.usageMessage = "/herobrine";
        this.setPermission("bukkit.command.herobrine");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;

        // Get the target player
        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Specify a player: /herobrine <player>");
            return true;
        }

        // Get the world
        World bworld = target.getWorld();
        net.minecraft.server.WorldServer world = ((CraftWorld) bworld).getHandle();
        
        // Get the EntityHuman for the target player
        EntityHuman entityHuman = world.a(target.getName());
        if (entityHuman == null) {
            sender.sendMessage(ChatColor.RED + "Could not find player entity.");
            return true;
        }

        // Check if event already active
        HerobrineEventManager manager = HerobrineEventManager.getInstance(world);
        if (manager.isActive()) {
            sender.sendMessage(ChatColor.YELLOW + "A Herobrine event is already active in this world.");
            return true;
        }

        // Start the event
        manager.startEvent(entityHuman);
        sender.sendMessage(ChatColor.DARK_RED + "The Herobrine event has been triggered for " + target.getName() + "...");
        
        return true;
    }

    @Override
    public boolean matches(String input) {
        return input.startsWith("herobrine");
    }
}

