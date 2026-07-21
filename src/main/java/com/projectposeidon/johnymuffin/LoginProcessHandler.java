package com.projectposeidon.johnymuffin;

import com.legacyminecraft.poseidon.PoseidonConfig;
import com.legacyminecraft.poseidon.PoseidonPlugin;
import com.legacyminecraft.poseidon.util.CrackedAllowlist;
import com.legacyminecraft.poseidon.uuid.ThreadUUIDFetcher;
import net.minecraft.server.NetLoginHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Packet1Login;
import net.minecraft.server.ThreadLoginVerifier;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerConnectionInitializationEvent;
import org.bukkit.event.player.PlayerPreLoginEvent;
import org.bukkit.plugin.Plugin;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.Future;

public class LoginProcessHandler {

    private NetLoginHandler netLoginHandler;
    private final Packet1Login packet1Login;
    private CraftServer server;
    private boolean onlineMode;
    private volatile boolean loginCancelled = false;
    private LoginProcessHandler loginProcessHandler;
    private volatile boolean loginSuccessful = false;
    private volatile Future<?> authenticationTask;
    private volatile boolean slowLoginWarningLogged = false;
    private long startTime;

    private HashSet<ConnectionPause> connectionPauses = new HashSet<ConnectionPause>();

    private final String msgKickAlreadyOnline;
    private boolean usingModernAuth = false; // Modern auth flag
    private ConnectionPause startupWarmupPause = null;
    private long startupWarmupHoldStartMs = 0L;
    private volatile boolean startupWarmupCheckScheduled = false;

    public LoginProcessHandler(NetLoginHandler netloginhandler, Packet1Login packet1login, CraftServer server, boolean onlineMode) {
        this.loginProcessHandler = this;
        this.netLoginHandler = netloginhandler;
        this.packet1Login = packet1login;
        this.server = server;
        this.onlineMode = onlineMode;
        this.netLoginHandler.setLoginProcessHandler(this);

        this.msgKickAlreadyOnline = PoseidonConfig.getInstance().getConfigString("message.kick.already-online");

        processAuthentication();

        long connectionStartTime = System.currentTimeMillis() / 1000L;
        runLoginTimer(connectionStartTime);

    }

    private void runLoginTimer(long connectionStartTime) {
        Bukkit.getScheduler().scheduleAsyncDelayedTask(new PoseidonPlugin(), () -> {
            int currentRunningTime = (int) (System.currentTimeMillis() / 1000L - connectionStartTime);
            if (!loginSuccessful && !loginCancelled) {
                if (currentRunningTime >= 10 && !this.slowLoginWarningLogged) {
                    this.slowLoginWarningLogged = true;
                    MinecraftServer.log.warning("[Poseidon] Login for " + packet1Login.name + " has been paused for "
                            + currentRunningTime + " seconds by: " + getConnectionPauseNames(true));
                }

                //Cancel the login process if it has been running for more than 20 seconds.
                if (currentRunningTime >= 20) {
                    cancelLoginProcess("Login Process Handler Timeout");
                    MinecraftServer.log.warning("[Poseidon] Login for " + packet1Login.name
                            + " timed out after 20 seconds; remaining pauses: " + getConnectionPauseNames(true));
                }

                if (currentRunningTime < 60) {
                    runLoginTimer(connectionStartTime);
                }
            }
        }, 20 * 5);
    }

    private void processAuthentication() {
        PlayerConnectionInitializationEvent event = new PlayerConnectionInitializationEvent(this.packet1Login.name, this.netLoginHandler.getSocket().getInetAddress(), loginProcessHandler);
        this.server.getPluginManager().callEvent(event);
        if (loginCancelled) {
            return;
        }

        // Account for cracked allowlist
        if (onlineMode && !CrackedAllowlist.get().contains(this.packet1Login.name)) {
            // The encryption/key exchange proves protocol possession, not account
            // ownership. Online-mode users must still pass Mojang hasJoined.
            verifyMojangSession();
        } else {
            // Server is not running online mode or user is allowlisted as cracked
            getUserUUID();
        }
    }

    private void getUserUUID() {
        //UUID uuid = UUIDManager.getInstance().getUUIDFromUsername(packet1Login.name, true);
        long unixTime = (System.currentTimeMillis() / 1000L);
        // If the server is in offline mode, do not contact Mojang at all – immediately use an offline UUID
        if (!this.onlineMode) {
            UUID offlineUUID = UUIDManager.generateOfflineUUID(packet1Login.name);
            this.userUUIDReceived(offlineUUID, false);
            return;
        }

        UUID uuid = UUIDManager.getInstance().getUUIDFromUsername(packet1Login.name, true, unixTime);
        if (uuid == null) {
            boolean useGetMethod = PoseidonConfig.getInstance().getString("settings.uuid-fetcher.method.value", "POST").equalsIgnoreCase("GET");
            (new ThreadUUIDFetcher(packet1Login, this, useGetMethod)).start();
        } else {
            MinecraftServer.log.fine("[Poseidon] Fetched cached UUID for " + packet1Login.name);
            connectPlayer(uuid);
        }


    }

    public synchronized void userUUIDReceived(UUID uuid, boolean onlineMode) {
        if (!onlineMode) {
            if (Boolean.valueOf(String.valueOf(PoseidonConfig.getInstance().getConfigOption("settings.check-username-validity.enabled", true))) && !isUsernameValid()) {
                //Username is invalid, and is a cracked user
                return;
            }
        }


        long unixTime = (System.currentTimeMillis() / 1000L) + 1382400;
        UUIDManager.getInstance().receivedUUID(packet1Login.name, uuid, unixTime, onlineMode);
        connectPlayer(uuid);

    }

    //This function is only run if the user is cracked
    public boolean isUsernameValid() {
        String username = this.packet1Login.name;
        if (username.isEmpty()) {
            cancelLoginProcess("Sorry, you don't have a username, messing with MC?????");
            return false;
        }
        String regex = String.valueOf(PoseidonConfig.getInstance().getConfigOption("settings.check-username-validity.regex", "[a-zA-Z0-9_?]*"));
        int minimumLength = Integer.valueOf(String.valueOf(PoseidonConfig.getInstance().getConfigOption("settings.check-username-validity.min-length", 3)));
        int maximumLength = Integer.valueOf(String.valueOf(PoseidonConfig.getInstance().getConfigOption("settings.check-username-validity.max-length", 16)));

        if (username.length() > maximumLength) {
            cancelLoginProcess("Sorry, your username is too long. The maximum length is: " + maximumLength);
            return false;
        }
        if (username.length() < minimumLength) {
            cancelLoginProcess("Sorry, your username is too short. The minimum length is: " + minimumLength);
            return false;
        }
        if (!username.matches(regex)) {
            cancelLoginProcess("Sorry, your username is invalid, allowed characters: " + regex);
            return false;
        }

        return true;
    }


    private void verifyMojangSession() {
        if (!loginSuccessful & !loginCancelled) {
            ThreadLoginVerifier verifier = new ThreadLoginVerifier(this, netLoginHandler, this.packet1Login, this.server);
            Future<?> submitted = ThreadLoginVerifier.submit(verifier);
            if (submitted == null) {
                cancelLoginProcess("Authentication service is busy, please try again.");
                return;
            }
            this.authenticationTask = submitted;
            if (this.loginCancelled || this.netLoginHandler.c) {
                submitted.cancel(true);
            }
        }
    }

    public boolean isLoginActive() {
        return !this.loginSuccessful && !this.loginCancelled && this.netLoginHandler != null && !this.netLoginHandler.c;
    }

    public void cancelAuthenticationTask() {
        Future<?> task = this.authenticationTask;
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
    }

    public synchronized void userMojangSessionVerified() {
        if (!loginSuccessful & !loginCancelled) {
            getUserUUID();
        }
    }

    private void connectPlayer(UUID uuid) {
        String username = packet1Login.name;
        //Check if a player with the same UUID or Username is already online which is mainly an issue in Offline Mode servers.
        for (Player p : server.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(username) || p.getUniqueId().equals(uuid)) {
                cancelLoginProcess(this.msgKickAlreadyOnline);
                System.out.println("[Poseidon] User " + username + " has been blocked from connecting as they share a username or UUID with a user who is already online called " + p.getName() + "\nMost likely the user has changed their UUID or the server is running in offline mode and someone has attempted to connect with their name");
            }
        }


        if (!loginSuccessful && !loginCancelled) {

            //Bukkit Login Event Start
            if (this.netLoginHandler.getSocket() == null) {
                return;
            }


            PlayerPreLoginEvent event = new PlayerPreLoginEvent(this.packet1Login.name, ((InetSocketAddress) netLoginHandler.networkManager.getSocketAddress()).getAddress(), loginProcessHandler);
            this.server.getPluginManager().callEvent(event);
            if (event.getResult() != PlayerPreLoginEvent.Result.ALLOWED) {
                cancelLoginProcess(event.getKickMessage());
                return;
            }
            //Bukkit Login Event End
            this.ensureStartupWarmupPause();
            if (isPlayerConnectionPaused()) {
                startTime = System.currentTimeMillis() / 1000L;
            } else {
                loginSuccessful = true;
                NetLoginHandler.a(netLoginHandler, packet1Login);
            }
        }
    }

    /**
     * Cancel a players login before join or login events
     */
    public void cancelLoginProcess(String s) {
        if (!loginCancelled && !loginSuccessful) {
            loginCancelled = true;
            cancelAuthenticationTask();
            netLoginHandler.disconnect(s);
        }
    }

    /**
     * Set a pause for your plugin
     * Connection pauses are for fetching data for a player before they MIGHT be allowed to join
     *
     * @param plugin              Instance of plugin
     * @param connectionPauseName Name of connection pause (Ensure no duplicates)
     * @return ConnectionPause Object, used to remove a connection pause
     */
    public ConnectionPause addConnectionInterrupt(Plugin plugin, String connectionPauseName) {
        final ConnectionPause connectionPause = new ConnectionPause(plugin.getDescription().getName(), connectionPauseName, loginProcessHandler);
        connectionPauses.add(connectionPause);
        return connectionPause;
    }

    @Deprecated
    public void removeConnectionPause(ConnectionPause connectionPause) {
        removeConnectionInterrupt(connectionPause);
    }

    /**
     * Remove a connection pause
     *
     * @param connectionPause ConnectionPause object
     */
    public void removeConnectionInterrupt(ConnectionPause connectionPause) {
        //Check if the connection pause is registered and active
        if (!connectionPauses.contains(connectionPause)) {
            System.out.println("[Poseidon] A plugin has tried to remove a connection pause from the player " + packet1Login.name + " called " + connectionPause.getConnectionPauseName() + " from the plugin " + connectionPause.getPluginName() + ". Please contact the plugin author and get them to check their logic as this is a duplicate remove, or a pause for another player.");
            return;
        }
        //Handle the completion of the pause
        connectionPause.setActive(false);
        //If there are no more pauses, connect the player
        if (!isPlayerConnectionPaused()) {
            long endTime = System.currentTimeMillis() / 1000L;
            int timeTaken = (int) (endTime - startTime);

            //If a pause has cancelled the login, don't connect the player
            if (loginCancelled) {
                System.out.println("[Poseidon] Player " + loginProcessHandler.packet1Login.name + " was not allowed to join after being on hold for " + timeTaken + " seconds by the following plugins: " + getConnectionPauseNames(false));
                return;
            }

            this.setLoginSuccessful(true);
            System.out.println("[Poseidon] Player " + loginProcessHandler.packet1Login.name + " has been allowed to join after being on hold for " + timeTaken + " seconds by the following plugins: " + getConnectionPauseNames(false));
            NetLoginHandler.a(netLoginHandler, packet1Login);
        }
    }


    public ConnectionPause[] getActiveConnectionPauses() {
        HashSet<ConnectionPause> activePauses = new HashSet<>();
        for (ConnectionPause connectionPause : connectionPauses) {
            if (connectionPause.isActive()) {
                activePauses.add(connectionPause);
            }
        }
        return activePauses.toArray(new ConnectionPause[activePauses.size()]);
    }

    public String getConnectionPauseNames(boolean activeOnly) {

        StringBuilder pauseNames = new StringBuilder();
        for (ConnectionPause connectionPause : connectionPauses) {
            String pluginName = connectionPause.getPluginName();
            String pauseName = connectionPause.getConnectionPauseName();
            boolean isActive = connectionPause.isActive();
            int time = connectionPause.getRunningTime();
            if (activeOnly) {
                if (connectionPause.isActive()) {
                    pauseNames.append(pluginName).append(":").append(pauseName).append(":").append(isActive ? "Running" : "Complete").append(":").append(time).append("-Seconds, ");
                }
            } else {
                pauseNames.append(pluginName).append(":").append(pauseName).append(":").append(isActive ? "Running" : "Complete").append(":").append(time).append("-Seconds, ");
            }
        }
        return pauseNames.toString();
    }


    private ConnectionPause legacyConnectionPause;

    /**
     * Set a pause for your plugin
     * Connection pauses are for fetching data for a player before they MIGHT be allowed to join
     */
    @Deprecated
    public void addConnectionPause(Plugin plugin) throws Exception {
        System.out.println("[Poseidon] " + plugin.getDescription().getName() + " is using the deprecated connection pause system which will be removed in the future. Contact the plugin author to get an updated version.");
        legacyConnectionPause = addConnectionInterrupt(plugin, "Legacy-Connection-Pause");
    }

    /**
     * Remove a pause for your plugin
     */
    @Deprecated
    public void removeConnectionPause(Plugin plugin) {
        if (legacyConnectionPause != null) {
            removeConnectionInterrupt(legacyConnectionPause);
            return;
        }
        System.out.println("[Poseidon] " + plugin.getDescription().getName() + " Attempted to remove a legacy (deprecated) connection pause that was never added. Please contact the plugin author and get them to check their logic and update to the new connection pause system.");
    }

    /**
     * See if the players connection currently paused
     */
    public boolean isPlayerConnectionPaused() {
        return getActiveConnectionPauses().length > 0;
    }


    private void setLoginSuccessful(boolean loginSuccessful) {
        this.loginSuccessful = loginSuccessful;
    }

    public void setUsingModernAuth(boolean modernAuth) {
        this.usingModernAuth = modernAuth;
    }

    public boolean isUsingModernAuth() {
        return this.usingModernAuth;
    }

    private void ensureStartupWarmupPause() {
        if (!PoseidonConfig.getInstance().getConfigBoolean("settings.startup-readiness.enabled", true)) {
            return;
        }
        if (!PoseidonConfig.getInstance().getConfigBoolean("settings.startup-readiness.hold-login-enabled", true)) {
            return;
        }

        if (this.netLoginHandler == null || this.netLoginHandler.getMinecraftServer() == null) {
            return;
        }

        if (this.netLoginHandler.getMinecraftServer().isStartupReady()) {
            return;
        }

        if (this.startupWarmupPause == null || !this.startupWarmupPause.isActive()) {
            this.startupWarmupPause = new ConnectionPause("Server", "ServerWarmup", this);
            this.connectionPauses.add(this.startupWarmupPause);
            this.startupWarmupHoldStartMs = System.currentTimeMillis();
            this.scheduleStartupWarmupCheck();
        }
    }

    private void scheduleStartupWarmupCheck() {
        if (this.startupWarmupCheckScheduled || this.loginSuccessful || this.loginCancelled) {
            return;
        }

        this.startupWarmupCheckScheduled = true;
        Bukkit.getScheduler().scheduleAsyncDelayedTask(new PoseidonPlugin(), () -> {
            boolean shouldReschedule = false;
            try {
                if (this.loginSuccessful || this.loginCancelled) {
                    return;
                }

                if (this.startupWarmupPause == null || !this.startupWarmupPause.isActive()) {
                    return;
                }

                if (this.netLoginHandler == null || this.netLoginHandler.getMinecraftServer() == null) {
                    return;
                }

                if (this.netLoginHandler.getMinecraftServer().isStartupReady()) {
                    this.removeConnectionInterrupt(this.startupWarmupPause);
                    return;
                }

                long heldForSeconds = (System.currentTimeMillis() - this.startupWarmupHoldStartMs) / 1000L;
                int maxHoldSeconds = getConfigInt("settings.startup-readiness.max-hold-seconds", 60);
                if (heldForSeconds >= (long) maxHoldSeconds) {
                    this.cancelLoginProcess("Server warmup still in progress, please rejoin in a moment.");
                    return;
                }

                shouldReschedule = true;
            } finally {
                this.startupWarmupCheckScheduled = false;
                if (shouldReschedule) {
                    this.scheduleStartupWarmupCheck();
                }
            }
        }, 20L);
    }

    private int getConfigInt(String key, int defaultValue) {
        try {
            Object value = PoseidonConfig.getInstance().getConfigOption(key, Integer.valueOf(defaultValue));
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }
}
