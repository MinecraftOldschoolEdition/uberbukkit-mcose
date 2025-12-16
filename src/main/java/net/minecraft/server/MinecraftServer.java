package net.minecraft.server;

import com.legacyminecraft.poseidon.Poseidon;
import com.legacyminecraft.poseidon.PoseidonConfig;
import com.legacyminecraft.poseidon.PoseidonPlugin;
import com.legacyminecraft.poseidon.util.ServerLogRotator;
// import com.legacyminecraft.poseidon.utility.PerformanceStatistic; // Not used in uberbukkit
import com.legacyminecraft.poseidon.utility.PoseidonVersionChecker;
import com.projectposeidon.johnymuffin.UUIDManager;
import com.legacyminecraft.poseidon.watchdog.WatchDogThread;
import jline.ConsoleReader;
import joptsimple.OptionSet;
import org.bukkit.Bukkit;
import org.bukkit.World.Environment;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.LoggerOutputStream;
import org.bukkit.craftbukkit.command.ColouredConsoleSender;
import org.bukkit.craftbukkit.scheduler.CraftScheduler;
import org.bukkit.craftbukkit.util.ServerShutdownThread;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginLoadOrder;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.server.threading.ThreadingManager;

// CraftBukkit start
//import com.projectposeidon.johnymuffin.UUIDCacheFile;
// CraftBukkit end

public class MinecraftServer implements Runnable, ICommandListener {

    public static Logger log = Logger.getLogger("Minecraft");
    public static HashMap trackerList = new HashMap();
    public NetworkListenThread networkListenThread;
    public PropertyManager propertyManager;
    // public WorldServer[] worldServer; // CraftBukkit - removed!
    public ServerConfigurationManager serverConfigurationManager;
    public ConsoleCommandHandler consoleCommandHandler; // CraftBukkit - made public
    private boolean isRunning = true;
    public boolean isStopped = false;
    int ticks = 0;
    public String i;
    public int j;
    private List r = new ArrayList();
    private List s = Collections.synchronizedList(new ArrayList());
    // public EntityTracker[] tracker = new EntityTracker[2]; // CraftBukkit - removed!
    public boolean onlineMode;
    public boolean spawnAnimals;
    public boolean pvpMode;
    public boolean allowFlight;
    public boolean voiceChatEnabled;
    public final VoiceChatRoomManager chatRoomManager;
    private static final double DEFAULT_VOICE_CHAT_RADIUS = 48.0D;
    private double voiceChatBroadcastRadius = DEFAULT_VOICE_CHAT_RADIUS;

    // CraftBukkit start
    public List<WorldServer> worlds = new ArrayList<WorldServer>();
    public CraftServer server;
    public OptionSet options;
    public ColouredConsoleSender console;
    public ConsoleReader reader;
    public static int currentTick;
    public String configuredLevelType; // Added for server.properties level-type
    public int defaultGameMode = 0; // 0=survival, 1=creative, 2=hardcore
    // CraftBukkit end

    //Poseidon Start
//    private WatchDogThread watchDogThread;
    private boolean modLoaderSupport = false;
//    private PoseidonVersionChecker poseidonVersionChecker;
    //Poseidon End
    
    // GUI mode flag - when true, don't call System.exit() on stop
    public static boolean guiMode = false;
    
    // Friends verification handler for P2P verification on online-mode servers
    public final FriendsVerificationHandler friendsVerificationHandler;

    public MinecraftServer(OptionSet options) { // CraftBukkit - adds argument OptionSet
        new ThreadSleepForever(this);
        this.chatRoomManager = new VoiceChatRoomManager(this);
        this.friendsVerificationHandler = new FriendsVerificationHandler(this);

        // CraftBukkit start
        this.options = options;
        try {
            this.reader = new ConsoleReader();
        } catch (IOException ex) {
            Logger.getLogger(MinecraftServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        Runtime.getRuntime().addShutdownHook(new ServerShutdownThread(this));
        // CraftBukkit end
    }

    private boolean init() throws UnknownHostException { // CraftBukkit - added throws UnknownHostException
        this.consoleCommandHandler = new ConsoleCommandHandler(this);
        
        // Only start the console reader thread if NOT in GUI mode
        // In GUI mode, commands come from the GUI text field, not stdin
        if (!guiMode) {
            ThreadCommandReader threadcommandreader = new ThreadCommandReader(this);
            threadcommandreader.setDaemon(true);
            threadcommandreader.start();
        }
        ConsoleLogManager.init(this); // CraftBukkit

        // CraftBukkit start
        System.setOut(new PrintStream(new LoggerOutputStream(log, Level.INFO), true));
        System.setErr(new PrintStream(new LoggerOutputStream(log, Level.SEVERE), true));
        // CraftBukkit end

        //If Poseidon Config DEBUG is enabled, enable debug mode
        if (options.has("debug-config")) {
            log.info("[Poseidon] Configuration debug mode has been enabled. This will cause the poseidon.yml to be reloaded every time the server starts.");
            PoseidonConfig.getInstance().resetConfig();
        }

        modLoaderSupport = PoseidonConfig.getInstance().getBoolean("settings.support.modloader.enable", false);

        if (modLoaderSupport) {
            log.info("EXPERIMENTAL MODLOADERMP SUPPORT ENABLED.");
            if (!isModloaderPresent()) {
                log.severe("ModLoaderMP support is enabled, however, it isn't present. Please install it before enabling this setting");
                return false;
            }
            try {
                Class.forName("net.minecraft.server.ModLoader");
                // Optional: invoke via reflection if present
            } catch (ClassNotFoundException ignore) {}
        }

        log.info("Starting minecraft server version Beta 1.7.3");
        if (Runtime.getRuntime().maxMemory() / 1024L / 1024L < 512L) {
            log.warning("**** NOT ENOUGH RAM!");
            log.warning("To start the server with more ram, launch it as \"java -Xmx1024M -Xms1024M -jar minecraft_server.jar\"");
        }

        log.info("Loading properties");
        this.propertyManager = new PropertyManager(this.options); // CraftBukkit - CLI argument support
        String s = this.propertyManager.getString("server-ip", "");

        this.onlineMode = this.propertyManager.getBoolean("online-mode", true);
        this.spawnAnimals = this.propertyManager.getBoolean("spawn-animals", true);
        this.pvpMode = this.propertyManager.getBoolean("pvp", true);
        this.allowFlight = this.propertyManager.getBoolean("allow-flight", false);
        this.voiceChatEnabled = this.propertyManager.getBoolean("voice-chat", true);
        if (this.voiceChatEnabled) {
            log.info("Voice chat broadcasting enabled");
        }
        this.configuredLevelType = this.propertyManager.getString("level-type", "DEFAULT").toUpperCase(); // Added
        
        // Parse default gamemode from server.properties (survival, creative, or hardcore)
        String gamemodeStr = this.propertyManager.getString("gamemode", "survival").toLowerCase();
        if (gamemodeStr.equals("creative") || gamemodeStr.equals("c") || gamemodeStr.equals("1")) {
            this.defaultGameMode = 1;
            log.info("Default game mode: Creative");
        } else if (gamemodeStr.equals("hardcore") || gamemodeStr.equals("h") || gamemodeStr.equals("2")) {
            this.defaultGameMode = 2;
            log.info("Default game mode: HARDCORE - Death is permanent!");
        } else {
            this.defaultGameMode = 0;
            if (!gamemodeStr.equals("survival") && !gamemodeStr.equals("s") && !gamemodeStr.equals("0")) {
                log.warning("Unknown gamemode '" + gamemodeStr + "' in server.properties. Defaulting to survival.");
            } else {
                log.info("Default game mode: Survival");
            }
        }
        
        InetAddress inetaddress = null;

        if (s.length() > 0) {
            inetaddress = InetAddress.getByName(s);
        }

        int i = this.propertyManager.getInt("server-port", 25565);

        log.info("Starting Minecraft server on " + (s.length() == 0 ? "*" : s) + ":" + i);

        try {
            this.networkListenThread = new NetworkListenThread(this, inetaddress, i);
        } catch (Throwable ioexception) { // CraftBukkit - IOException -> Throwable
            log.warning("**** FAILED TO BIND TO PORT!");
            log.log(Level.WARNING, "The exception was: " + ioexception.toString());
            log.warning("Perhaps a server is already running on that port?");
            return false;
        }

        if (!this.onlineMode) {
            log.warning("**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!");
            log.warning("The server will make no attempt to authenticate usernames. Beware.");
            log.warning("While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.");
            log.warning("To change this, set \"online-mode\" to \"true\" in the server.settings file.");
        }

        this.serverConfigurationManager = new ServerConfigurationManager(this);
        // CraftBukkit - removed trackers
        long j = System.nanoTime();
        String s1 = this.propertyManager.getString("level-name", "world");
        String s2 = this.propertyManager.getString("level-seed", "");
        long k = (new Random()).nextLong();

        if (s2.length() > 0) {
            try {
                k = Long.parseLong(s2);
            } catch (NumberFormatException numberformatexception) {
                k = (long) s2.hashCode();
            }
        }

        log.info("Preparing level \"" + s1 + "\"");
        this.a(new WorldLoaderServer(new File(".")), s1, k);

        // Bootstrap registries (blocks, items, block entity types, entities, biomes, generators, world types)
        try {
            net.minecraft.server.registry.BlockRegistryBootstrap.initialize();
            net.minecraft.server.registry.ItemRegistryBootstrap.initialize();
            net.minecraft.server.registry.BlockEntityTypeRegistryBootstrap.initialize();
            net.minecraft.server.registry.EntityTypeRegistryBootstrap.initialize();
            net.minecraft.server.registry.BiomeRegistryBootstrap.initialize();
            net.minecraft.server.registry.ChunkGeneratorTypeRegistryBootstrap.initialize();
            net.minecraft.server.registry.WorldTypeRegistryBootstrap.initialize();
            net.minecraft.server.registry.FluidRegistryBootstrap.initialize();
            net.minecraft.server.registry.DimensionTypeRegistryBootstrap.initialize();
            net.minecraft.server.registry.PaintingMotiveRegistryBootstrap.initialize();
            net.minecraft.server.registry.SoundEventRegistryBootstrap.initialize();
            net.minecraft.server.registry.ScreenHandlerRegistryBootstrap.initialize();
            net.minecraft.server.registry.StatRegistryBootstrap.initialize();
            net.minecraft.server.registry.ScheduleRegistryBootstrap.initialize();
            net.minecraft.server.registry.SensorTypeRegistryBootstrap.initialize();
            net.minecraft.server.registry.MemoryModuleTypeRegistryBootstrap.initialize();
            net.minecraft.server.registry.PointOfInterestRegistryBootstrap.initialize();
            net.minecraft.server.registry.ParticleTypeRegistryBootstrap.initialize();
            net.minecraft.server.registry.TreeDecoratorTypeRegistryBootstrap.initialize();
            net.minecraft.server.registry.FoliagePlacerTypeRegistryBootstrap.initialize();
            net.minecraft.server.registry.FeatureRegistryBootstrap.initialize();
            net.minecraft.server.registry.CarverRegistryBootstrap.initialize();
            net.minecraft.server.registry.SurfaceBuilderRegistryBootstrap.initialize();
        } catch (Throwable ignored) {}

        //Project Poseidon Start
        Poseidon.getServer().initializeServer();
        //Project Poseidon End

        // CraftBukkit start
        long elapsed = System.nanoTime() - j;
        String time = String.format("%.3fs", elapsed / 10000000000.0D);
        
        // UberBukkit - Initialize server-wide statistics tracking
        ServerStatistics.getInstance();
        log.info("[ServerStats] Server-wide statistics tracking initialized");
        
        log.info("Done (" + time + ")! For help, type \"help\" or \"?\"");

        // log rotator process start.
        if ((boolean) PoseidonConfig.getInstance().getConfigOption("settings.per-day-log-file.enabled") && (boolean) PoseidonConfig.getInstance().getConfigOption("settings.per-day-log-file.latest-log.enabled")) {
            String latestLogFileName = "latest";
            ServerLogRotator serverLogRotator = new ServerLogRotator(latestLogFileName);
            serverLogRotator.start();
        }

        if (this.propertyManager.properties.containsKey("spawn-protection")) {
            log.info("'spawn-protection' in server.properties has been moved to 'settings.spawn-radius' in bukkit.yml. I will move your config for you.");
            this.server.setSpawnRadius(this.propertyManager.getInt("spawn-protection", 16));
            this.propertyManager.properties.remove("spawn-protection");
            this.propertyManager.savePropertiesFile();
        }
        return true;
    }

    public boolean isModloaderPresent() {
        try {
            Class.forName("net.minecraft.server.ModLoader");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void a(Convertable convertable, String s, long i) {
        if (convertable.isConvertable(s)) {
            log.info("Converting map!");
            convertable.convert(s, new ConvertProgressUpdater(this));
        }

        // CraftBukkit start
        for (int j = 0; j < (this.propertyManager.getBoolean("allow-nether", true) ? 2 : 1); ++j) {
            WorldServer world;
            int dimension = j == 0 ? 0 : -1;
            String worldType = Environment.getEnvironment(dimension).toString().toLowerCase();
            String name = (dimension == 0) ? s : s + "_" + worldType;

            ChunkGenerator gen = this.server.getGenerator(name);

            if (j == 0) {
                System.out.println("[MINECRAFT_SERVER_DEBUG] Preparing Overworld. configuredLevelType: " + this.configuredLevelType + ", level-name: " + s + ", seed: " + i);
                IDataManager dataManager = new ServerNBTManager(new File("."), s, true);
                WorldData worldData = dataManager.c();
                long seedToUse = i;

                // Determine integer typeId from configuredLevelType string via registry first, then fall back to legacy names
                int typeId = 0; // Default to 0 (NORMAL/DEFAULT)
                try {
                    String lt = this.configuredLevelType == null ? "default" : this.configuredLevelType.trim();
                    String keyPath = lt.toLowerCase();
                    if (keyPath.indexOf(':') < 0) keyPath = "minecraft:" + keyPath;
                    Integer rid = net.minecraft.server.registry.Registries.WORLD_TYPE.get(new net.minecraft.server.util.ResourceLocation(keyPath));
                    if (rid != null) {
                        typeId = rid.intValue();
                        log.info("[MinecraftServer] level-type resolved via registry '" + keyPath + "' => ID " + typeId);
                    } else {
                        // Legacy synonyms fallback
                        if (lt.equalsIgnoreCase("ALPHA")) { typeId = 1; log.info("[MinecraftServer] Configured level-type ALPHA maps to ID 1."); }
                        else if (lt.equalsIgnoreCase("FLAT")) { typeId = 2; log.info("[MinecraftServer] Configured level-type FLAT maps to ID 2."); }
                        else if (lt.equalsIgnoreCase("SKY")) { typeId = 3; log.info("[MinecraftServer] Configured level-type SKY maps to ID 3."); }
                        else if (lt.equalsIgnoreCase("ALPHA_SNOW") || lt.equalsIgnoreCase("ALPHA-SNOW") || lt.equalsIgnoreCase("ALPHASNOW")) { typeId = 5; log.info("[MinecraftServer] Configured level-type ALPHA_SNOW maps to ID 5."); }
                        else if (lt.equalsIgnoreCase("CLASSIC")) { typeId = 6; log.info("[MinecraftServer] Configured level-type CLASSIC maps to ID 6."); }
                        else if (!lt.equalsIgnoreCase("DEFAULT") && !lt.equalsIgnoreCase("NORMAL")) {
                            log.warning("[MinecraftServer] Unknown level-type in server.properties: '" + lt + "'. Defaulting to type ID 0.");
                        }
                    }
                } catch (Throwable ignored) {}

                if (worldData == null) { // New world
                    log.info("[MinecraftServer] No existing world data for '" + s + "'. Creating new with seed: " + seedToUse + ", type ID: " + typeId);
                    worldData = new WorldData(seedToUse, s); // Creates with terrainType 0 initially
                    worldData.setTerrainType(typeId);      // Set the correct type
                    
                    // Set Alpha Snow flag when requested
                    if (typeId == 5) {
                        worldData.setSnowWorld(true);
                        log.info("[MinecraftServer] Enabled AlphaSnow flag for ALPHA_SNOW terrain type.");
                    }
                    
                    // Set appropriate default spawn for sky/alpha_snow worlds
                    if (typeId == 3) { // SKY terrain type
                        worldData.setSpawn(0, 90, 0); // Set a higher default Y for sky worlds
                        log.info("[MinecraftServer] Set initial spawn for SKY world to (0, 90, 0)");
                    } else if (typeId == 5) { // ALPHA_SNOW defaults to normal spawn; no special Y needed
                        log.info("[MinecraftServer] Creating ALPHA_SNOW world; spawn will be determined by world logic.");
                    }
                    
                    dataManager.a(worldData); // Save new WorldData (creates/updates level.dat)
                    log.info("[MinecraftServer] Saved new WorldData for '" + s + "' with TerrainType ID: " + worldData.getTerrainType());
                } else { // Existing world
                    seedToUse = worldData.getSeed(); // Use seed from loaded world data
                    log.info("[MinecraftServer] Loaded existing WorldData for '" + s + "'. Original TerrainType ID: " + worldData.getTerrainType() + ", Seed: " + seedToUse);
                    boolean changed = false;
                    if (worldData.getTerrainType() != typeId) {
                        log.info("[MinecraftServer] Overriding TerrainType ID for world '" + s + "' from " + worldData.getTerrainType() + " to " + typeId + " (from server.properties).");
                        worldData.setTerrainType(typeId);
                        changed = true;
                    }
                    // Ensure AlphaSnow flag aligns with ALPHA_SNOW type
                    if (typeId == 5 && !worldData.isSnowWorld()) {
                        log.info("[MinecraftServer] Enabling AlphaSnow flag on existing world '" + s + "' for ALPHA_SNOW terrain type.");
                        worldData.setSnowWorld(true);
                        changed = true;
                    }
                    if (changed) dataManager.a(worldData); // Save modified WorldData
                    log.info("[MinecraftServer] Using TerrainType ID: " + worldData.getTerrainType() + " for world '" + s + "'");
                }
                
                // WorldServer will use dataManager to load this worldData with the correct type and seed.
                world = new WorldServer(this, dataManager, s, dimension, seedToUse, org.bukkit.World.Environment.getEnvironment(dimension), gen);

            } else {
                String dim = "DIM-1";

                File newWorld = new File(new File(name), dim);
                File oldWorld = new File(new File(s), dim);

                if ((!newWorld.isDirectory()) && (oldWorld.isDirectory())) {
                    log.info("---- Migration of old " + worldType + " folder required ----");
                    log.info("Unfortunately due to the way that Minecraft implemented multiworld support in 1.6, Bukkit requires that you move your " + worldType + " folder to a new location in order to operate correctly.");
                    log.info("We will move this folder for you, but it will mean that you need to move it back should you wish to stop using Bukkit in the future.");
                    log.info("Attempting to move " + oldWorld + " to " + newWorld + "...");

                    if (newWorld.exists()) {
                        log.severe("A file or folder already exists at " + newWorld + "!");
                        log.info("---- Migration of old " + worldType + " folder failed ----");
                    } else if (newWorld.getParentFile().mkdirs()) {
                        if (oldWorld.renameTo(newWorld)) {
                            log.info("Success! To restore the nether in the future, simply move " + newWorld + " to " + oldWorld);
                            log.info("---- Migration of old " + worldType + " folder complete ----");
                        } else {
                            log.severe("Could not move folder " + oldWorld + " to " + newWorld + "!");
                            log.info("---- Migration of old " + worldType + " folder failed ----");
                        }
                    } else {
                        log.severe("Could not create path for " + newWorld + "!");
                        log.info("---- Migration of old " + worldType + " folder failed ----");
                    }
                }

                log.info("[MinecraftServer] Preparing Nether world '" + name + "' with seed from overworld: " + i);
                // Ensure Nether world data mirrors overworld terrain type for generator selection
                ServerNBTManager dataManagerNether = new ServerNBTManager(new File("."), name, true);
                WorldData dataNether = dataManagerNether.c();
                if (dataNether == null) {
                    dataNether = new WorldData(i, name);
                }
                try {
                    WorldServer overworld = this.worlds.get(0);
                    if (overworld != null && overworld.worldData != null) {
                        int ot = overworld.worldData.getTerrainType();
                        dataNether.setTerrainType(ot);
                    } else if (this.configuredLevelType != null && this.configuredLevelType.equalsIgnoreCase("CLASSIC")) {
                        dataNether.setTerrainType(6);
                    }
                } catch (Throwable ignore) {}
                dataManagerNether.a(dataNether);
                world = new SecondaryWorldServer(this, dataManagerNether, name, dimension, i, this.worlds.get(0), org.bukkit.World.Environment.getEnvironment(dimension), gen);
            }

            if (gen != null) {
                world.getWorld().getPopulators().addAll(gen.getDefaultPopulators(world.getWorld()));
            }

            this.server.getPluginManager().callEvent(new WorldInitEvent(world.getWorld()));

            world.tracker = new EntityTracker(this, dimension);
            world.addIWorldAccess(new WorldManager(this, world));
            world.spawnMonsters = this.propertyManager.getBoolean("spawn-monsters", true) ? 1 : 0;
            world.setSpawnFlags(this.propertyManager.getBoolean("spawn-monsters", true), this.spawnAnimals);
            this.worlds.add(world);
            this.serverConfigurationManager.setPlayerFileData(this.worlds.toArray(new WorldServer[0]));
        }
        // CraftBukkit end

        short short1 = 196;
        long k = System.currentTimeMillis();

        // CraftBukkit start
        for (int l = 0; l < this.worlds.size(); ++l) {
            // if (l == 0 || this.propertyManager.getBoolean("allow-nether", true)) {
            WorldServer worldserver = this.worlds.get(l);
            log.info("Preparing start region for level " + l + " (Seed: " + worldserver.getSeed() + ")");
            if (worldserver.getWorld().getKeepSpawnInMemory()) {
                // CraftBukkit end
                ChunkCoordinates chunkcoordinates = worldserver.getSpawn();

                for (int i1 = -short1; i1 <= short1 && this.isRunning; i1 += 16) {
                    for (int j1 = -short1; j1 <= short1 && this.isRunning; j1 += 16) {
                        long k1 = System.currentTimeMillis();

                        if (k1 < k) {
                            k = k1;
                        }

                        if (k1 > k + 1000L) {
                            int l1 = (short1 * 2 + 1) * (short1 * 2 + 1);
                            int i2 = (i1 + short1) * (short1 * 2 + 1) + j1 + 1;

                            this.a("Preparing spawn area", i2 * 100 / l1);
                            k = k1;
                        }

                        worldserver.chunkProviderServer.getChunkAt(chunkcoordinates.x + i1 >> 4, chunkcoordinates.z + j1 >> 4);

                        while (worldserver.doLighting() && this.isRunning) {
                            ;
                        }
                    }
                }
            } // CraftBukkit
        }

        // CraftBukkit start
        for (World world : this.worlds) {
            this.server.getPluginManager().callEvent(new WorldLoadEvent(world.getWorld()));
        }
        // CraftBukkit end
        
        // Initialize async threading systems
        ThreadingManager.getInstance().initialize(this);

        this.e();
    }

    private void a(String s, int i) {
        this.i = s;
        this.j = i;
        log.info(s + ": " + i + "%");
    }

    private void e() {
        this.i = null;
        this.j = 0;

        this.server.enablePlugins(PluginLoadOrder.POSTWORLD); // CraftBukkit
    }

    void saveChunks() { // CraftBukkit - private -> default
        log.info("Saving chunks");

        // CraftBukkit start
        for (int i = 0; i < this.worlds.size(); ++i) {
            WorldServer worldserver = this.worlds.get(i);

            worldserver.save(true, (IProgressUpdate) null);
            worldserver.saveLevel();

            WorldSaveEvent event = new WorldSaveEvent(worldserver.getWorld());
            this.server.getPluginManager().callEvent(event);
        }

        WorldServer world = this.worlds.get(0);
        if (!world.canSave) {
            this.serverConfigurationManager.savePlayers();
        }
        // CraftBukkit end
    }

    public void stop() { // CraftBukkit - private -> public
        log.info("Stopping server");
        
        // UberBukkit - Save server-wide statistics on shutdown
        try {
            ServerStatistics.getInstance().shutdown();
        } catch (Exception e) {
            log.warning("[ServerStats] Error saving server statistics: " + e.getMessage());
        }
        
        // Close the network socket first to release the port
        if (this.networkListenThread != null) {
            this.networkListenThread.closeSocket();
        }

        //Project Poseidon Start

        // This is done before disablePlugins() to ensure the watchdog doesn't detect plugins disabling as a server hang
        Poseidon.getServer().shutdownServer();

        //Project Poseidon End

        // CraftBukkit start
        if (this.server != null) {
            this.server.disablePlugins();
        }
        // CraftBukkit end

        if (this.serverConfigurationManager != null) {
            this.serverConfigurationManager.savePlayers();
        }
        
        // Shutdown async threading systems
        ThreadingManager.getInstance().shutdown();

        // CraftBukkit start - multiworld is handled in saveChunks() already.
        WorldServer worldserver = this.worlds.get(0);

        if (worldserver != null) {
            this.saveChunks();
        }
        // CraftBukkit end

        // Poseidon Start
        // UberBukkit: Performance statistics not available; stubbed to empty
        Map<String, Object> listenerStatistics = new java.util.HashMap<String, Object>();

        // Only get the Listener Statistics if the Poseidon Server is not null. Prevents null pointer exceptions.
        // if (Poseidon.getServer() != null && Poseidon.getServer().getConfig().getConfigBoolean("settings.performance-monitoring.listener-reporting.print-statistics-on-shutdown.enabled")) {
        //     listenerStatistics = Poseidon.getServer().getSortedListenerPerformance();
        // }

        // Check if the statistics map is not empty
        if (listenerStatistics != null && !listenerStatistics.isEmpty()) {
            log.info("[Poseidon] Listener statistics from this session:");

            // Iterate over each listener and log their statistics
            for (Map.Entry<String, Object> entry : listenerStatistics.entrySet()) {
                String listener = entry.getKey();
                Object stats = entry.getValue();

                /* if (stats.getMaxExecutionTime() == 0) {
                    continue;
                } */
            }
        }


        // Check if the statistics map is not empty

        Map<String, Object> taskStatistics = new java.util.HashMap<String, Object>();

        // Only get the Task Statistics if the Poseidon Server is not null. Prevents null pointer exceptions.
        // if (Poseidon.getServer() != null && Poseidon.getServer().getConfig().getConfigBoolean("settings.performance-monitoring.listener-reporting.print-statistics-on-shutdown.enabled")) {
        //     taskStatistics = Poseidon.getServer().getSortedTaskPerformance();
        // }

        if (taskStatistics != null && !taskStatistics.isEmpty()) {
            log.info("[Poseidon] Synchronous task statistics from this session:");

            // Iterate over each task and log their statistics
            for (Map.Entry<String, Object> entry : taskStatistics.entrySet()) {
                String task = entry.getKey();
                Object stats = entry.getValue();

                /* if (stats.getMaxExecutionTime() == 0) {
                    continue;
                } */
            }
        }
        // Poseidon End
        
        // Reset singletons to allow restart in GUI mode
        if (guiMode) {
            org.bukkit.Bukkit.resetServer();
            com.legacyminecraft.poseidon.Poseidon.resetServer();
        }
    }

    public void a() {
        this.isRunning = false;
    }

    public void run() {
        try {
            if (this.init()) {
                long i = System.currentTimeMillis();

                for (long j = 0L; this.isRunning; Thread.sleep(1L)) {
                    if (modLoaderSupport) {
                        try {
                            Class<?> ml = Class.forName("net.minecraft.server.ModLoader");
                            java.lang.reflect.Method m = ml.getMethod("OnTick", MinecraftServer.class);
                            m.invoke(null, this);
                        } catch (Throwable ignore) {}
                    }

                    long k = System.currentTimeMillis();
                    long l = k - i;

                    if (l > 2000L) {
                        log.warning("Can\'t keep up! Did the system time change, or is the server overloaded?");
                        l = 2000L;
                    }

                    if (l < 0L) {
                        log.warning("Time ran backwards! Did the system time change?");
                        l = 0L;
                    }

                    j += l;
                    i = k;
                    if (this.worlds.get(0).everyoneDeeplySleeping()) { // CraftBukkit
                        this.h();
                        j = 0L;
                    } else {
                        while (j > 50L) {
                            MinecraftServer.currentTick = (int) (System.currentTimeMillis() / 50); // CraftBukkit
                            getWatchdog().tickUpdate(); // Project Poseidon
                            j -= 50L;
                            this.h();
                        }
                    }
                }
            } else {
                while (this.isRunning) {
                    this.b();

                    try {
                        Thread.sleep(10L);
                    } catch (InterruptedException interruptedexception) {
                        interruptedexception.printStackTrace();
                    }
                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            log.log(Level.SEVERE, "Unexpected exception", throwable);

            while (this.isRunning) {
                this.b();

                try {
                    Thread.sleep(10L);
                } catch (InterruptedException interruptedexception1) {
                    interruptedexception1.printStackTrace();
                }
            }
        } finally {
            try {
                this.stop();
                this.isStopped = true;
            } catch (Throwable throwable1) {
                throwable1.printStackTrace();
            } finally {
                // Don't exit if running in GUI mode - let the GUI stay open
                if (!guiMode) {
                    System.exit(0);
                }
            }
        }
    }

    //Project Poseidon Start - Tick Update
    private final LinkedList<Double> tpsRecords = new LinkedList<>();
    private long lastTick = System.currentTimeMillis();
    private int tickCount = 0;

    public LinkedList<Double> getTpsRecords() {
        return tpsRecords;
    }
    //Project Poseidon End - Tick Update

    private void h() {
        ArrayList arraylist = new ArrayList();
        Iterator iterator = trackerList.keySet().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();
            int i = ((Integer) trackerList.get(s)).intValue();

            if (i > 0) {
                trackerList.put(s, Integer.valueOf(i - 1));
            } else {
                arraylist.add(s);
            }
        }

        int j;

        for (j = 0; j < arraylist.size(); ++j) {
            trackerList.remove(arraylist.get(j));
        }

        AxisAlignedBB.a();
        Vec3D.a();
        ++this.ticks;

        ((CraftScheduler) this.server.getScheduler()).mainThreadHeartbeat(this.ticks); // CraftBukkit

        //Project Poseidon Start - Tick Update
        long currentTime = System.currentTimeMillis();
        tickCount++;

        //Check if a second has passed
        if (currentTime - lastTick >= 1000) {
            double tps = tickCount / ((currentTime - lastTick) / 1000.0);
            tpsRecords.addFirst(tps);
            if (tpsRecords.size() > 900) { //Don't keep more than 15 minutes of data
                tpsRecords.removeLast();
            }

            tickCount = 0;
            lastTick = currentTime;
        }

        //Project Poseidon End - Tick Update


        for (j = 0; j < this.worlds.size(); ++j) { // CraftBukkit
            // if (j == 0 || this.propertyManager.getBoolean("allow-nether", true)) { // CraftBukkit
            WorldServer worldserver = this.worlds.get(j); // CraftBukkit

            if (this.ticks % 20 == 0) {
                // CraftBukkit start - only send timeupdates to the people in that world
                for (int i = 0; i < worldserver.players.size(); ++i) { // Project Poseidon: serverConfigurationManager -> worldserver.players
                    EntityPlayer entityPlayer = (EntityPlayer) worldserver.players.get(i);
                    if (entityPlayer != null) {
                        entityPlayer.netServerHandler.sendPacket(new Packet4UpdateTime(entityPlayer.getPlayerTime())); // Add support for per player time

                    }
                }
                // CraftBukkit end
            }

            worldserver.doTick();

            while (worldserver.doLighting()) {
                ;
            }

            worldserver.cleanUp();
        }
        // } // CraftBukkit

        this.networkListenThread.a();
        this.serverConfigurationManager.b();

        // CraftBukkit start
        for (j = 0; j < this.worlds.size(); ++j) {
            this.worlds.get(j).tracker.updatePlayers();
        }
        // CraftBukkit end

        for (j = 0; j < this.r.size(); ++j) {
            ((IUpdatePlayerListBox) this.r.get(j)).a();
        }

        try {
            this.b();
        } catch (Exception exception) {
            log.log(Level.WARNING, "Unexpected exception while parsing console command", exception);
        }
        
        // Process async threading results
        ThreadingManager.getInstance().processTick();
    }

    public void issueCommand(String s, ICommandListener icommandlistener) {
        this.s.add(new ServerCommand(s, icommandlistener));
    }

    public void b() {
        while (this.s.size() > 0) {
            ServerCommand servercommand = (ServerCommand) this.s.remove(0);

            // CraftBukkit start - ServerCommand for preprocessing
            ServerCommandEvent event = new ServerCommandEvent(this.console, servercommand.command);
            this.server.getPluginManager().callEvent(event);
            servercommand = new ServerCommand(event.getCommand(), servercommand.b);
            // CraftBukkit end

            // this.consoleCommandHandler.handle(servercommand); // CraftBukkit - Removed its now called in server.dispatchCommand
            this.server.dispatchCommand(this.console, servercommand); // CraftBukkit
        }
    }

    public void a(IUpdatePlayerListBox iupdateplayerlistbox) {
        this.r.add(iupdateplayerlistbox);
    }

    public static void main(final OptionSet options) { // CraftBukkit - replaces main(String args[])
        StatisticList.a();

        try {
            MinecraftServer minecraftserver = new MinecraftServer(options); // CraftBukkit - pass in the options

            // CraftBukkit - remove gui

            (new ThreadServerApplication("Server thread", minecraftserver)).start();
        } catch (Exception exception) {
            log.log(Level.SEVERE, "Failed to start the minecraft server", exception);
        }
    }

    public File a(String s) {
        return new File(s);
    }

    public void sendMessage(String s) {
        log.info(s);
    }

    public void c(String s) {
        log.warning(s);
    }

    public String getName() {
        return "CONSOLE";
    }

    public WorldServer getWorldServer(int i) {
        // CraftBukkit start
        for (WorldServer world : this.worlds) {
            if (world.dimension == i) {
                return world;
            }
        }

        return this.worlds.get(0);
        // CraftBukkit end
    }

    public EntityTracker getTracker(int i) {
        return this.getWorldServer(i).tracker; // CraftBukkit
    }

    public static boolean isRunning(MinecraftServer minecraftserver) {
        return minecraftserver.isRunning;
    }

    public WatchDogThread getWatchdog() {
        return Poseidon.getServer().getWatchDogThread();
    }

    public boolean isVoiceChatEnabled() {
        return this.voiceChatEnabled;
    }

    public double getVoiceChatBroadcastRadius() {
        return this.voiceChatBroadcastRadius;
    }
}
