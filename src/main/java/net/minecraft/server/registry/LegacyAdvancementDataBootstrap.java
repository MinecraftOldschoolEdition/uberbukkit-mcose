package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.Achievement;
import net.minecraft.server.AchievementList;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Loads, validates, and binds the fixed legacy advancement graph at startup. */
public final class LegacyAdvancementDataBootstrap {
    private static final Map<ResourceLocation, Integer> REQUIRED_IDENTITIES = identities();
    private static volatile Map<ResourceLocation, LegacyAdvancementDefinition> definitions =
            Collections.emptyMap();
    private static boolean initialized;

    private LegacyAdvancementDataBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        Map<ResourceLocation, LegacyAdvancementDefinition> loaded = load(null);
        Map<ResourceLocation, Achievement> bindings = validateRuntimeIdentities(loaded);
        if (!Registries.ADVANCEMENT.replaceAllAtomic(loaded)) {
            throw new IllegalStateException(
                    "Could not atomically publish legacy advancement definitions");
        }
        applyBindings(loaded, bindings);
        definitions = Collections.unmodifiableMap(
                new LinkedHashMap<ResourceLocation, LegacyAdvancementDefinition>(loaded));
        initialized = true;
    }

    public static LegacyAdvancementDefinition get(ResourceLocation key) {
        initialize();
        return definitions.get(key);
    }

    public static Map<ResourceLocation, LegacyAdvancementDefinition> definitions() {
        initialize();
        return definitions;
    }

    public static void writeCanonicalData(DataOutputStream out) throws Exception {
        initialize();
        List<ResourceLocation> keys = sortedKeys(definitions.keySet());
        out.writeInt(keys.size());
        for (ResourceLocation key : keys) {
            LegacyAdvancementDefinition definition = definitions.get(key);
            out.writeUTF(key.toString());
            out.writeInt(REQUIRED_IDENTITIES.get(key).intValue());
            out.writeBoolean(definition.getParent() != null);
            if (definition.getParent() != null) {
                out.writeUTF(definition.getParent().toString());
            }
            out.writeUTF(definition.getIcon().toString());
            out.writeInt(definition.getIconLegacyId());
            out.writeInt(definition.getIconLegacyDamage());
            out.writeUTF(definition.getTitleTranslationKey());
            out.writeUTF(definition.getDescriptionTranslationKey());
            out.writeUTF(definition.getFrame());
            out.writeBoolean(definition.shouldShowToast());
            out.writeBoolean(definition.shouldAnnounceToChat());
            out.writeBoolean(definition.isHidden());
            out.writeInt(definition.getColumn());
            out.writeInt(definition.getRow());

            List<String> criterionNames =
                    new ArrayList<String>(definition.getCriterionNames());
            Collections.sort(criterionNames);
            out.writeInt(criterionNames.size());
            for (String criterion : criterionNames) {
                out.writeUTF(criterion);
                out.writeUTF(definition.getCriteria().get(criterion).toString());
            }
            List<List<String>> requirements = canonicalRequirements(
                    definition.getRequirements());
            out.writeInt(requirements.size());
            for (List<String> group : requirements) {
                out.writeInt(group.size());
                for (String criterion : group) out.writeUTF(criterion);
            }
        }
    }

    /** Independent pre-schema capture used to prove paired canonical encoding. */
    public static String captureDefinitionFingerprint() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeCanonicalData(out);
            out.flush();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (int i = 0; i < digest.length; i++) {
                int value = digest[i] & 255;
                if (value < 16) hex.append('0');
                hex.append(Integer.toHexString(value));
            }
            return hex.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("Could not fingerprint legacy advancements", failure);
        }
    }

    static Map<ResourceLocation, LegacyAdvancementDefinition> loadForTests(
            RegistryDataLoader.ResourceProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Resource provider cannot be null");
        }
        return load(provider);
    }

    static void validateGraphForTests(
            Map<ResourceLocation, LegacyAdvancementDefinition> decoded) {
        validateExactKeys(decoded);
        validateGraph(decoded);
    }

    private static Map<ResourceLocation, LegacyAdvancementDefinition> load(
            RegistryDataLoader.ResourceProvider provider) {
        RegistryDataLoader.Decoder<LegacyAdvancementDefinition> decoder =
                new RegistryDataLoader.Decoder<LegacyAdvancementDefinition>() {
                    public LegacyAdvancementDefinition decode(
                            ResourceLocation key,
                            JsonObject json) {
                        return LegacyAdvancementCodec.decode(key, json);
                    }
                };
        Map<ResourceLocation, LegacyAdvancementDefinition> decoded = provider == null
                ? RegistryDataLoader.loadAll("advancement", decoder)
                : RegistryDataLoader.loadAll("advancement", provider, decoder);
        validateExactKeys(decoded);
        validateGraph(decoded);
        return decoded;
    }

    private static void validateExactKeys(
            Map<ResourceLocation, LegacyAdvancementDefinition> decoded) {
        if (decoded == null || !decoded.keySet().equals(REQUIRED_IDENTITIES.keySet())) {
            Set<ResourceLocation> actual = decoded == null
                    ? Collections.<ResourceLocation>emptySet() : decoded.keySet();
            throw new IllegalStateException("Legacy advancement data must contain exactly "
                    + REQUIRED_IDENTITIES.keySet() + " (was " + actual + ")");
        }
    }

    private static void validateGraph(
            Map<ResourceLocation, LegacyAdvancementDefinition> decoded) {
        for (LegacyAdvancementDefinition definition : decoded.values()) {
            ResourceLocation parent = definition.getParent();
            if (parent != null && !decoded.containsKey(parent)) {
                throw new IllegalStateException("Advancement " + definition.getId()
                        + " has missing parent " + parent);
            }
        }
        Map<ResourceLocation, Integer> states = new HashMap<ResourceLocation, Integer>();
        for (ResourceLocation key : decoded.keySet()) visit(key, decoded, states);
    }

    private static void visit(
            ResourceLocation key,
            Map<ResourceLocation, LegacyAdvancementDefinition> decoded,
            Map<ResourceLocation, Integer> states) {
        Integer state = states.get(key);
        if (state != null && state.intValue() == 2) return;
        if (state != null && state.intValue() == 1) {
            throw new IllegalStateException("Legacy advancement parent cycle at " + key);
        }
        states.put(key, Integer.valueOf(1));
        ResourceLocation parent = decoded.get(key).getParent();
        if (parent != null) visit(parent, decoded, states);
        states.put(key, Integer.valueOf(2));
    }

    private static Map<ResourceLocation, Achievement> validateRuntimeIdentities(
            Map<ResourceLocation, LegacyAdvancementDefinition> loaded) {
        if (!AchievementRegistryApi.keys().equals(REQUIRED_IDENTITIES.keySet())) {
            throw new IllegalStateException(
                    "Runtime achievement keys do not match fixed legacy identities");
        }
        Map<ResourceLocation, Achievement> bindings =
                new LinkedHashMap<ResourceLocation, Achievement>();
        for (Map.Entry<ResourceLocation, Integer> entry : REQUIRED_IDENTITIES.entrySet()) {
            Achievement achievement = AchievementRegistryApi.get(entry.getKey());
            if (achievement == null || achievement.e != entry.getValue().intValue()) {
                throw new IllegalStateException("Achievement " + entry.getKey()
                        + " must retain stat id " + entry.getValue());
            }
            bindings.put(entry.getKey(), achievement);
        }
        return bindings;
    }

    private static void applyBindings(
            Map<ResourceLocation, LegacyAdvancementDefinition> loaded,
            Map<ResourceLocation, Achievement> bindings) {
        int minColumn = Integer.MAX_VALUE;
        int minRow = Integer.MAX_VALUE;
        int maxColumn = Integer.MIN_VALUE;
        int maxRow = Integer.MIN_VALUE;
        for (Map.Entry<ResourceLocation, LegacyAdvancementDefinition> entry : loaded.entrySet()) {
            LegacyAdvancementDefinition definition = entry.getValue();
            Achievement parent = definition.getParent() == null
                    ? null : bindings.get(definition.getParent());
            bindings.get(entry.getKey()).applyDefinition(definition, parent);
            minColumn = Math.min(minColumn, definition.getColumn());
            minRow = Math.min(minRow, definition.getRow());
            maxColumn = Math.max(maxColumn, definition.getColumn());
            maxRow = Math.max(maxRow, definition.getRow());
        }
        AchievementList.a = minColumn - 3;
        AchievementList.b = minRow - 3;
        AchievementList.c = maxColumn + 3;
        AchievementList.d = maxRow + 3;
    }

    private static List<ResourceLocation> sortedKeys(Set<ResourceLocation> keys) {
        List<ResourceLocation> sorted = new ArrayList<ResourceLocation>(keys);
        Collections.sort(sorted, new Comparator<ResourceLocation>() {
            public int compare(ResourceLocation left, ResourceLocation right) {
                return left.toString().compareTo(right.toString());
            }
        });
        return sorted;
    }

    private static List<List<String>> canonicalRequirements(
            List<List<String>> requirements) {
        List<List<String>> canonical = new ArrayList<List<String>>();
        for (List<String> group : requirements) {
            List<String> sorted = new ArrayList<String>(
                    new java.util.TreeSet<String>(group));
            if (!canonical.contains(sorted)) canonical.add(sorted);
        }
        Collections.sort(canonical, new Comparator<List<String>>() {
            public int compare(List<String> left, List<String> right) {
                int common = Math.min(left.size(), right.size());
                for (int i = 0; i < common; i++) {
                    int compared = left.get(i).compareTo(right.get(i));
                    if (compared != 0) return compared;
                }
                return Integer.compare(left.size(), right.size());
            }
        });
        return canonical;
    }

    private static Map<ResourceLocation, Integer> identities() {
        LinkedHashMap<ResourceLocation, Integer> out =
                new LinkedHashMap<ResourceLocation, Integer>();
        put(out, AchievementKeys.OPEN_INVENTORY, 0);
        put(out, AchievementKeys.MINE_WOOD, 1);
        put(out, AchievementKeys.BUILD_WORKBENCH, 2);
        put(out, AchievementKeys.BUILD_PICKAXE, 3);
        put(out, AchievementKeys.BUILD_FURNACE, 4);
        put(out, AchievementKeys.ACQUIRE_IRON, 5);
        put(out, AchievementKeys.BUILD_BETTER_PICKAXE, 9);
        put(out, AchievementKeys.BUILD_HOE, 6);
        put(out, AchievementKeys.MAKE_BREAD, 7);
        put(out, AchievementKeys.BAKE_CAKE, 8);
        put(out, AchievementKeys.GROW_WHEAT, 36);
        put(out, AchievementKeys.COOK_FISH, 10);
        put(out, AchievementKeys.COOK_BACON, 16);
        put(out, AchievementKeys.DIAMONDS, 17);
        put(out, AchievementKeys.OBSIDIAN, 18);
        put(out, AchievementKeys.HOT_STUFF, 19);
        put(out, AchievementKeys.PORTAL, 20);
        put(out, AchievementKeys.GHAST_HUNTER, 21);
        put(out, AchievementKeys.BLAZING_HELL, 22);
        put(out, AchievementKeys.BUILD_SWORD, 12);
        put(out, AchievementKeys.KILL_ENEMY, 13);
        put(out, AchievementKeys.KILL_COW, 14);
        put(out, AchievementKeys.FLY_PIG, 15);
        put(out, AchievementKeys.OVERKILL, 39);
        put(out, AchievementKeys.BUILD_BOW, 23);
        put(out, AchievementKeys.SNIPE_SKELETON, 24);
        put(out, AchievementKeys.FULL_IRON, 25);
        put(out, AchievementKeys.FULL_DIAMOND, 26);
        put(out, AchievementKeys.SLEEP_IN_BED, 27);
        put(out, AchievementKeys.EGG_HUNT, 37);
        put(out, AchievementKeys.SHEAR_SHEEP, 34);
        put(out, AchievementKeys.RAINBOW_WOOL, 35);
        put(out, AchievementKeys.CRAFT_MAP, 28);
        put(out, AchievementKeys.BOOKSHELF, 29);
        put(out, AchievementKeys.JUKEBOX, 30);
        put(out, AchievementKeys.EXPLOSION, 31);
        put(out, AchievementKeys.PISTON, 32);
        put(out, AchievementKeys.REPEATER, 33);
        put(out, AchievementKeys.ON_A_RAIL, 11);
        put(out, AchievementKeys.BOAT_TRAVEL, 38);
        return Collections.unmodifiableMap(out);
    }

    private static void put(
            Map<ResourceLocation, Integer> out,
            ResourceLocation key,
            int suffix) {
        out.put(key, Integer.valueOf(5242880 + suffix));
    }
}
