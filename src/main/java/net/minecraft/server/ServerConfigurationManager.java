package net.minecraft.server;

import net.minecraft.server.event.EventBus;

import com.legacyminecraft.poseidon.Poseidon;
import com.legacyminecraft.poseidon.PoseidonConfig;

import uk.betacraft.uberbukkit.packet.Packet62Sound;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.command.ColouredConsoleSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

public class ServerConfigurationManager {

    public static Logger a = Logger.getLogger("Minecraft");
    private static final String MCOSE_LAN_OWNER_HANDOFF_FILE = "mcose_lan_owner.dat";
    private static final String MCOSE_LAN_OWNER_RESULT_FILE = "mcose_lan_owner_result.dat";
    private static final int PLAYER_AUTOSAVE_INTERVAL_TICKS = 6000;
    private static final int PLAYER_AUTOSAVE_MAX_PER_TICK = 2;
    public List players = new ArrayList();
    private final Map playersByName = new HashMap();
    private final Map playersByUUID = new HashMap();
    public MinecraftServer server; // CraftBukkit - private -> public
    // private PlayerManager[] d = new PlayerManager[2]; // CraftBukkit - removed
    public int maxPlayers; // CraftBukkit - private -> public
    public Set banByName = new HashSet(); // CraftBukkit - private -> public
    public Set banByIP = new HashSet(); // CraftBukkit - private -> public
    public Map<String, String> banReasons = new HashMap<String, String>(); // MCOSE: Ban reasons by name (lowercase)
    public Map<String, String> banIPReasons = new HashMap<String, String>(); // MCOSE: Ban reasons by IP
    private Set h = new HashSet();
    private Set i = new HashSet();
    private File j;
    private File k;
    private File l;
    private File m;
    public PlayerFileData playerFileData; // CraftBukkit - private - >public
    public boolean o; // Craftbukkit - private -> public
    private String localLanOwnerName = "";
    private int nextPlayerAutoSaveIndex = 0;
    private Map playerLastSaveTicks = new HashMap();

    // CraftBukkit start
    private CraftServer cserver;
    private final String msgKickBanned, msgKickIPBanned, msgKickWhitelist, msgKickServerFull, msgPlayerJoin, msgPlayerLeave;

    public ServerConfigurationManager(MinecraftServer minecraftserver) {
        minecraftserver.server = new CraftServer(minecraftserver, this);
        minecraftserver.console = new ColouredConsoleSender(minecraftserver.server);
        this.cserver = minecraftserver.server;
        // CraftBukkit end
        this.msgKickBanned = PoseidonConfig.getInstance().getConfigString("message.kick.banned");
        this.msgKickIPBanned = PoseidonConfig.getInstance().getConfigString("message.kick.ip-banned");
        this.msgKickWhitelist = PoseidonConfig.getInstance().getConfigString("message.kick.not-whitelisted");
        this.msgKickServerFull = PoseidonConfig.getInstance().getConfigString("message.kick.full");
        this.msgPlayerJoin = PoseidonConfig.getInstance().getConfigString("message.player.join");
        this.msgPlayerLeave = PoseidonConfig.getInstance().getConfigString("message.player.leave");

        this.server = minecraftserver;
        this.j = minecraftserver.a("banned-players.txt");
        this.k = minecraftserver.a("banned-ips.txt");
        this.l = minecraftserver.a("ops.txt");
        this.m = minecraftserver.a("white-list.txt");
        int i = minecraftserver.propertyManager.getInt("view-distance", 10);

        // CraftBukkit - removed playermanagers
        this.maxPlayers = minecraftserver.propertyManager.getInt("max-players", 20);
        this.o = minecraftserver.propertyManager.getBoolean("white-list", false);
        this.g();
        this.i();
        this.k();
        this.m();
        this.h();
        this.j();
        this.l();
        this.n();
    }

    public void setPlayerFileData(WorldServer[] aworldserver) {
        if (this.playerFileData != null) return; // CraftBukkit
        this.playerFileData = aworldserver[0].p().d();
    }

    public void a(EntityPlayer entityplayer) {
        // CraftBukkit - removed playermanagers
        for (WorldServer world : this.server.worlds) {
            if (world.manager.managedPlayers.contains(entityplayer)) {
                world.manager.removePlayer(entityplayer);
                break;
            }
        }
        this.getPlayerManager(entityplayer.dimension).addPlayer(entityplayer);
        WorldServer worldserver = this.server.getWorldServer(entityplayer.dimension);

        worldserver.chunkProviderServer.getChunkAt((int) entityplayer.locX >> 4, (int) entityplayer.locZ >> 4);
    }

    public int a() {
        // CraftBukkit start
        if (this.server.worlds.size() == 0) {
            return this.server.propertyManager.getInt("view-distance", 10) * 16 - 16;
        }
        return this.server.worlds.get(0).manager.getFurthestViewableBlock();
        // CraftBukkit end
    }

    private PlayerManager getPlayerManager(int i) {
        return this.server.getWorldServer(i).manager; // CraftBukkit
    }

    private static String getPlayerNameKey(String name) {
        return name == null ? null : name.toLowerCase(Locale.ROOT);
    }

    private void registerOnlinePlayer(EntityPlayer entityplayer) {
        if (entityplayer == null) {
            return;
        }

        String nameKey = getPlayerNameKey(entityplayer.name);
        if (nameKey != null) {
            this.playersByName.put(nameKey, entityplayer);
        }

        UUID uuid = entityplayer.getMojangUUID();
        if (uuid != null) {
            this.playersByUUID.put(uuid, entityplayer);
        }
    }

    private void unregisterOnlinePlayer(EntityPlayer entityplayer) {
        if (entityplayer == null) {
            return;
        }

        String nameKey = getPlayerNameKey(entityplayer.name);
        if (nameKey != null && this.playersByName.get(nameKey) == entityplayer) {
            this.playersByName.remove(nameKey);
        }

        UUID uuid = entityplayer.getMojangUUID();
        if (uuid != null && this.playersByUUID.get(uuid) == entityplayer) {
            this.playersByUUID.remove(uuid);
        }
    }

    public EntityPlayer getPlayerByExactName(String name) {
        String nameKey = getPlayerNameKey(name);
        return nameKey == null ? null : (EntityPlayer) this.playersByName.get(nameKey);
    }

    public EntityPlayer getPlayerByUUID(UUID uuid) {
        return uuid == null ? null : (EntityPlayer) this.playersByUUID.get(uuid);
    }

    public void b(EntityPlayer entityplayer) {
        // Check if player data file exists before loading (to detect new players)
        boolean isNewPlayer = !this.playerHasData(entityplayer.name);
        boolean isOperator = this.isListedOp(entityplayer.name);
        
        this.playerFileData.b(entityplayer);
        this.applyLocalLanOwnerSnapshot(entityplayer);
        this.syncLoadedPlayerWorld(entityplayer);
        this.movePlayerToCurrentColumnSurface(this.server.getWorldServer(entityplayer.dimension), entityplayer);
        
        // Apply default gamemode from server.properties for new players only
        if (isNewPlayer && this.server.defaultGameMode != 0) {
            entityplayer.setGameMode(this.server.defaultGameMode);
            if (this.server.defaultGameMode == 2) {
                a.info("[Hardcore] New player " + entityplayer.name + " will be in hardcore mode");
            }
        }
        
        // MCOSE - Handle server gamemode changes
        // If server is no longer hardcore but player was in hardcore mode, switch them to survival
        if (this.server.defaultGameMode != 2 && entityplayer.gameMode == 2) {
            a.info("[GameMode] Player " + entityplayer.name + " was in hardcore but server is now " + 
                   GameType.byId(this.server.defaultGameMode).getName() + " - switching player to match");
            entityplayer.setGameMode(this.server.defaultGameMode);
            // Reset death state so they can play normally
            if (entityplayer.health <= 0) {
                entityplayer.health = 20;
            }
            entityplayer.dead = false;
            entityplayer.deathTicks = 0;
        }

        // UberBukkit - On relog, keep non-OP players on server default gamemode.
        if (!isNewPlayer
                && !isOperator
                && entityplayer.gameMode != this.server.defaultGameMode
                && !shouldPreserveHardcoreSpectator(this.server.defaultGameMode, entityplayer.gameMode)) {
            a.info("[GameMode] Non-op player " + entityplayer.name + " relogged in " +
                   GameType.byId(entityplayer.gameMode).getName() +
                   " - resetting to server default " +
                   GameType.byId(this.server.defaultGameMode).getName());
            entityplayer.setGameMode(this.server.defaultGameMode);
        }
        
        // A player who disconnected before pressing "Spectate World" still
        // resumes as a spectator when their dead Hardcore save is loaded.
        if (entityplayer.gameMode == 2 && entityplayer.health <= 0) {
            a.info("[Hardcore] Restoring dead player " + entityplayer.name + " in spectator mode");
            entityplayer.setGameMode(GameType.SPECTATOR.getId());
            entityplayer.health = 20;
            entityplayer.dead = false;
            entityplayer.deathTicks = 0;
        }
        
        // MCOSE - Ensure players with dead=true but health>0 are reset
        // This catches edge cases where dead flag wasn't properly cleared
        if (entityplayer.dead && entityplayer.health > 0) {
            a.info("[Fix] Player " + entityplayer.name + " had dead=true but health=" + entityplayer.health + " - resetting dead flag");
            entityplayer.dead = false;
            entityplayer.deathTicks = 0;
        }
        
        // UberBukkit - Send full baseline player stats snapshot before live deltas.
        if (entityplayer.playerStatistics != null) {
            entityplayer.playerStatistics.sendFullSnapshotToClient();
        }

        // UberBukkit - Sync all unlocked achievements to client
        if (entityplayer.achievementManager != null) {
            entityplayer.achievementManager.syncAllToClient();
        }

        // UberBukkit - Record player join in server-wide statistics
        ServerStatistics.getInstance().recordPlayerJoin(entityplayer.name);
    }

    static boolean shouldPreserveHardcoreSpectator(int defaultGameMode, int loadedGameMode) {
        return defaultGameMode == GameType.HARDCORE.getId()
                && loadedGameMode == GameType.SPECTATOR.getId();
    }

    private void applyLocalLanOwnerSnapshot(EntityPlayer entityplayer) {
        if (entityplayer == null || this.server == null) {
            return;
        }

        File handoffFile = this.server.a(MCOSE_LAN_OWNER_HANDOFF_FILE);
        if (handoffFile == null || !handoffFile.isFile()) {
            return;
        }

        FileInputStream input = null;
        try {
            input = new FileInputStream(handoffFile);
            NBTTagCompound ownerTag = CompressedStreamTools.a((InputStream) input);
            String ownerName = ownerTag.hasKey("MCOSEOwnerName") ? ownerTag.getString("MCOSEOwnerName") : "";
            if (ownerName.length() > 0 && !ownerName.equalsIgnoreCase(entityplayer.name)) {
                return;
            }

            entityplayer.e(ownerTag);
            this.syncLoadedPlayerWorld(entityplayer);
            this.movePlayerToCurrentColumnSurface(this.server.getWorldServer(entityplayer.dimension), entityplayer);
            this.localLanOwnerName = entityplayer.name;
            this.saveLocalLanOwnerSnapshot(entityplayer);
            a.info("[MCOSE LAN] Applied exact singleplayer owner snapshot for " + entityplayer.name
                + " at (" + entityplayer.locX + ", " + entityplayer.locY + ", " + entityplayer.locZ
                + ") dimension " + entityplayer.dimension);

            input.close();
            input = null;
            if (!handoffFile.delete()) {
                File appliedFile = new File(handoffFile.getParentFile(), MCOSE_LAN_OWNER_HANDOFF_FILE + ".applied");
                if (appliedFile.exists()) {
                    appliedFile.delete();
                }
                handoffFile.renameTo(appliedFile);
            }
        } catch (Exception exception) {
            a.warning("[MCOSE LAN] Failed to apply exact singleplayer owner snapshot for " + entityplayer.name + ": " + exception.getMessage());
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public void savePlayerData(EntityHuman entityhuman) {
        if (entityhuman == null) {
            return;
        }

        if (this.isLocalLanOwner(entityhuman)) {
            this.saveLocalLanOwnerSnapshot(entityhuman);
            this.markPlayerSaved(entityhuman);
            return;
        }

        this.playerFileData.a(entityhuman);
        this.markPlayerSaved(entityhuman);
    }

    private String getPlayerSaveKey(EntityHuman entityhuman) {
        return entityhuman != null && entityhuman.name != null ? entityhuman.name.toLowerCase() : null;
    }

    private void markPlayerSaved(EntityHuman entityhuman) {
        String key = this.getPlayerSaveKey(entityhuman);

        if (key != null) {
            this.playerLastSaveTicks.put(key, Integer.valueOf(this.server.ticks));
        }
    }

    private boolean isLocalLanOwner(EntityHuman entityhuman) {
        return entityhuman != null
            && this.localLanOwnerName != null
            && this.localLanOwnerName.length() > 0
            && entityhuman.name != null
            && this.localLanOwnerName.equalsIgnoreCase(entityhuman.name);
    }

    private void saveLocalLanOwnerSnapshot(EntityHuman entityhuman) {
        File resultFile = this.server.a(MCOSE_LAN_OWNER_RESULT_FILE);
        if (resultFile == null) {
            return;
        }

        File parent = resultFile.getParentFile();
        File tmpFile = parent == null ? new File(MCOSE_LAN_OWNER_RESULT_FILE + ".tmp") : new File(parent, MCOSE_LAN_OWNER_RESULT_FILE + ".tmp");
        try {
            NBTTagCompound playerTag = new NBTTagCompound();
            entityhuman.d(playerTag);
            playerTag.setString("MCOSEOwnerName", entityhuman.name);
            CompressedStreamTools.a(playerTag, (OutputStream) (new FileOutputStream(tmpFile)));
            if (resultFile.exists()) {
                resultFile.delete();
            }
            if (!tmpFile.renameTo(resultFile)) {
                throw new IOException("Could not promote " + tmpFile.getName());
            }
        } catch (Exception exception) {
            a.warning("[MCOSE LAN] Failed to save local owner snapshot for " + entityhuman.name + ": " + exception.getMessage());
        } finally {
            if (tmpFile.exists()) {
                tmpFile.delete();
            }
        }
    }

    private void syncLoadedPlayerWorld(EntityPlayer entityplayer) {
        if (entityplayer == null || this.server == null) {
            return;
        }

        WorldServer targetWorld = this.server.getWorldServer(entityplayer.dimension);
        if (targetWorld == null) {
            entityplayer.dimension = 0;
            targetWorld = this.server.getWorldServer(0);
        }

        if (targetWorld != null && entityplayer.world != targetWorld) {
            entityplayer.spawnIn(targetWorld);
        }
    }
    
    /**
     * Check if a player has existing save data
     */
    private boolean playerHasData(String playerName) {
        if (this.playerFileData instanceof PlayerNBTManager) {
            PlayerNBTManager nbtManager = (PlayerNBTManager) this.playerFileData;
            NBTTagCompound data = nbtManager.a(playerName);
            return data != null;
        }
        return false;
    }
    
    /**
     * Check if the player's feet or head are inside solid blocks
     */
    private boolean isPlayerInsideSolidBlock(WorldServer world, EntityPlayer player) {
        if (world == null || player == null) {
            return false;
        }

        AxisAlignedBB bb = player.boundingBox.shrink(0.0010D, 0.0010D, 0.0010D);
        int minX = MathHelper.floor(bb.a);
        int maxX = MathHelper.floor(bb.d - 1.0E-7D);
        int minY = MathHelper.floor(bb.b);
        int maxY = MathHelper.floor(bb.e - 1.0E-7D);
        int minZ = MathHelper.floor(bb.c);
        int maxZ = MathHelper.floor(bb.f - 1.0E-7D);

        for (int x = minX; x <= maxX; ++x) {
            for (int y = minY; y <= maxY; ++y) {
                for (int z = minZ; z <= maxZ; ++z) {
                    int blockId = world.getTypeId(x, y, z);
                    if (blockId <= 0 || blockId >= Block.byId.length) {
                        continue;
                    }

                    Block block = Block.byId[blockId];
                    if (block == null || block.material == null || !block.material.isSolid() || block.material.isLiquid()) {
                        continue;
                    }

                    AxisAlignedBB blockBox = block.e(world, x, y, z);
                    if (blockBox == null || blockBox.a(bb)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void nudgePlayerUpUntilClear(WorldServer world, EntityPlayer player, int maxAttempts) {
        if (world == null || player == null) {
            return;
        }

        int attempts = 0;
        while (attempts < maxAttempts && world.getEntities(player, player.boundingBox).size() != 0) {
            player.setPosition(player.locX, player.locY + 1.0D, player.locZ);
            attempts++;
        }

        attempts = 0;
        while (attempts < maxAttempts && this.isPlayerInsideSolidBlock(world, player)) {
            player.setPosition(player.locX, player.locY + 1.0D, player.locZ);
            attempts++;
        }
    }

    private void movePlayerToCurrentColumnSurface(WorldServer world, EntityPlayer player) {
        if (world == null || player == null) {
            return;
        }

        int originalX = MathHelper.floor(player.locX);
        int originalZ = MathHelper.floor(player.locZ);
        world.chunkProviderServer.getChunkAt(originalX >> 4, originalZ >> 4);
        player.setPosition(player.locX, player.locY, player.locZ);
        if (world.getEntities(player, player.boundingBox).size() == 0 && !this.isPlayerInsideSolidBlock(world, player)) {
            return;
        }

        int originalY = MathHelper.floor(player.boundingBox.b + 0.001D);
        ChunkCoordinates safe = world.findSafePlayerSpawnAtColumn(originalX, originalY, originalZ);
        double standingEyeHeight = player.height < 1.0F ? 1.62D : (double) player.height;
        if (safe != null) {
            double targetX = safe.x == originalX ? player.locX : (double) safe.x + 0.5D;
            double targetZ = safe.z == originalZ ? player.locZ : (double) safe.z + 0.5D;
            player.setPosition(targetX, (double) safe.y + standingEyeHeight + 0.01D, targetZ);
        }

        this.nudgePlayerUpUntilClear(world, player, 128);
        player.motX = 0.0D;
        player.motY = 0.0D;
        player.motZ = 0.0D;
        player.fallDistance = 0.0F;
    }

    private void enforceSafeSpawnPosition(WorldServer world, EntityPlayer player) {
        if (world == null || player == null) {
            return;
        }

        this.nudgePlayerUpUntilClear(world, player, 24);

        if (this.isPlayerInsideSolidBlock(world, player) || !this.isStandingOnSolidGround(world, player)) {
            this.movePlayerToCurrentColumnSurface(world, player);
            this.nudgePlayerUpUntilClear(world, player, 12);
        }

        player.motX = 0.0D;
        player.motY = 0.0D;
        player.motZ = 0.0D;
        player.fallDistance = 0.0F;
    }

    public void c(EntityPlayer entityplayer) {
        this.players.add(entityplayer);
        this.registerOnlinePlayer(entityplayer);
        this.markPlayerSaved(entityplayer);
        //PlayerTracker.getInstance().addPlayer(entityplayer.name);
        WorldServer worldserver = this.server.getWorldServer(entityplayer.dimension);

        worldserver.chunkProviderServer.getChunkAt((int) entityplayer.locX >> 4, (int) entityplayer.locZ >> 4);
        // Always enforce spawn safety for all game modes (survival/creative/hardcore).
        this.movePlayerToCurrentColumnSurface(worldserver, entityplayer);

        // CraftBukkit start
        Player player = this.cserver.getPlayer(entityplayer);
        PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(player, msgPlayerJoin.replace("%player%", entityplayer.name));
        this.cserver.getPluginManager().callEvent(playerJoinEvent);
        EventBus.global().publish(new net.minecraft.server.event.events.PlayerJoinEvent(entityplayer, worldserver));

        String joinMessage = playerJoinEvent.getJoinMessage();

        if (joinMessage != null) {
            this.server.serverConfigurationManager.sendAll(new Packet3Chat(joinMessage));
        }
        // CraftBukkit end

        // Broadcast player join for Tab overlay
        try {
            int initialPing = (entityplayer.netServerHandler != null) ? entityplayer.netServerHandler.b() : 0;
            Packet201PlayerInfo joinInfo = new Packet201PlayerInfo(entityplayer.name, true, initialPing);
            this.sendAll(joinInfo);
        } catch (Throwable ignore) {}
        
        // Notify friends verification handler of player join
        if (this.server.friendsVerificationHandler != null) {
            this.server.friendsVerificationHandler.onPlayerJoin(entityplayer);
        }

        // Poseidon Start
        // Notify staff of Poseidon update if they are op or have poseidon.update permission
        if (PoseidonConfig.getInstance().getConfigBoolean("settings.update-checker.notify-staff.enabled", true) && Poseidon.getServer().isUpdateAvailable()) {
            if (player.isOp() || player.hasPermission("poseidon.update")) {
                String updateMessage = PoseidonConfig.getInstance().getConfigString("message.update.available");
                updateMessage = updateMessage.replace("%newversion%", Poseidon.getServer().getNewestVersion());
                updateMessage = updateMessage.replace("%currentversion%", Poseidon.getServer().getReleaseVersion());
                player.sendMessage(updateMessage);
            }
        }
        // Poseidon End

        // Plugins can alter position during join events; re-validate before inserting into world.
        this.enforceSafeSpawnPosition(worldserver, entityplayer);
        worldserver.addEntity(entityplayer);
        this.getPlayerManager(entityplayer.dimension).addPlayer(entityplayer);
        this.sendOperatorStatus(entityplayer);

        // Apply vanish state for the joiner against already-vanished players
        try {
            VanishAPI.applyForJoiner(player);
        } catch (Throwable ignore) {}
    }

    public void d(EntityPlayer entityplayer) {
        this.getPlayerManager(entityplayer.dimension).movePlayer(entityplayer);
    }

    public String disconnect(EntityPlayer entityplayer) { // CraftBukkit - changed return type
        //if(entityplayer.netServerHandler.disconnected) return null; // CraftBukkit - exploits fix https://github.com/OvercastNetwork/CraftBukkit/commit/6f79ca5c54d30d04803143975757713a01bf4e35


        // CraftBukkit start
        // Quitting must be before we do final save of data, in case plugins need to modify it
        this.getPlayerManager(entityplayer.dimension).removePlayer(entityplayer);
        PlayerQuitEvent playerQuitEvent = new PlayerQuitEvent(this.cserver.getPlayer(entityplayer), this.msgPlayerLeave.replace("%player%", entityplayer.name));
        this.cserver.getPluginManager().callEvent(playerQuitEvent);
        EventBus.global().publish(new net.minecraft.server.event.events.PlayerLeaveEvent(entityplayer, this.server.getWorldServer(entityplayer.dimension)));
        // CraftBukkit end

        this.server.chatRoomManager.removePlayer(entityplayer);
        try {
            VoiceChatUDPServer voiceServer = this.server.getVoiceChatUDPServer();
            if (voiceServer != null && entityplayer != null && entityplayer.getMojangUUID() != null) {
                voiceServer.onPlayerDisconnect(entityplayer.getMojangUUID());
            }
        } catch (Throwable ignored) {}

        //Project POSEIDON Start
        //        boolean found = false;
        //        for (int i = 0; i < this.players.size(); ++i) {
        //            EntityPlayer ep = (EntityPlayer) this.players.get(i);
        //            if (entityplayer.name.equalsIgnoreCase(ep.name)) {
        //                found = true;
        //                break;
        //            }
        //        }
        //        if (!found) {
        //            //return null; - This caused a bug which could block future connections if a quit event occurs before a join event, i think
        //            playerQuitEvent.setQuitMessage(null);
        //        }
        //        PlayerTracker.getInstance().removePlayer(entityplayer.name);
        //Project POSEIDON End

        this.movePlayerToCurrentColumnSurface(this.server.getWorldServer(entityplayer.dimension), entityplayer);
        this.savePlayerData(entityplayer);
        this.server.getWorldServer(entityplayer.dimension).kill(entityplayer);
        this.players.remove(entityplayer);
        this.unregisterOnlinePlayer(entityplayer);
        String saveKey = this.getPlayerSaveKey(entityplayer);
        if (saveKey != null) {
            this.playerLastSaveTicks.remove(saveKey);
        }
        this.getPlayerManager(entityplayer.dimension).removePlayer(entityplayer);

        // Notify friends verification handler of player leave
        if (this.server.friendsVerificationHandler != null) {
            this.server.friendsVerificationHandler.onPlayerLeave(entityplayer);
        }

        // Broadcast player leave for Tab overlay
        try {
            Packet201PlayerInfo leaveInfo = new Packet201PlayerInfo(entityplayer.name, false, 0);
            this.sendAll(leaveInfo);
        } catch (Throwable ignore) {}

        return playerQuitEvent.getQuitMessage(); // CraftBukkit
    }

    public EntityPlayer a(NetLoginHandler netloginhandler, String s) {
        // CraftBukkit start - note: this entire method needs to be changed
        // Instead of kicking then returning, we need to store the kick reason
        // in the event, check with plugins to see if it's ok, and THEN kick
        // depending on the outcome. Also change any reference to this.e.c to entity.world
        EntityPlayer entity = new EntityPlayer(this.server, this.server.getWorldServer(0), s, new ItemInWorldManager(this.server.getWorldServer(0)), netloginhandler.networkManager.pvn);
        Player player = (entity == null) ? null : (Player) entity.getBukkitEntity();
        PlayerLoginEvent event = new PlayerLoginEvent(player, netloginhandler); //Project Poseidon - pass player IP through

        String s1 = netloginhandler.networkManager.getSocketAddress().toString();

        s1 = s1.substring(s1.indexOf("/") + 1);
        s1 = s1.substring(0, s1.indexOf(":"));

        PlayerLoginEvent.Result result = this.banByName.contains(s.trim().toLowerCase()) ? PlayerLoginEvent.Result.KICK_BANNED : this.banByIP.contains(s1) ? PlayerLoginEvent.Result.KICK_BANNED_IP : !this.isWhitelisted(s) ? PlayerLoginEvent.Result.KICK_WHITELIST : this.players.size() >= this.maxPlayers ? PlayerLoginEvent.Result.KICK_FULL : PlayerLoginEvent.Result.ALLOWED;

        // MCOSE: Build kick message with ban reason if available
        String kickMessage;
        if (result.equals(PlayerLoginEvent.Result.KICK_BANNED)) {
            String banReason = this.getBanReason(s.trim());
            if (banReason != null && !banReason.isEmpty()) {
                kickMessage = this.msgKickBanned + "\n\u00A7cReason: " + banReason;
            } else {
                kickMessage = this.msgKickBanned;
            }
        } else if (result.equals(PlayerLoginEvent.Result.KICK_BANNED_IP)) {
            String ipReason = this.banIPReasons.get(s1);
            if (ipReason != null && !ipReason.isEmpty()) {
                kickMessage = this.msgKickIPBanned + "\n\u00A7cReason: " + ipReason;
            } else {
                kickMessage = this.msgKickIPBanned;
            }
        } else if (result.equals(PlayerLoginEvent.Result.KICK_WHITELIST)) {
            kickMessage = this.msgKickWhitelist;
        } else if (result.equals(PlayerLoginEvent.Result.KICK_FULL)) {
            kickMessage = this.msgKickServerFull;
        } else {
            kickMessage = s1;
        }

        event.disallow(result, kickMessage);

        this.cserver.getPluginManager().callEvent(event);
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            netloginhandler.disconnect(event.getKickMessage());
            return null;
        }

        EntityPlayer onlinePlayer = this.getPlayerByExactName(s);
        if (onlinePlayer != null) {
            onlinePlayer.netServerHandler.disconnect("You logged in from another location");
        }

        return entity;
        // CraftBukkit end
    }

    // CraftBukkit start
    public EntityPlayer moveToWorld(EntityPlayer entityplayer, int i) {
        return this.moveToWorld(entityplayer, i, null);
    }

    public EntityPlayer moveToWorld(EntityPlayer entityplayer, int i, Location location) {
        boolean explicitLocationProvided = location != null;
        this.server.getTracker(entityplayer.dimension).untrackPlayer(entityplayer);
        // this.server.getTracker(entityplayer.dimension).untrackEntity(entityplayer); // CraftBukkit
        this.getPlayerManager(entityplayer.dimension).removePlayer(entityplayer);
        this.players.remove(entityplayer);
        this.unregisterOnlinePlayer(entityplayer);
        //PlayerTracker.getInstance().removePlayer(entityplayer.name); //Project POSEIDON
        this.server.getWorldServer(entityplayer.dimension).removeEntity(entityplayer);
        ChunkCoordinates chunkcoordinates = entityplayer.getBed();

        // CraftBukkit start
        EntityPlayer entityplayer1 = entityplayer;
        org.bukkit.World fromWorld = entityplayer1.getBukkitEntity().getWorld();
        boolean isBedSpawn = false;

        if (location == null) {
            CraftWorld cworld = (CraftWorld) this.server.server.getWorld(entityplayer.spawnWorld);
            if (cworld != null && chunkcoordinates != null) {
                ChunkCoordinates chunkcoordinates1 = EntityHuman.getBed(cworld.getHandle(), chunkcoordinates);
                if (chunkcoordinates1 != null) {
                    isBedSpawn = true;
                    location = new Location(cworld, chunkcoordinates1.x + 0.5, chunkcoordinates1.y, chunkcoordinates1.z + 0.5);
                } else {
                    entityplayer1.netServerHandler.sendPacket(new Packet70Bed(0));
                }
            }

            if (location == null) {
                cworld = (CraftWorld) this.server.server.getWorlds().get(0);
                chunkcoordinates = cworld.getHandle().getSpawn();
                location = new Location(cworld, chunkcoordinates.x + 0.5, chunkcoordinates.y, chunkcoordinates.z + 0.5);
            }

            Player respawnPlayer = this.cserver.getPlayer(entityplayer);
            PlayerRespawnEvent respawnEvent = new PlayerRespawnEvent(respawnPlayer, location, isBedSpawn);
            this.cserver.getPluginManager().callEvent(respawnEvent);

            location = respawnEvent.getRespawnLocation();
            entityplayer.health = 20;
            entityplayer.fireTicks = 0;
            entityplayer.fallDistance = 0;
        } else {
            location.setWorld(this.server.getWorldServer(i).getWorld());
        }

        // Resolve respawn positions to safe, above-ground coordinates.
        // Only apply this path for respawns (location inferred in this method), not explicit teleports/portals.
        if (!explicitLocationProvided && location != null) {
            CraftWorld craftWorld = (CraftWorld) location.getWorld();
            if (craftWorld != null) {
                WorldServer targetWorld = craftWorld.getHandle();
                int lx = MathHelper.floor(location.getX());
                int ly = MathHelper.floor(location.getY());
                int lz = MathHelper.floor(location.getZ());
                boolean locationIsSafe = targetWorld.isSafePlayerSpawnAt(lx, ly, lz);

                if (!isBedSpawn) {
                    ChunkCoordinates safe = this.resolveRespawnFromWorldSpawn(targetWorld);
                    location = new Location(craftWorld, safe.x + 0.5D, safe.y + 0.01D, safe.z + 0.5D, location.getYaw(), location.getPitch());
                } else if (!locationIsSafe) {
                    ChunkCoordinates safe = targetWorld.findSafeSpawnNear(lx, lz, 24, false);
                    location = new Location(craftWorld, safe.x + 0.5D, safe.y + 0.01D, safe.z + 0.5D, location.getYaw(), location.getPitch());
                }
            }
        }

        WorldServer worldserver = ((CraftWorld) location.getWorld()).getHandle();
        entityplayer1.setLocation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        // CraftBukkit end

        worldserver.chunkProviderServer.getChunkAt((int) entityplayer1.locX >> 4, (int) entityplayer1.locZ >> 4);
        this.enforceSafeSpawnPosition(worldserver, entityplayer1);

        if (this.isSkyTerrainWorld(worldserver)) {
            int safetyAttempts = 0;
            while (safetyAttempts < 8) {
                boolean insideSolid = this.isPlayerInsideSolidBlock(worldserver, entityplayer1);
                boolean grounded = this.isStandingOnSolidGround(worldserver, entityplayer1);

                if (!insideSolid && grounded) {
                    break;
                }

                int x = MathHelper.floor(entityplayer1.locX);
                int z = MathHelper.floor(entityplayer1.locZ);
                int safeY = this.findSkyRespawnY(worldserver, x, z);

                if (safeY > 0) {
                    entityplayer1.setPosition(entityplayer1.locX, (double) safeY + 0.01D, entityplayer1.locZ);
                } else if (insideSolid) {
                    // Last-resort escape upward if the local column cannot currently resolve to a safe sky surface.
                    entityplayer1.setPosition(entityplayer1.locX, entityplayer1.locY + 1.0D, entityplayer1.locZ);
                } else {
                    break;
                }

                ++safetyAttempts;
            }
        }
        this.enforceSafeSpawnPosition(worldserver, entityplayer1);

        // CraftBukkit start
        byte actualDimension = this.getClientDimensionForWorld(worldserver);
        entityplayer1.netServerHandler.sendPacket(new Packet9Respawn((byte) (actualDimension >= 0 ? -1 : 0), worldserver.getSeed()));
        entityplayer1.netServerHandler.sendPacket(new Packet9Respawn(actualDimension, worldserver.getSeed()));
        entityplayer1.spawnIn(worldserver);
        entityplayer1.dead = false;
        GameRuleSync.send(entityplayer1);
        // Keep the selected terrain renderer authoritative across every
        // dimension transition, including returning from the Nether.
        entityplayer1.netServerHandler.sendPacket(new Packet70Bed(
            Packet70Bed.terrainTypeStateReason(this.getClientTerrainTypeForWorld(worldserver))
        ));
        entityplayer1.netServerHandler.teleport(new Location(worldserver.getWorld(), entityplayer1.locX, entityplayer1.locY, entityplayer1.locZ, entityplayer1.yaw, entityplayer1.pitch));
        // CraftBukkit end
        this.a(entityplayer1, worldserver);
        // Notify client to enable/disable special terrain rendering based on overworld terrain type on world change.
        try {
            int terrainType = worldserver.worldData != null ? worldserver.worldData.getTerrainType() : 0;
            boolean skyTerrainType = this.hasSkyTerrainType(worldserver);
            if (worldserver.worldProvider != null && !(worldserver.worldProvider instanceof WorldProviderHell)) {
                if (isAlphaVisualTerrain(terrainType)) {
                    // Mirror login behavior for Alpha: deferred alpha enable before first chunk.
                    entityplayer1.netServerHandler.sendPacket(new Packet70Bed(5));
                    // Explicit ALPHA_SNOW indicator so client uses snowy biomes for precipitation
                    if (terrainType == 5) {
                        entityplayer1.netServerHandler.sendPacket(new Packet70Bed(10));
                    }
                    // And immediate terrain override in case chunks already started
                    entityplayer1.netServerHandler.sendPacket(new Packet70Bed(8));
                } else if (terrainType == 7) {
                    // Mirror deferred path for INFDEV visuals.
                    entityplayer1.netServerHandler.sendPacket(new Packet70Bed(20));
                    entityplayer1.netServerHandler.sendPacket(new Packet70Bed(8));
                } else if (terrainType == 6) {
                    // CLASSIC uses the same renderer path as INFDEV.
                    entityplayer1.netServerHandler.sendPacket(new Packet70Bed(21));
                    entityplayer1.netServerHandler.sendPacket(new Packet70Bed(8));
                } else if (skyTerrainType) {
                    entityplayer1.netServerHandler.sendPacket(new Packet70Bed(6));
                } else {
                    // Explicitly disable terrain override
                    entityplayer1.netServerHandler.sendPacket(new Packet70Bed(9));
                }
            } else if (skyTerrainType) {
                entityplayer1.netServerHandler.sendPacket(new Packet70Bed(6));
            } else {
                // Legacy clients also need to leave the overworld terrain atlas
                // behind when entering the Nether.
                entityplayer1.netServerHandler.sendPacket(new Packet70Bed(9));
            }
        } catch (Throwable ignore) {}
        this.getPlayerManager(entityplayer1.dimension).addPlayer(entityplayer1);
        worldserver.addEntity(entityplayer1);
        this.players.add(entityplayer1);
        this.registerOnlinePlayer(entityplayer1);
        this.sendOperatorStatus(entityplayer1);
        //PlayerTracker.getInstance().addPlayer(entityplayer1.name); //Project POSEIDON
        this.updateClient(entityplayer1); // CraftBukkit
        entityplayer1.x();
        // CraftBukkit start - don't fire on respawn
        if (fromWorld != location.getWorld()) {
            org.bukkit.event.player.PlayerChangedWorldEvent event = new org.bukkit.event.player.PlayerChangedWorldEvent((Player) entityplayer1.getBukkitEntity(), fromWorld);
            Bukkit.getServer().getPluginManager().callEvent(event);
        }
        // CraftBukkit end
        return entityplayer1;
    }

    public void f(EntityPlayer entityplayer) {
        // CraftBukkit start -- Replaced the standard handling of portals with a more customised method.
        int dimension = entityplayer.dimension;
        WorldServer fromWorld = this.server.getWorldServer(dimension);
        WorldServer toWorld = null;
        if (dimension < 10) {
            int toDimension = dimension == -1 ? 0 : -1;
            for (WorldServer world : this.server.worlds) {
                if (world.dimension == toDimension) {
                    toWorld = world;
                }
            }
        }
        double blockRatio = dimension == -1 ? 8 : 0.125;

        Location fromLocation = new Location(fromWorld.getWorld(), entityplayer.locX, entityplayer.locY, entityplayer.locZ, entityplayer.yaw, entityplayer.pitch);
        Location toLocation = toWorld == null ? null : new Location(toWorld.getWorld(), (entityplayer.locX * blockRatio), entityplayer.locY, (entityplayer.locZ * blockRatio), entityplayer.yaw, entityplayer.pitch);

        org.bukkit.craftbukkit.PortalTravelAgent pta = new org.bukkit.craftbukkit.PortalTravelAgent();
        PlayerPortalEvent event = new PlayerPortalEvent((Player) entityplayer.getBukkitEntity(), fromLocation, toLocation, pta);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getTo() == null) {
            return;
        }

        Location finalLocation = event.getTo();
        if (event.useTravelAgent()) {
            finalLocation = event.getPortalTravelAgent().findOrCreate(finalLocation);
        }
        toWorld = ((CraftWorld) finalLocation.getWorld()).getHandle();

        String legacy = net.minecraft.server.registry.SoundEventResolver.resolve("minecraft:block.portal.travel");
        this.sendPacketNearby(entityplayer, finalLocation.getX(), finalLocation.getY(), finalLocation.getZ(), 64D, toWorld.dimension, new Packet62Sound(legacy, finalLocation.getX(), finalLocation.getY(), finalLocation.getZ(), 1.0F, toWorld.random.nextFloat() * 0.4F + 0.8F));

        this.moveToWorld(entityplayer, toWorld.dimension, finalLocation);
        // CraftBukkit end
    }

    public void b() {
        // CraftBukkit start
        for (int i = 0; i < this.server.worlds.size(); ++i) {
            this.server.worlds.get(i).manager.flush();
        }
        // CraftBukkit end
    }

    public void flagDirty(int i, int j, int k, int l) {
        this.getPlayerManager(l).flagDirty(i, j, k);
    }

    public void sendAll(Packet packet) {
        List<EntityPlayer> recipients = this.getOnlinePlayersSnapshot();
        for (int i = 0; i < recipients.size(); ++i) {
            EntityPlayer entityplayer = recipients.get(i);
            entityplayer.netServerHandler.sendPacket(packet);
        }
    }

    public void a(Packet packet, int i) {
        List<EntityPlayer> recipients = this.getPlayersInDimensionSnapshot(i);
        for (int j = 0; j < recipients.size(); ++j) {
            EntityPlayer entityplayer = recipients.get(j);

            entityplayer.netServerHandler.sendPacket(packet);
        }
    }

    public String c() {
        String s = "";
        List<EntityPlayer> recipients = this.getOnlinePlayersSnapshot();

        for (int i = 0; i < recipients.size(); ++i) {
            if (i > 0) {
                s = s + ", ";
            }

            s = s + recipients.get(i).name;
        }

        return s;
    }

    public void a(String s) {
        this.banByName.add(s.toLowerCase());
        this.h();
    }
    
    /**
     * Ban a player with a reason.
     */
    public void banWithReason(String name, String reason) {
        String lowername = name.toLowerCase();
        this.banByName.add(lowername);
        if (reason != null && !reason.isEmpty()) {
            this.banReasons.put(lowername, reason);
        }
        this.h();
    }
    
    /**
     * Get the ban reason for a player, or null if not set.
     */
    public String getBanReason(String name) {
        return this.banReasons.get(name.toLowerCase());
    }

    public void b(String s) {
        this.banByName.remove(s.toLowerCase());
        this.banReasons.remove(s.toLowerCase());
        this.h();
    }

    private void g() {
        try {
            this.banByName.clear();
            BufferedReader bufferedreader = new BufferedReader(new FileReader(this.j));
            String s = "";

            while ((s = bufferedreader.readLine()) != null) {
                this.banByName.add(s.trim().toLowerCase());
            }

            bufferedreader.close();
        } catch (Exception exception) {
            a.warning("Failed to load ban list: " + exception);
        }
    }

    private void h() {
        try {
            PrintWriter printwriter = new PrintWriter(new FileWriter(this.j, false));
            Iterator iterator = this.banByName.iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();

                printwriter.println(s);
            }

            printwriter.close();
        } catch (Exception exception) {
            a.warning("Failed to save ban list: " + exception);
        }
    }

    public void c(String s) {
        this.banByIP.add(s.toLowerCase());
        this.j();
    }

    public void d(String s) {
        this.banByIP.remove(s.toLowerCase());
        this.j();
    }

    private void i() {
        try {
            this.banByIP.clear();
            BufferedReader bufferedreader = new BufferedReader(new FileReader(this.k));
            String s = "";

            while ((s = bufferedreader.readLine()) != null) {
                this.banByIP.add(s.trim().toLowerCase());
            }

            bufferedreader.close();
        } catch (Exception exception) {
            a.warning("Failed to load ip ban list: " + exception);
        }
    }

    private void j() {
        try {
            PrintWriter printwriter = new PrintWriter(new FileWriter(this.k, false));
            Iterator iterator = this.banByIP.iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();

                printwriter.println(s);
            }

            printwriter.close();
        } catch (Exception exception) {
            a.warning("Failed to save ip ban list: " + exception);
        }
    }

    public void e(String s) {
        this.h.add(s.toLowerCase());
        this.l();
        EntityPlayer online = this.i(s);
        if (online != null) {
            this.sendOperatorStatus(online);
        }

        // Craftbukkit start
        Player player = server.server.getPlayer(s);
        if (player != null) {
            player.recalculatePermissions();
        }
        // Craftbukkit end
    }

    public void f(String s) {
        this.h.remove(s.toLowerCase());
        this.l();
        EntityPlayer online = this.i(s);
        if (online != null) {
            this.sendOperatorStatus(online);
        }

        // Craftbukkit start
        Player player = server.server.getPlayer(s);
        if (player != null) {
            player.recalculatePermissions();
        }
        // Craftbukkit end
    }

    private void k() {
        try {
            this.h.clear();
            BufferedReader bufferedreader = new BufferedReader(new FileReader(this.l));
            String s = "";

            while ((s = bufferedreader.readLine()) != null) {
                this.h.add(s.trim().toLowerCase());
            }

            bufferedreader.close();
        } catch (Exception exception) {
            // CraftBukkit - corrected text
            a.warning("Failed to load ops: " + exception);
        }
    }

    private void l() {
        try {
            PrintWriter printwriter = new PrintWriter(new FileWriter(this.l, false));
            Iterator iterator = this.h.iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();

                printwriter.println(s);
            }

            printwriter.close();
        } catch (Exception exception) {
            // CraftBukkit - corrected text
            a.warning("Failed to save ops: " + exception);
        }
    }

    private void m() {
        try {
            this.i.clear();
            BufferedReader bufferedreader = new BufferedReader(new FileReader(this.m));
            String s = "";

            while ((s = bufferedreader.readLine()) != null) {
                this.i.add(s.trim().toLowerCase());
            }

            bufferedreader.close();
        } catch (Exception exception) {
            a.warning("Failed to load white-list: " + exception);
        }
    }

    private void n() {
        try {
            PrintWriter printwriter = new PrintWriter(new FileWriter(this.m, false));
            Iterator iterator = this.i.iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();

                printwriter.println(s);
            }

            printwriter.close();
        } catch (Exception exception) {
            a.warning("Failed to save white-list: " + exception);
        }
    }

    public boolean isWhitelisted(String s) {
        s = s.trim().toLowerCase();
        return !this.o || this.h.contains(s) || this.i.contains(s);
    }

    public boolean isOp(String s) {
        return this.isListedOp(s) || this.server.allowCommandsForAllPlayers;
    }

    private boolean isListedOp(String s) {
        return s != null && this.h.contains(s.trim().toLowerCase());
    }

    private void sendOperatorStatus(EntityPlayer entityplayer) {
        if (entityplayer == null || entityplayer.netServerHandler == null) {
            return;
        }
        boolean isOp = this.isOp(entityplayer.name);
        entityplayer.netServerHandler.sendPacket(new Packet70Bed(isOp ? 15 : 16));
        entityplayer.netServerHandler.sendPacket(new Packet3Chat("[[ADMINOPS:" + (isOp ? "1" : "0") + "]]"));
    }

    public EntityPlayer i(String s) {
        return this.getPlayerByExactName(s);
    }

    public void a(String s, String s1) {
        EntityPlayer entityplayer = this.i(s);

        if (entityplayer != null) {
            entityplayer.netServerHandler.sendPacket(new Packet3Chat(s1));
        }
    }

    public void sendPacketNearby(double d0, double d1, double d2, double d3, int i, Packet packet) {
        this.sendPacketNearby((EntityHuman) null, d0, d1, d2, d3, i, packet);
    }

    public void sendPacketNearbyToScale(EntityHuman entityhuman, double d0, double d1, double d2, double d3, int i, Packet packet) {
        float var10 = 16.0F;
        if (d3 > 1.0F) {
            var10 *= d3;
        }
        this.sendPacketNearby(entityhuman, d0, d1, d2, var10, i, packet);
    }

    public void sendPacketNearby(EntityHuman entityhuman, double d0, double d1, double d2, double d3, int i, Packet packet) {
        List<EntityPlayer> recipients = this.getPlayersInDimensionSnapshot(i);
        double radiusSq = d3 * d3;

        for (int j = 0; j < recipients.size(); ++j) {
            EntityPlayer entityplayer = recipients.get(j);
            if (entityplayer == null || entityplayer == entityhuman) {
                continue;
            }

            double dx = d0 - entityplayer.locX;
            double dy = d1 - entityplayer.locY;
            double dz = d2 - entityplayer.locZ;
            if (dx * dx + dy * dy + dz * dz < radiusSq) {
                entityplayer.netServerHandler.sendPacket(packet);
            }
        }
    }

    public void j(String s) {
        Packet3Chat packet3chat = new Packet3Chat(s);
        List<EntityPlayer> recipients = this.getOnlinePlayersSnapshot();

        for (int i = 0; i < recipients.size(); ++i) {
            EntityPlayer entityplayer = recipients.get(i);

            if (this.isOp(entityplayer.name)) {
                entityplayer.netServerHandler.sendPacket(packet3chat);
            }
        }
    }

    public List<EntityPlayer> getOnlinePlayersSnapshot() {
        return this.copyEntityPlayers(this.players);
    }

    private WorldServer getWorldServerIfLoaded(int dimension) {
        for (int i = 0; i < this.server.worlds.size(); ++i) {
            WorldServer world = (WorldServer) this.server.worlds.get(i);
            if (world != null && world.dimension == dimension) {
                return world;
            }
        }

        return null;
    }

    public List<EntityPlayer> getPlayersInDimensionSnapshot(int dimension) {
        WorldServer world = this.getWorldServerIfLoaded(dimension);

        if (world == null) {
            List<EntityPlayer> online = this.getOnlinePlayersSnapshot();
            List<EntityPlayer> filtered = new ArrayList<EntityPlayer>();

            for (int i = 0; i < online.size(); ++i) {
                EntityPlayer entityplayer = online.get(i);
                if (entityplayer.dimension == dimension) {
                    filtered.add(entityplayer);
                }
            }

            return filtered;
        }

        return this.copyEntityPlayers(world.players);
    }

    private List<EntityPlayer> copyEntityPlayers(List source) {
        Object[] raw = source.toArray();
        List<EntityPlayer> snapshot = new ArrayList<EntityPlayer>(raw.length);
        for (int i = 0; i < raw.length; i++) {
            Object obj = raw[i];
            if (obj instanceof EntityPlayer) {
                snapshot.add((EntityPlayer) obj);
            }
        }
        return snapshot;
    }

    public List<EntityPlayer> getNearbyPlayersSnapshot(EntityHuman excluded,
                                                       double x,
                                                       double y,
                                                       double z,
                                                       double radius,
                                                       int dimension) {
        List<EntityPlayer> online = this.getPlayersInDimensionSnapshot(dimension);
        List<EntityPlayer> nearby = new ArrayList<EntityPlayer>();
        double radiusSq = radius * radius;

        for (int i = 0; i < online.size(); i++) {
            EntityPlayer entityplayer = online.get(i);
            if (entityplayer == null || entityplayer == excluded) {
                continue;
            }
            double dx = x - entityplayer.locX;
            double dy = y - entityplayer.locY;
            double dz = z - entityplayer.locZ;
            if (dx * dx + dy * dy + dz * dz < radiusSq) {
                nearby.add(entityplayer);
            }
        }
        return nearby;
    }

    public boolean a(String s, Packet packet) {
        EntityPlayer entityplayer = this.i(s);

        if (entityplayer != null) {
            entityplayer.netServerHandler.sendPacket(packet);
            return true;
        } else {
            return false;
        }
    }

    public void savePlayers() {
        for (int i = 0; i < this.players.size(); ++i) {
            this.savePlayerData((EntityHuman) this.players.get(i));
        }
    }

    public void savePlayersIncrementally() {
        if (this.playerFileData == null || this.players.isEmpty()) {
            this.nextPlayerAutoSaveIndex = 0;
            return;
        }

        int playerCount = this.players.size();
        int currentTick = this.server.ticks;
        int saved = 0;

        for (int checked = 0; checked < playerCount && saved < PLAYER_AUTOSAVE_MAX_PER_TICK; ++checked) {
            if (this.nextPlayerAutoSaveIndex >= this.players.size()) {
                this.nextPlayerAutoSaveIndex = 0;
            }

            EntityHuman entityhuman = (EntityHuman) this.players.get(this.nextPlayerAutoSaveIndex++);
            String key = this.getPlayerSaveKey(entityhuman);

            if (key == null) {
                continue;
            }

            Integer lastSaveTick = (Integer) this.playerLastSaveTicks.get(key);

            if (lastSaveTick == null || currentTick - lastSaveTick.intValue() >= PLAYER_AUTOSAVE_INTERVAL_TICKS) {
                this.savePlayerData(entityhuman);
                ++saved;
            }
        }
    }

    public void a(int i, int j, int k, TileEntity tileentity) {
    }

    public void k(String s) {
        this.i.add(s);
        this.n();
    }

    public void l(String s) {
        this.i.remove(s);
        this.n();
    }

    public Set e() {
        return this.i;
    }

    public void f() {
        this.m();
    }

    public void a(EntityPlayer entityplayer, WorldServer worldserver) {
        entityplayer.netServerHandler.sendPacket(new Packet4UpdateTime(worldserver.getTime()));
        if (worldserver.v()) {
            entityplayer.netServerHandler.sendPacket(new Packet70Bed(1));
        }
        // Poseidon parity: ensure HUD matches gamemode on world attach
        try {
            entityplayer.updateContainer();
            entityplayer.syncGameModeToClient();
            if (worldserver.worldData != null && worldserver.worldData.getTerrainType() == 3) {
                entityplayer.netServerHandler.sendPacket(new Packet70Bed(2));
            }
        } catch (Throwable ignore) {}
    }

    public void updateClient(EntityPlayer entityplayer) {
        entityplayer.updateInventory(entityplayer.defaultContainer);
        entityplayer.C();
    }

    private static boolean isAlphaVisualTerrain(int terrainType) {
        return terrainType == 1 || terrainType == 5;
    }

    private static boolean isInfdevVisualTerrain(int terrainType) {
        return terrainType == 6 || terrainType == 7;
    }

    private byte getClientDimensionForWorld(WorldServer worldserver) {
        if (worldserver == null || worldserver.worldProvider == null) {
            return 0;
        }

        if (worldserver.worldProvider instanceof WorldProviderHell) {
            return -1;
        }

        try {
            if (worldserver.worldData != null && worldserver.worldData.getTerrainType() == 3) {
                // Present SKY terrain worlds as client dimension 1 for proper sky provider visuals.
                return 1;
            }
        } catch (Throwable ignore) {}

        return (byte) worldserver.worldProvider.dimension;
    }

    private int getClientTerrainTypeForWorld(WorldServer worldserver) {
        if (worldserver == null) {
            return 0;
        }

        try {
            if (worldserver.worldProvider instanceof WorldProviderHell) {
                WorldServer overworld = this.server.getWorldServer(0);
                if (overworld != null && overworld.worldData != null) {
                    return overworld.worldData.getTerrainType();
                }
            }
            if (worldserver.worldData != null) {
                return worldserver.worldData.getTerrainType();
            }
        } catch (Throwable ignore) {}
        return 0;
    }

    private boolean isSkyTerrainWorld(WorldServer worldserver) {
        return worldserver != null
            && worldserver.worldData != null
            && worldserver.worldData.getTerrainType() == 3
            && !(worldserver.worldProvider instanceof WorldProviderHell);
    }

    private boolean hasSkyTerrainType(WorldServer worldserver) {
        if (worldserver == null) {
            return false;
        }
        if (worldserver.worldData != null && worldserver.worldData.getTerrainType() == 3) {
            return true;
        }
        if (worldserver.worldProvider instanceof WorldProviderHell) {
            try {
                WorldServer overworld = this.server.getWorldServer(0);
                return overworld != null && overworld.worldData != null && overworld.worldData.getTerrainType() == 3;
            } catch (Throwable ignore) {}
        }
        return false;
    }

    private boolean isWithinRespawnRadius(ChunkCoordinates center, ChunkCoordinates point, int radius) {
        if (center == null || point == null) {
            return false;
        }
        if (radius <= 0) {
            return point.x == center.x && point.z == center.z;
        }

        long dx = (long) point.x - (long) center.x;
        long dz = (long) point.z - (long) center.z;
        long radiusSq = (long) radius * (long) radius;
        return dx * dx + dz * dz <= radiusSq;
    }

    private ChunkCoordinates resolveRespawnFromWorldSpawn(WorldServer worldserver) {
        if (worldserver == null) {
            return new ChunkCoordinates(0, 64, 0);
        }

        ChunkCoordinates worldSpawn = worldserver.getSpawn();
        int centerX = worldSpawn != null ? worldSpawn.x : 0;
        int centerZ = worldSpawn != null ? worldSpawn.z : 0;
        int radius = worldserver.worldData != null ? worldserver.worldData.getSpawnRadius() : 10;
        if (radius < 0) {
            radius = 0;
        }

        boolean preferShoreline = this.shouldPreferShorelineSpawn(worldserver);

        if (radius == 0) {
            return worldserver.findSafeSpawnNear(centerX, centerZ, 0, preferShoreline);
        }

        int attempts = Math.max(64, radius * 10);
        if (attempts > 4096) {
            attempts = 4096;
        }

        long radiusSq = (long) radius * (long) radius;
        for (int attempt = 0; attempt < attempts; ++attempt) {
            int dx = worldserver.random.nextInt(radius * 2 + 1) - radius;
            int dz = worldserver.random.nextInt(radius * 2 + 1) - radius;
            long distSq = (long) dx * (long) dx + (long) dz * (long) dz;
            if (distSq > radiusSq) {
                continue;
            }

            ChunkCoordinates candidate = worldserver.findSafeSpawnNear(centerX + dx, centerZ + dz, 0, preferShoreline);
            if (this.isWithinRespawnRadius(worldSpawn, candidate, radius)) {
                return candidate;
            }
        }

        ChunkCoordinates fallback = worldserver.findSafeSpawnNear(centerX, centerZ, radius, preferShoreline);
        if (this.isWithinRespawnRadius(worldSpawn, fallback, radius)) {
            return fallback;
        }

        return worldserver.findSafeSpawnNear(centerX, centerZ, 0, preferShoreline);
    }

    private boolean shouldPreferShorelineSpawn(WorldServer worldserver) {
        if (worldserver == null || worldserver.worldData == null) {
            return false;
        }

        int terrainType = worldserver.worldData.getTerrainType();
        return terrainType == 0 || terrainType == 1;
    }

    private boolean isSafeSkyRespawnAt(WorldServer worldserver, int x, int y, int z) {
        if (worldserver == null || y <= 1 || y >= 126) {
            return false;
        }

        int groundId = worldserver.getTypeId(x, y - 1, z);
        if (groundId <= 0 || groundId >= Block.byId.length) {
            return false;
        }

        Block ground = Block.byId[groundId];
        if (ground == null || !ground.material.isSolid() || ground.material.isLiquid()) {
            return false;
        }

        return worldserver.getTypeId(x, y, z) == 0 && worldserver.getTypeId(x, y + 1, z) == 0;
    }

    private int findSkyRespawnY(WorldServer worldserver, int x, int z) {
        if (worldserver == null) {
            return -1;
        }

        int y = worldserver.e(x, z); // air block above top solid
        return this.isSafeSkyRespawnAt(worldserver, x, y, z) ? y : -1;
    }

    private boolean isStandingOnSolidGround(WorldServer worldserver, EntityPlayer entityplayer) {
        if (worldserver == null || entityplayer == null) {
            return false;
        }

        int x = MathHelper.floor(entityplayer.locX);
        int y = MathHelper.floor(entityplayer.boundingBox.b + 0.001D);
        int z = MathHelper.floor(entityplayer.locZ);

        if (y <= 1) {
            return false;
        }

        int groundId = worldserver.getTypeId(x, y - 1, z);
        if (groundId <= 0 || groundId >= Block.byId.length) {
            return false;
        }

        Block ground = Block.byId[groundId];
        return ground != null && ground.material.isSolid() && !ground.material.isLiquid();
    }

    private int computeSkySupport(WorldServer worldserver, int x, int z) {
        int score = 0;
        for (int dx = -2; dx <= 2; ++dx) {
            for (int dz = -2; dz <= 2; ++dz) {
                if (this.findSkyRespawnY(worldserver, x + dx, z + dz) > 0) {
                    ++score;
                }
            }
        }
        return score;
    }

    private ChunkCoordinates findNearestSafeSkyRespawn(WorldServer worldserver, int centerX, int centerZ) {
        if (worldserver == null) {
            return null;
        }

        final int maxRadius = 256;
        final int step = 2;
        final int desiredSupport = 9;

        int bestX = 0;
        int bestY = -1;
        int bestZ = 0;
        int bestSupport = -1;
        int bestDist = Integer.MAX_VALUE;

        for (int r = 0; r <= maxRadius; r += step) {
            int ringBestX = 0;
            int ringBestY = -1;
            int ringBestZ = 0;
            int ringBestSupport = -1;
            int ringBestDist = Integer.MAX_VALUE;

            for (int x = centerX - r; x <= centerX + r; x += step) {
                int zTop = centerZ + r;
                int zBottom = centerZ - r;

                int yTop = this.findSkyRespawnY(worldserver, x, zTop);
                if (yTop > 0) {
                    int support = this.computeSkySupport(worldserver, x, zTop);
                    int dist = Math.abs(x - centerX) + Math.abs(zTop - centerZ);
                    if (support > ringBestSupport || (support == ringBestSupport && dist < ringBestDist)) {
                        ringBestX = x;
                        ringBestY = yTop;
                        ringBestZ = zTop;
                        ringBestSupport = support;
                        ringBestDist = dist;
                    }
                }

                if (zBottom != zTop) {
                    int yBottom = this.findSkyRespawnY(worldserver, x, zBottom);
                    if (yBottom > 0) {
                        int support = this.computeSkySupport(worldserver, x, zBottom);
                        int dist = Math.abs(x - centerX) + Math.abs(zBottom - centerZ);
                        if (support > ringBestSupport || (support == ringBestSupport && dist < ringBestDist)) {
                            ringBestX = x;
                            ringBestY = yBottom;
                            ringBestZ = zBottom;
                            ringBestSupport = support;
                            ringBestDist = dist;
                        }
                    }
                }
            }

            for (int z = centerZ - r + step; z <= centerZ + r - step; z += step) {
                int xRight = centerX + r;
                int xLeft = centerX - r;

                int yRight = this.findSkyRespawnY(worldserver, xRight, z);
                if (yRight > 0) {
                    int support = this.computeSkySupport(worldserver, xRight, z);
                    int dist = Math.abs(xRight - centerX) + Math.abs(z - centerZ);
                    if (support > ringBestSupport || (support == ringBestSupport && dist < ringBestDist)) {
                        ringBestX = xRight;
                        ringBestY = yRight;
                        ringBestZ = z;
                        ringBestSupport = support;
                        ringBestDist = dist;
                    }
                }

                if (xLeft != xRight) {
                    int yLeft = this.findSkyRespawnY(worldserver, xLeft, z);
                    if (yLeft > 0) {
                        int support = this.computeSkySupport(worldserver, xLeft, z);
                        int dist = Math.abs(xLeft - centerX) + Math.abs(z - centerZ);
                        if (support > ringBestSupport || (support == ringBestSupport && dist < ringBestDist)) {
                            ringBestX = xLeft;
                            ringBestY = yLeft;
                            ringBestZ = z;
                            ringBestSupport = support;
                            ringBestDist = dist;
                        }
                    }
                }
            }

            if (ringBestY > 0) {
                if (ringBestSupport > bestSupport || (ringBestSupport == bestSupport && ringBestDist < bestDist)) {
                    bestX = ringBestX;
                    bestY = ringBestY;
                    bestZ = ringBestZ;
                    bestSupport = ringBestSupport;
                    bestDist = ringBestDist;
                }

                if (ringBestSupport >= desiredSupport) {
                    break;
                }
            }
        }

        return bestY > 0 ? new ChunkCoordinates(bestX, bestY, bestZ) : null;
    }
}
