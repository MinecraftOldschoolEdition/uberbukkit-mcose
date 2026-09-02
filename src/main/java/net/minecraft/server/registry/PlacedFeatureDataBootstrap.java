package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Loads supported legacy placements from the 26.3 worldgen/placed_feature path. */
public final class PlacedFeatureDataBootstrap {
    public static final ResourceLocation CLAY = key("clay");
    public static final ResourceLocation LAKE_WATER = key("lake_water");
    public static final ResourceLocation LAKE_LAVA = key("lake_lava");
    public static final ResourceLocation SPRING_WATER = key("spring_water");
    public static final ResourceLocation SPRING_LAVA = key("spring_lava");
    public static final ResourceLocation SPRING_LAVA_CLASSIC_HELL =
            key("spring_lava_classic_hell");
    public static final ResourceLocation SPRING_OPEN = key("spring_open");
    public static final ResourceLocation FREEZE_TOP_LAYER =
            ConfiguredFeatureDataBootstrap.FREEZE_TOP_LAYER;

    public static final List<ResourceLocation> REQUIRED_FEATURES =
            Collections.unmodifiableList(Arrays.asList(
                    ConfiguredFeatureDataBootstrap.ORE_DIRT,
                    ConfiguredFeatureDataBootstrap.ORE_GRAVEL,
                    ConfiguredFeatureDataBootstrap.ORE_COAL,
                    ConfiguredFeatureDataBootstrap.ORE_IRON,
                    ConfiguredFeatureDataBootstrap.ORE_GOLD,
                    ConfiguredFeatureDataBootstrap.ORE_REDSTONE,
                    ConfiguredFeatureDataBootstrap.ORE_DIAMOND,
                    ConfiguredFeatureDataBootstrap.ORE_LAPIS,
                    CLAY,
                    ConfiguredFeatureDataBootstrap.MONSTER_ROOM,
                    LAKE_WATER, LAKE_LAVA,
                    SPRING_WATER, SPRING_LAVA,
                    SPRING_LAVA_CLASSIC_HELL, SPRING_OPEN,
                    FREEZE_TOP_LAYER));

    private static volatile Map<ResourceLocation, PlacedFeatureDefinition> values =
            Collections.emptyMap();
    private static boolean initialized;

    private PlacedFeatureDataBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        ConfiguredFeatureDataBootstrap.initialize();
        Map<ResourceLocation, PlacedFeatureDefinition> decoded = load(null);
        if (!Registries.PLACED_FEATURE.replaceAllAtomic(decoded)) {
            throw new IllegalStateException(
                    "Could not atomically publish placed worldgen features");
        }
        values = Collections.unmodifiableMap(
                new LinkedHashMap<ResourceLocation, PlacedFeatureDefinition>(decoded));
        initialized = true;
        System.out.println("[PlacedFeatureDataBootstrap] Loaded "
                + decoded.size() + " placed features");
    }

    public static PlacedFeatureDefinition get(ResourceLocation key) {
        initialize();
        return values.get(key);
    }

    public static Map<ResourceLocation, PlacedFeatureDefinition> rawFeatures() {
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

    static Map<ResourceLocation, PlacedFeatureDefinition> loadForTests(
            RegistryDataLoader.ResourceProvider provider) {
        ConfiguredFeatureDataBootstrap.initialize();
        return load(provider);
    }

    private static Map<ResourceLocation, PlacedFeatureDefinition> load(
            RegistryDataLoader.ResourceProvider provider) {
        RegistryDataLoader.Decoder<PlacedFeatureDefinition> decoder =
                new RegistryDataLoader.Decoder<PlacedFeatureDefinition>() {
                    public PlacedFeatureDefinition decode(
                            ResourceLocation key, JsonObject json) {
                        return PlacedFeatureCodec.decode(key, json);
                    }
                };
        Map<ResourceLocation, PlacedFeatureDefinition> decoded = provider == null
                ? RegistryDataLoader.loadAll("worldgen/placed_feature", decoder)
                : RegistryDataLoader.loadAll(
                        "worldgen/placed_feature", provider, decoder);
        for (int i = 0; i < REQUIRED_FEATURES.size(); i++) {
            ResourceLocation required = REQUIRED_FEATURES.get(i);
            PlacedFeatureDefinition definition = decoded.get(required);
            if (definition == null || !required.equals(definition.getId())) {
                throw new IllegalStateException(
                        "Missing required placed feature " + required);
            }
        }
        return decoded;
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
