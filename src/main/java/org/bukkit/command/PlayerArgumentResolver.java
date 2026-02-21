package org.bukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Resolves player command arguments, including selector tokens.
 */
public final class PlayerArgumentResolver {
    public static final String SELECTOR_ALL = "@a";
    public static final String SELECTOR_NEAREST = "@p";
    public static final String SELECTOR_RANDOM = "@r";

    private static final String[] SELECTOR_TOKENS = new String[] { SELECTOR_ALL, SELECTOR_NEAREST, SELECTOR_RANDOM };
    private static final Random RANDOM = new Random();

    private static final Comparator<Player> PLAYER_NAME_ORDER = new Comparator<Player>() {
        public int compare(Player a, Player b) {
            String an = a == null || a.getName() == null ? "" : a.getName();
            String bn = b == null || b.getName() == null ? "" : b.getName();

            int cmp = an.compareToIgnoreCase(bn);
            if (cmp != 0) {
                return cmp;
            }
            return an.compareTo(bn);
        }
    };

    private PlayerArgumentResolver() {}

    public static List<String> suggest(String prefixLower) {
        String prefix = safeLower(prefixLower);
        ArrayList<String> out = new ArrayList<String>();

        for (int i = 0; i < SELECTOR_TOKENS.length; i++) {
            String selector = SELECTOR_TOKENS[i];
            if (selector.startsWith(prefix)) {
                out.add(selector);
            }
        }

        List<Player> players = snapshotOnlinePlayers();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (player == null || player.getName() == null) {
                continue;
            }
            String name = player.getName();
            if (safeLower(name).startsWith(prefix)) {
                out.add(name);
            }
        }

        return out;
    }

    public static boolean isSelectorToken(String token) {
        if (token == null) {
            return false;
        }
        String normalized = safeLower(token.trim());
        return SELECTOR_ALL.equals(normalized) || SELECTOR_NEAREST.equals(normalized) || SELECTOR_RANDOM.equals(normalized);
    }

    public static boolean isValidPlayerArgument(CommandSender sender, String token) {
        return !resolve(sender, token).isEmpty();
    }

    public static List<Player> resolve(CommandSender sender, String token) {
        if (token == null) {
            return Collections.emptyList();
        }

        String normalized = safeLower(token.trim());
        if (normalized.length() == 0) {
            return Collections.emptyList();
        }

        List<Player> players = snapshotOnlinePlayers();
        if (players.isEmpty()) {
            return Collections.emptyList();
        }

        if (SELECTOR_ALL.equals(normalized)) {
            return players;
        }

        if (SELECTOR_NEAREST.equals(normalized)) {
            Player nearest = nearestPlayer(sender, players);
            if (nearest == null) {
                return Collections.emptyList();
            }
            ArrayList<Player> one = new ArrayList<Player>();
            one.add(nearest);
            return one;
        }

        if (SELECTOR_RANDOM.equals(normalized)) {
            Player random = players.get(RANDOM.nextInt(players.size()));
            ArrayList<Player> one = new ArrayList<Player>();
            one.add(random);
            return one;
        }

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (player != null && player.getName() != null && player.getName().equalsIgnoreCase(token)) {
                ArrayList<Player> one = new ArrayList<Player>();
                one.add(player);
                return one;
            }
        }

        return Collections.emptyList();
    }

    public static Player resolveSingle(CommandSender sender, String token) {
        List<Player> players = resolve(sender, token);
        return players.isEmpty() ? null : players.get(0);
    }

    private static List<Player> snapshotOnlinePlayers() {
        ArrayList<Player> players = new ArrayList<Player>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != null && player.getName() != null) {
                players.add(player);
            }
        }
        Collections.sort(players, PLAYER_NAME_ORDER);
        return players;
    }

    private static Player nearestPlayer(CommandSender sender, List<Player> players) {
        if (!(sender instanceof Player)) {
            return players.get(0);
        }

        Player source = (Player) sender;
        Location sourceLocation = source.getLocation();
        if (sourceLocation == null || sourceLocation.getWorld() == null) {
            return players.get(0);
        }

        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int i = 0; i < players.size(); i++) {
            Player candidate = players.get(i);
            Location candidateLocation = candidate.getLocation();
            if (candidateLocation == null || candidateLocation.getWorld() != sourceLocation.getWorld()) {
                continue;
            }

            double distanceSquared = sourceLocation.distanceSquared(candidateLocation);
            if (nearest == null || distanceSquared < nearestDistance
                || (distanceSquared == nearestDistance && PLAYER_NAME_ORDER.compare(candidate, nearest) < 0)) {
                nearest = candidate;
                nearestDistance = distanceSquared;
            }
        }

        return nearest == null ? players.get(0) : nearest;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
