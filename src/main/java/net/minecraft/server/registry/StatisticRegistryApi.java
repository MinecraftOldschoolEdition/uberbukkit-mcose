package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.Statistic;
import net.minecraft.server.StatisticList;
import net.minecraft.server.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Hardened registry-backed API for server-side player statistics.
 */
public final class StatisticRegistryApi {
    public static final int GENERAL = 0;
    public static final int BLOCK_MINED = 1;
    public static final int ITEM_USED = 2;
    public static final int ITEM_CRAFTED = 3;
    public static final int ITEM_DEPLETED = 4;
    public static final int ITEM_PICKUP = 5;

    private static final int NO_CATEGORY = -1;

    private static final Map<ResourceLocation, Statistic> BY_KEY = new HashMap<ResourceLocation, Statistic>();
    private static final Map<Statistic, ResourceLocation> KEY_OF = new IdentityHashMap<Statistic, ResourceLocation>();
    private static final Map<Integer, Statistic> BY_STAT_ID = new HashMap<Integer, Statistic>();
    private static final Map<String, ResourceLocation> ALIASES = new HashMap<String, ResourceLocation>();
    private static final Map<Integer, List<Statistic>> BY_CATEGORY = new HashMap<Integer, List<Statistic>>();

    private static boolean bootstrapped = false;
    private static int lastBlockFingerprint = Integer.MIN_VALUE;
    private static int lastItemFingerprint = Integer.MIN_VALUE;
    private static int lastStatFingerprint = Integer.MIN_VALUE;

    private StatisticRegistryApi() {
    }

    public static synchronized void bootstrapBuiltins() {
        initializeCategoryLists();
        registerGenericStats();
        registerBlockStats();
        registerItemStats();
        rebuildIndexesInternal();
        updateFingerprints();
        bootstrapped = true;
    }

    public static synchronized boolean register(ResourceLocation key, Statistic statistic) {
        if (key == null || statistic == null) {
            warn("Ignored null registration key=" + key + ", stat=" + statistic);
            return false;
        }

        Statistic existingByKey = Registries.STAT.get(key);
        if (existingByKey == statistic) {
            return true;
        }
        if (existingByKey != null && existingByKey != statistic) {
            return false;
        }

        ResourceLocation existingKeyForStat = Registries.STAT.getKey(statistic);
        if (existingKeyForStat != null && !existingKeyForStat.equals(key)) {
            Registries.STAT.registerIfAbsent(key, statistic);
            return true;
        }

        Statistic existingById = BY_STAT_ID.get(Integer.valueOf(statistic.e));
        if (existingById != null && existingById != statistic) {
            warn("Duplicate stat id seen statId=" + statistic.e + ", existing=" + existingById.f + ", incoming=" + statistic.f);
        }

        Registries.STAT.registerIfAbsent(key, statistic);
        ResourceLocation canonical = Registries.STAT.getKey(statistic);
        if (canonical == null) {
            Registries.STAT.register(key, statistic);
            canonical = key;
        }

        rebuildIndexesInternal();
        updateFingerprints();
        return true;
    }

    public static synchronized void rebuildIndexesIfRegistryChanged() {
        if (!bootstrapped) {
            bootstrapBuiltins();
            return;
        }

        int blockFingerprint = fingerprint(Registries.BLOCK.keys());
        int itemFingerprint = fingerprint(Registries.ITEM.keys());
        int statFingerprint = fingerprint(Registries.STAT.keys());
        if (blockFingerprint == lastBlockFingerprint && itemFingerprint == lastItemFingerprint && statFingerprint == lastStatFingerprint) {
            return;
        }

        registerBlockStats();
        registerItemStats();
        rebuildIndexesInternal();
        lastBlockFingerprint = blockFingerprint;
        lastItemFingerprint = itemFingerprint;
        lastStatFingerprint = statFingerprint;
    }

    public static synchronized Statistic get(ResourceLocation key) {
        rebuildIndexesIfRegistryChanged();
        return key == null ? null : BY_KEY.get(key);
    }

    public static synchronized Statistic getByStatId(int statId) {
        rebuildIndexesIfRegistryChanged();
        return BY_STAT_ID.get(Integer.valueOf(statId));
    }

    public static synchronized ResourceLocation getKey(Statistic statistic) {
        rebuildIndexesIfRegistryChanged();
        return statistic == null ? null : KEY_OF.get(statistic);
    }

    public static synchronized Set<ResourceLocation> keys() {
        rebuildIndexesIfRegistryChanged();
        return Registries.STAT.keys();
    }

    public static synchronized Collection<Statistic> values() {
        rebuildIndexesIfRegistryChanged();
        return Registries.STAT.values();
    }

    public static synchronized int size() {
        rebuildIndexesIfRegistryChanged();
        return BY_KEY.size();
    }

    public static synchronized List<Statistic> getStatsForCategory(int category) {
        rebuildIndexesIfRegistryChanged();
        List<Statistic> stats = BY_CATEGORY.get(Integer.valueOf(category));
        return stats == null ? new ArrayList<Statistic>() : new ArrayList<Statistic>(stats);
    }

    public static synchronized Statistic getCategoryStatisticForLegacyId(int category, int legacyId) {
        rebuildIndexesIfRegistryChanged();
        ResourceLocation key = buildCategoryKey(category, legacyId);
        if (key != null) {
            Statistic byKey = BY_KEY.get(key);
            if (byKey != null) {
                return byKey;
            }
        }

        return getLegacyCategoryStat(category, legacyId);
    }

    public static synchronized String canonicalizeIdentifier(String any) {
        rebuildIndexesIfRegistryChanged();
        ResourceLocation resolved = resolveIdentifier(any, true);
        if (resolved == null) {
            return null;
        }

        Statistic statistic = BY_KEY.get(resolved);
        if (statistic == null) {
            return null;
        }

        ResourceLocation canonical = KEY_OF.get(statistic);
        return canonical == null ? null : canonical.toString();
    }

    public static synchronized String normalizeInputIdentifier(String any) {
        rebuildIndexesIfRegistryChanged();
        ResourceLocation resolved = resolveIdentifier(any, true);
        return resolved == null ? null : resolved.toString();
    }

    private static void registerGenericStats() {
        register(StatisticKeys.START_GAME, StatisticList.f);
        register(StatisticKeys.CREATE_WORLD, StatisticList.g);
        register(StatisticKeys.LOAD_WORLD, StatisticList.h);
        register(StatisticKeys.JOIN_MULTIPLAYER, StatisticList.i);
        register(StatisticKeys.LEAVE_GAME, StatisticList.j);
        register(StatisticKeys.PLAY_ONE_MINUTE, StatisticList.k);
        register(StatisticKeys.DISTANCE_WALKED_CM, StatisticList.l);
        register(StatisticKeys.DISTANCE_SWUM_CM, StatisticList.m);
        register(StatisticKeys.DISTANCE_FALLEN_CM, StatisticList.n);
        register(StatisticKeys.DISTANCE_CLIMBED_CM, StatisticList.o);
        register(StatisticKeys.DISTANCE_FLOWN_CM, StatisticList.p);
        register(StatisticKeys.DISTANCE_DOVE_CM, StatisticList.q);
        register(StatisticKeys.DISTANCE_BY_MINECART_CM, StatisticList.r);
        register(StatisticKeys.DISTANCE_BY_BOAT_CM, StatisticList.s);
        register(StatisticKeys.DISTANCE_BY_PIG_CM, StatisticList.t);
        register(StatisticKeys.JUMP, StatisticList.u);
        register(StatisticKeys.DROP, StatisticList.v);
        register(StatisticKeys.DAMAGE_DEALT, StatisticList.w);
        register(StatisticKeys.DAMAGE_TAKEN, StatisticList.x);
        register(StatisticKeys.DEATHS, StatisticList.y);
        register(StatisticKeys.MOB_KILLS, StatisticList.z);
        register(StatisticKeys.PLAYER_KILLS, StatisticList.A);
        register(StatisticKeys.FISH_CAUGHT, StatisticList.B);
    }

    private static void registerBlockStats() {
        Statistic[] stats = StatisticList.C;
        if (stats == null) {
            return;
        }

        for (int id = 0; id < stats.length; ++id) {
            Statistic statistic = stats[id];
            if (statistic == null) {
                continue;
            }

            registerCategoryStat(buildBlockKey(id), statistic, id);
        }
    }

    private static void registerItemStats() {
        registerItemStatArray(StatisticKeys.PREFIX_ITEM_CRAFTED, StatisticList.D);
        registerItemStatArray(StatisticKeys.PREFIX_ITEM_USED, StatisticList.E);
        registerItemStatArray(StatisticKeys.PREFIX_ITEM_DEPLETED, StatisticList.F);
    }

    private static void registerItemStatArray(String prefix, Statistic[] stats) {
        if (stats == null) {
            return;
        }

        for (int id = 0; id < stats.length; ++id) {
            Statistic statistic = stats[id];
            if (statistic == null) {
                continue;
            }

            registerCategoryStat(buildItemKey(prefix, id), statistic, id);
        }
    }

    private static void registerCategoryStat(ResourceLocation preferredKey, Statistic statistic, int legacyId) {
        if (preferredKey == null || statistic == null) {
            return;
        }

        if (register(preferredKey, statistic)) {
            return;
        }

        ResourceLocation canonical = Registries.STAT.getKey(statistic);
        if (canonical != null) {
            addAlias(preferredKey.toString(), canonical);
            String preferredPath = preferredKey.getPath();
            if (preferredPath != null && preferredPath.length() > 0) {
                addAlias(preferredPath, canonical);
                addAlias(preferredPath.replace('/', '.'), canonical);
            }
            return;
        }

        ResourceLocation fallback = appendLegacySuffix(preferredKey, legacyId);
        if (fallback != null && !fallback.equals(preferredKey) && register(fallback, statistic)) {
            return;
        }

        warn("Unable to register stat key=" + preferredKey + ", statId=" + statistic.e + ", fallback=" + fallback);
    }

    private static ResourceLocation buildCategoryKey(int category, int legacyId) {
        if (category == BLOCK_MINED) {
            return buildBlockKey(legacyId);
        }
        if (category == ITEM_USED) {
            return buildItemKey(StatisticKeys.PREFIX_ITEM_USED, legacyId);
        }
        if (category == ITEM_CRAFTED) {
            return buildItemKey(StatisticKeys.PREFIX_ITEM_CRAFTED, legacyId);
        }
        if (category == ITEM_DEPLETED) {
            return buildItemKey(StatisticKeys.PREFIX_ITEM_DEPLETED, legacyId);
        }
        if (category == ITEM_PICKUP) {
            return buildItemKey(StatisticKeys.PREFIX_ITEM_PICKUP, legacyId);
        }

        return null;
    }

    private static Statistic getLegacyCategoryStat(int category, int legacyId) {
        if (legacyId < 0) {
            return null;
        }

        if (category == BLOCK_MINED) {
            return getArrayStat(StatisticList.C, legacyId);
        }
        if (category == ITEM_USED) {
            return getArrayStat(StatisticList.E, legacyId);
        }
        if (category == ITEM_CRAFTED) {
            return getArrayStat(StatisticList.D, legacyId);
        }
        if (category == ITEM_DEPLETED) {
            return getArrayStat(StatisticList.F, legacyId);
        }
        if (category == ITEM_PICKUP) {
            return null;
        }

        return null;
    }

    private static Statistic getArrayStat(Statistic[] stats, int id) {
        return stats != null && id >= 0 && id < stats.length ? stats[id] : null;
    }

    private static ResourceLocation buildBlockKey(int legacyId) {
        Block block = legacyId >= 0 && legacyId < Block.byId.length ? Block.byId[legacyId] : null;
        return new ResourceLocation("minecraft", StatisticKeys.PREFIX_BLOCK_MINED + getBlockKeyPath(block, legacyId));
    }

    private static ResourceLocation buildItemKey(String prefix, int legacyId) {
        Item item = legacyId >= 0 && legacyId < Item.byId.length ? Item.byId[legacyId] : null;
        return new ResourceLocation("minecraft", prefix + getItemKeyPath(item, legacyId));
    }

    private static ResourceLocation appendLegacySuffix(ResourceLocation key, int legacyId) {
        if (key == null) {
            return null;
        }

        String path = key.getPath();
        if (path == null || path.length() == 0) {
            return null;
        }

        String suffix = "_legacy_" + legacyId;
        if (path.endsWith(suffix)) {
            return key;
        }

        return new ResourceLocation(key.getNamespace(), path + suffix);
    }

    private static String getBlockKeyPath(Block block, int legacyIdFallback) {
        if (block != null) {
            ResourceLocation key = Registries.BLOCK.getKey(block);
            if (key == null) {
                key = BlockRegistry.getKey(block);
            }
            if (key != null && key.getPath() != null && key.getPath().length() > 0) {
                return sanitizePath(key.getPath());
            }

            String name = block.l();
            if (name != null && name.startsWith("tile.")) {
                name = name.substring(5);
            }
            if (name != null && name.length() > 0) {
                return sanitizePath(toSnake(name));
            }
        }

        return "legacy_" + legacyIdFallback;
    }

    private static String getItemKeyPath(Item item, int legacyIdFallback) {
        if (item != null) {
            ResourceLocation key = Registries.ITEM.getKey(item);
            if (key == null) {
                key = ItemRegistry.getKey(item);
            }
            if (key != null && key.getPath() != null && key.getPath().length() > 0) {
                return sanitizePath(key.getPath());
            }

            String name = item.a();
            if (name != null && name.startsWith("item.")) {
                name = name.substring(5);
            }
            if (name != null && name.length() > 0) {
                return sanitizePath(toSnake(name));
            }
        }

        return "legacy_" + legacyIdFallback;
    }

    private static void rebuildIndexesInternal() {
        BY_KEY.clear();
        KEY_OF.clear();
        BY_STAT_ID.clear();
        ALIASES.clear();
        initializeCategoryLists();
        clearCategoryLists();

        Iterator<ResourceLocation> keyIterator = Registries.STAT.keys().iterator();
        while (keyIterator.hasNext()) {
            ResourceLocation key = keyIterator.next();
            Statistic statistic = Registries.STAT.get(key);
            if (statistic == null) {
                continue;
            }

            BY_KEY.put(key, statistic);
            addAliases(key);

            if (!KEY_OF.containsKey(statistic)) {
                KEY_OF.put(statistic, key);

                int category = classifyCategory(key);
                if (category != NO_CATEGORY) {
                    List<Statistic> categoryStats = BY_CATEGORY.get(Integer.valueOf(category));
                    if (categoryStats != null && !categoryStats.contains(statistic)) {
                        categoryStats.add(statistic);
                    }
                }
            }

            Integer statId = Integer.valueOf(statistic.e);
            Statistic existingById = BY_STAT_ID.get(statId);
            if (existingById != null && existingById != statistic) {
                warn("Duplicate stat id detected statId=" + statistic.e + ", existing=" + existingById.f + ", incoming=" + statistic.f);
            }
            BY_STAT_ID.put(statId, statistic);
        }

        Comparator<Statistic> byId = new Comparator<Statistic>() {
            public int compare(Statistic a, Statistic b) {
                return a.e - b.e;
            }
        };

        Iterator<List<Statistic>> categories = BY_CATEGORY.values().iterator();
        while (categories.hasNext()) {
            Collections.sort(categories.next(), byId);
        }
    }

    private static void initializeCategoryLists() {
        ensureCategoryList(GENERAL);
        ensureCategoryList(BLOCK_MINED);
        ensureCategoryList(ITEM_USED);
        ensureCategoryList(ITEM_CRAFTED);
        ensureCategoryList(ITEM_DEPLETED);
        ensureCategoryList(ITEM_PICKUP);
    }

    private static void ensureCategoryList(int category) {
        Integer key = Integer.valueOf(category);
        if (!BY_CATEGORY.containsKey(key)) {
            BY_CATEGORY.put(key, new ArrayList<Statistic>());
        }
    }

    private static void clearCategoryLists() {
        Iterator<List<Statistic>> iterator = BY_CATEGORY.values().iterator();
        while (iterator.hasNext()) {
            iterator.next().clear();
        }
    }

    private static int classifyCategory(ResourceLocation key) {
        if (key == null) {
            return NO_CATEGORY;
        }

        String path = key.getPath();
        if (path == null) {
            return NO_CATEGORY;
        }

        if (path.startsWith(StatisticKeys.PREFIX_GENERIC)) {
            return GENERAL;
        }
        if (path.startsWith(StatisticKeys.PREFIX_BLOCK_MINED)) {
            return BLOCK_MINED;
        }
        if (path.startsWith(StatisticKeys.PREFIX_ITEM_USED)) {
            return ITEM_USED;
        }
        if (path.startsWith(StatisticKeys.PREFIX_ITEM_CRAFTED)) {
            return ITEM_CRAFTED;
        }
        if (path.startsWith(StatisticKeys.PREFIX_ITEM_DEPLETED)) {
            return ITEM_DEPLETED;
        }
        if (path.startsWith(StatisticKeys.PREFIX_ITEM_PICKUP)) {
            return ITEM_PICKUP;
        }

        return NO_CATEGORY;
    }

    private static ResourceLocation resolveIdentifier(String any, boolean allowCategoryCanonicalization) {
        if (any == null) {
            return null;
        }

        String input = any.trim();
        if (input.length() == 0) {
            return null;
        }

        ResourceLocation direct = parse(input);
        if (direct != null && BY_KEY.containsKey(direct)) {
            return direct;
        }

        if (input.indexOf(':') < 0) {
            ResourceLocation namespaced = new ResourceLocation("minecraft", sanitizePath(input));
            if (BY_KEY.containsKey(namespaced)) {
                return namespaced;
            }
        }

        ResourceLocation aliased = ALIASES.get(normalizeAlias(input));
        if (aliased != null) {
            return aliased;
        }

        if (allowCategoryCanonicalization) {
            ResourceLocation canonicalized = canonicalizeCategoryObjectIdentifier(input);
            if (canonicalized != null && BY_KEY.containsKey(canonicalized)) {
                return canonicalized;
            }
        }

        return null;
    }

    private static ResourceLocation canonicalizeCategoryObjectIdentifier(String any) {
        ResourceLocation input = parse(any);
        if (input == null) {
            input = new ResourceLocation("minecraft", any);
        }

        String path = sanitizePath(input.getPath());
        int slash = path.lastIndexOf('/');
        if (slash <= 0 || slash >= path.length() - 1) {
            return null;
        }

        String prefix = path.substring(0, slash + 1);
        String objectPath = path.substring(slash + 1);
        String canonicalObjectPath = null;

        if (StatisticKeys.PREFIX_BLOCK_MINED.equals(prefix)) {
            canonicalObjectPath = canonicalizeBlockPath(objectPath);
        } else if (StatisticKeys.PREFIX_ITEM_USED.equals(prefix) || StatisticKeys.PREFIX_ITEM_CRAFTED.equals(prefix) || StatisticKeys.PREFIX_ITEM_DEPLETED.equals(prefix) || StatisticKeys.PREFIX_ITEM_PICKUP.equals(prefix)) {
            canonicalObjectPath = canonicalizeItemPath(objectPath);
        }

        if (canonicalObjectPath == null || canonicalObjectPath.length() == 0) {
            return null;
        }

        return new ResourceLocation("minecraft", prefix + canonicalObjectPath);
    }

    private static String canonicalizeBlockPath(String objectPath) {
        if (objectPath == null || objectPath.length() == 0) {
            return null;
        }

        ResourceLocation[] candidates = new ResourceLocation[] {
            parse(objectPath),
            new ResourceLocation("minecraft", sanitizePath(objectPath))
        };

        for (int i = 0; i < candidates.length; ++i) {
            ResourceLocation candidate = candidates[i];
            if (candidate == null) {
                continue;
            }
            Block block = Registries.BLOCK.get(candidate);
            if (block != null) {
                ResourceLocation canonical = Registries.BLOCK.getKey(block);
                if (canonical == null) {
                    canonical = BlockRegistry.getKey(block);
                }
                if (canonical != null) {
                    return sanitizePath(canonical.getPath());
                }
            }
        }

        String canonicalFromBlockRegistry = BlockRegistry.canonicalizeIdentifier(objectPath);
        ResourceLocation canonical = parse(canonicalFromBlockRegistry);
        return canonical == null ? sanitizePath(objectPath) : sanitizePath(canonical.getPath());
    }

    private static String canonicalizeItemPath(String objectPath) {
        if (objectPath == null || objectPath.length() == 0) {
            return null;
        }

        ResourceLocation[] candidates = new ResourceLocation[] {
            parse(objectPath),
            new ResourceLocation("minecraft", sanitizePath(objectPath))
        };

        for (int i = 0; i < candidates.length; ++i) {
            ResourceLocation candidate = candidates[i];
            if (candidate == null) {
                continue;
            }

            Item item = Registries.ITEM.get(candidate);
            if (item != null) {
                ResourceLocation canonical = Registries.ITEM.getKey(item);
                if (canonical == null) {
                    canonical = ItemRegistry.getKey(item);
                }
                if (canonical != null) {
                    return sanitizePath(canonical.getPath());
                }
            }

            // Variant defaults are used only for command/input normalization compatibility.
            // Stat IDs remain tied to the underlying item legacy id and are not split by variant.
            if (VariantDefaults.get(candidate.toString()) >= 0) {
                Item variantItem = Registries.ITEM.get(candidate);
                if (variantItem != null) {
                    ResourceLocation canonical = Registries.ITEM.getKey(variantItem);
                    if (canonical != null) {
                        return sanitizePath(canonical.getPath());
                    }
                }
            }
        }

        String canonicalFromItemRegistry = ItemRegistry.canonicalizeIdentifier(objectPath);
        ResourceLocation canonical = parse(canonicalFromItemRegistry);
        return canonical == null ? sanitizePath(objectPath) : sanitizePath(canonical.getPath());
    }

    private static void addAliases(ResourceLocation canonical) {
        if (canonical == null) {
            return;
        }

        addAlias(canonical.toString(), canonical);
        addAlias(canonical.getPath(), canonical);
        addAlias(canonical.getPath().replace('/', '.'), canonical);
    }

    private static void addAlias(String alias, ResourceLocation canonical) {
        if (alias == null || canonical == null) {
            return;
        }

        String normalized = normalizeAlias(alias);
        if (normalized.length() == 0) {
            return;
        }

        ResourceLocation existing = ALIASES.get(normalized);
        if (existing == null) {
            ALIASES.put(normalized, canonical);
        } else if (!existing.equals(canonical)) {
            warn("Alias collision alias=" + alias + ", existing=" + existing + ", incoming=" + canonical);
        }
    }

    private static String sanitizePath(String path) {
        if (path == null) {
            return "";
        }

        String sanitized = path.trim().toLowerCase(Locale.ROOT);
        sanitized = sanitized.replace(' ', '_');
        sanitized = sanitized.replace('\\', '/');
        sanitized = sanitized.replace(':', '/');
        while (sanitized.indexOf("//") >= 0) {
            sanitized = sanitized.replace("//", "/");
        }
        return sanitized;
    }

    private static String toSnake(String in) {
        if (in == null) {
            return "";
        }

        StringBuilder out = new StringBuilder(in.length() + 8);
        for (int i = 0; i < in.length(); ++i) {
            char c = in.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    out.append('_');
                }
                out.append(Character.toLowerCase(c));
            } else if (c == '.') {
                out.append('_');
            } else {
                out.append(Character.toLowerCase(c));
            }
        }

        return out.toString();
    }

    private static String normalizeAlias(String alias) {
        if (alias == null) {
            return "";
        }

        String normalized = alias.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replace(' ', '_');
        normalized = normalized.replace('\\', '/');
        while (normalized.indexOf("//") >= 0) {
            normalized = normalized.replace("//", "/");
        }
        return normalized;
    }

    private static ResourceLocation parse(String raw) {
        if (raw == null || raw.length() == 0) {
            return null;
        }

        try {
            return new ResourceLocation(raw);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int fingerprint(Set<ResourceLocation> keys) {
        int hash = 1;
        if (keys == null) {
            return hash;
        }

        Iterator<ResourceLocation> iterator = keys.iterator();
        while (iterator.hasNext()) {
            ResourceLocation key = iterator.next();
            hash = 31 * hash + (key == null ? 0 : key.hashCode());
        }

        return hash;
    }

    private static void updateFingerprints() {
        lastBlockFingerprint = fingerprint(Registries.BLOCK.keys());
        lastItemFingerprint = fingerprint(Registries.ITEM.keys());
        lastStatFingerprint = fingerprint(Registries.STAT.keys());
    }

    private static void warn(String message) {
        System.err.println("[StatisticRegistryApi] " + message);
    }
}
