package com.legacyminecraft.poseidon.uuid;

import com.legacyminecraft.poseidon.PoseidonConfig;
import com.legacyminecraft.poseidon.util.GetUUIDFetcher;
import com.legacyminecraft.poseidon.util.UUIDResult;
import com.projectposeidon.johnymuffin.LoginProcessHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Packet1Login;
import org.bukkit.ChatColor;

import java.util.UUID;

import static com.legacyminecraft.poseidon.util.UUIDFetcher.getUUIDOf;
import static com.projectposeidon.johnymuffin.UUIDManager.generateOfflineUUID;

public class ThreadUUIDFetcher extends Thread {

    final Packet1Login loginPacket;
    //    final NetLoginHandler netLoginHandler;
    final LoginProcessHandler loginProcessHandler;
    final boolean useGetMethod;
    private static volatile boolean warnedPostFailure = false; // reduce startup spam

    public ThreadUUIDFetcher(Packet1Login packet1Login, LoginProcessHandler loginProcessHandler, boolean useGetMethod) {
//        this.netLoginHandler = netloginhandler; // The login handler
        this.loginProcessHandler = loginProcessHandler;
        this.loginPacket = packet1Login; // The login packet
        this.useGetMethod = useGetMethod;

    }

    public void run() {
        if (useGetMethod) {
            getMethod();
        } else {
            postMethod();
        }

    }

    public void getMethod() {
        UUIDResult uuidResult;
        GetUUIDFetcher.UUIDAndUsernameResult uuidAndUsernameResult = GetUUIDFetcher.getUUID(loginPacket.name);
        uuidResult = uuidAndUsernameResult.getUuidResult();

        if (uuidResult.getReturnType().equals(UUIDResult.ReturnType.ONLINE) && uuidAndUsernameResult.getReturnedUsername().equals(loginPacket.name)) {
            MinecraftServer.log.fine("[Poseidon] Fetched Mojang UUID for " + loginPacket.name + " using GET");
            loginProcessHandler.userUUIDReceived(uuidResult.getUuid(), true);
            return;
        } else if (uuidResult.getReturnType().equals(UUIDResult.ReturnType.ONLINE)) {
            if (PoseidonConfig.getInstance().getConfigBoolean("settings.uuid-fetcher.get.enforce-case-sensitivity.enabled")) {
                MinecraftServer.log.warning("[Poseidon] Rejected " + loginPacket.name + " because Mojang returned username "
                        + uuidAndUsernameResult.getReturnedUsername() + " with different casing");
                loginProcessHandler.cancelLoginProcess(ChatColor.RED + "Sorry, that username has invalid casing");
                return;
            } else {
                MinecraftServer.log.fine("[Poseidon] Fetched Mojang UUID for " + loginPacket.name + " using GET");
                loginProcessHandler.userUUIDReceived(uuidResult.getUuid(), true);
                return;
            }
        } else if (uuidResult.getReturnType().equals(UUIDResult.ReturnType.OFFLINE)) {
            if ((boolean) PoseidonConfig.getInstance().getProperty("settings.uuid-fetcher.allow-graceful-uuids.value")) {
                UUID offlineUUID = uuidResult.getUuid();
                loginProcessHandler.userUUIDReceived(offlineUUID, false);
                MinecraftServer.log.fine("[Poseidon] Using an offline UUID for " + loginPacket.name);
            } else {
                MinecraftServer.log.warning("[Poseidon] Rejected " + loginPacket.name + " because no Mojang UUID exists and graceful UUIDs are disabled");
                loginProcessHandler.cancelLoginProcess(ChatColor.RED + "Sorry, we only support premium accounts");
            }
            return;
        }
        String reason = uuidResult.getException() == null ? "unknown error" : uuidResult.getException().getMessage();
        MinecraftServer.log.warning("[Poseidon] Mojang UUID lookup failed for " + loginPacket.name + ": " + reason);
        loginProcessHandler.cancelLoginProcess(ChatColor.RED + "Sorry, we can't connect to Mojang currently, please try again later");

    }


    public void postMethod() {
        UUID uuid;
        try {
            uuid = getUUIDOf(loginPacket.name);
            if (uuid == null) {
                if (PoseidonConfig.getInstance().getConfigBoolean("settings.uuid-fetcher.allow-graceful-uuids.value", true)) {
                    UUID offlineUUID = generateOfflineUUID(loginPacket.name);
                    loginProcessHandler.userUUIDReceived(offlineUUID, false);
                    MinecraftServer.log.fine("[Poseidon] Using an offline UUID for " + loginPacket.name);
                } else {
                    MinecraftServer.log.warning("[Poseidon] Rejected " + loginPacket.name + " because no Mojang UUID exists and graceful UUIDs are disabled");
                    loginProcessHandler.cancelLoginProcess(ChatColor.RED + "Sorry, we only support premium accounts");
                }
            } else {
                MinecraftServer.log.fine("[Poseidon] Fetched Mojang UUID for " + loginPacket.name + " using POST");
                loginProcessHandler.userUUIDReceived(uuid, true);
            }
        } catch (Exception e) {
            // First try a seamless fallback to GET to avoid disconnecting players on transient POST failures
            if (!warnedPostFailure) {
                warnedPostFailure = true;
                MinecraftServer.log.warning("[Poseidon] POST UUID lookup failed; falling back to GET"
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            }
            // Attempt GET method path
            try {
                getMethod();
                return; // handled by getMethod (either success or it cancels with a message)
            } catch (Throwable ignore) {
                // If GET path threw unexpectedly, fall through to original behavior
            }

            // As a last resort, keep original message and cancel
            MinecraftServer.log.warning("[Poseidon] Mojang UUID lookup failed for " + loginPacket.name
                    + "; set settings.uuid-fetcher.method.value to GET if POST failures persist");
            loginProcessHandler.cancelLoginProcess(ChatColor.RED + "Sorry, we can't connect to Mojang currently, please try again later");
        }

    }


}

