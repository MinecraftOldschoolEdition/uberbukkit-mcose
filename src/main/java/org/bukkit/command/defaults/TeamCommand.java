package org.bukkit.command.defaults;

import net.minecraft.server.scoreboard.ModernScoreboard;
import net.minecraft.server.scoreboard.ScoreboardCommandSupport;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Full 26.3 /team command surface backed by the modern scoreboard. */
public final class TeamCommand extends VanillaCommand {
    public TeamCommand() {
        super("team");
        this.description = "Manages scoreboard teams.";
        this.usageMessage = "/team <list|add|remove|empty|join|leave|modify> ...";
        this.setPermission("bukkit.command.team");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;
        if (args.length < 1) return usage(sender);
        try {
            ModernScoreboard board = ScoreboardCommandSupport.manager().getScoreboard();
            String action = args[0].toLowerCase(Locale.ROOT);
            if ("list".equals(action)) return list(sender, args, board);
            if ("add".equals(action)) return add(sender, args, board);
            if ("remove".equals(action)) {
                if (args.length != 2) return usage(sender);
                requireTeam(board, args[1]);
                board.removeTeam(args[1]);
                sender.sendMessage(ChatColor.YELLOW + "Removed team " + args[1] + ".");
                return true;
            }
            if ("empty".equals(action)) {
                if (args.length != 2) return usage(sender);
                ModernScoreboard.Team team = requireTeam(board, args[1]);
                int count = board.emptyTeam(team);
                if (count == 0) throw new IllegalArgumentException("Team " + team.name + " is already empty");
                sender.sendMessage(ChatColor.YELLOW + "Removed " + count + " member(s) from " + team.name + ".");
                return true;
            }
            if ("join".equals(action)) return join(sender, args, board);
            if ("leave".equals(action)) return leave(sender, args, board);
            if ("modify".equals(action)) return modify(sender, args, board);
            return usage(sender);
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage(ChatColor.RED + invalid.getMessage());
            return true;
        } catch (IllegalStateException invalid) {
            sender.sendMessage(ChatColor.RED + invalid.getMessage());
            return true;
        }
    }

    private boolean list(CommandSender sender, String[] args, ModernScoreboard board) {
        if (args.length == 1) {
            Collection<ModernScoreboard.Team> teams = board.getTeams();
            if (teams.isEmpty()) sender.sendMessage(ChatColor.YELLOW + "There are no teams.");
            for (ModernScoreboard.Team team : teams) {
                sender.sendMessage("- " + team.name + " (" + team.displayName + ", " + team.players.size() + " members)");
            }
            return true;
        }
        if (args.length != 2) return usage(sender);
        ModernScoreboard.Team team = requireTeam(board, args[1]);
        sender.sendMessage(ChatColor.YELLOW + "Members of " + team.name + " (" + team.players.size() + "): " + joinNames(team.players));
        return true;
    }

    private boolean add(CommandSender sender, String[] args, ModernScoreboard board) {
        if (args.length < 2) return usage(sender);
        String display = args.length > 2
            ? ScoreboardCommandSupport.component(ScoreboardCommandSupport.join(args, 2)) : args[1];
        board.addTeam(args[1], display);
        sender.sendMessage(ChatColor.YELLOW + "Added team " + args[1] + ".");
        return true;
    }

    private boolean join(CommandSender sender, String[] args, ModernScoreboard board) {
        if (args.length < 2 || args.length > 3) return usage(sender);
        ModernScoreboard.Team team = requireTeam(board, args[1]);
        String selector = args.length == 3 ? args[2] : "@s";
        Collection<String> members = ScoreboardCommandSupport.holders(sender, selector, board, true);
        if (members.isEmpty()) throw new IllegalArgumentException("No team members matched " + selector);
        int changed = 0;
        for (String member : members) if (board.joinTeam(member, team)) ++changed;
        sender.sendMessage(ChatColor.YELLOW + "Added " + changed + " member(s) to " + team.name + ".");
        return true;
    }

    private boolean leave(CommandSender sender, String[] args, ModernScoreboard board) {
        if (args.length != 2) return usage(sender);
        Collection<String> members = ScoreboardCommandSupport.holders(sender, args[1], board, true);
        if (members.isEmpty()) throw new IllegalArgumentException("No team members matched " + args[1]);
        int changed = 0;
        for (String member : members) if (board.leaveTeam(member)) ++changed;
        sender.sendMessage(ChatColor.YELLOW + "Removed " + changed + " member(s) from their teams.");
        return true;
    }

    private boolean modify(CommandSender sender, String[] args, ModernScoreboard board) {
        if (args.length < 4) return usage(sender);
        ModernScoreboard.Team team = requireTeam(board, args[1]);
        String option = args[2].toLowerCase(Locale.ROOT);
        if ("displayname".equals(option)) {
            team.displayName = ScoreboardCommandSupport.component(ScoreboardCommandSupport.join(args, 3));
        } else if ("color".equals(option)) {
            if (args.length != 4) return usage(sender);
            if ("reset".equalsIgnoreCase(args[3])) team.color = -1;
            else {
                team.color = ScoreboardCommandSupport.colorId(args[3]);
                if (team.color < 0) throw new IllegalArgumentException("Unknown team color: " + args[3]);
            }
        } else if ("friendlyfire".equals(option)) {
            if (args.length != 4) return usage(sender);
            team.friendlyFire = parseBoolean(args[3]);
        } else if ("seefriendlyinvisibles".equals(option)) {
            if (args.length != 4) return usage(sender);
            team.seeFriendlyInvisibles = parseBoolean(args[3]);
        } else if ("nametagvisibility".equals(option)) {
            if (args.length != 4) return usage(sender);
            team.nameTagVisibility = ModernScoreboard.normalizeVisibility(args[3]);
        } else if ("deathmessagevisibility".equals(option)) {
            if (args.length != 4) return usage(sender);
            team.deathMessageVisibility = ModernScoreboard.normalizeVisibility(args[3]);
        } else if ("collisionrule".equals(option)) {
            if (args.length != 4) return usage(sender);
            team.collisionRule = ModernScoreboard.normalizeCollision(args[3]);
        } else if ("prefix".equals(option)) {
            team.prefix = ScoreboardCommandSupport.component(ScoreboardCommandSupport.join(args, 3));
        } else if ("suffix".equals(option)) {
            team.suffix = ScoreboardCommandSupport.component(ScoreboardCommandSupport.join(args, 3));
        } else {
            return usage(sender);
        }
        board.updateTeam(team);
        sender.sendMessage(ChatColor.YELLOW + "Updated team " + team.name + ".");
        return true;
    }

    private static ModernScoreboard.Team requireTeam(ModernScoreboard board, String name) {
        ModernScoreboard.Team team = board.getTeam(name);
        if (team == null) throw new IllegalArgumentException("Unknown team: " + name);
        return team;
    }

    private static boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Expected true or false, got " + value);
    }

    private static String joinNames(Collection<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage: " + this.usageMessage);
        return true;
    }

    @Override
    public boolean matches(String input) {
        return input.equals("team") || input.startsWith("team ");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        ModernScoreboard board = ScoreboardCommandSupport.manager().getScoreboard();
        if (args.length == 1) return ScoreboardCommandSupport.complete(args[0],
            Arrays.asList("list", "add", "remove", "empty", "join", "leave", "modify"));
        List<String> teams = new ArrayList<String>();
        for (ModernScoreboard.Team team : board.getTeams()) teams.add(team.name);
        if (args.length == 2 && !args[0].equalsIgnoreCase("leave") && !args[0].equalsIgnoreCase("add")) {
            return ScoreboardCommandSupport.complete(args[1], teams);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("modify")) {
            return ScoreboardCommandSupport.complete(args[2], Arrays.asList("displayName", "color", "friendlyFire",
                "seeFriendlyInvisibles", "nametagVisibility", "deathMessageVisibility", "collisionRule", "prefix", "suffix"));
        }
        return Collections.emptyList();
    }
}
