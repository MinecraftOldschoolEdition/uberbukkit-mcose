package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NetworkListenThread {

    public static Logger a = Logger.getLogger("Minecraft");
    private ServerSocket d;
    private Thread e;
    public volatile boolean b = false;
    private int f = 0;
    private final Queue pendingAcceptedLogins = new ConcurrentLinkedQueue();
    private final int maxLoginCompletionsPerTick;
    private final int maxPendingLogins;
    private final AtomicInteger pendingLoginReservations = new AtomicInteger();
    private int loginCompletionTick = Integer.MIN_VALUE;
    private int loginCompletionsThisTick = 0;
    private final ArrayList g = new ArrayList();
    private final ArrayList h = new ArrayList();
    public MinecraftServer c;

    public NetworkListenThread(MinecraftServer minecraftserver, InetAddress inetaddress, int i) throws IOException {
        this.c = minecraftserver;
        // Create unbound socket first so we can set options before binding
        this.d = new ServerSocket();
        // Allow immediate rebind after server restart (avoids "Address already in use")
        this.d.setReuseAddress(true);
        this.d.setPerformancePreferences(0, 2, 1);
        // Now bind to the address and port
        this.d.bind(new InetSocketAddress(inetaddress, i), 128);
        this.b = true;
        int configuredMaxJoinsPerTick = PoseidonConfig.getInstance().getInt("settings.max-joins-per-tick", 5);
        this.maxLoginCompletionsPerTick = configuredMaxJoinsPerTick <= 0 ? Integer.MAX_VALUE : configuredMaxJoinsPerTick;
        int configuredMaxPendingLogins = PoseidonConfig.getInstance().getInt("settings.max-pending-logins", 128);
        this.maxPendingLogins = configuredMaxPendingLogins <= 0 ? Integer.MAX_VALUE : configuredMaxPendingLogins;
        this.e = new NetworkAcceptThread(this, "Listen thread", minecraftserver);
        this.e.start();
    }

    public void a(NetServerHandler netserverhandler) {
        this.h.add(netserverhandler);
    }

    private void a(NetLoginHandler netloginhandler) {
        if (netloginhandler == null) {
            throw new IllegalArgumentException("Got null pendingconnection!");
        } else {
            this.pendingAcceptedLogins.add(netloginhandler);
        }
    }

    public void a() {
        this.drainAcceptedLogins();
        int i;
        if (this.loginCompletionTick != MinecraftServer.currentTick) {
            this.loginCompletionTick = MinecraftServer.currentTick;
            this.loginCompletionsThisTick = 0;
        }

        for (i = 0; i < this.g.size(); ++i) {
            NetLoginHandler netloginhandler = (NetLoginHandler) this.g.get(i);

            try {
                boolean allowLoginCompletion = !netloginhandler.hasPendingLoginCompletion()
                        || this.loginCompletionsThisTick < this.maxLoginCompletionsPerTick;
                if (netloginhandler.a(allowLoginCompletion)) {
                    ++this.loginCompletionsThisTick;
                }
            } catch (Exception exception) {
                if (netloginhandler == null) {
                    a.log(Level.WARNING, "Looks like someone tried to crash the server, stopped their attempt.");
                    this.g.remove(i);
                    return;
                } else {
                    netloginhandler.disconnect("Internal server error");
                    a.log(Level.WARNING, "Failed to handle packet: " + exception, exception);
                }
            }

            if (netloginhandler.c) {
                this.g.remove(i--);
                this.releasePendingLogin();
            }

            netloginhandler.networkManager.a();
        }

        for (i = 0; i < this.h.size(); ++i) {
            NetServerHandler netserverhandler = (NetServerHandler) this.h.get(i);

            try {
                netserverhandler.a();
            } catch (Exception exception1) {
                a.log(Level.WARNING, "Failed to handle packet: " + exception1, exception1);
                netserverhandler.disconnect("Internal server error");
            }

            if (netserverhandler.disconnected) {
                this.h.remove(i--);
            }

            netserverhandler.networkManager.a();
        }
    }

    public int getPendingLoginCount() {
        return this.pendingLoginReservations.get();
    }

    boolean tryReservePendingLogin() {
        while (true) {
            int current = this.pendingLoginReservations.get();
            if (current >= this.maxPendingLogins) {
                return false;
            }
            if (this.pendingLoginReservations.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    void releasePendingLogin() {
        while (true) {
            int current = this.pendingLoginReservations.get();
            if (current <= 0) {
                return;
            }
            if (this.pendingLoginReservations.compareAndSet(current, current - 1)) {
                return;
            }
        }
    }

    public int getActiveHandlerCount() {
        return this.h.size();
    }

    static ServerSocket a(NetworkListenThread networklistenthread) {
        return networklistenthread.d;
    }

    static int b(NetworkListenThread networklistenthread) {
        return networklistenthread.f++;
    }

    static void a(NetworkListenThread networklistenthread, NetLoginHandler netloginhandler) {
        networklistenthread.a(netloginhandler);
    }

    private void drainAcceptedLogins() {
        NetLoginHandler netloginhandler;
        while ((netloginhandler = (NetLoginHandler) this.pendingAcceptedLogins.poll()) != null) {
            this.g.add(netloginhandler);
        }
    }
    
    /**
     * Closes the server socket and stops accepting new connections.
     * This should be called during server shutdown to release the port.
     */
    public void closeSocket() {
        this.b = false; // Stop accepting new connections
        try {
            if (this.d != null && !this.d.isClosed()) {
                this.d.close();
                a.info("Server socket closed");
            }
        } catch (IOException e) {
            a.log(Level.WARNING, "Error closing server socket", e);
        }
    }
}
