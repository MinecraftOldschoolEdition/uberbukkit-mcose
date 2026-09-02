package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Loads the configured-feature registry from the 26.3 worldgen/feature path. */
public final class ConfiguredFeatureDataBootstrap {
    public static final ResourceLocation ORE_DIRT = key("ore_dirt");
    public static final ResourceLocation ORE_GRAVEL = key("ore_gravel");
    public static final ResourceLocation ORE_COAL = key("ore_coal");
    public static final ResourceLocation ORE_IRON = key("ore_iron");
    public static final ResourceLocation ORE_GOLD = key("ore_gold");
    public static final ResourceLocation ORE_REDSTONE = key("ore_redstone");
    public static final ResourceLocation ORE_DIAMOND = key("ore_diamond");
    public static final ResourceLocation ORE_LAPIS = key("ore_lapis");
    public static final ResourceLocation CLAY = key("clay");
    public static final ResourceLocation MONSTER_ROOM = key("monster_room");
    public static final ResourceLocation LAKE_WATER = key("lake_water");
    public static final ResourceLocation LAKE_LAVA = key("lake_lava");
    public static final ResourceLocation SPRING_WATER = key("spring_water");
    public static final ResourceLocation SPRING_LAVA_OVERWORLD =
            key("spring_lava_overworld");
    public static final ResourceLocation SPRING_NETHER_OPEN =
            key("spring_nether_open");
    public static final ResourceLocation FREEZE_TOP_LAYER =
            key("freeze_top_layer");

    public static final List<ResourceLocation> REQUIRED_FEATURES =
            Collections.unmodifiableList(Arrays.asList(
                    ORE_DIRT, ORE_GRAVEL, ORE_COAL, ORE_IRON,
                    ORE_GOLD, ORE_REDSTONE, ORE_DIAMOND, ORE_LAPIS,
                    CLAY, MONSTER_ROOM, LAKE_WATER, LAKE_LAVA, SPRING_WATER,
                    SPRING_LAVA_OVERWORLD, SPRING_NETHER_OPEN,
                    FREEZE_TOP_LAYER));

    private static volatile Map<ResourceLocation, ConfiguredFeatureDefinition> values =
            Collections.emptyMap();
    private static boolean initialized;

    private ConfiguredFeatureDataBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        FeatureRegistryBootstrap.initialize();
        Map<ResourceLocation, ConfiguredFeatureDefinition> decoded = load(null);
        if (!Registries.CONFIGURED_FEATURE.replaceAllAtomic(decoded)) {
            throw new IllegalStateException(
                    "Could not atomically publish configured worldgen features");
        }
        values = Collections.unmodifiableMap(
                new LinkedHashMap<ResourceLocation, ConfiguredFeatureDefinition>(decoded));
        initialized = true;
        System.out.println("[ConfiguredFeatureDataBootstrap] Loaded "
                + decoded.size() + " configured features");
    }

    public static ConfiguredFeatureDefinition get(ResourceLocation key) {
        initialize();
        return values.get(key);
    }

    public static Map<ResourceLocation, ConfiguredFeatureDefinition> rawFeatures() {
        initialize();
        return values;
    }

    public static int size() {
        initialize();
        return values.size();
    }

    public static List<ResourceLocation> builtInKeys() {
        return REQUIRED_FEATURES;
    }

    static Map<ResourceLocation, ConfiguredFeatureDefinition> loadForTests(
            RegistryDataLoader.ResourceProvider provider) {
        FeatureRegistryBootstrap.initialize();
        return load(provider);
    }

    private static Map<ResourceLocation, ConfiguredFeatureDefinition> load(
            RegistryDataLoader.ResourceProvider provider) {
        RegistryDataLoader.Decoder<ConfiguredFeatureDefinition> decoder =
                new RegistryDataLoader.Decoder<ConfiguredFeatureDefinition>() {
                    public ConfiguredFeatureDefinition decode(
                            ResourceLocation key, JsonObject json) {
                        return ConfiguredFeatureCodec.decode(key, json);
                    }
                };
        Map<ResourceLocation, ConfiguredFeatureDefinition> decoded = provider == null
                ? RegistryDataLoader.loadAll("worldgen/feature", decoder)
                : RegistryDataLoader.loadAll("worldgen/feature", provider, decoder);
        for (int i = 0; i < REQUIRED_FEATURES.size(); i++) {
            ResourceLocation required = REQUIRED_FEATURES.get(i);
            ConfiguredFeatureDefinition definition = decoded.get(required);
            if (definition == null || !required.equals(definition.getId())) {
                throw new IllegalStateException(
                        "Missing required configured feature " + required);
            }
        }
        return decoded;
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
