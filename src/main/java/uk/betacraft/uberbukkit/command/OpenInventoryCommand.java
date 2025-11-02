package uk.betacraft.uberbukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class OpenInventoryCommand extends Command {

    // Track active admin viewers: viewer -> target -> taskId
    private static final Map<String, Map<String, Integer>> activeSyncTasks = new ConcurrentHashMap<String, Map<String, Integer>>();

    public OpenInventoryCommand(String name) {
        super(name);
        this.description = "Open the admin inventory GUI for a player";
        this.usageMessage = "/" + name + " <playername>";
        // Keep legacy names working
        this.setAliases(Arrays.asList("openinventory", "openinv"));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Player viewer = (Player) sender;
        if (args.length < 1) {
            sender.sendMessage("Usage: /" + label + " <playername>");
            return true;
        }
        String targetName = args[0];
        Player target = Bukkit.getServer().getPlayer(targetName);
        if (target == null) {
            // Allow offline target: just open the admin UI; inventory snapshots will be empty
            viewer.sendMessage("[[ADMININV:" + targetName + "]]" );
            return true;
        }
        // Send full inventory snapshot (ALL slots, including empty ones) for validation
        sendFullInventorySnapshot(viewer, target);
        // Schedule periodic syncing while the admin panel might be open
        startPeriodicSync(viewer, target);
        // Now send admin-open token (client will use the admin GUI asset)
        viewer.sendMessage("[[ADMININV:" + target.getName() + "]]" );
        return true;
    }

    public static void sendFullInventorySnapshotStatic(Player viewer, Player target) {
        // Send full sync marker first to clear client-side ghost items
        viewer.sendMessage("[[OPENINV_CLEAR:" + target.getName() + "]]");
        
        // Send ALL slots (including empty) to clear ghost items on client
        org.bukkit.inventory.PlayerInventory inv = target.getInventory();
        org.bukkit.inventory.ItemStack[] contents = inv.getContents();
        
        // Send main inventory (slots 0-35)
        for (int i = 0; i < contents.length; i++) {
            org.bukkit.inventory.ItemStack it = contents[i];
            int id = 0, cnt = 0, dmg = 0;
            if (it != null && it.getTypeId() > 0 && it.getAmount() > 0) {
                id = it.getTypeId();
                cnt = it.getAmount();
                dmg = it.getDurability();
            }
            viewer.sendMessage("[[OPENINV_SET:" + target.getName() + "|" + i + "|" + id + "|" + cnt + "|" + dmg + "]]");
        }
        
        // Send armor (slots 100-103)
        org.bukkit.inventory.ItemStack[] armor = inv.getArmorContents();
        for (int a = 0; a < armor.length; a++) {
            org.bukkit.inventory.ItemStack it = armor[a];
            int id = 0, cnt = 0, dmg = 0;
            if (it != null && it.getTypeId() > 0 && it.getAmount() > 0) {
                id = it.getTypeId();
                cnt = it.getAmount();
                dmg = it.getDurability();
            }
            int slot = 100 + a;
            viewer.sendMessage("[[OPENINV_SET:" + target.getName() + "|" + slot + "|" + id + "|" + cnt + "|" + dmg + "]]");
        }
    }

    private void sendFullInventorySnapshot(Player viewer, Player target) {
        sendFullInventorySnapshotStatic(viewer, target);
    }

    private void startPeriodicSync(Player viewer, Player target) {
        // Cancel any existing sync task for this viewer+target pair
        stopPeriodicSync(viewer, target);
        
        // Create new sync task: sync every 2 seconds (40 ticks)
        // Find a valid plugin to own the task (UberBukkit or any plugin)
        org.bukkit.plugin.Plugin owner = null;
        for (org.bukkit.plugin.Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin != null && plugin.isEnabled()) {
                owner = plugin;
                break;
            }
        }
        // Fallback: create a dummy plugin if none found (shouldn't happen)
        if (owner == null) {
            // Use Bukkit's scheduler without plugin (some implementations allow this)
            try {
                java.lang.reflect.Method method = Bukkit.getScheduler().getClass().getMethod("scheduleSyncRepeatingTask", 
                    org.bukkit.plugin.Plugin.class, Runnable.class, long.class, long.class);
                // If we get here, we need a plugin - create a minimal one or skip periodic sync
                return; // Skip periodic sync if no plugin available
            } catch (Exception e) {
                return; // Skip periodic sync
            }
        }
        final org.bukkit.plugin.Plugin pluginOwner = owner;
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            pluginOwner,
            new Runnable() {
                @Override
                public void run() {
                    // Check if viewer and target are still online
                    if (viewer.isOnline() && target.isOnline()) {
                        sendFullInventorySnapshot(viewer, target);
                    } else {
                        // Clean up if either player disconnected
                        stopPeriodicSync(viewer, target);
                    }
                }
            },
            40L, // Initial delay: 2 seconds
            40L  // Repeat every 2 seconds
        );
        
        // Store task ID
        Map<String, Integer> viewerTasks = activeSyncTasks.get(viewer.getName());
        if (viewerTasks == null) {
            viewerTasks = new ConcurrentHashMap<String, Integer>();
            activeSyncTasks.put(viewer.getName(), viewerTasks);
        }
        viewerTasks.put(target.getName(), taskId);
    }

    public static void stopPeriodicSync(Player viewer, Player target) {
        Map<String, Integer> viewerTasks = activeSyncTasks.get(viewer.getName());
        if (viewerTasks != null) {
            Integer taskId = viewerTasks.remove(target.getName());
            if (taskId != null) {
                Bukkit.getScheduler().cancelTask(taskId);
            }
            if (viewerTasks.isEmpty()) {
                activeSyncTasks.remove(viewer.getName());
            }
        }
    }

    public static void stopAllSyncTasksForPlayer(Player player) {
        Map<String, Integer> viewerTasks = activeSyncTasks.remove(player.getName());
        if (viewerTasks != null) {
            for (Integer taskId : viewerTasks.values()) {
                Bukkit.getScheduler().cancelTask(taskId);
            }
        }
    }
}


