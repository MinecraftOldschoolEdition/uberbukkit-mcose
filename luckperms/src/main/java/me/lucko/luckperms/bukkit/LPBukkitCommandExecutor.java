/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Backported for Uberbukkit/Project Poseidon
 */

package me.lucko.luckperms.bukkit;

import com.projectposeidon.api.PoseidonUUID;
import me.lucko.luckperms.common.model.Group;
import me.lucko.luckperms.common.model.PermissionHolder;
import me.lucko.luckperms.common.model.Track;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.node.Node;
import me.lucko.luckperms.common.sender.Sender;
import me.lucko.luckperms.common.util.Tristate;
import me.lucko.luckperms.common.webeditor.WebEditorRequest;
import me.lucko.luckperms.common.webeditor.WebEditorResponse;
import me.lucko.luckperms.common.webeditor.WebEditorSession;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Command executor for LuckPerms commands.
 */
public class LPBukkitCommandExecutor implements CommandExecutor, TabCompleter {
    
    private final LPBukkitPlugin plugin;
    
    private static final String PREFIX = ChatColor.AQUA + "[LP] " + ChatColor.WHITE;
    
    public LPBukkitCommandExecutor(LPBukkitPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        
        if (subCommand.equals("user")) {
            return handleUser(sender, subArgs);
        } else if (subCommand.equals("group")) {
            return handleGroup(sender, subArgs);
        } else if (subCommand.equals("info")) {
            return handleInfo(sender);
        } else if (subCommand.equals("sync")) {
            return handleSync(sender);
        } else if (subCommand.equals("reload")) {
            return handleReload(sender);
        } else if (subCommand.equals("editor")) {
            return handleEditor(sender, subArgs);
        } else if (subCommand.equals("applyedits")) {
            return handleApplyEdits(sender, subArgs);
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "Unknown sub-command. Use /lp for help.");
            return true;
        }
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== LuckPermsOldschool ===");
        sender.sendMessage(ChatColor.YELLOW + "/lp editor" + ChatColor.WHITE + " - Open the web editor");
        sender.sendMessage(ChatColor.YELLOW + "/lp applyedits <code>" + ChatColor.WHITE + " - Apply changes from web editor");
        sender.sendMessage(ChatColor.YELLOW + "/lp user <player> info" + ChatColor.WHITE + " - View user info");
        sender.sendMessage(ChatColor.YELLOW + "/lp user <player> permission set <perm> [true/false]" + ChatColor.WHITE + " - Set permission");
        sender.sendMessage(ChatColor.YELLOW + "/lp user <player> permission unset <perm>" + ChatColor.WHITE + " - Remove permission");
        sender.sendMessage(ChatColor.YELLOW + "/lp user <player> parent add <group>" + ChatColor.WHITE + " - Add to group");
        sender.sendMessage(ChatColor.YELLOW + "/lp user <player> parent remove <group>" + ChatColor.WHITE + " - Remove from group");
        sender.sendMessage(ChatColor.YELLOW + "/lp group <group> info" + ChatColor.WHITE + " - View group info");
        sender.sendMessage(ChatColor.YELLOW + "/lp group <group> permission set <perm> [true/false]" + ChatColor.WHITE + " - Set group permission");
        sender.sendMessage(ChatColor.YELLOW + "/lp info" + ChatColor.WHITE + " - Show plugin info");
        sender.sendMessage(ChatColor.YELLOW + "/lp sync" + ChatColor.WHITE + " - Sync data");
        sender.sendMessage(ChatColor.YELLOW + "/lp reload" + ChatColor.WHITE + " - Reload config");
    }
    
    private boolean handleUser(CommandSender sender, String[] args) {
        if (!sender.hasPermission("luckperms.user")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You don't have permission to do this.");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /lp user <player> <action>");
            return true;
        }
        
        String playerName = args[0];
        String action = args[1].toLowerCase();
        String[] actionArgs = Arrays.copyOfRange(args, 2, args.length);
        
        // Try to find the player
        Player onlinePlayer = this.plugin.getServer().getPlayer(playerName);
        UUID uuid;
        
        if (onlinePlayer != null) {
            uuid = onlinePlayer.getUniqueId();
            playerName = onlinePlayer.getName();
        } else {
            // Use Poseidon's UUID API for offline players
            uuid = PoseidonUUID.getPlayerGracefulUUID(playerName);
        }
        
        User user = this.plugin.getStorage().loadUser(uuid, playerName);
        if (user == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Could not load user data.");
            return true;
        }
        
        if (action.equals("info")) {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "User: " + ChatColor.WHITE + user.getPlainDisplayName());
            sender.sendMessage(PREFIX + ChatColor.GREEN + "UUID: " + ChatColor.WHITE + user.getUniqueId());
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Primary Group: " + ChatColor.WHITE + user.getPrimaryGroup());
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Groups: " + ChatColor.WHITE + join(user.getInheritedGroups(), ", "));
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Permissions: " + ChatColor.WHITE + user.getPermissionNodes().size());
            for (Node node : user.getPermissionNodes()) {
                String prefix = node.getValue() ? ChatColor.GREEN + "+" : ChatColor.RED + "-";
                sender.sendMessage("  " + prefix + " " + ChatColor.WHITE + node.getKey());
            }
        } else if (action.equals("permission")) {
            if (actionArgs.length < 2) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /lp user <player> permission <set/unset> <permission> [value]");
                return true;
            }
            handlePermission(sender, user, actionArgs);
            this.plugin.getStorage().saveUser(user);
            user.getCachedData().refresh();
        } else if (action.equals("parent")) {
            if (actionArgs.length < 2) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /lp user <player> parent <add/remove> <group>");
                return true;
            }
            handleParent(sender, user, actionArgs);
            this.plugin.getStorage().saveUser(user);
            user.getCachedData().refresh();
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "Unknown action. Use: info, permission, parent");
        }
        
        return true;
    }
    
    private boolean handleGroup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("luckperms.group")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You don't have permission to do this.");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /lp group <name> <action>");
            return true;
        }
        
        String groupName = args[0].toLowerCase();
        String action = args[1].toLowerCase();
        String[] actionArgs = Arrays.copyOfRange(args, 2, args.length);
        
        Group group = this.plugin.getGroupManager().getOrMake(groupName);
        
        if (action.equals("info")) {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Group: " + ChatColor.WHITE + group.getName());
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Weight: " + ChatColor.WHITE + group.getWeight());
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Inherits: " + ChatColor.WHITE + join(group.getInheritedGroups(), ", "));
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Permissions: " + ChatColor.WHITE + group.getPermissionNodes().size());
            for (Node node : group.getPermissionNodes()) {
                String prefix = node.getValue() ? ChatColor.GREEN + "+" : ChatColor.RED + "-";
                sender.sendMessage("  " + prefix + " " + ChatColor.WHITE + node.getKey());
            }
        } else if (action.equals("permission")) {
            if (actionArgs.length < 2) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /lp group <name> permission <set/unset> <permission> [value]");
                return true;
            }
            handlePermission(sender, group, actionArgs);
            this.plugin.getStorage().saveGroup(group);
        } else if (action.equals("parent")) {
            if (actionArgs.length < 2) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /lp group <name> parent <add/remove> <group>");
                return true;
            }
            handleParent(sender, group, actionArgs);
            this.plugin.getStorage().saveGroup(group);
        } else if (action.equals("setweight")) {
            if (actionArgs.length < 1) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /lp group <name> setweight <weight>");
                return true;
            }
            try {
                int weight = Integer.parseInt(actionArgs[0]);
                group.setWeight(weight);
                this.plugin.getStorage().saveGroup(group);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Set weight to " + weight);
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Invalid weight number.");
            }
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "Unknown action. Use: info, permission, parent, setweight");
        }
        
        return true;
    }
    
    private void handlePermission(CommandSender sender, me.lucko.luckperms.common.model.PermissionHolder holder, String[] args) {
        String subAction = args[0].toLowerCase();
        String permission = args[1];
        
        if (subAction.equals("set")) {
            boolean value = args.length < 3 || Boolean.parseBoolean(args[2]);
            holder.setNode(Node.builder(permission).value(value).build());
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Set " + permission + " to " + value);
        } else if (subAction.equals("unset")) {
            holder.unsetNode(Node.builder(permission).build());
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Unset " + permission);
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "Unknown permission action. Use: set, unset");
        }
    }
    
    private void handleParent(CommandSender sender, me.lucko.luckperms.common.model.PermissionHolder holder, String[] args) {
        String subAction = args[0].toLowerCase();
        String groupName = args[1].toLowerCase();
        
        if (subAction.equals("add")) {
            holder.setNode(Node.builder("group." + groupName).build());
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Added parent group " + groupName);
        } else if (subAction.equals("remove")) {
            holder.unsetNode(Node.builder("group." + groupName).build());
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Removed parent group " + groupName);
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "Unknown parent action. Use: add, remove");
        }
    }
    
    private boolean handleInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== LuckPermsOldschool ===");
        sender.sendMessage(ChatColor.YELLOW + "Version: " + ChatColor.WHITE + "1.0");
        sender.sendMessage(ChatColor.YELLOW + "Storage: " + ChatColor.WHITE + "YAML");
        sender.sendMessage(ChatColor.YELLOW + "Loaded Users: " + ChatColor.WHITE + this.plugin.getUserManager().getAll().size());
        sender.sendMessage(ChatColor.YELLOW + "Loaded Groups: " + ChatColor.WHITE + this.plugin.getGroupManager().getAll().size());
        sender.sendMessage(ChatColor.YELLOW + "Loaded Tracks: " + ChatColor.WHITE + this.plugin.getTrackManager().getAll().size());
        return true;
    }
    
    private boolean handleSync(CommandSender sender) {
        if (!sender.hasPermission("luckperms.sync")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You don't have permission to do this.");
            return true;
        }
        
        // Refresh all online users
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            User user = this.plugin.getUserManager().getIfLoaded(player.getUniqueId());
            if (user != null) {
                this.plugin.getStorage().loadUser(player.getUniqueId(), player.getName());
            }
        }
        
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Synced permissions for all online players.");
        return true;
    }
    
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("luckperms.reload")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You don't have permission to do this.");
            return true;
        }
        
        this.plugin.getConfiguration().reload();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Configuration reloaded.");
        return true;
    }
    
    private boolean handleEditor(CommandSender sender, String[] args) {
        if (!sender.hasPermission("luckperms.editor")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You don't have permission to do this.");
            return true;
        }
        
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Generating web editor session...");
        
        // Create a sender wrapper
        final BukkitSender senderWrapper = new BukkitSender(this.plugin, sender);
        
        // Run async to not block the main thread
        this.plugin.getServer().getScheduler().scheduleAsyncDelayedTask(this.plugin.getLoader(), new Runnable() {
            @Override
            public void run() {
                // Collect all groups and all users (including offline)
                List<PermissionHolder> holders = new ArrayList<PermissionHolder>();
                List<Track> tracks = new ArrayList<Track>();
                
                // Add all groups
                holders.addAll(plugin.getGroupManager().getAll().values());
                
                // Add all users (including offline from storage)
                List<User> users = new ArrayList<User>();
                WebEditorRequest.includeMatchingUsers(users, true, plugin);
                holders.addAll(users);
                
                // Add all tracks
                tracks.addAll(plugin.getTrackManager().getAll().values());
                
                if (holders.isEmpty()) {
                    senderWrapper.sendMessage(PREFIX + ChatColor.RED + "No permission holders to edit.");
                    return;
                }
                
                WebEditorSession session = WebEditorSession.create(holders, tracks, senderWrapper, "lp", plugin);
                session.open();
            }
        });
        
        return true;
    }
    
    private boolean handleApplyEdits(CommandSender sender, String[] args) {
        if (!sender.hasPermission("luckperms.applyedits")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You don't have permission to do this.");
            return true;
        }
        
        if (args.length < 1) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /lp applyedits <code>");
            return true;
        }
        
        String code = args[0];
        
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Downloading and applying changes...");
        
        // Create a sender wrapper
        final BukkitSender senderWrapper = new BukkitSender(this.plugin, sender);
        
        // Run async
        this.plugin.getServer().getScheduler().scheduleAsyncDelayedTask(this.plugin.getLoader(), new Runnable() {
            @Override
            public void run() {
                WebEditorResponse response = WebEditorResponse.download(code, plugin, senderWrapper);
                if (response != null) {
                    response.apply();
                }
            }
        });
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<String>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("user", "group", "info", "sync", "reload", "editor", "applyedits"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("user")) {
            for (Player player : this.plugin.getServer().getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("group")) {
            completions.addAll(this.plugin.getGroupManager().getAll().keySet());
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("user") || args[0].equalsIgnoreCase("group")) {
                completions.addAll(Arrays.asList("info", "permission", "parent"));
                if (args[0].equalsIgnoreCase("group")) {
                    completions.add("setweight");
                }
            }
        } else if (args.length == 4) {
            if (args[2].equalsIgnoreCase("permission")) {
                completions.addAll(Arrays.asList("set", "unset"));
            } else if (args[2].equalsIgnoreCase("parent")) {
                completions.addAll(Arrays.asList("add", "remove"));
            }
        } else if (args.length == 5) {
            if (args[2].equalsIgnoreCase("parent")) {
                completions.addAll(this.plugin.getGroupManager().getAll().keySet());
            }
        }
        
        // Filter based on input
        String input = args[args.length - 1].toLowerCase();
        List<String> filtered = new ArrayList<String>();
        for (String s : completions) {
            if (s.toLowerCase().startsWith(input)) {
                filtered.add(s);
            }
        }
        
        return filtered;
    }
    
    private static String join(List<String> list, String delimiter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }
    
    /**
     * Wrapper to adapt Bukkit CommandSender to LuckPerms Sender interface.
     */
    private static class BukkitSender implements Sender {
        private final LPBukkitPlugin plugin;
        private final CommandSender handle;
        
        BukkitSender(LPBukkitPlugin plugin, CommandSender handle) {
            this.plugin = plugin;
            this.handle = handle;
        }
        
        @Override
        public LPBukkitPlugin getPlugin() {
            return this.plugin;
        }
        
        @Override
        public String getName() {
            return this.handle.getName();
        }
        
        @Override
        public UUID getUniqueId() {
            if (this.handle instanceof Player) {
                return ((Player) this.handle).getUniqueId();
            }
            return Sender.CONSOLE_UUID;
        }
        
        @Override
        public void sendMessage(String message) {
            // Convert color codes (Uberbukkit only has basic colors)
            String colored = message.replace("&a", ChatColor.GREEN.toString())
                    .replace("&b", ChatColor.AQUA.toString())
                    .replace("&c", ChatColor.RED.toString())
                    .replace("&d", ChatColor.LIGHT_PURPLE.toString())
                    .replace("&e", ChatColor.YELLOW.toString())
                    .replace("&f", ChatColor.WHITE.toString())
                    .replace("&7", ChatColor.GRAY.toString())
                    .replace("&6", ChatColor.GOLD.toString())
                    .replace("&0", ChatColor.BLACK.toString())
                    .replace("&1", ChatColor.DARK_BLUE.toString())
                    .replace("&2", ChatColor.DARK_GREEN.toString())
                    .replace("&3", ChatColor.DARK_AQUA.toString())
                    .replace("&4", ChatColor.DARK_RED.toString())
                    .replace("&5", ChatColor.DARK_PURPLE.toString())
                    .replace("&8", ChatColor.DARK_GRAY.toString())
                    .replace("&9", ChatColor.BLUE.toString())
                    // Format codes not available in Uberbukkit - just remove them
                    .replace("&l", "").replace("&o", "").replace("&n", "")
                    .replace("&m", "").replace("&k", "").replace("&r", "");
            this.handle.sendMessage(colored);
        }
        
        @Override
        public Tristate getPermissionValue(String permission) {
            if (this.handle.hasPermission(permission)) {
                return Tristate.TRUE;
            }
            return Tristate.UNDEFINED;
        }
        
        @Override
        public boolean hasPermission(String permission) {
            return this.handle.hasPermission(permission);
        }
        
        @Override
        public void performCommand(String commandLine) {
            this.plugin.getServer().dispatchCommand(this.handle, commandLine);
        }
        
        @Override
        public boolean isConsole() {
            return !(this.handle instanceof Player);
        }
    }
}
