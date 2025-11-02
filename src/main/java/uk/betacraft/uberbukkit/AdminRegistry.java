package uk.betacraft.uberbukkit;

import java.util.HashSet;
import java.util.Set;

/**
 * Lightweight server-wide registry for admin controls (freeze/mute).
 * Stored in-memory by lowercase player name.
 */
public final class AdminRegistry {
    private static final Set<String> FROZEN = new HashSet<String>();
    private static final Set<String> MUTED = new HashSet<String>();

    private AdminRegistry() {}

    public static boolean toggleFrozen(String name) {
        String key = safe(name);
        if (FROZEN.contains(key)) { FROZEN.remove(key); return false; }
        FROZEN.add(key); return true;
    }

    public static boolean isFrozen(String name) {
        return FROZEN.contains(safe(name));
    }

    public static boolean toggleMuted(String name) {
        String key = safe(name);
        if (MUTED.contains(key)) { MUTED.remove(key); return false; }
        MUTED.add(key); return true;
    }

    public static boolean isMuted(String name) {
        return MUTED.contains(safe(name));
    }

    private static String safe(String s) { return (s == null) ? "" : s.toLowerCase(); }
}


