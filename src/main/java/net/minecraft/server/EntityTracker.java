package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class EntityTracker {

    private Set a = new HashSet();
    public EntityList b = new EntityList(); //Project Poseidon: private -> public
    private MinecraftServer c;
    private int d;
    private int e;

    // Cached config values for entity tracking - uberbukkit smooth entity tracking
    private int mobUpdateFrequency;
    private int playerUpdateFrequency;
    private int vehicleUpdateFrequency;
    private int projectileUpdateFrequency;
    private int itemUpdateFrequency;

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
            this.a(entity, 80, vehicleUpdateFrequency, true); // uberbukkit - smoother vehicles
        } else if (entity instanceof EntityBoat) {
            this.a(entity, 80, vehicleUpdateFrequency, true); // uberbukkit - smoother vehicles
        } else if (entity instanceof EntitySquid) {
            this.a(entity, 64, mobUpdateFrequency, true); // uberbukkit - smoother mobs
        } else if (entity instanceof EntitySnowman) {
            this.a(entity, 160, mobUpdateFrequency, true); // uberbukkit - smoother mobs, added velocity
        } else if (entity instanceof EntityHerobrine) {
            this.a(entity, 160, mobUpdateFrequency, true); // Herobrine - track like other mobs
        } else if (entity instanceof IAnimal) {
            this.a(entity, 80, mobUpdateFrequency, true); // uberbukkit - smoother mobs
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
            entitytrackerentry1.a();
        }
    }

    // CraftBukkit - synchronized
    public synchronized void updatePlayers() {
        ArrayList arraylist = new ArrayList();
        Iterator iterator = this.a.iterator();

        while (iterator.hasNext()) {
            EntityTrackerEntry entitytrackerentry = (EntityTrackerEntry) iterator.next();

            entitytrackerentry.track(this.c.getWorldServer(this.e).players);
            if (entitytrackerentry.m && entitytrackerentry.tracker instanceof EntityPlayer) {
                arraylist.add((EntityPlayer) entitytrackerentry.tracker);
            }
        }

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
        Iterator iterator = this.a.iterator();

        while (iterator.hasNext()) {
            EntityTrackerEntry entitytrackerentry = (EntityTrackerEntry) iterator.next();

            if (entitytrackerentry.tracker != entityplayer && entitytrackerentry.tracker.bH == chunk.x && entitytrackerentry.tracker.bJ == chunk.z) {
                entitytrackerentry.b(entityplayer);
            }
        }
    }
}
