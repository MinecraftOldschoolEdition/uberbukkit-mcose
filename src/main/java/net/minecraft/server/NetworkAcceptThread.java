package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

class NetworkAcceptThread extends Thread {

    private static final int THROTTLE_CLEANUP_INTERVAL = 256;
    private static final int MAX_THROTTLE_ADDRESSES = 65536;

    final MinecraftServer a;

    final NetworkListenThread b;
    
    // Connection rate limiting - configurable to allow clients that ping then connect.
    // A burst allowance preserves ping-then-connect and small shared-NAT groups.
    private final long connectionThrottleMs;
    private final int connectionThrottleBurst;

    NetworkAcceptThread(NetworkListenThread networklistenthread, String s, MinecraftServer minecraftserver) {
        super(s);
        this.b = networklistenthread;
        this.a = minecraftserver;
        this.connectionThrottleMs = (long) PoseidonConfig.getInstance().getInt("settings.connection-throttle-ms.value", 4000);
        // Allow a small burst within the throttle window so multiple players behind one NAT
        // can still join together without tripping the limiter.
        this.connectionThrottleBurst = Math.max(1, PoseidonConfig.getInstance().getInt("settings.connection-throttle-ms.burst", 8));
    }

    public void run() {
        Map<InetAddress, ThrottleWindow> throttleByAddress = new LinkedHashMap<InetAddress, ThrottleWindow>(256, 0.75F, true);
        int connectionsSinceThrottleCleanup = 0;

        while (this.b.b) {
            try {
                Socket socket = NetworkListenThread.a(this.b).accept();

                if (socket != null) {
                    InetAddress inetaddress = socket.getInetAddress();
                    long now = System.currentTimeMillis();
                    String hostAddress = inetaddress.getHostAddress();
                    boolean localhost = "127.0.0.1".equals(hostAddress) || "::1".equals(hostAddress) || "0:0:0:0:0:0:0:1".equals(hostAddress);
                    boolean throttleTriggered = false;

                    // Rate limit connections per IP (except localhost)
                    // This prevents connection spam but allows normal client behavior
                    // (clients ping the server list, then connect shortly after)
                    if (this.connectionThrottleMs > 0L && !localhost) {
                        if (++connectionsSinceThrottleCleanup >= THROTTLE_CLEANUP_INTERVAL) {
                            removeExpiredThrottleEntries(throttleByAddress, now, this.connectionThrottleMs);
                            connectionsSinceThrottleCleanup = 0;
                        }

                        ThrottleWindow throttleWindow = throttleByAddress.get(inetaddress);
                        if (throttleWindow == null || now - throttleWindow.windowStartMs >= this.connectionThrottleMs) {
                            if (throttleWindow == null && throttleByAddress.size() >= MAX_THROTTLE_ADDRESSES) {
                                removeEldestThrottleEntry(throttleByAddress);
                            }
                            throttleWindow = new ThrottleWindow(now);
                            throttleByAddress.put(inetaddress, throttleWindow);
                        } else {
                            ++throttleWindow.attempts;
                        }

                        if (throttleWindow.attempts > this.connectionThrottleBurst) {
                            throttleTriggered = true;
                        }
                    }

                    if (throttleTriggered) {
                        socket.close();
                    } else if (!this.b.tryReservePendingLogin()) {
                        socket.close();
                    } else {
                        boolean handedOff = false;
                        try {
                            try { socket.setTcpNoDelay(true); } catch (IOException ignored) {}
                            try { socket.setSoTimeout(30000); } catch (IOException ignored) {}
                            try { socket.setKeepAlive(false); } catch (IOException ignored) {}
                            NetLoginHandler netloginhandler = new NetLoginHandler(this.a, socket, "Connection #" + NetworkListenThread.b(this.b));
                            NetworkListenThread.a(this.b, netloginhandler);
                            handedOff = true;
                        } finally {
                            if (!handedOff) {
                                this.b.releasePendingLogin();
                                try {
                                    socket.close();
                                } catch (IOException ignored) {
                                }
                            }
                        }
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

    private static void removeExpiredThrottleEntries(Map<InetAddress, ThrottleWindow> throttleByAddress, long now, long throttleMs) {
        Iterator<Map.Entry<InetAddress, ThrottleWindow>> iterator = throttleByAddress.entrySet().iterator();
        while (iterator.hasNext()) {
            ThrottleWindow window = iterator.next().getValue();
            long age = now - window.windowStartMs;
            if (age < 0L || age >= throttleMs) {
                iterator.remove();
            }
        }
    }

    private static void removeEldestThrottleEntry(Map<InetAddress, ThrottleWindow> throttleByAddress) {
        Iterator<Map.Entry<InetAddress, ThrottleWindow>> iterator = throttleByAddress.entrySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static final class ThrottleWindow {
        final long windowStartMs;
        int attempts;

        ThrottleWindow(long windowStartMs) {
            this.windowStartMs = windowStartMs;
            this.attempts = 1;
        }
    }
}
