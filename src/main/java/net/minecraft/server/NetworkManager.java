package net.minecraft.server;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import com.legacyminecraft.poseidon.PoseidonConfig;
import com.legacyminecraft.poseidon.event.PlayerReceivePacketEvent;

import uk.betacraft.uberbukkit.Uberbukkit;
import uk.betacraft.uberbukkit.packet.Packet62Sound;
import uk.betacraft.uberbukkit.protocol.Protocol;

public class NetworkManager implements Packet.LoginPhaseState {

    public static final Object a = new Object();
    public static int b;
    public static int c;
    private Object g = new Object();
    private final Object writeLock = new Object();
    public Socket socket; // CraftBukkit - private -> public
    private SocketAddress i; //Project Poseidon - remove final statement
    private volatile DataInputStream input;
    private volatile DataOutputStream output;
    private boolean l = true;
    private List m = Collections.synchronizedList(new LinkedList());
    private List urgentQueue = Collections.synchronizedList(new LinkedList());
    private List highPriorityQueue = Collections.synchronizedList(new LinkedList());
    private List lowPriorityQueue = Collections.synchronizedList(new LinkedList());
    private volatile NetHandler p;
    private boolean q = false;
    private volatile boolean readOnly = false;
    private Thread r;
    private Thread s;
    private boolean t = false;
    private String u = "";
    private Object[] v;
    private int w = 0;
    private int x = 0;
    private long inboundQueueBytes = 0L;
    public static int[] d = new int[256];
    public static int[] e = new int[256];
    public int f = 0;
    private int lowPriorityQueueDelay = 50;
    private int highPriorityBurstCount = 0;
    private int inboundPriorityBurstCount = 0;
    private static final int INBOUND_PRIORITY_BACKLOG_THRESHOLD = 2;
    private static final int INBOUND_PRIORITY_SCAN_LIMIT = 64;
    private static final int INBOUND_PRIORITY_BURST_LIMIT = 6;
    private static final int LOW_PRIORITY_COALESCE_SCAN_LIMIT = 320;
    private static final int LOW_PRIORITY_MOVEMENT_HARD_CAP = 420;
    private static final int OUTBOUND_MAX_PACKETS = 8192;
    private static final int OUTBOUND_WRITE_BATCH_LIMIT = 64;
    private static final int INBOUND_MAX_PACKETS = 8192;
    private static final long INBOUND_MAX_BYTES = 16L * 1024L * 1024L;
    private static final long MALFORMED_PACKET_LOG_INTERVAL_MS = 10000L;
    private static final AtomicLong LAST_MALFORMED_PACKET_LOG_AT = new AtomicLong(0L);
    private static final AtomicLong SUPPRESSED_MALFORMED_PACKET_LOGS = new AtomicLong(0L);
    private final boolean firePacketEvents;
    private final int movementCoalesceThreshold;
    private final int movementDropHardCap;
    private final Object encryptionLock = new Object();
    private volatile boolean encryptionReadPaused = false;
    private volatile boolean encryptionEnabled = false;
    private volatile boolean statusReadPaused = false;

    private final boolean spamDetection;

    private final int threshold;
    private final boolean packetRateLimitEnabled;
    private final int packetRateLimit;
    private long packetRateWindowStartedAt = System.currentTimeMillis();
    private int packetsReceivedThisWindow = 0;
    private float averageReceivedPacketsPerSecond = 0.0F;

    // uberbukkit
    public int pvn = 0;
    public Protocol protocol = null;

    public NetworkManager(Socket socket, String s, NetHandler nethandler) {
        this.socket = socket;
        this.i = socket.getRemoteSocketAddress();
        this.p = nethandler;

        //Poseidon
        this.firePacketEvents = PoseidonConfig.getInstance().getBoolean("settings.packet-events.enabled", false);
        this.spamDetection = PoseidonConfig.getInstance().getBoolean("settings.packet-spam-detection.enabled", true);
        this.threshold = PoseidonConfig.getInstance().getInt("settings.packet-spam-detection.threshold", 1000);
        this.packetRateLimitEnabled = PoseidonConfig.getInstance().getBoolean("settings.packet-rate-limit.enabled", true);
        this.packetRateLimit = Math.max(1, PoseidonConfig.getInstance().getInt("settings.packet-rate-limit.packets-per-second", 500));
        this.movementCoalesceThreshold = Math.max(1, PoseidonConfig.getInstance().getInt("settings.entity-tracking.action-priority.enter-low-queue", 96));
        this.movementDropHardCap = Math.max(64, LOW_PRIORITY_MOVEMENT_HARD_CAP);

        //Debug for packet spam detection
//        System.out.println("[Poseidon] Packet spam detection is " + (this.spamDetection ? "enabled" : "disabled") + " with a threshold of " + this.threshold + " packets");

        // CraftBukkit start - IPv6 stack in Java on BSD/OSX doesn't support setTrafficClass
        try {
            socket.setTrafficClass(24);
        } catch (SocketException e) {
        }
        // CraftBukkit end

        try {
            // CraftBukkit start - cant compile these outside the try
            socket.setSoTimeout(30000);
            boolean tcpNoDelay = PoseidonConfig.getEmptyNode().getBoolean(
                    "settings.enable-tpc-nodelay",
                    PoseidonConfig.getEmptyNode().getBoolean("settings.enable-tcp-nodelay", true)
            );
            if (tcpNoDelay) {
                socket.setTcpNoDelay(true);
            }

            // uberbukkit
            if (Uberbukkit.getTargetPVN() >= 11) {
                this.input = new DataInputStream(socket.getInputStream());
                this.output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 5120));
            } else {
                this.input = new DataInputStream(socket.getInputStream());
                this.output = new DataOutputStream(socket.getOutputStream());
            }
        } catch (java.io.IOException socketexception) {
            // CraftBukkit end
            System.err.println(socketexception.getMessage());
        }

        /* CraftBukkit start - moved up
        this.input = new DataInputStream(socket.getInputStream());
        this.output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 5120));
        // CraftBukkit end */
        this.s = new NetworkReaderThread(this, s + " read thread");
        this.r = new NetworkWriterThread(this, s + " write thread");
        this.s.start();
        this.r.start();
    }

    //Project Poseidon Start
    public void setSocketAddress(SocketAddress socketAddress) {
        this.i = socketAddress;
    }

    public SocketAddress generateSocketAddress(String hostname, int port) {
        return new InetSocketAddress(hostname, port);
    }

    //Project Poseidon End

    public void a(NetHandler nethandler) {
        this.p = nethandler;
    }

    @Override
    public boolean isLoginPhase() {
        return this.p instanceof NetLoginHandler;
    }

    private String getProfilerPlayerName() {
        if (this.p instanceof NetServerHandler) {
            NetServerHandler handler = (NetServerHandler) this.p;
            if (handler.player != null) {
                return handler.player.name;
            }
        }
        return null;
    }

    public void queue(Packet packet) {
        if (!this.q) {
            Object object = this.g;

            // uberbukkit
            if (this.protocol != null && !this.protocol.canReceivePacket(packet.b())) {
                boolean allowMcoseSound = packet instanceof Packet62Sound
                        && this.p instanceof NetServerHandler
                        && ((NetServerHandler) this.p).isMcoseClient();
                if (!allowMcoseSound) {
                    return;
                }
            }

            // uberbukkit
            if (this.pvn != 0) packet.pvn = this.pvn;

            synchronized (this.g) {
                boolean urgent = isOutboundUrgentPacket(packet);
                boolean lowPriority = packet.k || isOutboundLowPriorityPacket(packet);
                if (urgent) {
                    this.urgentQueue.add(packet);
                } else if (lowPriority) {
                    if (shouldDropLowPriorityPacket(packet)) {
                        return;
                    }
                    this.lowPriorityQueue.add(packet);
                } else {
                    this.highPriorityQueue.add(packet);
                }
                this.x += packet.a() + 1;
                if (this.x > 2097152 || getQueuedPacketCount() > OUTBOUND_MAX_PACKETS) {
                    this.a("disconnect.overflow", new Object[0]);
                }
            }
        }
    }

    private boolean shouldDropLowPriorityPacket(Packet packet) {
        if (packet == null) {
            return true;
        }

        int entityId = getMovementEntityId(packet);
        if (entityId != Integer.MIN_VALUE && this.lowPriorityQueue.size() >= this.movementCoalesceThreshold) {
            int queueSizeBeforeCoalesce = this.lowPriorityQueue.size();
            int removed = coalesceMovementPackets(entityId);
            if (removed > 0) {
                if (queueSizeBeforeCoalesce >= this.movementDropHardCap) {
                    ServerProfiler.getInstance().recordMovementPacketDropped(removed);
                } else {
                    ServerProfiler.getInstance().recordMovementPacketCoalesced(removed);
                }
            }
        }

        return false;
    }

    private int coalesceMovementPackets(int entityId) {
        int scanned = 0;
        int removed = 0;
        ListIterator iterator = this.lowPriorityQueue.listIterator(this.lowPriorityQueue.size());
        while (iterator.hasPrevious() && scanned < LOW_PRIORITY_COALESCE_SCAN_LIMIT) {
            Packet queued = (Packet) iterator.previous();
            scanned++;
            if (queued == null) {
                continue;
            }

            int queuedEntityId = getMovementEntityId(queued);
            if (queuedEntityId == entityId) {
                iterator.remove();
                this.x -= queued.a() + 1;
                removed++;
            }
        }
        return removed;
    }

    private int getMovementEntityId(Packet packet) {
        if (packet instanceof Packet30Entity) {
            return ((Packet30Entity) packet).a;
        }
        if (packet instanceof Packet28EntityVelocity) {
            return ((Packet28EntityVelocity) packet).a;
        }
        if (packet instanceof Packet34EntityTeleport) {
            return ((Packet34EntityTeleport) packet).a;
        }
        return Integer.MIN_VALUE;
    }

    private boolean isOutboundUrgentPacket(Packet packet) {
        if (packet == null) {
            return false;
        }
        switch (packet.b()) {
            case 1:   // login
            case 3:   // chat / command response
            case 6:   // spawn position
            case 8:   // health
            case 9:   // respawn
            case 70:  // bed / animation that affects player state
            case 100: // open window
            case 101: // close window
            case 103: // set slot
            case 104: // window items
            case 105: // update progress bar
            case 106: // transaction
            case 255: // kick
            case 64:  // voice (TCP failover) - latency sensitive
                return true;
            default:
                return false;
        }
    }

    private boolean isOutboundLowPriorityPacket(Packet packet) {
        if (packet == null) {
            return false;
        }
        switch (packet.b()) {
            case 4:   // time update
            case 20:  // named entity spawn
            case 22:  // collect item
            case 23:  // vehicle spawn
            case 24:  // mob spawn
            case 28:  // entity velocity
            case 29:  // destroy entity
            case 30:  // entity
            case 31:  // rel move
            case 32:  // look
            case 33:  // rel move + look
            case 34:  // teleport
            case 39:  // attach entity
            case 50:  // pre-chunk
            case 51:  // map chunk
            case 52:  // multiblock change
            case 200: // stats
            case 201: // tab/player info
                return true;
            default:
                return false;
        }
    }

    private boolean isInboundCriticalPacketId(int packetId) {
        switch (packetId) {
            case 3:   // chat + commands
            case 7:   // use entity (combat)
            case 14:  // digging
            case 15:  // place/interact
            case 16:  // held item switch
            case 18:  // arm animation
            case 19:  // entity action
            case 101: // close window
            case 102: // window click
            case 106: // transaction
            case 130: // sign update
            case 205: // client command
            case 250: // plugin payload
            case 64:  // voice (TCP failover) - latency sensitive
                return true;
            default:
                return false;
        }
    }

    private Packet pollNextInboundPacket() {
        synchronized (this.m) {
            int size = this.m.size();
            if (size <= 0) {
                return null;
            }

            boolean startupPriorityBoost = isStartupPriorityBoostWindow();
            int burstLimit = startupPriorityBoost ? 24 : INBOUND_PRIORITY_BURST_LIMIT;
            int scanLimitCap = startupPriorityBoost ? Math.min(size, INBOUND_PRIORITY_SCAN_LIMIT * 2) : Math.min(size, INBOUND_PRIORITY_SCAN_LIMIT);

            if (size < INBOUND_PRIORITY_BACKLOG_THRESHOLD || this.inboundPriorityBurstCount >= burstLimit) {
                this.inboundPriorityBurstCount = 0;
                return this.removeInboundPacketAt(0);
            }

            ListIterator iterator = this.m.listIterator();
            for (int scanned = 0; iterator.hasNext() && scanned < scanLimitCap; scanned++) {
                Packet candidate = (Packet) iterator.next();
                if (candidate == null) {
                    continue;
                }
                if (isInboundCriticalPacketId(candidate.b())) {
                    this.inboundPriorityBurstCount++;
                    iterator.remove();
                    this.inboundQueueBytes -= safePacketSize(candidate);
                    if (this.inboundQueueBytes < 0L) {
                        this.inboundQueueBytes = 0L;
                    }
                    return candidate;
                }
            }

            this.inboundPriorityBurstCount = 0;
            return this.removeInboundPacketAt(0);
        }
    }

    private Packet removeInboundPacketAt(int index) {
        Packet packet = (Packet) this.m.remove(index);
        this.inboundQueueBytes -= safePacketSize(packet);
        if (this.inboundQueueBytes < 0L) {
            this.inboundQueueBytes = 0L;
        }
        return packet;
    }

    private static long safePacketSize(Packet packet) {
        if (packet == null) {
            return 1L;
        }
        try {
            return Math.max(1L, (long) packet.a() + 1L);
        } catch (RuntimeException invalidSize) {
            return 1L;
        }
    }

    private boolean isStartupPriorityBoostWindow() {
        if (!(this.p instanceof NetServerHandler)) {
            return false;
        }

        NetServerHandler handler = (NetServerHandler) this.p;
        if (handler.player == null || handler.player.world == null || !(handler.player.world instanceof WorldServer)) {
            return false;
        }

        WorldServer worldServer = (WorldServer) handler.player.world;
        if (worldServer.server == null) {
            return false;
        }

        return worldServer.server.ticks < 1200;
    }

    private boolean f() {
        try {
            return this.writeOutboundBatch(OUTBOUND_WRITE_BATCH_LIMIT);
        } catch (Exception exception) {
            if (!this.t) {
                this.a(exception);
            }

            return false;
        }
    }

    private boolean writeOutboundBatch(int maxPackets) throws IOException {
        if (this.tickEmptyOutboundQueues()) {
            return false;
        }

        int sentPackets = 0;
        List egressRecords = new ArrayList(Math.min(maxPackets, OUTBOUND_WRITE_BATCH_LIMIT));

        synchronized (this.writeLock) {
            while (sentPackets < maxPackets) {
                int sentThisCycle = this.writeOutboundCycleLocked(maxPackets - sentPackets, egressRecords);

                if (sentThisCycle <= 0) {
                    break;
                }

                sentPackets += sentThisCycle;
            }
        }

        this.recordOutboundEgress(egressRecords);
        return sentPackets > 0;
    }

    private boolean tickEmptyOutboundQueues() {
        synchronized (this.g) {
            if (this.urgentQueue.isEmpty() && this.highPriorityQueue.isEmpty() && this.lowPriorityQueue.isEmpty()) {
                --this.lowPriorityQueueDelay;
                return true;
            }

            return false;
        }
    }

    private int writeOutboundCycleLocked(int remainingBatchSlots, List egressRecords) throws IOException {
        boolean sentInCycle = false;
        int sent = 0;
        long now = System.currentTimeMillis();

        OutboundQueuedPacket queuedPacket = this.pollUrgentPacket(now);
        if (queuedPacket != null) {
            egressRecords.add(this.writeQueuedPacketLocked(queuedPacket, now, true));
            ++this.highPriorityBurstCount;
            sentInCycle = true;
            ++sent;
        }

        if (sent < remainingBatchSlots) {
            queuedPacket = this.pollHighPriorityPacket(now);
            if (queuedPacket != null) {
                egressRecords.add(this.writeQueuedPacketLocked(queuedPacket, now, true));
                ++this.highPriorityBurstCount;
                sentInCycle = true;
                ++sent;
            }
        }

        if (sent < remainingBatchSlots) {
            queuedPacket = this.pollLowPriorityPacket(sentInCycle);
            if (queuedPacket != null) {
                egressRecords.add(this.writeQueuedPacketLocked(queuedPacket, now, false));
                this.lowPriorityQueueDelay = 0;
                this.highPriorityBurstCount = 0;
                ++sent;
            }
        }

        return sent;
    }

    private OutboundQueuedPacket pollUrgentPacket(long now) {
        synchronized (this.g) {
            if (!this.urgentQueue.isEmpty() && (this.f == 0 || now - ((Packet) this.urgentQueue.get(0)).timestamp >= (long) this.f)) {
                return this.removeQueuedPacket(this.urgentQueue);
            }

            return null;
        }
    }

    private OutboundQueuedPacket pollHighPriorityPacket(long now) {
        synchronized (this.g) {
            if (!this.highPriorityQueue.isEmpty() && (this.f == 0 || now - ((Packet) this.highPriorityQueue.get(0)).timestamp >= (long) this.f)) {
                return this.removeQueuedPacket(this.highPriorityQueue);
            }

            return null;
        }
    }

    private OutboundQueuedPacket pollLowPriorityPacket(boolean sentInCycle) {
        synchronized (this.g) {
            boolean allowLowPriorityFairness = this.highPriorityBurstCount >= 3;
            boolean allowLowPriority = sentInCycle;

            if (!allowLowPriority) {
                allowLowPriority = this.lowPriorityQueueDelay-- <= 0;
            }

            if (!allowLowPriority) {
                allowLowPriority = allowLowPriorityFairness;
            }

            if (allowLowPriority
                    && !this.lowPriorityQueue.isEmpty()
                    && this.urgentQueue.isEmpty()
                    && (this.highPriorityQueue.isEmpty() || allowLowPriorityFairness || ((Packet) this.highPriorityQueue.get(0)).timestamp > ((Packet) this.lowPriorityQueue.get(0)).timestamp)) {
                return this.removeQueuedPacket(this.lowPriorityQueue);
            }

            return null;
        }
    }

    private OutboundQueuedPacket removeQueuedPacket(List queue) {
        Packet packet = (Packet) queue.remove(0);
        int packetBytes = packet.a() + 1;

        this.x -= packetBytes;
        return new OutboundQueuedPacket(packet, packetBytes);
    }

    private OutboundQueuedPacket writeQueuedPacketLocked(OutboundQueuedPacket queuedPacket, long now, boolean highPriority) throws IOException {
        Packet packet = queuedPacket.packet;
        DataOutputStream currentOutput = this.output;

        if (currentOutput == null) {
            throw new IOException("Output stream closed");
        }

        Packet.a(packet, currentOutput);
        int packetId = packet.b();
        e[packetId] += queuedPacket.packetBytes;
        queuedPacket.markEgress(
            packetId,
            packet.getClass().getSimpleName(),
            Math.max(0L, now - packet.timestamp),
            highPriority,
            getProfilerPlayerName()
        );
        return queuedPacket;
    }

    private void recordOutboundEgress(List egressRecords) {
        for (int i = 0; i < egressRecords.size(); ++i) {
            OutboundQueuedPacket queuedPacket = (OutboundQueuedPacket) egressRecords.get(i);
            ServerProfiler.getInstance().recordPacketEgress(
                queuedPacket.packetId,
                queuedPacket.packetClass,
                queuedPacket.queueWaitMs,
                queuedPacket.highPriority,
                queuedPacket.packetBytes,
                queuedPacket.playerName
            );
        }
    }

    private static final class OutboundQueuedPacket {
        private final Packet packet;
        private final int packetBytes;
        private int packetId;
        private String packetClass;
        private long queueWaitMs;
        private boolean highPriority;
        private String playerName;

        private OutboundQueuedPacket(Packet packet, int packetBytes) {
            this.packet = packet;
            this.packetBytes = packetBytes;
        }

        private void markEgress(int packetId, String packetClass, long queueWaitMs, boolean highPriority, String playerName) {
            this.packetId = packetId;
            this.packetClass = packetClass;
            this.queueWaitMs = queueWaitMs;
            this.highPriority = highPriority;
            this.playerName = playerName;
        }
    }

    public void flushOutputStream() throws IOException {
        synchronized (this.writeLock) {
            if (this.output != null) {
                this.output.flush();
            }
        }
    }

    public void flushOutboundQueue(long maxWaitMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, maxWaitMs);
        try {
            while (this.getQueuedPacketCount() > 0 && System.currentTimeMillis() <= deadline) {
                if (!this.f()) {
                    break;
                }
            }
            this.flushOutputStream();
        } catch (Exception exception) {
            if (!this.t) {
                this.a(exception);
            }
        }
    }

    public void a() {
        this.s.interrupt();
        this.r.interrupt();
    }

    private boolean g() {
        boolean flag = false;

        try {
            if (this.readOnly) {
                return false;
            }
            if (this.statusReadPaused) {
                return false;
            }
            if (this.encryptionReadPaused && !this.encryptionEnabled) {
                return false;
            }

            Packet packet = Packet.a(this.input, this.p.c(), this.pvn, this); // uberbukkit - allows packets to be read accordingly to client version

            if (packet != null) {
                if (packet instanceof Packet252SharedKey && this.p instanceof NetLoginHandler && !this.encryptionEnabled) {
                    // Stop the reader at the exact plaintext/encrypted boundary. The
                    // login handler enables AES after validating this packet.
                    this.encryptionReadPaused = true;
                }
                if (packet instanceof Packet254ServerPing && this.p instanceof NetLoginHandler) {
                    // A legacy status request owns the connection. Do not let bytes
                    // following 0xFE race into the login packet decoder before the
                    // main thread sends the status response and closes the socket.
                    this.statusReadPaused = true;
                }
                if (!recordInboundPacketRate()) {
                    return false;
                }

                int[] aint = d;
                int i = packet.b();

                aint[i] += packet.a() + 1;
                boolean consumedByParallelLane = false;
                if (this.p instanceof NetServerHandler) {
                    NetServerHandler handler = (NetServerHandler) this.p;
                    if (packet instanceof Packet3Chat) {
                        consumedByParallelLane = handler.tryHandleParallelChat((Packet3Chat) packet);
                    } else if (packet instanceof Packet64Voice) {
                        consumedByParallelLane = handler.tryHandleParallelVoice((Packet64Voice) packet);
                    }
                }
                if (!consumedByParallelLane) {
                    synchronized (this.m) {
                        this.m.add(packet);
                        this.inboundQueueBytes += safePacketSize(packet);
                    }
                }
                if (this.m.size() > INBOUND_MAX_PACKETS || this.inboundQueueBytes > INBOUND_MAX_BYTES) {
                    this.a("disconnect.overflow", new Object[0]);
                    return false;
                }
                ServerProfiler.getInstance().recordPacketIngress(i, packet.getClass().getSimpleName(), this.m.size());
                flag = true;
            } else {
                this.a("disconnect.endOfStream", new Object[0]);
            }

            return flag;
        } catch (Exception exception) {
            if (!this.t) {
                this.handleInboundException(exception);
            }

            return false;
        }
    }

    /**
     * Enable the modern protocol's AES/CFB8 transport after Packet252 has been
     * fully decoded. The client encrypts every subsequent byte with the shared
     * secret as both key and IV.
     */
    public void enableEncryption(SecretKey key) throws Exception {
        if (key == null || key.getEncoded() == null || key.getEncoded().length != 16) {
            throw new IOException("Invalid AES session key");
        }

        synchronized (this.encryptionLock) {
            if (this.encryptionEnabled) {
                throw new IOException("Encryption is already enabled");
            }
            if (!(this.p instanceof NetLoginHandler) || !this.encryptionReadPaused) {
                throw new IOException("Encryption enabled outside the shared-key transition");
            }

            Cipher decryptCipher = createStreamCipher(Cipher.DECRYPT_MODE, key);
            Cipher encryptCipher = createStreamCipher(Cipher.ENCRYPT_MODE, key);

            synchronized (this.writeLock) {
                if (this.output == null || this.socket == null) {
                    throw new IOException("Connection closed during encryption setup");
                }
                this.output.flush();
                this.input = new DataInputStream(new CipherInputStream(this.socket.getInputStream(), decryptCipher));
                if (Uberbukkit.getTargetPVN() >= 11) {
                    this.output = new DataOutputStream(new BufferedOutputStream(
                            new CipherOutputStream(this.socket.getOutputStream(), encryptCipher), 5120));
                } else {
                    this.output = new DataOutputStream(new CipherOutputStream(this.socket.getOutputStream(), encryptCipher));
                }
            }

            this.encryptionEnabled = true;
            this.encryptionReadPaused = false;
        }
        this.s.interrupt();
    }

    static Cipher createStreamCipher(int mode, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
        cipher.init(mode, key, new IvParameterSpec(key.getEncoded()));
        return cipher;
    }

    private void a(Exception exception) {
        exception.printStackTrace();
        this.a("disconnect.genericReason", new Object[] { "Internal exception: " + exception.toString() });
    }

    private void handleInboundException(Exception exception) {
        if (exception instanceof IOException) {
            this.logMalformedPacket(exception);
            this.a("disconnect.genericReason", new Object[] { "Malformed packet" });
            return;
        }
        this.a(exception);
    }

    private void logMalformedPacket(Exception exception) {
        long now = System.currentTimeMillis();
        long previous = LAST_MALFORMED_PACKET_LOG_AT.get();
        if (now - previous >= MALFORMED_PACKET_LOG_INTERVAL_MS
                && LAST_MALFORMED_PACKET_LOG_AT.compareAndSet(previous, now)) {
            long suppressed = SUPPRESSED_MALFORMED_PACKET_LOGS.getAndSet(0L);
            System.err.println("[Network] Rejected malformed packet from " + String.valueOf(this.i)
                    + " (" + describeMalformedPacket(exception) + ")"
                    + (suppressed > 0L ? " (" + suppressed + " similar messages suppressed)" : ""));
        } else {
            SUPPRESSED_MALFORMED_PACKET_LOGS.incrementAndGet();
        }
    }

    static String describeMalformedPacket(Exception exception) {
        if (exception == null) {
            return "unknown error";
        }

        String type = exception.getClass().getSimpleName();
        String message = exception.getMessage();
        if (message == null || message.length() == 0) {
            return type;
        }

        message = message.replace('\r', ' ').replace('\n', ' ').trim();
        String prefix = type + ": ";
        if (prefix.length() >= 160) {
            return prefix.substring(0, 157) + "...";
        }
        int maximumMessageLength = 160 - prefix.length();
        if (message.length() > maximumMessageLength) {
            message = message.substring(0, maximumMessageLength - 3) + "...";
        }
        return prefix + message;
    }

    private boolean recordInboundPacketRate() {
        if (!this.packetRateLimitEnabled || this.t) {
            return true;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - this.packetRateWindowStartedAt;
        if (elapsed >= 1000L) {
            updateInboundPacketAverage(elapsed);
            this.packetRateWindowStartedAt = now;
            this.packetsReceivedThisWindow = 0;
        }

        ++this.packetsReceivedThisWindow;
        if (this.averageReceivedPacketsPerSecond > (float) this.packetRateLimit || this.packetsReceivedThisWindow > this.packetRateLimit * 2) {
            float observedRate = Math.max(this.averageReceivedPacketsPerSecond, (float) this.packetsReceivedThisWindow);
            return disconnectForPacketRate(observedRate);
        }

        return true;
    }

    private void updateInboundPacketAverage(long elapsedMs) {
        float currentRate = elapsedMs <= 0L ? (float) this.packetsReceivedThisWindow : (float) this.packetsReceivedThisWindow * 1000.0F / (float) elapsedMs;
        if (this.averageReceivedPacketsPerSecond <= 0.0F) {
            this.averageReceivedPacketsPerSecond = currentRate;
        } else {
            this.averageReceivedPacketsPerSecond = this.averageReceivedPacketsPerSecond * 0.75F + currentRate * 0.25F;
        }
    }

    private boolean disconnectForPacketRate(float observedRate) {
        this.setReadOnly();
        String playerUsername = "Unknown";
        if (this.p instanceof NetServerHandler) {
            NetServerHandler handler = (NetServerHandler) this.p;
            if (handler.player != null) {
                playerUsername = handler.player.name;
            }
            handler.disconnect(ChatColor.RED + "[Poseidon] You have been kicked for packet spamming.");
        } else {
            this.a("disconnect.spam", new Object[0]);
        }
        System.out.println("[Poseidon] Player " + playerUsername + " has been kicked for packet rate spam. The observed rate was " + observedRate + " packets/sec and the limit was " + this.packetRateLimit + ".");
        return false;
    }

    public void setReadOnly() {
        this.readOnly = true;
        try {
            if (this.socket != null) {
                this.socket.shutdownInput();
            }
        } catch (Throwable throwable) {
            ;
        }
    }

    public void a(String s, Object... aobject) {
        if (this.l) {
            this.readOnly = true;
            this.t = true;
            this.u = s;
            this.v = aobject;
            (new NetworkMasterThread(this)).start();
            this.l = false;

            try {
                this.input.close();
                this.input = null;
            } catch (Throwable throwable) {
                ;
            }

            try {
                this.output.close();
                this.output = null;
            } catch (Throwable throwable1) {
                ;
            }

            try {
                this.socket.close();
                this.socket = null;
            } catch (Throwable throwable2) {
                ;
            }
        }
    }

    public void b() {
        boolean fast = PoseidonConfig.getInstance().getBoolean("settings.faster-packets.enabled", true);
        if (this.x > (fast ? 2097152 : 1048576)) {
            this.a("disconnect.overflow", new Object[0]);
        }

        if (this.m.isEmpty()) {
            if (this.w++ == 1200) {
                this.a("disconnect.timeout", new Object[0]);
            }
        } else {
            this.w = 0;
        }

        int i = this.getInboundProcessBudget(fast);

        //Poseidon - Packet spam detection
        if (spamDetection) {
            if (this.m.size() > threshold) {
                String playerUsername = "Unknown";
                if (this.p instanceof NetServerHandler) {
                    playerUsername = ((NetServerHandler) this.p).player.name;
                    ((NetServerHandler) this.p).disconnect(ChatColor.RED + "[Poseidon] You have been kicked for packet spamming.");
                } else {
                    this.a("disconnect.spam", new Object[0]);
                }
                System.out.println("[Poseidon] Player " + playerUsername + " has been kicked for packet spamming. The queue size was " + this.m.size() + " and the threshold was " + threshold + ".");
            }
        }

//        if(this.m.size() > 1000) {
//            String playerUsername = "Unknown";
//            if (this.p instanceof NetServerHandler) {
//                System.out.println("The packet queue size is " + this.m.size() + " for player " + ((NetServerHandler) this.p).player.name + ".");
//            }
//        }


        while (!this.m.isEmpty() && i-- >= 0) {
            Packet packet = pollNextInboundPacket();
            if (packet == null) {
                break;
            }
            String profilerPlayer = getProfilerPlayerName();
            int packetId = packet.b();
            double queueWaitMs = Math.max(0L, System.currentTimeMillis() - packet.timestamp);
            long processStart = System.nanoTime();

            //Poseidon Start - Packet Receive Event
            if (firePacketEvents && this.p instanceof NetServerHandler) {
                PlayerReceivePacketEvent event = new PlayerReceivePacketEvent(((NetServerHandler) this.p).player.name, packet);
                Bukkit.getPluginManager().callEvent(event);
                packet = event.getPacket();
                if (!event.isCancelled()) {
                    packet.a(this.p);
                }

            } else {
                packet.a(this.p);
            }

            //Poseidon End

            //            packet.a(this.p);
            double processMs = (System.nanoTime() - processStart) / 1_000_000.0D;
            ServerProfiler.getInstance().recordPacketProcess(packetId, packet.getClass().getSimpleName(), queueWaitMs, processMs, profilerPlayer);
        }

        this.a();
        if (this.t && this.m.isEmpty()) {
            this.p.a(this.u, this.v);
        }
    }

    private int getInboundProcessBudget(boolean fast) {
        int queueSize = this.m.size();
        if (queueSize >= 2048) {
            return fast ? 1800 : 600;
        }
        if (queueSize >= 1024) {
            return fast ? 1400 : 450;
        }
        if (queueSize >= 512) {
            return fast ? 1100 : 320;
        }
        if (queueSize >= 256) {
            return fast ? 900 : 220;
        }
        return fast ? 1000 : 100;
    }

    public SocketAddress getSocketAddress() {
        return this.i;
    }

    public void d() {
        this.a();
        this.q = true;
        this.s.interrupt();
        (new ThreadMonitorConnection(this)).start();
    }

    public int e() {
        return this.lowPriorityQueue.size();
    }

    public int getQueuedPacketCount() {
        synchronized (this.g) {
            return this.urgentQueue.size() + this.highPriorityQueue.size() + this.lowPriorityQueue.size();
        }
    }

    public int getHighPriorityQueueSize() {
        synchronized (this.g) {
            return this.urgentQueue.size() + this.highPriorityQueue.size();
        }
    }

    public int getLowPriorityQueueSize() {
        synchronized (this.g) {
            return this.lowPriorityQueue.size();
        }
    }

    public int getInboundQueueSize() {
        return this.m.size();
    }

    public int getQueuedBytes() {
        synchronized (this.g) {
            return this.x;
        }
    }

    static boolean a(NetworkManager networkmanager) {
        return networkmanager.l;
    }

    static boolean b(NetworkManager networkmanager) {
        return networkmanager.q;
    }

    static boolean c(NetworkManager networkmanager) {
        return networkmanager.g();
    }

    static boolean d(NetworkManager networkmanager) {
        return networkmanager.f();
    }

    static DataOutputStream e(NetworkManager networkmanager) {
        return networkmanager.output;
    }

    static boolean f(NetworkManager networkmanager) {
        return networkmanager.t;
    }

    static void a(NetworkManager networkmanager, Exception exception) {
        networkmanager.a(exception);
    }

    static Thread g(NetworkManager networkmanager) {
        return networkmanager.s;
    }

    static Thread h(NetworkManager networkmanager) {
        return networkmanager.r;
    }
}
