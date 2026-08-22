package net.minecraft.server.serverdirectory;

import com.legacyminecraft.poseidon.PoseidonPlugin;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.NetServerHandler;
import net.minecraft.server.Packet250CustomPayload;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Coordinates permission checks, bounded website I/O, and main-thread client responses. */
public final class ServerDirectoryManager {
    public static final String PERMISSION = "mcose.serverbrowser.advertise";
    private static final ServerDirectoryManager INSTANCE = new ServerDirectoryManager();
    private static final PoseidonPlugin CORE_PLUGIN = new PoseidonPlugin();

    private final ServerDirectoryWebsiteClient website;
    private final ThreadPoolExecutor workers;

    public static ServerDirectoryManager getInstance() {
        return INSTANCE;
    }

    ServerDirectoryManager() {
        this(new ServerDirectoryWebsiteClient(), new ThreadPoolExecutor(
                1,
                2,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(32),
                new DirectoryThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()));
    }

    ServerDirectoryManager(ServerDirectoryWebsiteClient website, ThreadPoolExecutor workers) {
        this.website = website;
        this.workers = workers;
        this.workers.allowCoreThreadTimeOut(true);
    }

    public void handleCapabilityRequest(final NetServerHandler handler, byte[] payload) {
        try {
            ServerDirectoryProtocol.validateRequestPayload(payload);
        } catch (IOException invalid) {
            sendResult(handler, ServerDirectoryProtocol.RESULT_VALIDATION, "Invalid server-directory capability request.");
            return;
        }
        if (handler == null || handler.player == null || !handler.supportsServerDirectory()) return;
        final EntityPlayer player = handler.player;
        final boolean permitted = hasPermission(player);
        final ServerDirectoryConfig config = ServerDirectoryConfig.load();
        if (!permitted || !config.isReady()) {
            sendState(handler, stateFor(player, permitted, config, false,
                    permitted ? config.configurationError : "You do not have permission to advertise this server.", null));
            return;
        }
        sendState(handler, stateFor(player, true, config, true, "Loading the current listing...", null));
        submit(handler, new Runnable() {
            public void run() {
                try {
                    final ServerDirectoryProtocol.Listing listing = website.getListing(config);
                    dispatch(new Runnable() {
                        public void run() {
                            sendState(handler, stateFor(player, hasPermission(player), config, false, "", listing));
                        }
                    });
                } catch (final ServerDirectoryWebsiteClient.WebsiteException rejected) {
                    dispatch(new Runnable() {
                        public void run() {
                            sendResult(handler, resultForWebsiteStatus(rejected.status), rejected.getMessage());
                            sendState(handler, stateFor(player, hasPermission(player), config, false, rejected.getMessage(), rejected.listing));
                        }
                    });
                } catch (final IOException unavailable) {
                    dispatch(new Runnable() {
                        public void run() {
                            sendResult(handler, ServerDirectoryProtocol.RESULT_WEBSITE, "The directory website is unavailable or returned an invalid response.");
                            sendState(handler, stateFor(player, hasPermission(player), config, false, "Directory website unavailable.", null));
                        }
                    });
                }
            }
        });
    }

    public void handleMutation(final NetServerHandler handler, byte[] payload) {
        if (handler == null || handler.player == null || !handler.supportsServerDirectory()) return;
        final EntityPlayer player = handler.player;
        if (!hasPermission(player)) {
            sendResult(handler, ServerDirectoryProtocol.RESULT_AUTHORIZATION, "You do not have permission to advertise this server.");
            return;
        }
        final ServerDirectoryProtocol.Mutation mutation;
        try {
            mutation = ServerDirectoryProtocol.readMutationPayload(payload);
        } catch (IOException invalid) {
            sendResult(handler, ServerDirectoryProtocol.RESULT_VALIDATION, invalid.getMessage());
            return;
        }
        final ServerDirectoryConfig config = ServerDirectoryConfig.load();
        if (!config.isReady()) {
            sendResult(handler, ServerDirectoryProtocol.RESULT_CONFIGURATION, config.configurationError);
            sendState(handler, stateFor(player, true, config, false, config.configurationError, null));
            return;
        }
        final String creatorUsername = player.name == null ? "" : player.name;
        final String creatorUuid = player.playerUUID == null ? null : player.playerUUID.toString();
        submit(handler, new Runnable() {
            public void run() {
                try {
                    final ServerDirectoryProtocol.Listing listing;
                    final String success;
                    if (mutation.operation == ServerDirectoryProtocol.OP_DELETE) {
                        website.deleteListing(config, mutation.expectedRevision);
                        listing = null;
                        success = "The server listing was removed.";
                    } else {
                        listing = website.putListing(config, mutation, creatorUsername, creatorUuid);
                        success = mutation.expectedRevision > 0 ? "The server listing was updated." : "The server listing was published.";
                    }
                    dispatch(new Runnable() {
                        public void run() {
                            sendResult(handler, ServerDirectoryProtocol.RESULT_SUCCESS, success);
                            sendState(handler, stateFor(player, hasPermission(player), config, false, success, listing));
                        }
                    });
                } catch (final ServerDirectoryWebsiteClient.WebsiteException rejected) {
                    dispatch(new Runnable() {
                        public void run() {
                            sendResult(handler, resultForWebsiteStatus(rejected.status), rejected.getMessage());
                            sendState(handler, stateFor(player, hasPermission(player), config, false, rejected.getMessage(), rejected.listing));
                        }
                    });
                } catch (final IOException unavailable) {
                    dispatch(new Runnable() {
                        public void run() {
                            sendResult(handler, ServerDirectoryProtocol.RESULT_WEBSITE, "The directory website timed out or returned an invalid response.");
                        }
                    });
                }
            }
        });
    }

    public void handleRateLimited(NetServerHandler handler) {
        sendResult(handler, ServerDirectoryProtocol.RESULT_VALIDATION,
                "Server-directory requests are arriving too quickly. Please wait a moment and try again.");
    }

    private void submit(NetServerHandler handler, Runnable task) {
        try {
            this.workers.execute(task);
        } catch (RejectedExecutionException busy) {
            sendResult(handler, ServerDirectoryProtocol.RESULT_WEBSITE, "The server-directory worker queue is busy. Please try again shortly.");
        }
    }

    static boolean hasPermission(EntityPlayer player) {
        if (player == null || !(player.getBukkitEntity() instanceof Player)) return false;
        return hasPermission((Player) player.getBukkitEntity());
    }

    static boolean hasPermission(Player player) {
        return player != null && player.hasPermission(PERMISSION);
    }

    static int resultForWebsiteStatus(int status) {
        if (status == 400 || status == 413) return ServerDirectoryProtocol.RESULT_VALIDATION;
        if (status == 409) return ServerDirectoryProtocol.RESULT_CONFLICT;
        if (status == 401 || status == 403) return ServerDirectoryProtocol.RESULT_CONFIGURATION;
        return ServerDirectoryProtocol.RESULT_WEBSITE;
    }

    private static ServerDirectoryProtocol.State stateFor(EntityPlayer player, boolean permitted,
                                                           ServerDirectoryConfig config, boolean loading,
                                                           String message, ServerDirectoryProtocol.Listing listing) {
        return new ServerDirectoryProtocol.State(
                true,
                permitted,
                config.isReady(),
                loading,
                config.publicEndpoint,
                player == null || player.name == null ? "" : player.name,
                message == null ? "" : message,
                listing);
    }

    private static void sendState(NetServerHandler handler, ServerDirectoryProtocol.State state) {
        if (handler == null || handler.disconnected) return;
        try {
            handler.sendPacket(new Packet250CustomPayload(
                    ServerDirectoryProtocol.CHANNEL_STATE,
                    ServerDirectoryProtocol.createStatePayload(state)));
        } catch (IOException invalidState) {
            sendResult(handler, ServerDirectoryProtocol.RESULT_WEBSITE, "The server produced an invalid directory state.");
        }
    }

    private static void sendResult(NetServerHandler handler, int code, String message) {
        if (handler == null || handler.disconnected) return;
        try {
            handler.sendPacket(new Packet250CustomPayload(
                    ServerDirectoryProtocol.CHANNEL_RESULT,
                    ServerDirectoryProtocol.createResultPayload(code, message)));
        } catch (IOException ignored) {
        }
    }

    private static void dispatch(Runnable task) {
        if (Bukkit.getServer() == null) return;
        Bukkit.getScheduler().scheduleSyncDelayedTask(CORE_PLUGIN, task);
    }

    int workerMaximum() {
        return this.workers.getMaximumPoolSize();
    }

    int workerQueueCapacity() {
        return this.workers.getQueue().size() + this.workers.getQueue().remainingCapacity();
    }

    private static final class DirectoryThreadFactory implements ThreadFactory {
        private final AtomicInteger ids = new AtomicInteger();

        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "MCOSE server-directory worker " + ids.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
