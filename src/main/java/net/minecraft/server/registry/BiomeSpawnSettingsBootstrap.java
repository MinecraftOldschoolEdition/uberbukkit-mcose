package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.BiomeBase;
import net.minecraft.server.BiomeMeta;
import net.minecraft.server.EnumCreatureType;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;
import uk.betacraft.uberbukkit.UberbukkitConfig;

/** Loads, validates, binds, and atomically publishes biome spawn projections. */
public final class BiomeSpawnSettingsBootstrap {
    public static final List<ResourceLocation> REQUIRED_BIOMES =
            Collections.unmodifiableList(Arrays.asList(
                    key("rainforest"),
                    key("swampland"),
                    key("seasonal_forest"),
                    key("forest"),
                    key("savanna"),
                    key("shrubland"),
                    key("taiga"),
                    key("desert"),
                    key("plains"),
                    key("ice_desert"),
                    key("tundra"),
                    key("hell"),
                    key("sky")));

    private static volatile Snapshot snapshot = Snapshot.empty();
    private static boolean initialized;

    private BiomeSpawnSettingsBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        BlockRegistryBootstrap.initialize();
        EntityTypeRegistryBootstrap.initialize();
        BiomeRegistryBootstrap.initialize();
        Map<ResourceLocation, BiomeSpawnSettings> raw = decode(null);
        StartupGates gates = StartupGates.fromConfig();
        snapshot = prepare(raw, gates);
        initialized = true;
        System.out.println("[BiomeSpawnSettingsBootstrap] Loaded "
                + raw.size() + " biome spawn settings; bound "
                + snapshot.primaryBindings.size() + " primary biomes");
    }

    /** Raw, unfiltered decoded data used by synchronization and fingerprints. */
    public static Map<ResourceLocation, BiomeSpawnSettings> rawTables() {
        initialize();
        return snapshot.rawTables;
    }

    /** Canonical biome key to raw table key bindings; aliases are never keys. */
    public static Map<ResourceLocation, ResourceLocation> primaryBindings() {
        initialize();
        return snapshot.primaryBindings;
    }

    public static BiomeSpawnSettings getRaw(ResourceLocation key) {
        initialize();
        return snapshot.rawTables.get(key);
    }

    /**
     * Returns the immutable startup-filtered list for a bound primary biome,
     * or {@code null} so absent and late custom biomes retain constructor data.
     */
    public static List getEffectiveSpawns(
            BiomeBase biome, EnumCreatureType creatureType) {
        if (biome == null || creatureType == null) return null;
        initialize();
        Map<String, List<BiomeMeta>> byCategory = snapshot.effectiveByBiome.get(biome);
        if (byCategory == null) return null;
        return byCategory.get(categoryName(creatureType));
    }

    static Map<ResourceLocation, BiomeSpawnSettings> decodeForTests(
            RegistryDataLoader.ResourceProvider provider) {
        BlockRegistryBootstrap.initialize();
        EntityTypeRegistryBootstrap.initialize();
        BiomeRegistryBootstrap.initialize();
        return decode(provider);
    }

    static Map<String, List<BiomeMeta>> deriveEffectiveForTests(
            BiomeSpawnSettings settings,
            boolean spawnSquids,
            boolean spawnSlimes,
            boolean spawnWolves) {
        return deriveEffective(
                settings, new StartupGates(spawnSquids, spawnSlimes, spawnWolves));
    }

    private static Map<ResourceLocation, BiomeSpawnSettings> decode(
            RegistryDataLoader.ResourceProvider provider) {
        RegistryDataLoader.Decoder<BiomeSpawnSettings> decoder =
                new RegistryDataLoader.Decoder<BiomeSpawnSettings>() {
                    public BiomeSpawnSettings decode(ResourceLocation key, JsonObject json) {
                        return BiomeSpawnSettingsCodec.decode(key, json);
                    }
                };
        Map<ResourceLocation, BiomeSpawnSettings> decoded = provider == null
                ? RegistryDataLoader.loadAll("worldgen/biome", decoder)
                : RegistryDataLoader.loadAll("worldgen/biome", provider, decoder);
        for (int i = 0; i < REQUIRED_BIOMES.size(); i++) {
            ResourceLocation required = REQUIRED_BIOMES.get(i);
            if (!decoded.containsKey(required)) {
                throw new IllegalStateException(
                        "Missing required biome spawn settings: " + required);
            }
        }
        return decoded;
    }

    private static Snapshot prepare(
            Map<ResourceLocation, BiomeSpawnSettings> raw,
            StartupGates gates) {
        LinkedHashMap<ResourceLocation, ResourceLocation> bindings =
                new LinkedHashMap<ResourceLocation, ResourceLocation>();
        IdentityHashMap<BiomeBase, Map<String, List<BiomeMeta>>> effective =
                new IdentityHashMap<BiomeBase, Map<String, List<BiomeMeta>>>();

        for (Map.Entry<ResourceLocation, BiomeSpawnSettings> entry : raw.entrySet()) {
            ResourceLocation tableKey = entry.getKey();
            BiomeBase biome = BiomeRegistryApi.get(tableKey);
            if (biome == null) {
                continue;
            }
            ResourceLocation primaryKey = BiomeRegistryApi.getKey(biome);
            if (!tableKey.equals(primaryKey)) {
                throw new IllegalStateException(
                        "Biome spawn settings cannot target alias '" + tableKey
                                + "'; primary key is '" + primaryKey + "'");
            }
            bindings.put(primaryKey, tableKey);
            effective.put(biome, deriveEffective(entry.getValue(), gates));
        }

        for (int i = 0; i < REQUIRED_BIOMES.size(); i++) {
            ResourceLocation required = REQUIRED_BIOMES.get(i);
            BiomeBase biome = BiomeRegistryApi.get(required);
            ResourceLocation primary = biome == null ? null : BiomeRegistryApi.getKey(biome);
            if (biome == null || !required.equals(primary)
                    || !required.equals(bindings.get(required))) {
                throw new IllegalStateException(
                        "Required biome spawn settings are not bound to primary biome "
                                + required + " (biome=" + biome + ", primary=" + primary
                                + ", binding=" + bindings.get(required) + ")");
            }
        }
        return new Snapshot(raw, bindings, effective);
    }

    private static Map<String, List<BiomeMeta>> deriveEffective(
            BiomeSpawnSettings settings,
            StartupGates gates) {
        LinkedHashMap<String, List<BiomeMeta>> result =
                new LinkedHashMap<String, List<BiomeMeta>>();
        for (int i = 0; i < BiomeSpawnSettings.CATEGORY_ORDER.size(); i++) {
            String category = BiomeSpawnSettings.CATEGORY_ORDER.get(i);
            List<BiomeSpawnSettings.SpawnEntry> rawEntries = settings.getSpawns(category);
            ArrayList<BiomeMeta> effective = new ArrayList<BiomeMeta>();
            for (int j = 0; j < rawEntries.size(); j++) {
                BiomeSpawnSettings.SpawnEntry entry = rawEntries.get(j);
                if (gates.allows(entry.getEntityType())) {
                    effective.add(new BiomeMeta(
                            entry.getEntityClass(), entry.getWeight(), entry.getCount()));
                }
            }
            result.put(category, Collections.unmodifiableList(effective));
        }
        return Collections.unmodifiableMap(result);
    }

    private static String categoryName(EnumCreatureType type) {
        if (type == EnumCreatureType.MONSTER) return BiomeSpawnSettings.MONSTER;
        if (type == EnumCreatureType.CREATURE) return BiomeSpawnSettings.CREATURE;
        if (type == EnumCreatureType.WATER_CREATURE) {
            return BiomeSpawnSettings.WATER_CREATURE;
        }
        return null;
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }

    private static final class StartupGates {
        private static final ResourceLocation SQUID = key("squid");
        private static final ResourceLocation SLIME = key("slime");
        private static final ResourceLocation WOLF = key("wolf");

        private final boolean spawnSquids;
        private final boolean spawnSlimes;
        private final boolean spawnWolves;

        private StartupGates(
                boolean spawnSquids, boolean spawnSlimes, boolean spawnWolves) {
            this.spawnSquids = spawnSquids;
            this.spawnSlimes = spawnSlimes;
            this.spawnWolves = spawnWolves;
        }

        private static StartupGates fromConfig() {
            UberbukkitConfig config = UberbukkitConfig.getInstance();
            return new StartupGates(
                    config.getBoolean("mechanics.spawn_squids", true),
                    config.getBoolean("mechanics.spawn_slimes", true),
                    config.getBoolean("mechanics.spawn_wolves", true));
        }

        private boolean allows(ResourceLocation entityType) {
            if (SQUID.equals(entityType)) return this.spawnSquids;
            if (SLIME.equals(entityType)) return this.spawnSlimes;
            if (WOLF.equals(entityType)) return this.spawnWolves;
            return true;
        }
    }

    private static final class Snapshot {
        private final Map<ResourceLocation, BiomeSpawnSettings> rawTables;
        private final Map<ResourceLocation, ResourceLocation> primaryBindings;
        private final Map<BiomeBase, Map<String, List<BiomeMeta>>> effectiveByBiome;

        private Snapshot(
                Map<ResourceLocation, BiomeSpawnSettings> rawTables,
                Map<ResourceLocation, ResourceLocation> primaryBindings,
                Map<BiomeBase, Map<String, List<BiomeMeta>>> effectiveByBiome) {
            this.rawTables = Collections.unmodifiableMap(
                    new LinkedHashMap<ResourceLocation, BiomeSpawnSettings>(rawTables));
            this.primaryBindings = Collections.unmodifiableMap(
                    new LinkedHashMap<ResourceLocation, ResourceLocation>(primaryBindings));
            this.effectiveByBiome = Collections.unmodifiableMap(
                    new IdentityHashMap<BiomeBase, Map<String, List<BiomeMeta>>>(
                            effectiveByBiome));
        }

        private static Snapshot empty() {
            return new Snapshot(
                    Collections.<ResourceLocation, BiomeSpawnSettings>emptyMap(),
                    Collections.<ResourceLocation, ResourceLocation>emptyMap(),
                    Collections.<BiomeBase, Map<String, List<BiomeMeta>>>emptyMap());
        }
    }
}
