package org.bukkit.command.defaults;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import net.minecraft.server.ServerProfiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to control the server performance profiler.
 * Usage:
 *   /profile start - Start profiling
 *   /profile stop - Stop profiling
 *   /profile status - Show current status
 *   /profile report - Show report in console
 *   /profile save - Save detailed report to file
 *   /profile clear - Clear all profiling data
 */
public class ProfileCommand extends VanillaCommand {
    
    public ProfileCommand() {
        super("profile");
        this.description = "Server performance profiler commands";
        this.usageMessage = "/profile <start|stop|status|report|save|clear>";
        this.setPermission("bukkit.command.profile");
    }
    
    @Override
    public boolean matches(String input) {
        return input != null && (input.equalsIgnoreCase("profile") || input.toLowerCase().startsWith("profile "));
    }
    
    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }
        
        ServerProfiler profiler = ServerProfiler.getInstance();
        
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: " + usageMessage);
            sender.sendMessage(ChatColor.GRAY + "  start  - Start profiling session");
            sender.sendMessage(ChatColor.GRAY + "  stop   - Stop profiling session");
            sender.sendMessage(ChatColor.GRAY + "  status - Show current profiler status");
            sender.sendMessage(ChatColor.GRAY + "  report - Print report to console");
            sender.sendMessage(ChatColor.GRAY + "  save   - Save detailed report to file");
            sender.sendMessage(ChatColor.GRAY + "  clear  - Clear all profiling data");
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        switch (subcommand) {
            case "start":
                profiler.start();
                sender.sendMessage(ChatColor.GREEN + "Profiler started.");
                sender.sendMessage(ChatColor.GRAY + "Run '/profile stop' to stop, then '/profile report' or '/profile save'.");
                break;
                
            case "stop":
                profiler.stop();
                sender.sendMessage(ChatColor.YELLOW + "Profiler stopped.");
                sender.sendMessage(ChatColor.GRAY + "Use '/profile report' to view or '/profile save' to save.");
                break;
                
            case "status":
                sender.sendMessage(ChatColor.AQUA + profiler.getStatusSummary());
                break;
                
            case "report":
                String report = profiler.generateReport();
                // Print to console since reports are long
                System.out.println(report);
                sender.sendMessage(ChatColor.GREEN + "Report printed to server console.");
                // Also show a brief summary to the sender
                sender.sendMessage(ChatColor.AQUA + profiler.getStatusSummary());
                break;
                
            case "save":
                String path = profiler.saveReport();
                if (path != null) {
                    sender.sendMessage(ChatColor.GREEN + "Report saved to: " + path);
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to save report. Check server console for errors.");
                }
                break;
                
            case "clear":
                profiler.clear();
                sender.sendMessage(ChatColor.GREEN + "Profiler data cleared.");
                break;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subcommand);
                sender.sendMessage(ChatColor.YELLOW + "Usage: " + usageMessage);
                break;
        }
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        List<String> completions = new ArrayList<String>();
        
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String cmd : new String[]{"start", "stop", "status", "report", "save", "clear"}) {
                if (cmd.startsWith(prefix)) {
                    completions.add(cmd);
                }
            }
        }
        
        return completions;
    }
}
