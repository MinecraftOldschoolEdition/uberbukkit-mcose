package org.bukkit.command.defaults;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import net.minecraft.server.ServerStatistics;
import net.minecraft.server.StatisticList;
import net.minecraft.server.AchievementList;
import net.minecraft.server.Achievement;
import net.minecraft.server.Statistic;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * Command to view and manage server-wide statistics.
 * Usage:
 *   /serverstats - View summary
 *   /serverstats leaderboard <stat> - View leaderboard for a statistic
 *   /serverstats save - Force save statistics
 *   /serverstats snapshot [year] - Create yearly snapshot
 */
public class ServerStatsCommand extends VanillaCommand {
    
    public ServerStatsCommand() {
        super("serverstats");
        this.description = "View and manage server-wide statistics";
        this.usageMessage = "/serverstats [leaderboard <stat>|save|snapshot [year]]";
        this.setPermission("bukkit.command.serverstats");
    }
    
    @Override
    public boolean matches(String input) {
        return input != null && (input.equalsIgnoreCase("serverstats") || input.toLowerCase().startsWith("serverstats "));
    }
    
    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }
        
        ServerStatistics stats = ServerStatistics.getInstance();
        
        if (args.length == 0) {
            // Show summary
            showSummary(sender, stats);
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        if (subcommand.equals("save")) {
            stats.save();
            sender.sendMessage(ChatColor.GREEN + "Server statistics saved.");
            return true;
        }
        
        if (subcommand.equals("snapshot")) {
            int year = Calendar.getInstance().get(Calendar.YEAR);
            if (args.length > 1) {
                try {
                    year = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid year: " + args[1]);
                    return true;
                }
            }
            stats.createYearlySnapshot(year);
            sender.sendMessage(ChatColor.GREEN + "Created yearly snapshot for " + year);
            return true;
        }
        
        if (subcommand.equals("leaderboard") && args.length > 1) {
            String statName = args[1].toLowerCase();
            Statistic stat = findStatistic(statName);
            if (stat == null) {
                sender.sendMessage(ChatColor.RED + "Unknown statistic: " + statName);
                sender.sendMessage(ChatColor.GRAY + "Available: walks, deaths, kills, mobkills, damage, jumps, playtime");
                return true;
            }
            
            showLeaderboard(sender, stats, stat);
            return true;
        }
        
        if (subcommand.equals("achievements")) {
            showAchievementStats(sender, stats);
            return true;
        }
        
        sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
        return true;
    }
    
    private void showSummary(CommandSender sender, ServerStatistics stats) {
        sender.sendMessage(ChatColor.GOLD + "=== Server Statistics ===");
        sender.sendMessage(ChatColor.YELLOW + "Unique Players: " + ChatColor.WHITE + stats.getTotalUniquePlayers());
        sender.sendMessage(ChatColor.YELLOW + "Total Joins: " + ChatColor.WHITE + stats.getTotalPlayerJoins());
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "-- Combined Totals --");
        sender.sendMessage(ChatColor.GRAY + "Distance Walked: " + formatDistance(stats.getCombinedTotal(StatisticList.l)));
        sender.sendMessage(ChatColor.GRAY + "Total Deaths: " + stats.getCombinedTotal(StatisticList.y));
        sender.sendMessage(ChatColor.GRAY + "Mobs Killed: " + stats.getCombinedTotal(StatisticList.z));
        sender.sendMessage(ChatColor.GRAY + "Player Kills: " + stats.getCombinedTotal(StatisticList.A));
        sender.sendMessage(ChatColor.GRAY + "Damage Dealt: " + stats.getCombinedTotal(StatisticList.w));
        sender.sendMessage(ChatColor.GRAY + "Jumps: " + stats.getCombinedTotal(StatisticList.u));
        sender.sendMessage(ChatColor.GRAY + "Play Time: " + formatTime(stats.getCombinedTotal(StatisticList.k)));
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Use /serverstats leaderboard <stat> for rankings");
    }
    
    private void showLeaderboard(CommandSender sender, ServerStatistics stats, Statistic stat) {
        List<Map.Entry<String, Long>> leaders = stats.getLeaderboard(stat, 10);
        
        sender.sendMessage(ChatColor.GOLD + "=== Leaderboard: " + stat.f + " ===");
        
        if (leaders.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No data yet.");
            return;
        }
        
        int rank = 1;
        for (Map.Entry<String, Long> entry : leaders) {
            String medal = rank == 1 ? ChatColor.GOLD + "\u2605 " : 
                          rank == 2 ? ChatColor.WHITE + "\u2606 " : 
                          rank == 3 ? ChatColor.YELLOW + "\u2606 " : "";
            sender.sendMessage(medal + ChatColor.YELLOW + "#" + rank + " " + 
                             ChatColor.WHITE + entry.getKey() + ": " + 
                             ChatColor.AQUA + formatValue(stat, entry.getValue()));
            rank++;
        }
    }
    
    private void showAchievementStats(CommandSender sender, ServerStatistics stats) {
        sender.sendMessage(ChatColor.GOLD + "=== Achievement Unlock Counts ===");
        
        for (Object obj : AchievementList.e) {
            if (obj instanceof Achievement) {
                Achievement achievement = (Achievement) obj;
                int count = stats.getAchievementUnlockCount(achievement);
                if (count > 0) {
                    sender.sendMessage(ChatColor.GRAY + achievement.f + ": " + ChatColor.WHITE + count + " players");
                }
            }
        }
    }
    
    private Statistic findStatistic(String name) {
        switch (name) {
            case "walks": case "walk": case "distance": return StatisticList.l;
            case "deaths": case "death": return StatisticList.y;
            case "kills": case "playerkills": return StatisticList.A;
            case "mobkills": case "mobs": return StatisticList.z;
            case "damage": case "damagedealt": return StatisticList.w;
            case "jumps": case "jump": return StatisticList.u;
            case "playtime": case "time": case "played": return StatisticList.k;
            case "swim": case "swims": return StatisticList.m;
            case "fall": case "falls": return StatisticList.n;
            case "climb": case "climbs": return StatisticList.o;
            case "fly": case "flies": return StatisticList.p;
            case "boat": case "boats": return StatisticList.s;
            case "minecart": case "minecarts": return StatisticList.r;
            case "pig": case "pigs": return StatisticList.t;
            case "fish": case "fishing": return StatisticList.B;
            case "drops": case "dropped": return StatisticList.v;
            default: return null;
        }
    }
    
    private String formatDistance(long cm) {
        if (cm < 100) return cm + " cm";
        if (cm < 100000) return String.format("%.1f m", cm / 100.0);
        return String.format("%.2f km", cm / 100000.0);
    }
    
    private String formatTime(long ticks) {
        // ticks are actually in minutes for playtime stat
        long minutes = ticks;
        if (minutes < 60) return minutes + " minutes";
        long hours = minutes / 60;
        if (hours < 24) return hours + " hours " + (minutes % 60) + " min";
        long days = hours / 24;
        return days + " days " + (hours % 24) + " hours";
    }
    
    private String formatValue(Statistic stat, long value) {
        // Distance stats are in cm
        if (stat == StatisticList.l || stat == StatisticList.m || stat == StatisticList.n ||
            stat == StatisticList.o || stat == StatisticList.p || stat == StatisticList.r ||
            stat == StatisticList.s || stat == StatisticList.t) {
            return formatDistance(value);
        }
        // Time stat
        if (stat == StatisticList.k) {
            return formatTime(value);
        }
        return String.valueOf(value);
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        List<String> completions = new ArrayList<String>();
        
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String cmd : new String[]{"save", "snapshot", "leaderboard", "achievements"}) {
                if (cmd.startsWith(prefix)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("leaderboard")) {
            String prefix = args[1].toLowerCase();
            for (String stat : new String[]{"walks", "deaths", "kills", "mobkills", "damage", "jumps", "playtime", "swim", "boat", "minecart", "fish"}) {
                if (stat.startsWith(prefix)) {
                    completions.add(stat);
                }
            }
        }
        
        return completions;
    }
}

