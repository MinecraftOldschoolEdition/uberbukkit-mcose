package org.bukkit.command.defaults;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.scoreboard.HudScoreboardProtocol;
import net.minecraft.server.scoreboard.ScoreboardCommandSupport;
import net.minecraft.server.scoreboard.ServerScoreboardManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 26.3-compatible title/subtitle/action-bar command over the MCOSE HUD channel. */
public final class TitleCommand extends VanillaCommand {
    public TitleCommand() {
        super("title");
        this.description = "Controls title, subtitle, and action-bar text.";
        this.usageMessage = "/title <targets> <clear|reset|title|subtitle|actionbar|times> ...";
        this.setPermission("bukkit.command.title");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;
        if (args.length < 2) return usage(sender);
        List<EntityPlayer> targets = ScoreboardCommandSupport.players(sender, args[0]);
        if (targets.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No players matched " + args[0]);
            return true;
        }
        String action = args[1].toLowerCase();
        ServerScoreboardManager manager = ScoreboardCommandSupport.manager();
        if ("clear".equals(action) || "reset".equals(action)) {
            if (args.length != 2) return usage(sender);
            for (EntityPlayer target : targets) manager.clearTitle(target, "reset".equals(action));
        } else if ("title".equals(action) || "subtitle".equals(action) || "actionbar".equals(action)) {
            if (args.length < 3) return usage(sender);
            String text = ScoreboardCommandSupport.component(ScoreboardCommandSupport.join(args, 2));
            int type = "title".equals(action) ? HudScoreboardProtocol.TITLE
                : "subtitle".equals(action) ? HudScoreboardProtocol.SUBTITLE : HudScoreboardProtocol.ACTION_BAR;
            for (EntityPlayer target : targets) manager.sendTitle(target, type, text);
        } else if ("times".equals(action)) {
            if (args.length != 5) return usage(sender);
            try {
                int fadeIn = parseTime(args[2]);
                int stay = parseTime(args[3]);
                int fadeOut = parseTime(args[4]);
                for (EntityPlayer target : targets) manager.setTitleTimes(target, fadeIn, stay, fadeOut);
            } catch (IllegalArgumentException invalid) {
                sender.sendMessage(ChatColor.RED + invalid.getMessage());
                return true;
            }
        } else {
            return usage(sender);
        }
        sender.sendMessage(ChatColor.YELLOW + "Updated titles for " + targets.size() + " player(s). ");
        return true;
    }

    static int parseTime(String value) {
        if (value == null || value.length() == 0) throw new IllegalArgumentException("Invalid time");
        int multiplier = 1;
        char suffix = Character.toLowerCase(value.charAt(value.length() - 1));
        String number = value;
        if (suffix == 't' || suffix == 's' || suffix == 'd') {
            number = value.substring(0, value.length() - 1);
            multiplier = suffix == 's' ? 20 : suffix == 'd' ? 24000 : 1;
        }
        long result;
        try {
            result = Math.multiplyExact(Long.parseLong(number), (long)multiplier);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid time: " + value);
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException("Invalid time: " + value);
        }
        if (result < 0 || result > Integer.MAX_VALUE) throw new IllegalArgumentException("Time is out of range: " + value);
        return (int) result;
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage: " + this.usageMessage);
        return true;
    }

    @Override
    public boolean matches(String input) {
        return input.equals("title") || input.startsWith("title ");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) return ScoreboardCommandSupport.complete(args[0], ScoreboardCommandSupport.onlineNamesAndSelectors());
        if (args.length == 2) return ScoreboardCommandSupport.complete(args[1],
            Arrays.asList("clear", "reset", "title", "subtitle", "actionbar", "times"));
        return Collections.emptyList();
    }
}
