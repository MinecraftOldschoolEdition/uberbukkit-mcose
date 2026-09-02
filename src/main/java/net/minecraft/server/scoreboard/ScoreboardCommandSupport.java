package net.minecraft.server.scoreboard;

import net.minecraft.server.EntityPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class ScoreboardCommandSupport {
    private static final Random RANDOM = new Random();
    private static final String[] COLORS = new String[] {"black", "dark_blue", "dark_green", "dark_aqua",
        "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red",
        "light_purple", "yellow", "white"};
    private static final char[] COLOR_CODES = new char[] {'0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private ScoreboardCommandSupport() {
    }

    public static ServerScoreboardManager manager() {
        return ((org.bukkit.craftbukkit.CraftServer) Bukkit.getServer()).getServer().modernScoreboardManager;
    }

    public static List<EntityPlayer> players(CommandSender sender, String selector) {
        List<EntityPlayer> online = new ArrayList<EntityPlayer>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player instanceof CraftPlayer) online.add(((CraftPlayer) player).getHandle());
        }
        if ("@a".equalsIgnoreCase(selector) || "*".equals(selector)) return online;
        if ("@s".equalsIgnoreCase(selector)) {
            return sender instanceof CraftPlayer
                ? Collections.singletonList(((CraftPlayer) sender).getHandle())
                : Collections.<EntityPlayer>emptyList();
        }
        if ("@p".equalsIgnoreCase(selector)) {
            if (sender instanceof CraftPlayer) return Collections.singletonList(((CraftPlayer) sender).getHandle());
            return online.isEmpty() ? Collections.<EntityPlayer>emptyList() : Collections.singletonList(online.get(0));
        }
        if ("@r".equalsIgnoreCase(selector)) {
            return online.isEmpty() ? Collections.<EntityPlayer>emptyList()
                : Collections.singletonList(online.get(RANDOM.nextInt(online.size())));
        }
        for (EntityPlayer player : online) {
            if (player.name.equalsIgnoreCase(selector)) return Collections.singletonList(player);
        }
        return Collections.emptyList();
    }

    public static Collection<String> holders(CommandSender sender, String selector, ModernScoreboard scoreboard,
                                      boolean wildcardUsesTracked) {
        Set<String> result = new LinkedHashSet<String>();
        if ("*".equals(selector) && wildcardUsesTracked) {
            result.addAll(scoreboard.getTrackedHolders());
            return result;
        }
        if (selector.startsWith("@")) {
            for (EntityPlayer player : players(sender, selector)) result.add(player.name);
            return result;
        }
        result.add(selector);
        return result;
    }

    public static String join(String[] args, int start) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < args.length; ++i) {
            if (result.length() > 0) result.append(' ');
            result.append(args[i]);
        }
        return result.toString();
    }

    /**
     * Converts the literal/string or common SNBT component shapes used by
     * 26.3 commands into the legacy renderer's section-formatted string.
     */
    public static String component(String raw) {
        if (raw == null) return "";
        raw = raw.trim();
        if (isQuoted(raw)) return unescape(raw.substring(1, raw.length() - 1));
        if (!raw.startsWith("{") && !raw.startsWith("[")) return raw;
        String style = stylePrefix(raw);
        List<String> values = new ArrayList<String>();
        collectFieldValues(raw, "text", values);
        if (values.isEmpty()) collectFieldValues(raw, "translate", values);
        if (values.isEmpty()) return raw;
        StringBuilder result = new StringBuilder(style);
        for (String value : values) result.append(value);
        if (style.length() > 0) result.append('\u00a7').append('r');
        return result.toString();
    }

    public static String stylePrefix(String raw) {
        StringBuilder style = new StringBuilder();
        String color = fieldValue(raw, "color");
        int colorId = colorId(color);
        if (colorId >= 0) style.append('\u00a7').append(COLOR_CODES[colorId]);
        if (booleanField(raw, "obfuscated")) style.append("\u00a7k");
        if (booleanField(raw, "bold")) style.append("\u00a7l");
        if (booleanField(raw, "strikethrough")) style.append("\u00a7m");
        if (booleanField(raw, "underlined")) style.append("\u00a7n");
        if (booleanField(raw, "italic")) style.append("\u00a7o");
        return style.toString();
    }

    public static int colorId(String name) {
        if (name == null) return -1;
        for (int i = 0; i < COLORS.length; ++i) if (COLORS[i].equalsIgnoreCase(name)) return i;
        return -1;
    }

    public static List<String> complete(String value, Collection<String> choices) {
        String prefix = value == null ? "" : value.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (String choice : choices) if (choice.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(choice);
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public static List<String> onlineNamesAndSelectors() {
        List<String> result = new ArrayList<String>();
        result.add("@a");
        result.add("@p");
        result.add("@r");
        result.add("@s");
        for (Player player : Bukkit.getOnlinePlayers()) result.add(player.getName());
        return result;
    }

    private static void collectFieldValues(String raw, String key, List<String> result) {
        int from = 0;
        while (from < raw.length()) {
            int keyAt = findKey(raw, key, from);
            if (keyAt < 0) return;
            int colon = raw.indexOf(':', keyAt + key.length());
            if (colon < 0) return;
            ParsedValue value = parseValue(raw, colon + 1);
            if (value != null) {
                result.add(value.value);
                from = value.end;
            } else {
                from = colon + 1;
            }
        }
    }

    private static String fieldValue(String raw, String key) {
        int keyAt = findKey(raw, key, 0);
        if (keyAt < 0) return null;
        int colon = raw.indexOf(':', keyAt + key.length());
        ParsedValue value = colon < 0 ? null : parseValue(raw, colon + 1);
        return value == null ? null : value.value;
    }

    private static boolean booleanField(String raw, String key) {
        String value = fieldValue(raw, key);
        return "true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static int findKey(String raw, String key, int from) {
        String lower = raw.toLowerCase(Locale.ROOT);
        String target = key.toLowerCase(Locale.ROOT);
        while (from >= 0 && from < raw.length()) {
            int found = lower.indexOf(target, from);
            if (found < 0) return -1;
            int before = found - 1;
            int after = found + target.length();
            boolean left = before < 0 || raw.charAt(before) == '\'' || raw.charAt(before) == '"'
                || raw.charAt(before) == '{' || raw.charAt(before) == ',' || Character.isWhitespace(raw.charAt(before));
            boolean right = after >= raw.length() || raw.charAt(after) == '\'' || raw.charAt(after) == '"'
                || raw.charAt(after) == ':' || Character.isWhitespace(raw.charAt(after));
            if (left && right) return found;
            from = found + target.length();
        }
        return -1;
    }

    private static ParsedValue parseValue(String raw, int start) {
        while (start < raw.length() && Character.isWhitespace(raw.charAt(start))) ++start;
        if (start >= raw.length()) return null;
        char first = raw.charAt(start);
        if (first == '\'' || first == '"') {
            StringBuilder value = new StringBuilder();
            boolean escaped = false;
            for (int i = start + 1; i < raw.length(); ++i) {
                char ch = raw.charAt(i);
                if (escaped) {
                    value.append(ch == 'n' ? '\n' : ch);
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == first) {
                    return new ParsedValue(value.toString(), i + 1);
                } else {
                    value.append(ch);
                }
            }
            return null;
        }
        int end = start;
        while (end < raw.length() && raw.charAt(end) != ',' && raw.charAt(end) != '}'
                && raw.charAt(end) != ']' && !Character.isWhitespace(raw.charAt(end))) ++end;
        return end == start ? null : new ParsedValue(raw.substring(start, end), end);
    }

    private static boolean isQuoted(String value) {
        return value.length() >= 2 && ((value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"')
            || (value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\''));
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\'", "'").replace("\\\\", "\\");
    }

    private static final class ParsedValue {
        final String value;
        final int end;

        ParsedValue(String value, int end) {
            this.value = value;
            this.end = end;
        }
    }
}
