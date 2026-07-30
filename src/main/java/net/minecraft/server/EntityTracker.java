package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;
import org.bukkit.craftbukkit.util.LongHashtable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class EntityTracker {

    private Set a = new HashSet();
    public EntityList b = new EntityList(); //Project Poseidon: private -> public
    private LongHashtable<Set> trackedEntriesByChunk = new LongHashtable<Set>();
    private final ArrayList movedPlayerTrackers = new ArrayList();
    private MinecraftServer c;
    private int d;
    private int e;

    // Cached config values for entity tracking - uberbukkit smooth entity tracking
    private int mobUpdateFrequency;
    private int playerUpdateFrequency;
    private int vehicleUpdateFrequency;
    private int projectileUpdateFrequency;
    private int itemUpdateFrequency;
    private boolean actionPriorityModeEnabled;
    private boolean adaptiveActionPriorityMode;
    private int actionPriorityStartupTicks;
    private int enterLowQueueThreshold;
    private int exitLowQueueThreshold;
    private int enterHighQueueThreshold;
    private int exitHighQueueThreshold;
    private int enterPlayerQueuedThreshold;
    private int exitPlayerQueuedThreshold;
    private int minPressureTicks;
    private int minRecoveryTicks;
    private int nearChunkRadius;
    private int midChunkRadius;

    private TrackingPressureState trackingPressureState = TrackingPressureState.NORMAL;
    private int pressureTicks = 0;
    private int recoveryTicks = 0;
    private int recoveryCooldownTicks = 0;
    private long trackingStateLastTransitionMillis = System.currentTimeMillis();
    private int skippedNearThisTick = 0;
    private int skippedMidThisTick = 0;
    private int skippedFarThisTick = 0;

    public static enum TrackingPressureState {
        NORMAL,
        PRESSURE,
        RECOVERY
    }

    private static enum TrackingDistanceBand {
        NEAR,
        MID,
        FAR
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 4294967295L);
    }

    private void removeFromChunkIndex(EntityTrackerEntry entitytrackerentry, long key) {
        Set entries = (Set) this.trackedEntriesByChunk.get(key);

        if (entries != null) {
            entries.remove(entitytrackerentry);
            if (entries.isEmpty()) {
                this.trackedEntriesByChunk.remove(key);
            }
        }
    }

    private void updateChunkIndex(EntityTrackerEntry entitytrackerentry) {
        long key = this.chunkKey(entitytrackerentry.tracker.bH, entitytrackerentry.tracker.bJ);

        if (entitytrackerentry.hasTrackedChunkKey && key == entitytrackerentry.trackedChunkKey) {
            return;
        }

        if (entitytrackerentry.hasTrackedChunkKey) {
            this.removeFromChunkIndex(entitytrackerentry, entitytrackerentry.trackedChunkKey);
        }

        Set entries = (Set) this.trackedEntriesByChunk.get(key);

        if (entries == null) {
            entries = new HashSet();
            this.trackedEntriesByChunk.put(key, entries);
        }

        entries.add(entitytrackerentry);
        entitytrackerentry.trackedChunkKey = key;
        entitytrackerentry.hasTrackedChunkKey = true;
    }

    private void removeFromChunkIndex(EntityTrackerEntry entitytrackerentry) {
        if (entitytrackerentry.hasTrackedChunkKey) {
            this.removeFromChunkIndex(entitytrackerentry, entitytrackerentry.trackedChunkKey);
            entitytrackerentry.hasTrackedChunkKey = false;
        }
    }

    public EntityTracker(MinecraftServer minecraftserver, int i) {
        this.c = minecraftserver;
        this.e = i;
        this.d = minecraftserver.serverConfigurationManager.a();
        
        // Load entity tracking config - uberbukkit smooth entity tracking
        loadTrackingConfig();
    }
    
    /**
     * Loads entity tracking configuration from poseidon.yml
     * Lower update frequencies = smoother movement but more bandwidth
     */
    private void loadTrackingConfig() {
        PoseidonConfig config = PoseidonConfig.getInstance();
        boolean enabled = config.getConfigBoolean("settings.entity-tracking.enabled", true);
        this.actionPriorityModeEnabled = config.getConfigBoolean("settings.entity-tracking.action-priority.enabled", true);
        String mode = getConfigString(config, "settings.entity-tracking.action-priority.mode", "adaptive");
        this.adaptiveActionPriorityMode = "adaptive".equalsIgnoreCase(mode);
        int startupSeconds = getConfigInt(config, "settings.entity-tracking.action-priority.startup-seconds", 20);
        this.actionPriorityStartupTicks = Math.max(0, startupSeconds) * 20;
        this.enterLowQueueThreshold = Math.max(1, getConfigInt(config, "settings.entity-tracking.action-priority.enter-low-queue", 96));
        this.exitLowQueueThreshold = Math.max(1, getConfigInt(config, "settings.entity-tracking.action-priority.exit-low-queue", 48));
        this.enterHighQueueThreshold = Math.max(1, getConfigInt(config, "settings.entity-tracking.action-priority.enter-high-queue", 20));
        this.exitHighQueueThreshold = Math.max(1, getConfigInt(config, "settings.entity-tracking.action-priority.exit-high-queue", 8));
        this.enterPlayerQueuedThreshold = Math.max(1, getConfigInt(config, "settings.entity-tracking.action-priority.enter-player-queued", 96));
        this.exitPlayerQueuedThreshold = Math.max(1, getConfigInt(config, "settings.entity-tracking.action-priority.exit-player-queued", 48));
        this.minPressureTicks = Math.max(1, getConfigInt(config, "settings.entity-tracking.action-priority.min-pressure-ticks", 10));
        this.minRecoveryTicks = Math.max(1, getConfigInt(config, "settings.entity-tracking.action-priority.min-recovery-ticks", 80));
        this.nearChunkRadius = Math.max(0, getConfigInt(config, "settings.entity-tracking.action-priority.near-chunk-radius", 2));
        this.midChunkRadius = Math.max(this.nearChunkRadius + 1, getConfigInt(config, "settings.entity-tracking.action-priority.mid-chunk-radius", 4));

        if (this.exitLowQueueThreshold > this.enterLowQueueThreshold) {
            this.exitLowQueueThreshold = this.enterLowQueueThreshold;
        }
        if (this.exitHighQueueThreshold > this.enterHighQueueThreshold) {
            this.exitHighQueueThreshold = this.enterHighQueueThreshold;
        }
        if (this.exitPlayerQueuedThreshold > this.enterPlayerQueuedThreshold) {
            this.exitPlayerQueuedThreshold = this.enterPlayerQueuedThreshold;
        }
        
        if (enabled) {
            this.mobUpdateFrequency = getConfigInt(config, "settings.entity-tracking.mob-update-frequency", 2);
            this.playerUpdateFrequency = getConfigInt(config, "settings.entity-tracking.player-update-frequency", 2);
            this.vehicleUpdateFrequency = getConfigInt(config, "settings.entity-tracking.vehicle-update-frequency", 2);
            this.projectileUpdateFrequency = getConfigInt(config, "settings.entity-tracking.projectile-update-frequency", 3);
            this.itemUpdateFrequency = getConfigInt(config, "settings.entity-tracking.item-update-frequency", 10);
        } else {
            // Use vanilla b1.7.3 values when disabled
            this.mobUpdateFrequency = 3;
            this.playerUpdateFrequency = 2;
            this.vehicleUpdateFrequency = 3;
            this.projectileUpdateFrequency = 5;
            this.itemUpdateFrequency = 20;
        }
        
        // Clamp values to sane ranges (2-20 ticks) - don't allow 1 as it causes lag
        this.mobUpdateFrequency = Math.max(2, Math.min(20, this.mobUpdateFrequency));
        this.playerUpdateFrequency = Math.max(2, Math.min(20, this.playerUpdateFrequency));
        this.vehicleUpdateFrequency = Math.max(2, Math.min(20, this.vehicleUpdateFrequency));
        this.projectileUpdateFrequency = Math.max(2, Math.min(20, this.projectileUpdateFrequency));
        this.itemUpdateFrequency = Math.max(2, Math.min(20, this.itemUpdateFrequency));
    }
    
    private int getConfigInt(PoseidonConfig config, String key, int defaultValue) {
        try {
            Object value = config.getConfigOption(key, defaultValue);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String getConfigString(PoseidonConfig config, String key, String defaultValue) {
        try {
            Object value = config.getConfigOption(key, defaultValue);
            if (value == null) {
                return defaultValue;
            }
            return String.valueOf(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // CraftBukkit - synchronized
    public synchronized void track(Entity entity) {
        if (entity instanceof EntityPlayer) {
            this.a(entity, 512, playerUpdateFrequency);
            EntityPlayer entityplayer = (EntityPlayer) entity;
            Iterator iterator = this.a.iterator();

            while (iterator.hasNext()) {
                EntityTrackerEntry entitytrackerentry = (EntityTrackerEntry) iterator.next();

                if (entitytrackerentry.tracker != entityplayer) {
                    entitytrackerentry.b(entityplayer);
                }
            }
        } else if (entity instanceof EntityFish) {
            this.a(entity, 64, projectileUpdateFrequency, true);
        } else if (entity instanceof EntityArrow) {
            // uberbukkit - smoother projectiles
            this.a(entity, 64, projectileUpdateFrequency, true);
        } else if (entity instanceof EntitySnowball) {
            this.a(entity, 64, projectileUpdateFrequency, true);
        } else if (entity instanceof EntityFireball) {
            this.a(entity, 64, projectileUpdateFrequency, true);
        } else if (entity instanceof EntityEgg) {
            this.a(entity, 64, projectileUpdateFrequency, true);
        } else if (entity instanceof EntityItem) {
            this.a(entity, 64, itemUpdateFrequency, true);
        } else if (entity instanceof EntityMinecart) {
            this.a(entity, 80, this.vehicleUpdateFrequency, true); // uberbukkit - smoother vehicles
        } else if (entity instanceof EntityBoat) {
            this.a(entity, 80, this.vehicleUpdateFrequency, true); // uberbukkit - smoother vehicles
        } else if (entity instanceof EntitySquid) {
            this.a(entity, 64, this.mobUpdateFrequency, true); // uberbukkit - smoother mobs
        } else if (entity instanceof EntitySnowman) {
            this.a(entity, 160, this.mobUpdateFrequency, true); // uberbukkit - smoother mobs, added velocity
        } else if (entity instanceof EntityHerobrine) {
            this.a(entity, 160, this.mobUpdateFrequency, true); // Herobrine - track like other mobs
        } else if (entity instanceof IAnimal) {
            this.a(entity, 80, this.mobUpdateFrequency, true); // uberbukkit - smoother mobs
        } else if (entity instanceof EntityTNTPrimed) {
            this.a(entity, 160, projectileUpdateFrequency, true); // Smoother TNT
        } else if (entity instanceof EntityFallingSand) {
            this.a(entity, 160, projectileUpdateFrequency, true); // Smoother falling blocks
        } else if (entity instanceof EntityPainting) {
            this.a(entity, 160, Integer.MAX_VALUE, false);
        } else if (entity instanceof EntityMapHanging) {
            this.a(entity, 160, Integer.MAX_VALUE, false);
        }
    }

    private TrackingPressureState evaluateTrackingPressureState() {
        if (!this.actionPriorityModeEnabled || !this.adaptiveActionPriorityMode) {
            this.pressureTicks = 0;
            this.recoveryTicks = 0;
            this.recoveryCooldownTicks = 0;
            return transitionTrackingState(TrackingPressureState.NORMAL);
        }

        QueuePressureSnapshot pressure = collectQueuePressureSnapshot();
        boolean startupPressure = this.c.ticks <= this.actionPriorityStartupTicks;
        boolean enterPressure = startupPressure
            || pressure.lowQueueMax >= this.enterLowQueueThreshold
            || pressure.highQueueMax >= this.enterHighQueueThreshold
            || pressure.playerQueuedMax >= this.enterPlayerQueuedThreshold;
        boolean lowPressure = !startupPressure
            && pressure.lowQueueMax <= this.exitLowQueueThreshold
            && pressure.highQueueMax <= this.exitHighQueueThreshold
            && pressure.playerQueuedMax <= this.exitPlayerQueuedThreshold;

        if (enterPressure) {
            this.pressureTicks++;
        } else {
            this.pressureTicks = 0;
        }
        if (lowPressure) {
            this.recoveryTicks++;
        } else {
            this.recoveryTicks = 0;
        }

        if (this.trackingPressureState == TrackingPressureState.NORMAL) {
            if (this.pressureTicks >= this.minPressureTicks) {
                this.recoveryCooldownTicks = this.minRecoveryTicks;
                this.recoveryTicks = 0;
                return transitionTrackingState(TrackingPressureState.PRESSURE);
            }
            return this.trackingPressureState;
        }

        if (this.trackingPressureState == TrackingPressureState.PRESSURE) {
            if (this.recoveryTicks >= this.minRecoveryTicks) {
                this.recoveryCooldownTicks = this.minRecoveryTicks;
                this.pressureTicks = 0;
                this.recoveryTicks = 0;
                return transitionTrackingState(TrackingPressureState.RECOVERY);
            }
            return this.trackingPressureState;
        }

        if (this.pressureTicks >= this.minPressureTicks) {
            this.recoveryCooldownTicks = this.minRecoveryTicks;
            this.recoveryTicks = 0;
            return transitionTrackingState(TrackingPressureState.PRESSURE);
        }

        if (lowPressure) {
            if (this.recoveryCooldownTicks > 0) {
                this.recoveryCooldownTicks--;
            }
        } else {
            this.recoveryCooldownTicks = this.minRecoveryTicks;
        }

        if (this.recoveryCooldownTicks <= 0) {
            this.pressureTicks = 0;
            this.recoveryTicks = 0;
            return transitionTrackingState(TrackingPressureState.NORMAL);
        }

        return this.trackingPressureState;
    }

    private TrackingPressureState transitionTrackingState(TrackingPressureState state) {
        if (this.trackingPressureState != state) {
            this.trackingPressureState = state;
            this.trackingStateLastTransitionMillis = System.currentTimeMillis();
        }
        return this.trackingPressureState;
    }

    private QueuePressureSnapshot collectQueuePressureSnapshot() {
        QueuePressureSnapshot pressure = new QueuePressureSnapshot();
        if (this.c.serverConfigurationManager == null) {
            return pressure;
        }

        for (int i = 0; i < this.c.serverConfigurationManager.players.size(); i++) {
            EntityPlayer player = (EntityPlayer) this.c.serverConfigurationManager.players.get(i);
            if (player == null || player.netServerHandler == null || player.netServerHandler.networkManager == null) {
                continue;
            }

            NetworkManager nm = player.netServerHandler.networkManager;
            int high = nm.getHighPriorityQueueSize();
            int low = nm.getLowPriorityQueueSize();
            int queued = player.netServerHandler.getQueuedPacketCount();

            if (high > pressure.highQueueMax) {
                pressure.highQueueMax = high;
            }
            if (low > pressure.lowQueueMax) {
                pressure.lowQueueMax = low;
            }
            if (queued > pressure.playerQueuedMax) {
                pressure.playerQueuedMax = queued;
            }
        }
        return pressure;
    }

    private boolean isPressureThrottleCandidate(Entity entity) {
        if (entity instanceof EntityPlayer) {
            return false;
        }
        return entity instanceof EntityLiving || entity instanceof EntityBoat || entity instanceof EntityMinecart;
    }

    private TrackingDistanceBand getDistanceBand(Entity entity, List players) {
        if (players == null || players.isEmpty()) {
            return TrackingDistanceBand.FAR;
        }

        int entityChunkX = entity.bH;
        int entityChunkZ = entity.bJ;
        int nearest = Integer.MAX_VALUE;
        for (int i = 0; i < players.size(); i++) {
            EntityPlayer player = (EntityPlayer) players.get(i);
            if (player == null) {
                continue;
            }
            int distance = Math.max(Math.abs(player.bH - entityChunkX), Math.abs(player.bJ - entityChunkZ));
            if (distance < nearest) {
                nearest = distance;
                if (nearest <= this.nearChunkRadius) {
                    return TrackingDistanceBand.NEAR;
                }
            }
        }

        if (nearest <= this.midChunkRadius) {
            return TrackingDistanceBand.MID;
        }
        return TrackingDistanceBand.FAR;
    }

    private TrackingDistanceBand getSkipBand(EntityTrackerEntry entry, TrackingPressureState state, List players) {
        if (state != TrackingPressureState.PRESSURE) {
            return null;
        }

        Entity entity = entry.tracker;
        if (!isPressureThrottleCandidate(entity)) {
            return null;
        }

        TrackingDistanceBand band = getDistanceBand(entity, players);
        if (band == TrackingDistanceBand.NEAR) {
            return null;
        }

        int tick = this.c.ticks + entity.id;
        if (band == TrackingDistanceBand.MID) {
            // Mild staggering in pressure keeps mid-distance movement smoother than far entities.
            if (tick % 3 == 0) {
                return TrackingDistanceBand.MID;
            }
            return null;
        }

        if ((tick & 1) != 0) {
            return TrackingDistanceBand.FAR;
        }
        return null;
    }

    public void a(Entity entity, int i, int j) {
        this.a(entity, i, j, false);
    }

    // CraftBukkit - synchronized
    public synchronized void a(Entity entity, int i, int j, boolean flag) {
        if (i > this.d) {
            i = this.d;
        }

        if (this.b.b(entity.id)) {
            // CraftBukkit - removed exception throw as tracking an already tracked entity theoretically shouldn't cause any issues.
            // throw new IllegalStateException("Entity is already tracked!");
        } else {
            EntityTrackerEntry entitytrackerentry = new EntityTrackerEntry(entity, i, j, flag);

            this.a.add(entitytrackerentry);
            this.b.a(entity.id, entitytrackerentry);
            this.updateChunkIndex(entitytrackerentry);
            entitytrackerentry.scanPlayers(this.c.getWorldServer(this.e).players);
        }
    }

    // CraftBukkit - synchronized
    public synchronized void untrackEntity(Entity entity) {
        if (entity instanceof EntityPlayer) {
            EntityPlayer entityplayer = (EntityPlayer) entity;
            Iterator iterator = this.a.iterator();

            while (iterator.hasNext()) {
                EntityTrackerEntry entitytrackerentry = (EntityTrackerEntry) iterator.next();

                entitytrackerentry.a(entityplayer);
            }
        }

        EntityTrackerEntry entitytrackerentry1 = (EntityTrackerEntry) this.b.d(entity.id);

        if (entitytrackerentry1 != null) {
            this.a.remove(entitytrackerentry1);
            this.removeFromChunkIndex(entitytrackerentry1);
            entitytrackerentry1.a();
        }
    }

    // CraftBukkit - synchronized
    public synchronized void updatePlayers() {
        ArrayList arraylist = this.movedPlayerTrackers;
        arraylist.clear();
        Iterator iterator = this.a.iterator();
        List players = this.c.getWorldServer(this.e).players;
        TrackingPressureState state = evaluateTrackingPressureState();
        int skippedNear = 0;
        int skippedMid = 0;
        int skippedFar = 0;

        while (iterator.hasNext()) {
            EntityTrackerEntry entitytrackerentry = (EntityTrackerEntry) iterator.next();

            this.updateChunkIndex(entitytrackerentry);
            TrackingDistanceBand skipBand = getSkipBand(entitytrackerentry, state, players);
            if (skipBand != null) {
                if (skipBand == TrackingDistanceBand.NEAR) {
                    skippedNear++;
                } else if (skipBand == TrackingDistanceBand.MID) {
                    skippedMid++;
                } else {
                    skippedFar++;
                }
                continue;
            }

            entitytrackerentry.track(players);
            if (entitytrackerentry.m && entitytrackerentry.tracker instanceof EntityPlayer) {
                arraylist.add((EntityPlayer) entitytrackerentry.tracker);
            }
        }

        this.skippedNearThisTick = skippedNear;
        this.skippedMidThisTick = skippedMid;
        this.skippedFarThisTick = skippedFar;

        for (int i = 0; i < arraylist.size(); ++i) {
            EntityPlayer entityplayer = (EntityPlayer) arraylist.get(i);
            Iterator iterator1 = this.a.iterator();

            while (iterator1.hasNext()) {
                EntityTrackerEntry entitytrackerentry1 = (EntityTrackerEntry) iterator1.next();

                if (entitytrackerentry1.tracker != entityplayer) {
                    entitytrackerentry1.b(entityplayer);
                }
            }
        }

        arraylist.clear();
    }

    public synchronized TrackingPressureState getTrackingPressureState() {
        return this.trackingPressureState;
    }

    public synchronized String getTrackingPressureStateName() {
        return this.trackingPressureState.name();
    }

    public synchronized long getTrackingStateLastTransitionMillis() {
        return this.trackingStateLastTransitionMillis;
    }

    public synchronized int getNearChunkRadius() {
        return this.nearChunkRadius;
    }

    public synchronized int consumeSkippedNearCount() {
        int value = this.skippedNearThisTick;
        this.skippedNearThisTick = 0;
        return value;
    }

    public synchronized int consumeSkippedMidCount() {
        int value = this.skippedMidThisTick;
        this.skippedMidThisTick = 0;
        return value;
    }

    public synchronized int consumeSkippedFarCount() {
        int value = this.skippedFarThisTick;
        this.skippedFarThisTick = 0;
        return value;
    }

    // CraftBukkit - synchronized
    public synchronized void a(Entity entity, Packet packet) {
        EntityTrackerEntry entitytrackerentry = (EntityTrackerEntry) this.b.a(entity.id);

        if (entitytrackerentry != null) {
            entitytrackerentry.a(packet);
        }
    }

    // CraftBukkit - synchronized
    public synchronized void sendPacketToEntity(Entity entity, Packet packet) {
        EntityTrackerEntry entitytrackerentry = (EntityTrackerEntry) this.b.a(entity.id);

        if (entitytrackerentry != null) {
            entitytrackerentry.b(packet);
        }
    }

    /**
     * Re-evaluates both sides of the 1.22 spectator visibility contract:
     * normal players stop tracking a spectator immediately, while a spectator
     * observer can track other spectators as floating heads.
     */
    public synchronized void refreshPlayerVisibility(EntityPlayer player) {
        if (player == null) {
            return;
        }

        WorldServer worldServer = this.c.getWorldServer(this.e);
        List players = worldServer == null ? null : worldServer.players;
        EntityTrackerEntry playerEntry = (EntityTrackerEntry)this.b.a(player.id);
        if (playerEntry != null && players != null) {
            playerEntry.scanPlayers(players);
        }

        Iterator iterator = this.a.iterator();
        while (iterator.hasNext()) {
            EntityTrackerEntry entry = (EntityTrackerEntry)iterator.next();
            if (entry.tracker != player) {
                entry.b(player);
            }
        }
    }

    // CraftBukkit - synchronized
    public synchronized void untrackPlayer(EntityPlayer entityplayer) {
        Iterator iterator = this.a.iterator();

        while (iterator.hasNext()) {
            EntityTrackerEntry entitytrackerentry = (EntityTrackerEntry) iterator.next();

            entitytrackerentry.c(entityplayer);
        }
    }

    // Poseidon
    // CraftBukkit - synchronized
    public synchronized void a(EntityPlayer entityplayer, Chunk chunk) {
        Set entries = (Set) this.trackedEntriesByChunk.get(this.chunkKey(chunk.x, chunk.z));

        if (entries == null || entries.isEmpty()) {
            return;
        }

        Iterator iterator = entries.iterator();

        while (iterator.hasNext()) {
            EntityTrackerEntry entitytrackerentry = (EntityTrackerEntry) iterator.next();

            if (entitytrackerentry.tracker != entityplayer && entitytrackerentry.tracker.bH == chunk.x && entitytrackerentry.tracker.bJ == chunk.z) {
                entitytrackerentry.b(entityplayer);
            }
        }
    }

    private static class QueuePressureSnapshot {
        private int highQueueMax;
        private int lowQueueMax;
        private int playerQueuedMax;
    }
}
