package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;

class NetworkAcceptThread extends Thread {

    final MinecraftServer a;

    final NetworkListenThread b;
    
    // Connection rate limiting - configurable to allow clients that ping then connect
    // Default 1000ms (1 second) - enough to prevent spam but allows poll+connect flow
    private final long connectionThrottleMs;

    NetworkAcceptThread(NetworkListenThread networklistenthread, String s, MinecraftServer minecraftserver) {
        super(s);
        this.b = networklistenthread;
        this.a = minecraftserver;
        // Load from config, default to 1000ms (was hardcoded 5000ms which broke clients)
        this.connectionThrottleMs = (long) PoseidonConfig.getInstance().getInt("settings.connection-throttle-ms.value", 1000);
    }

    public void run() {
        HashMap hashmap = new HashMap();

        while (this.b.b) {
            try {
                Socket socket = NetworkListenThread.a(this.b).accept();

                if (socket != null) {
                    InetAddress inetaddress = socket.getInetAddress();

                    // Rate limit connections per IP (except localhost)
                    // This prevents connection spam but allows normal client behavior
                    // (clients ping the server list, then connect shortly after)
                    if (connectionThrottleMs > 0 && hashmap.containsKey(inetaddress) && !"127.0.0.1".equals(inetaddress.getHostAddress()) && System.currentTimeMillis() - ((Long) hashmap.get(inetaddress)).longValue() < connectionThrottleMs) {
                        hashmap.put(inetaddress, Long.valueOf(System.currentTimeMillis()));
                        socket.close();
                    } else {
                        hashmap.put(inetaddress, Long.valueOf(System.currentTimeMillis()));
                        NetLoginHandler netloginhandler = new NetLoginHandler(this.a, socket, "Connection #" + NetworkListenThread.b(this.b));

                        NetworkListenThread.a(this.b, netloginhandler);
                    }
                }
            } catch (IOException ioexception) {
                // Only log if we're still supposed to be running (not shutdown)
                if (this.b.b) {
                    ioexception.printStackTrace();
                }
                // If b.b is false, socket was closed for shutdown - exit gracefully
            }
        }
    }
}
