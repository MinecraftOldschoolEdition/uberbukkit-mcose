package org.bukkit.command.defaults;

import net.minecraft.server.scoreboard.ModernScoreboard;
import net.minecraft.server.scoreboard.ScoreboardCommandSupport;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Player-facing one-shot mutation for enabled trigger objectives. */
public final class TriggerCommand extends VanillaCommand {
    public TriggerCommand() {
        super("trigger");
        this.description = "Modifies an enabled trigger objective.";
        this.usageMessage = "/trigger <objective> [add|set] <value>";
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /trigger.");
            return true;
        }
        if (args.length != 1 && args.length != 3) return usage(sender);
        ModernScoreboard board = ScoreboardCommandSupport.manager().getScoreboard();
        ModernScoreboard.Objective objective = board.getObjective(args[0]);
        if (objective == null || !"trigger".equals(objective.criteria)) {
            sender.sendMessage(ChatColor.RED + "Unknown trigger objective: " + args[0]);
            return true;
        }
        ModernScoreboard.Score score = board.getScore(sender.getName(), objective.name);
        if (score == null || score.locked) {
            sender.sendMessage(ChatColor.RED + "Trigger " + objective.name + " is not enabled.");
            return true;
        }
        int value = 1;
        boolean add = true;
        if (args.length == 3) {
            add = args[1].equalsIgnoreCase("add");
            if (!add && !args[1].equalsIgnoreCase("set")) return usage(sender);
            try {
                value = Integer.parseInt(args[2]);
            } catch (NumberFormatException invalid) {
                sender.sendMessage(ChatColor.RED + "Invalid trigger value: " + args[2]);
                return true;
            }
        }
        board.setScore(sender.getName(), objective, add ? score.value + value : value, true);
        board.setScoreLocked(sender.getName(), objective, true);
        sender.sendMessage(ChatColor.YELLOW + "Triggered " + objective.name + ".");
        return true;
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage: " + this.usageMessage);
        return true;
    }

    @Override
    public boolean matches(String input) {
        return input.equals("trigger") || input.startsWith("trigger ");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            List<String> objectives = new ArrayList<String>();
            ModernScoreboard board = ScoreboardCommandSupport.manager().getScoreboard();
            for (ModernScoreboard.Objective objective : board.getObjectives()) {
                ModernScoreboard.Score score = board.getScore(sender.getName(), objective.name);
                if ("trigger".equals(objective.criteria) && score != null && !score.locked) objectives.add(objective.name);
            }
            return ScoreboardCommandSupport.complete(args[0], objectives);
        }
        if (args.length == 2) return ScoreboardCommandSupport.complete(args[1], java.util.Arrays.asList("add", "set"));
        return Collections.emptyList();
    }
}
