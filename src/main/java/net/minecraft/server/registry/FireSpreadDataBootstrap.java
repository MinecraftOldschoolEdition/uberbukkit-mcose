package net.minecraft.server.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.Block;
import net.minecraft.server.FireSpreadRule;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Loads and atomically publishes block-keyed fire-spread odds. */
public final class FireSpreadDataBootstrap {
    static final List<ResourceLocation> REQUIRED_BLOCKS =
            Collections.unmodifiableList(Arrays.asList(
                    key("oak_planks"),
                    key("oak_fence"),
                    key("oak_stairs"),
                    key("oak_log"),
                    key("oak_leaves"),
                    key("bookshelf"),
                    key("tnt"),
                    key("tall_grass"),
                    key("white_wool")));
    private static boolean initialized;

    private FireSpreadDataBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        publish(prepare(null));
        initialized = true;
        System.out.println("[FireSpreadDataBootstrap] Registered "
                + FireSpreadRegistryApi.size() + " fire-spread rules");
    }

    public static synchronized void reload() {
        reload(null);
    }

    static synchronized void reload(
            RegistryDataLoader.ResourceProvider provider) {
        publish(prepare(provider));
        initialized = true;
    }

    static Map<ResourceLocation, FireSpreadRule> loadForTests(
            RegistryDataLoader.ResourceProvider provider) {
        return prepare(provider).rules;
    }

    /** Pure prepare phase used by focused rollback and parity tests. */
    static PreparedFireSpreadData prepare(
            RegistryDataLoader.ResourceProvider provider) {
        BlockRegistry.bootstrapFromBlocksList();
        long revision = BlockRegistry.registrationRevision();
        RegistryDataLoader.Decoder<FireSpreadRule> decoder =
                new RegistryDataLoader.Decoder<FireSpreadRule>() {
                    public FireSpreadRule decode(
                            ResourceLocation key,
                            JsonObject json) {
                        return decodeRule(key, json);
                    }
                };
        Map<ResourceLocation, FireSpreadRule> decoded = provider == null
                ? RegistryDataLoader.loadAll("fire_spread", decoder)
                : RegistryDataLoader.loadAll("fire_spread", provider, decoder);
        if (revision != BlockRegistry.registrationRevision()) {
            throw new IllegalStateException(
                    "Block registry changed while fire-spread data was decoded");
        }

        for (int i = 0; i < REQUIRED_BLOCKS.size(); i++) {
            ResourceLocation required = REQUIRED_BLOCKS.get(i);
            if (!decoded.containsKey(required)) {
                throw new IllegalStateException(
                        "Missing required fire-spread rule " + required);
            }
        }

        LinkedHashMap<ResourceLocation, FireSpreadRule> rules =
                new LinkedHashMap<ResourceLocation, FireSpreadRule>();
        for (Map.Entry<ResourceLocation, FireSpreadRule> entry
                : decoded.entrySet()) {
            ResourceLocation requested = entry.getKey();
            Block block = BlockRegistry.get(requested);
            ResourceLocation canonical = block == null
                    ? null : BlockRegistry.getKey(block);
            if (block == null || canonical == null
                    || !requested.equals(canonical)) {
                throw new IllegalArgumentException(
                        "Fire-spread data contains missing or non-canonical block "
                                + requested);
            }
            rules.put(requested, entry.getValue());
        }

        if (revision != BlockRegistry.registrationRevision()) {
            throw new IllegalStateException(
                    "Block registry changed while fire-spread data was prepared");
        }
        return new PreparedFireSpreadData(revision, rules);
    }

    private static FireSpreadRule decodeRule(
            ResourceLocation key,
            JsonObject json) {
        requireOnlyFields(json, key, "ignite_odds", "burn_odds");
        int igniteOdds = nonNegative(exactInteger(
                requiredValue(json, "ignite_odds", key),
                key + " ignite_odds"), key + " ignite_odds");
        int burnOdds = nonNegative(exactInteger(
                requiredValue(json, "burn_odds", key),
                key + " burn_odds"), key + " burn_odds");
        return new FireSpreadRule(igniteOdds, burnOdds);
    }

    private static void publish(PreparedFireSpreadData prepared) {
        if (!FireSpreadRegistryApi.publishReplacementAtomic(
                prepared.blockRegistryRevision, prepared.rules)) {
            throw new IllegalStateException(
                    "Prepared fire-spread generation was rejected");
        }
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }

    private static JsonElement requiredValue(
            JsonObject json,
            String field,
            ResourceLocation key) {
        JsonElement value = json.get(field);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Fire-spread rule " + key
                            + " is missing required field '" + field + "'");
        }
        return value;
    }

    private static int exactInteger(
            JsonElement raw,
            String description) {
        if (raw == null || !raw.isJsonPrimitive()) {
            throw new IllegalArgumentException(description
                    + " must be an integer");
        }
        JsonPrimitive primitive = raw.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(description
                    + " must be an integer");
        }
        try {
            return new BigDecimal(primitive.getAsString()).intValueExact();
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(description
                    + " must be an integer", failure);
        }
    }

    private static int nonNegative(int value, String description) {
        if (value < 0) {
            throw new IllegalArgumentException(description
                    + " must be non-negative (was " + value + ")");
        }
        return value;
    }

    private static void requireOnlyFields(
            JsonObject json,
            ResourceLocation key,
            String... allowedFields) {
        Set<String> allowed = new HashSet<String>(
                Arrays.asList(allowedFields));
        for (Map.Entry<String, JsonElement> field : json.entrySet()) {
            if (!allowed.contains(field.getKey())) {
                throw new IllegalArgumentException("Fire-spread rule " + key
                        + " contains unsupported field '" + field.getKey()
                        + "'");
            }
        }
    }

    static final class PreparedFireSpreadData {
        final long blockRegistryRevision;
        final Map<ResourceLocation, FireSpreadRule> rules;

        PreparedFireSpreadData(
                long blockRegistryRevision,
                Map<ResourceLocation, FireSpreadRule> rules) {
            this.blockRegistryRevision = blockRegistryRevision;
            this.rules = Collections.unmodifiableMap(
                    new LinkedHashMap<ResourceLocation, FireSpreadRule>(rules));
        }
    }
}
