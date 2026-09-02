package net.minecraft.server.scoreboard;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Packet250CustomPayload;
import net.minecraft.server.Packet3Chat;
import net.minecraft.server.Statistic;
import net.minecraft.server.registry.StatisticRegistryApi;
import net.minecraft.server.util.ResourceLocation;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Owns scoreboard persistence, criteria publication, and client snapshots. */
public final class ServerScoreboardManager {
    private final MinecraftServer server;
    private final ModernScoreboard scoreboard = new ModernScoreboard();
    private final net.minecraft.world.scores.Scoreboard namedScoreboard =
        new net.minecraft.world.scores.Scoreboard(this.scoreboard);
    private File dataFile;
    private boolean dirty;
    private boolean syncPending;
    private int ticks;

    public ServerScoreboardManager(MinecraftServer server) {
        this.server = server;
        this.scoreboard.setChangeListener(new Runnable() {
            public void run() {
                dirty = true;
                syncPending = true;
            }
        });
    }

    public ModernScoreboard getScoreboard() {
        return this.scoreboard;
    }

    public net.minecraft.world.scores.Scoreboard getNamedScoreboard() {
        return this.namedScoreboard;
    }

    public synchronized void initialize(File primaryWorldDirectory) {
        File dataDirectory = new File(primaryWorldDirectory, "data");
        this.dataFile = new File(dataDirectory, "scoreboard.dat");
        if (!this.dataFile.isFile()) return;
        try {
            DataInputStream in = new DataInputStream(new GZIPInputStream(
                new BufferedInputStream(new FileInputStream(this.dataFile))));
            ModernScoreboard loaded = ModernScoreboard.read(in);
            if (in.read() != -1) throw new IOException("Trailing scoreboard data");
            in.close();
            this.scoreboard.replaceWith(loaded);
            this.dirty = false;
            this.syncPending = true;
            MinecraftServer.log.info("Loaded modern scoreboard from " + this.dataFile.getAbsolutePath());
        } catch (Throwable failure) {
            MinecraftServer.log.log(Level.SEVERE, "Could not load scoreboard " + this.dataFile, failure);
        }
    }

    public synchronized void tick() {
        ++this.ticks;
        if ((this.ticks % 10) == 0 && this.server.serverConfigurationManager != null) {
            List<EntityPlayer> players = this.server.serverConfigurationManager.getOnlinePlayersSnapshot();
            for (EntityPlayer player : players) updateReadOnlyCriteria(player);
        }
        if (this.syncPending) {
            this.syncPending = false;
            broadcastSnapshot();
        }
        if (this.dirty && (this.ticks % 100) == 0) save();
    }

    public synchronized void save() {
        if (!this.dirty || this.dataFile == null) return;
        File parent = this.dataFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            MinecraftServer.log.severe("Could not create scoreboard data directory " + parent);
            return;
        }
        File temporary = new File(parent, this.dataFile.getName() + ".tmp");
        try {
            DataOutputStream out = new DataOutputStream(new GZIPOutputStream(
                new BufferedOutputStream(new FileOutputStream(temporary))));
            this.scoreboard.write(out);
            out.close();
            try {
                Files.move(temporary.toPath(), this.dataFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), this.dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            this.dirty = false;
        } catch (Throwable failure) {
            MinecraftServer.log.log(Level.SEVERE, "Could not save scoreboard " + this.dataFile, failure);
        }
    }

    public synchronized void sendSnapshot(EntityPlayer player) {
        if (player == null || player.netServerHandler == null || !player.netServerHandler.supportsHudScoreboard()) return;
        List<byte[]> parts = HudScoreboardProtocol.scoreboardSnapshot(this.scoreboard);
        for (byte[] payload : parts) {
            player.netServerHandler.sendPacket(new Packet250CustomPayload(HudScoreboardProtocol.CHANNEL_SCOREBOARD, payload));
        }
    }

    public synchronized void broadcastSnapshot() {
        if (this.server.serverConfigurationManager == null) return;
        List<byte[]> parts = HudScoreboardProtocol.scoreboardSnapshot(this.scoreboard);
        if (parts.isEmpty()) {
            MinecraftServer.log.warning("Scoreboard snapshot was too large or could not be encoded");
            return;
        }
        List<EntityPlayer> players = this.server.serverConfigurationManager.getOnlinePlayersSnapshot();
        for (EntityPlayer player : players) {
            if (player == null || player.netServerHandler == null || !player.netServerHandler.supportsHudScoreboard()) continue;
            for (byte[] payload : parts) {
                player.netServerHandler.sendPacket(new Packet250CustomPayload(HudScoreboardProtocol.CHANNEL_SCOREBOARD, payload));
            }
        }
    }

    public void sendTitle(EntityPlayer player, int action, String text) {
        if (player == null || player.netServerHandler == null || !player.netServerHandler.supportsHudScoreboard()) return;
        player.netServerHandler.sendPacket(new Packet250CustomPayload(
            HudScoreboardProtocol.CHANNEL_HUD,
            HudScoreboardProtocol.titleText(action, text)));
    }

    public void clearTitle(EntityPlayer player, boolean resetTimes) {
        if (player == null || player.netServerHandler == null || !player.netServerHandler.supportsHudScoreboard()) return;
        player.netServerHandler.sendPacket(new Packet250CustomPayload(
            HudScoreboardProtocol.CHANNEL_HUD,
            HudScoreboardProtocol.titleClear(resetTimes)));
    }

    public void setTitleTimes(EntityPlayer player, int fadeIn, int stay, int fadeOut) {
        if (player == null || player.netServerHandler == null || !player.netServerHandler.supportsHudScoreboard()) return;
        player.netServerHandler.sendPacket(new Packet250CustomPayload(
            HudScoreboardProtocol.CHANNEL_HUD,
            HudScoreboardProtocol.titleTimes(fadeIn, stay, fadeOut)));
    }

    public void recordDeath(EntityPlayer victim, net.minecraft.server.Entity source) {
        if (victim == null) return;
        ModernScoreboard board = this.scoreboard;
        board.incrementCriteria(victim.name, "deathCount", 1);
        EntityPlayer killer = resolvePlayerSource(source);
        if (killer == null || killer == victim) return;
        board.incrementCriteria(killer.name, "playerKillCount", 1);
        board.incrementCriteria(killer.name, "totalKillCount", 1);
        ModernScoreboard.Team victimTeam = board.getPlayersTeam(victim.name);
        ModernScoreboard.Team killerTeam = board.getPlayersTeam(killer.name);
        if (victimTeam != null && victimTeam.color >= 0) {
            board.incrementCriteria(killer.name, "teamkill." + colorName(victimTeam.color), 1);
        }
        if (killerTeam != null && killerTeam.color >= 0) {
            board.incrementCriteria(victim.name, "killedByTeam." + colorName(killerTeam.color), 1);
        }
    }

    public void recordMobKill(EntityPlayer killer) {
        if (killer != null) this.scoreboard.incrementCriteria(killer.name, "totalKillCount", 1);
    }

    public void recordMobKill(net.minecraft.server.Entity source) {
        recordMobKill(resolvePlayerSource(source));
    }

    public void sendDeathMessage(EntityPlayer victim, String message) {
        if (victim == null || message == null || this.server.serverConfigurationManager == null) return;
        ModernScoreboard.Team victimTeam = this.scoreboard.getPlayersTeam(victim.name);
        String visibility = victimTeam == null ? "always" : victimTeam.deathMessageVisibility;
        if ("never".equals(visibility)) return;
        for (EntityPlayer recipient : this.server.serverConfigurationManager.getOnlinePlayersSnapshot()) {
            ModernScoreboard.Team recipientTeam = this.scoreboard.getPlayersTeam(recipient.name);
            boolean sameTeam = victimTeam != null && victimTeam == recipientTeam;
            if ("hideForOtherTeams".equals(visibility) && !sameTeam) continue;
            if ("hideForOwnTeam".equals(visibility) && sameTeam) continue;
            recipient.netServerHandler.sendPacket(new Packet3Chat(message));
        }
    }

    public void recordStatistic(EntityPlayer player, Statistic statistic, int amount) {
        if (player == null || statistic == null || amount <= 0) return;
        ResourceLocation key = StatisticRegistryApi.getKey(statistic);
        if (key == null) return;
        String raw = key.toString();
        this.scoreboard.incrementCriteria(player.name, raw, amount);
        String modern = modernCriteria(raw);
        if (!modern.equals(raw)) this.scoreboard.incrementCriteria(player.name, modern, amount);
    }

    private void updateReadOnlyCriteria(EntityPlayer player) {
        if (player == null) return;
        this.scoreboard.updateReadOnlyCriteria(player.name, "health", player.health);
        this.scoreboard.updateReadOnlyCriteria(player.name, "air", Math.max(0, player.airTicks));
        this.scoreboard.updateReadOnlyCriteria(player.name, "armor", player.inventory == null ? 0 : player.inventory.g());
        // Beta has no hunger or XP systems. Their modern read-only criteria are
        // still present and deterministically expose the compatibility values.
        this.scoreboard.updateReadOnlyCriteria(player.name, "food", 20);
        this.scoreboard.updateReadOnlyCriteria(player.name, "xp", 0);
        this.scoreboard.updateReadOnlyCriteria(player.name, "level", 0);
    }

    private static EntityPlayer resolvePlayerSource(net.minecraft.server.Entity source) {
        if (source instanceof EntityPlayer) return (EntityPlayer) source;
        if (source instanceof net.minecraft.server.EntityArrow
                && ((net.minecraft.server.EntityArrow) source).shooter instanceof EntityPlayer) {
            return (EntityPlayer) ((net.minecraft.server.EntityArrow) source).shooter;
        }
        return null;
    }

    private static String modernCriteria(String registryKey) {
        int colon = registryKey.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : registryKey.substring(0, colon);
        String path = colon < 0 ? registryKey : registryKey.substring(colon + 1);
        if (path.startsWith("generic/")) {
            return "minecraft.custom:" + namespace + "." + path.substring("generic/".length()).replace('/', '.');
        }
        if (path.startsWith("block/mined/")) {
            return "minecraft.mined:" + namespace + "." + path.substring("block/mined/".length()).replace('/', '.');
        }
        if (path.startsWith("item/used/")) {
            return "minecraft.used:" + namespace + "." + path.substring("item/used/".length()).replace('/', '.');
        }
        if (path.startsWith("item/crafted/")) {
            return "minecraft.crafted:" + namespace + "." + path.substring("item/crafted/".length()).replace('/', '.');
        }
        if (path.startsWith("item/depleted/")) {
            return "minecraft.broken:" + namespace + "." + path.substring("item/depleted/".length()).replace('/', '.');
        }
        if (path.startsWith("item/pickup/")) {
            return "minecraft.picked_up:" + namespace + "." + path.substring("item/pickup/".length()).replace('/', '.');
        }
        return registryKey;
    }

    private static String colorName(int color) {
        String[] names = new String[] {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
            "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red",
            "light_purple", "yellow", "white"};
        return color >= 0 && color < names.length ? names[color] : "white";
    }
}
