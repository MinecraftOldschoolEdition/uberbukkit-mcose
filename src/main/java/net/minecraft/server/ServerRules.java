package net.minecraft.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import net.minecraft.server.network.ServerRulesProtocol;
import org.bukkit.entity.Player;

/** Persistent, server-wide rules shown by the negotiated MCOSE client GUI. */
public final class ServerRules {
    private static final Logger LOGGER = Logger.getLogger("Minecraft");
    private static final String FILE_NAME = "rules.txt";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final List<String> DEFAULT_RULES = Collections.unmodifiableList(Arrays.asList(
            "Be respectful to other players.",
            "Do not cheat, exploit bugs, or use unauthorized mods.",
            "Follow directions from server staff."));

    private ServerRules() {
    }

    public static synchronized List<String> getRules(MinecraftServer server) {
        File file = getRulesFile(server);
        if (file == null || !file.isFile()) {
            saveRules(server, DEFAULT_RULES);
            return DEFAULT_RULES;
        }
        ArrayList<String> rules = new ArrayList<String>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.length() > 0 && !trimmed.startsWith("#")) {
                    rules.add(trimmed);
                }
            }
        } catch (Exception exception) {
            LOGGER.warning("[Rules] Could not read " + FILE_NAME + ": " + exception.getMessage());
            return DEFAULT_RULES;
        } finally {
            closeQuietly(reader);
        }
        List<String> normalized = ServerRulesProtocol.normalizeRules(rules, false);
        return normalized == null ? DEFAULT_RULES : normalized;
    }

    public static synchronized boolean saveRules(MinecraftServer server, List<String> rules) {
        List<String> normalized = ServerRulesProtocol.normalizeRules(rules, true);
        File file = getRulesFile(server);
        if (normalized == null || file == null) {
            return false;
        }
        File parent = file.getParentFile();
        File temporary = parent == null ? new File(file.getPath() + ".tmp") : new File(parent, file.getName() + ".tmp");
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(temporary), UTF_8));
            writer.write("# Server Rules\n");
            writer.write("# Edit in game with /rules edit. One rule per line.\n");
            for (int i = 0; i < normalized.size(); ++i) {
                writer.write((String) normalized.get(i));
                writer.write('\n');
            }
            writer.flush();
            closeQuietly(writer);
            writer = null;
            if (file.exists() && !file.delete()) {
                throw new IllegalStateException("Could not replace " + file.getName());
            }
            if (!temporary.renameTo(file)) {
                throw new IllegalStateException("Could not move new " + file.getName() + " into place");
            }
            return true;
        } catch (Exception exception) {
            LOGGER.warning("[Rules] Could not save " + FILE_NAME + ": " + exception.getMessage());
            return false;
        } finally {
            closeQuietly(writer);
            if (temporary.exists()) {
                temporary.delete();
            }
        }
    }

    public static boolean canEdit(EntityPlayer player) {
        if (player == null || player.netServerHandler == null || player.netServerHandler.getPlayer() == null) {
            return false;
        }
        Player bukkitPlayer = player.netServerHandler.getPlayer();
        return bukkitPlayer.hasPermission("bukkit.command.rules.edit");
    }

    public static void sendScreen(EntityPlayer player, int screen) {
        if (player == null || player.netServerHandler == null || !player.netServerHandler.supportsServerRules()) {
            return;
        }
        player.netServerHandler.sendServerRulesScreen(screen, getRules(player.b));
    }

    private static File getRulesFile(MinecraftServer server) {
        return server == null ? null : server.a(FILE_NAME);
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
