package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.CraftWorld;

public class SummonCommand extends VanillaCommand {
    public SummonCommand() {
        super("summon");
        this.description = "Summons an entity at your location or given coordinates";
        this.usageMessage = "/summon <minecraft:entity_type> [x] [y] [z] [block]";
        this.setPermission("bukkit.command.summon");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;
        if (!(args.length == 1 || args.length == 4 || args.length == 5)) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }

        // Determine world and base position
        World bworld;
        double x; double y; double z;
        if (sender instanceof Player) {
            Player p = (Player)sender;
            bworld = p.getWorld();
            Location loc = p.getLocation();
            x = loc.getX(); y = loc.getY(); z = loc.getZ();
        } else {
            bworld = Bukkit.getWorlds().get(0);
            x = bworld.getSpawnLocation().getX();
            y = bworld.getSpawnLocation().getY();
            z = bworld.getSpawnLocation().getZ();
        }

        if (args.length >= 4) {
            try {
                x = Double.parseDouble(args[1]);
                y = Double.parseDouble(args[2]);
                z = Double.parseDouble(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Coordinates must be numbers.");
                return true;
            }
        }

        // Resolve entity class via registry
        String raw = args[0];
        String id = net.minecraft.server.registry.EntityTypeRegistry.normalizeInputIdentifier(raw);
        if (id == null) {
            sender.sendMessage(ChatColor.RED + "Unknown entity: " + raw);
            return true;
        }
        String canon = net.minecraft.server.registry.EntityTypeRegistry.canonicalizeIdentifier(id);
        net.minecraft.server.registry.EntityTypeRegistry.bootstrapFromEntityTypes(); // ensure populated
        net.minecraft.server.util.ResourceLocation key = new net.minecraft.server.util.ResourceLocation(canon != null ? canon : id);
        Class<?> clazz = net.minecraft.server.registry.EntityTypeRegistry.get(key);
        if (clazz == null) {
            sender.sendMessage(ChatColor.RED + "Unknown entity: " + id);
            return true;
        }
        if (clazz == net.minecraft.server.EntityItem.class) {
            sender.sendMessage(ChatColor.RED + "Entity not summonable: use /give to obtain items.");
            return true;
        }
        if (clazz == net.minecraft.server.EntityPainting.class) {
            sender.sendMessage(ChatColor.RED + "Entity not summonable: place a painting item instead.");
            return true;
        }

        net.minecraft.server.WorldServer world = ((CraftWorld)bworld).getHandle();
        net.minecraft.server.Entity ent;
        try {
            java.lang.reflect.Constructor<?> ctor = clazz.getConstructor(new Class[]{net.minecraft.server.World.class});
            ent = (net.minecraft.server.Entity)ctor.newInstance(new Object[]{world});
        } catch (Throwable t) {
            sender.sendMessage(ChatColor.RED + "Failed to instantiate entity: " + (canon != null ? canon : id));
            return true;
        }

        // Apply known variant defaults (e.g., minecart types)
        int variant = net.minecraft.server.registry.EntityTypeRegistry.getDefaultVariant(canon != null ? canon : id);
        if (variant >= 0 && ent instanceof net.minecraft.server.EntityMinecart) {
            ((net.minecraft.server.EntityMinecart)ent).type = variant;
        }

        // Falling sand block preference & optional block arg
        if (ent instanceof net.minecraft.server.EntityFallingSand) {
            int blockId = net.minecraft.server.Block.SAND.id; // default to sand
            try {
                int colon = id.indexOf(':');
                String pathOnly = colon >= 0 ? id.substring(colon + 1) : id;
                if ("falling_gravel".equals(pathOnly)) blockId = net.minecraft.server.Block.GRAVEL.id;
            } catch (Throwable ignore) {}
            if (args.length == 5) {
                String blockArg = args[4];
                if (net.minecraft.server.registry.RegistryKeyPolicy.looksNumeric(blockArg)) {
                    sender.sendMessage(ChatColor.RED + "Numeric block IDs are disabled. Use a block key like netherrack.");
                    return true;
                }
                String normalizedBlock = net.minecraft.server.registry.BlockRegistry.normalizeInputIdentifier(blockArg);
                if (normalizedBlock == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown block key: " + blockArg);
                    return true;
                }
                net.minecraft.server.Block resolved = net.minecraft.server.registry.BlockRegistry.get(new net.minecraft.server.util.ResourceLocation(normalizedBlock));
                if (resolved == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown block key: " + blockArg);
                    return true;
                }
                blockId = resolved.id;
            }
            ((net.minecraft.server.EntityFallingSand)ent).a = blockId;
        }

        ent.setPositionRotation(x, y, z, 0.0F, 0.0F);
        if (world.addEntity(ent)) {
            sender.sendMessage(ChatColor.YELLOW + "Summoned " + (canon != null ? canon : raw) + " at " + (int)x + ", " + (int)y + ", " + (int)z);
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to spawn entity (chunk not loaded or blocked).");
        }
        return true;
    }

    @Override
    public boolean matches(String input) {
        return input.startsWith("summon ");
    }
    
    @Override
    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<String>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            // Get entity types from registry
            for (net.minecraft.server.util.ResourceLocation key : net.minecraft.server.registry.EntityTypeRegistry.primaryKeys()) {
                Class<?> c = net.minecraft.server.registry.EntityTypeRegistry.get(key);
                if (c == null) continue;
                // Skip non-summonable entities
                if (c == net.minecraft.server.EntityItem.class) continue;
                if (c == net.minecraft.server.EntityPainting.class) continue;
                String path = key.getPath();
                if (path.startsWith(prefix)) {
                    completions.add(path);
                }
            }
        }
        return completions;
    }
}
