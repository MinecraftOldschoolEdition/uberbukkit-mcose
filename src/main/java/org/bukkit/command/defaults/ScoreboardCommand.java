package org.bukkit.command.defaults;

import net.minecraft.server.scoreboard.ModernScoreboard;
import net.minecraft.server.scoreboard.ScoreboardCommandSupport;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Full 26.3 /scoreboard objective and player command surface. */
public final class ScoreboardCommand extends VanillaCommand {
    public ScoreboardCommand() {
        super("scoreboard");
        this.description = "Manages scoreboard objectives and scores.";
        this.usageMessage = "/scoreboard <objectives|players> ...";
        this.setPermission("bukkit.command.scoreboard");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;
        if (args.length < 2) return usage(sender);
        try {
            ModernScoreboard board = ScoreboardCommandSupport.manager().getScoreboard();
            if (args[0].equalsIgnoreCase("objectives")) return objectives(sender, args, board);
            if (args[0].equalsIgnoreCase("players")) return players(sender, args, board);
            return usage(sender);
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage(ChatColor.RED + invalid.getMessage());
            return true;
        } catch (IllegalStateException invalid) {
            sender.sendMessage(ChatColor.RED + invalid.getMessage());
            return true;
        }
    }

    private boolean objectives(CommandSender sender, String[] args, ModernScoreboard board) {
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            if (args.length != 2) return usage(sender);
            Collection<ModernScoreboard.Objective> objectives = board.getObjectives();
            if (objectives.isEmpty()) sender.sendMessage(ChatColor.YELLOW + "There are no objectives.");
            for (ModernScoreboard.Objective objective : objectives) {
                sender.sendMessage("- " + objective.name + " (" + objective.displayName + ", " + objective.criteria + ")");
            }
            return true;
        }
        if ("add".equals(action)) {
            if (args.length < 4) return usage(sender);
            validateCriteria(args[3]);
            String display = args.length > 4
                ? ScoreboardCommandSupport.component(ScoreboardCommandSupport.join(args, 4)) : args[2];
            ModernScoreboard.Objective objective = board.addObjective(args[2], args[3], display);
            sender.sendMessage(ChatColor.YELLOW + "Added objective " + objective.name + ".");
            return true;
        }
        if ("remove".equals(action)) {
            if (args.length != 3) return usage(sender);
            requireObjective(board, args[2]);
            board.removeObjective(args[2]);
            sender.sendMessage(ChatColor.YELLOW + "Removed objective " + args[2] + ".");
            return true;
        }
        if ("setdisplay".equals(action)) {
            if (args.length < 3 || args.length > 4) return usage(sender);
            int slot = ModernScoreboard.displaySlotByName(args[2]);
            if (slot < 0) throw new IllegalArgumentException("Unknown display slot: " + args[2]);
            String objective = args.length == 4 ? requireObjective(board, args[3]).name : null;
            board.setDisplayObjective(slot, objective);
            sender.sendMessage(ChatColor.YELLOW + (objective == null
                ? "Cleared display slot " + args[2] + "." : "Set display slot " + args[2] + " to " + objective + "."));
            return true;
        }
        if ("modify".equals(action)) {
            if (args.length < 4) return usage(sender);
            ModernScoreboard.Objective objective = requireObjective(board, args[2]);
            String option = args[3].toLowerCase(Locale.ROOT);
            if ("displayname".equals(option)) {
                if (args.length < 5) return usage(sender);
                board.updateObjective(objective,
                    ScoreboardCommandSupport.component(ScoreboardCommandSupport.join(args, 4)), null, null, null);
            } else if ("rendertype".equals(option)) {
                if (args.length != 5 || !(args[4].equalsIgnoreCase("integer") || args[4].equalsIgnoreCase("hearts"))) return usage(sender);
                board.updateObjective(objective, null, ModernScoreboard.RenderType.byName(args[4]), null, null);
            } else if ("displayautoupdate".equals(option)) {
                if (args.length != 5) return usage(sender);
                board.updateObjective(objective, null, null, Boolean.valueOf(parseBoolean(args[4])), null);
            } else if ("numberformat".equals(option)) {
                board.setObjectiveNumberFormat(objective, parseFormat(args, 4));
            } else {
                return usage(sender);
            }
            sender.sendMessage(ChatColor.YELLOW + "Updated objective " + objective.name + ".");
            return true;
        }
        return usage(sender);
    }

    private boolean players(CommandSender sender, String[] args, ModernScoreboard board) {
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            if (args.length == 2) {
                Collection<String> holders = board.getTrackedHolders();
                sender.sendMessage(ChatColor.YELLOW + "Tracked score holders (" + holders.size() + "): " + joinNames(holders));
                return true;
            }
            if (args.length != 3) return usage(sender);
            List<ModernScoreboard.ScoreEntry> entries = board.getScores(args[2]);
            if (entries.isEmpty()) sender.sendMessage(ChatColor.YELLOW + args[2] + " has no scores.");
            for (ModernScoreboard.ScoreEntry entry : entries) {
                sender.sendMessage("- " + entry.objective.name + " = " + entry.score.value);
            }
            return true;
        }
        if ("get".equals(action)) {
            if (args.length != 4) return usage(sender);
            ModernScoreboard.Objective objective = requireObjective(board, args[3]);
            ModernScoreboard.Score score = board.getScore(args[2], objective.name);
            if (score == null) throw new IllegalArgumentException(args[2] + " has no score for " + objective.name);
            sender.sendMessage(args[2] + " has " + score.value + " in " + objective.name + ".");
            return true;
        }
        if ("set".equals(action) || "add".equals(action) || "remove".equals(action)) {
            if (args.length != 5) return usage(sender);
            Collection<String> targets = requireHolders(sender, args[2], board, true);
            ModernScoreboard.Objective objective = writableObjective(board, args[3]);
            int amount = parseInt(args[4], "score");
            for (String target : targets) {
                ModernScoreboard.Score old = board.getScore(target, objective.name);
                int value = "set".equals(action) ? amount : (old == null ? 0 : old.value)
                    + ("add".equals(action) ? amount : -amount);
                board.setScore(target, objective, value, false);
            }
            sender.sendMessage(ChatColor.YELLOW + "Updated " + targets.size() + " score holder(s). ");
            return true;
        }
        if ("reset".equals(action)) {
            if (args.length < 3 || args.length > 4) return usage(sender);
            Collection<String> targets = requireHolders(sender, args[2], board, true);
            String objective = args.length == 4 ? requireObjective(board, args[3]).name : null;
            for (String target : targets) board.resetScore(target, objective);
            sender.sendMessage(ChatColor.YELLOW + "Reset " + targets.size() + " score holder(s). ");
            return true;
        }
        if ("enable".equals(action)) {
            if (args.length != 4) return usage(sender);
            Collection<String> targets = requireHolders(sender, args[2], board, true);
            ModernScoreboard.Objective objective = requireObjective(board, args[3]);
            if (!"trigger".equals(objective.criteria)) throw new IllegalArgumentException(objective.name + " is not a trigger objective");
            int changed = 0;
            for (String target : targets) {
                ModernScoreboard.Score score = board.getOrCreateScore(target, objective, true);
                if (score.locked) {
                    board.setScoreLocked(target, objective, false);
                    ++changed;
                }
            }
            sender.sendMessage(ChatColor.YELLOW + "Enabled trigger for " + changed + " score holder(s). ");
            return true;
        }
        if ("display".equals(action)) {
            if (args.length < 5) return usage(sender);
            String option = args[2].toLowerCase(Locale.ROOT);
            Collection<String> targets = requireHolders(sender, args[3], board, true);
            ModernScoreboard.Objective objective = requireObjective(board, args[4]);
            if ("name".equals(option)) {
                String display = args.length == 5 ? null
                    : ScoreboardCommandSupport.component(ScoreboardCommandSupport.join(args, 5));
                for (String target : targets) board.setScoreDisplay(target, objective, display);
            } else if ("numberformat".equals(option)) {
                ModernScoreboard.NumberFormat format = parseFormat(args, 5);
                for (String target : targets) board.setScoreNumberFormat(target, objective, format);
            } else {
                return usage(sender);
            }
            sender.sendMessage(ChatColor.YELLOW + "Updated score display for " + targets.size() + " score holder(s). ");
            return true;
        }
        if ("operation".equals(action)) {
            if (args.length != 7) return usage(sender);
            Collection<String> targets = requireHolders(sender, args[2], board, true);
            ModernScoreboard.Objective targetObjective = writableObjective(board, args[3]);
            String operation = args[4];
            Collection<String> sources = requireHolders(sender, args[5], board, true);
            ModernScoreboard.Objective sourceObjective = requireObjective(board, args[6]);
            for (String target : targets) {
                for (String source : sources) performOperation(board, target, targetObjective, operation, source, sourceObjective);
            }
            sender.sendMessage(ChatColor.YELLOW + "Applied scoreboard operation to " + targets.size() + " score holder(s). ");
            return true;
        }
        return usage(sender);
    }

    static void performOperation(ModernScoreboard board, String target, ModernScoreboard.Objective targetObjective,
                                 String operation, String source, ModernScoreboard.Objective sourceObjective) {
        ModernScoreboard.Score left = board.getOrCreateScore(target, targetObjective, false);
        ModernScoreboard.Score right = board.getOrCreateScore(source, sourceObjective, true);
        int a = left.value;
        int b = right.value;
        if ("=".equals(operation)) a = b;
        else if ("+=".equals(operation)) a += b;
        else if ("-=".equals(operation)) a -= b;
        else if ("*=".equals(operation)) a *= b;
        else if ("/=".equals(operation)) {
            if (b == 0) throw new IllegalArgumentException("Cannot divide by zero");
            a = Math.floorDiv(a, b);
        } else if ("%=".equals(operation)) {
            if (b == 0) throw new IllegalArgumentException("Cannot divide by zero");
            a = Math.floorMod(a, b);
        } else if ("<".equals(operation)) a = Math.min(a, b);
        else if (">".equals(operation)) a = Math.max(a, b);
        else if ("><".equals(operation)) {
            board.setScore(target, targetObjective, b, false);
            board.setScore(source, sourceObjective, a, true);
            return;
        } else throw new IllegalArgumentException("Unknown scoreboard operation: " + operation);
        board.setScore(target, targetObjective, a, false);
    }

    static ModernScoreboard.NumberFormat parseFormat(String[] args, int start) {
        if (start >= args.length) return null;
        String type = args[start].toLowerCase(Locale.ROOT);
        if ("blank".equals(type) && args.length == start + 1) return ModernScoreboard.NumberFormat.blank();
        if ("fixed".equals(type) && args.length > start + 1) {
            return ModernScoreboard.NumberFormat.fixed(
                ScoreboardCommandSupport.component(ScoreboardCommandSupport.join(args, start + 1)));
        }
        if ("styled".equals(type) && args.length > start + 1) {
            return ModernScoreboard.NumberFormat.styled(
                ScoreboardCommandSupport.stylePrefix(ScoreboardCommandSupport.join(args, start + 1)));
        }
        throw new IllegalArgumentException("Number format must be blank, fixed <text>, styled <style>, or omitted");
    }

    private static ModernScoreboard.Objective requireObjective(ModernScoreboard board, String name) {
        ModernScoreboard.Objective objective = board.getObjective(name);
        if (objective == null) throw new IllegalArgumentException("Unknown objective: " + name);
        return objective;
    }

    private static ModernScoreboard.Objective writableObjective(ModernScoreboard board, String name) {
        ModernScoreboard.Objective objective = requireObjective(board, name);
        if (objective.isReadOnly()) throw new IllegalArgumentException("Objective is read-only: " + name);
        return objective;
    }

    private static Collection<String> requireHolders(CommandSender sender, String token,
                                                     ModernScoreboard board, boolean wildcard) {
        Collection<String> holders = ScoreboardCommandSupport.holders(sender, token, board, wildcard);
        if (holders.isEmpty()) throw new IllegalArgumentException("No score holders matched " + token);
        return holders;
    }

    private static void validateCriteria(String criteria) {
        if (ObjectiveCriteria.getCustomCriteriaNames().contains(criteria) || criteria.indexOf(':') > 0) return;
        throw new IllegalArgumentException("Unknown objective criteria: " + criteria);
    }

    private static boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Expected true or false, got " + value);
    }

    private static int parseInt(String value, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
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
        return input.equals("scoreboard") || input.startsWith("scoreboard ");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        ModernScoreboard board = ScoreboardCommandSupport.manager().getScoreboard();
        if (args.length == 1) return ScoreboardCommandSupport.complete(args[0], Arrays.asList("objectives", "players"));
        if (args.length == 2 && args[0].equalsIgnoreCase("objectives")) {
            return ScoreboardCommandSupport.complete(args[1], Arrays.asList("list", "add", "modify", "remove", "setdisplay"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("players")) {
            return ScoreboardCommandSupport.complete(args[1], Arrays.asList("list", "set", "get", "add", "remove", "reset", "enable", "display", "operation"));
        }
        List<String> objectiveNames = new ArrayList<String>();
        for (ModernScoreboard.Objective objective : board.getObjectives()) objectiveNames.add(objective.name);
        if (args.length == 3 && args[0].equalsIgnoreCase("objectives")
                && (args[1].equalsIgnoreCase("modify") || args[1].equalsIgnoreCase("remove"))) {
            return ScoreboardCommandSupport.complete(args[2], objectiveNames);
        }
        return Collections.emptyList();
    }
}
