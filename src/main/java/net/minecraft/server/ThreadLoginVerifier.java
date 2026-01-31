package net.minecraft.server;

import com.projectposeidon.johnymuffin.LoginProcessHandler;
import com.legacyminecraft.poseidon.util.SessionAPI;
import org.bukkit.craftbukkit.CraftServer;

import java.net.InetSocketAddress;

// CraftBukkit start
// CraftBukkit end

public class ThreadLoginVerifier extends Thread {

    final Packet1Login loginPacket;

    final NetLoginHandler netLoginHandler;

    final LoginProcessHandler loginProcessHandler;  //Project Poseidon

    // CraftBukkit start
    CraftServer server;

    public ThreadLoginVerifier(LoginProcessHandler loginProcessHandler, NetLoginHandler netloginhandler, Packet1Login packet1login, CraftServer server) {
        this.server = server;
        // CraftBukkit end
        this.loginProcessHandler = loginProcessHandler;  //Project Poseidon

        this.netLoginHandler = netloginhandler;
        this.loginPacket = packet1login;
    }

    private String getIP() {
        return ((InetSocketAddress) netLoginHandler.networkManager.getSocketAddress()).getAddress().getHostAddress();
    }

    public void run() {
        try {
            String serverId = netLoginHandler.getServerID();
            String playerName = loginPacket.name;
            String clientIP = getIP();

            SessionAPI.hasJoined(playerName, serverId, clientIP, (int responseCode, String username, String uuid, String ip) -> {
                // Check if client is connecting from localhost (skip IP verification for local connections)
                boolean isLocalhost = "127.0.0.1".equals(clientIP) || "localhost".equals(clientIP);

                // make sure the request didn't fail (-1), and the response wasn't empty (204)
                if (responseCode != -1 && responseCode != 204) {
                    // make sure username matches (docs say username is case insensitive https://wiki.vg/Protocol_Encryption#Server)
                    if (username.equalsIgnoreCase(playerName)) {
                        // For non-localhost, verify IP if Mojang returned one
                        if (!isLocalhost && ip != null && !"noip".equals(ip) && !ip.isEmpty()) {
                            if (ip.equals(clientIP)) {
                                loginProcessHandler.userMojangSessionVerified();
                            } else {
                                loginProcessHandler.cancelLoginProcess("Failed to verify username! (IP mismatch)");
                            }
                        } else {
                            // Localhost or no IP in response - just verify
                            loginProcessHandler.userMojangSessionVerified();
                        }
                    } else {
                        loginProcessHandler.cancelLoginProcess("Failed to verify username!");
                    }
                } else {
                    // If Mojang returns 204, optionally try legacy endpoint as a fallback
                    if (responseCode == 204) {
                        if (SessionAPI.hasJoined(playerName, serverId)) {
                            loginProcessHandler.userMojangSessionVerified();
                        } else {
                            loginProcessHandler.cancelLoginProcess("Failed to verify username!");
                        }
                    } else {
                        loginProcessHandler.cancelLoginProcess("Failed to verify username!");
                    }
                }
            });
        } catch (Exception exception) {
            this.loginProcessHandler.cancelLoginProcess("Failed to verify username! [internal error " + exception + "]");
            exception.printStackTrace();
        }
    }
}
