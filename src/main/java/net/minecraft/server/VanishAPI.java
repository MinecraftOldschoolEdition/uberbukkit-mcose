package net.minecraft.server;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public final class VanishAPI {
    private static final Set<String> VANISHED = new HashSet<String>(); // lowercase names

    private VanishAPI() {}

    public static boolean isVanished(Player p) {
        if (p == null) return false;
        return VANISHED.contains(p.getName().toLowerCase());
    }

    public static void vanish(Player target) {
        if (target == null) return;
        String key = target.getName().toLowerCase();
        if (!VANISHED.add(key)) return; // already vanished
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target)) viewer.hidePlayer(target);
        }
        // fun: emit smoke puff at vanish location
        try {
            org.bukkit.Location loc = target.getLocation();
            WorldServer world = ((org.bukkit.craftbukkit.CraftWorld) loc.getWorld()).getHandle();
            world.server.getTracker(world.dimension).sendPacketToEntity(((org.bukkit.craftbukkit.entity.CraftPlayer) target).getHandle(), new Packet61(2001, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), 0));
        } catch (Throwable ignore) {}
    }

    public static void reveal(Player target) {
        if (target == null) return;
        String key = target.getName().toLowerCase();
        if (!VANISHED.remove(key)) return; // already visible
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target)) viewer.showPlayer(target);
        }
        // fun: emit smoke puff at reveal location
        try {
            org.bukkit.Location loc = target.getLocation();
            WorldServer world = ((org.bukkit.craftbukkit.CraftWorld) loc.getWorld()).getHandle();
            world.server.getTracker(world.dimension).sendPacketToEntity(((org.bukkit.craftbukkit.entity.CraftPlayer) target).getHandle(), new Packet61(2001, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), 0));
        } catch (Throwable ignore) {}
    }

    public static boolean toggle(Player target) {
        boolean nowVanished;
        if (isVanished(target)) {
            reveal(target);
            nowVanished = false;
        } else {
            vanish(target);
            nowVanished = true;
        }
        return nowVanished;
    }

    public static void applyForJoiner(Player joiner) {
        if (joiner == null) return;
        for (String name : VANISHED) {
            Player target = Bukkit.getPlayerExact(name);
            if (target != null && !joiner.equals(target)) {
                joiner.hidePlayer(target);
            }
        }
    }
}


