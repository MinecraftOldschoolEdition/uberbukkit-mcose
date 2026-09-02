package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Loads and atomically publishes the 26.3-style worldgen/carver registry. */
public final class ConfiguredCarverDataBootstrap {
    public static final ResourceLocation CAVE = key("cave");
    public static final ResourceLocation NETHER_CAVE = key("nether_cave");
    public static final ResourceLocation SKY_CAVE = key("sky_cave");
    public static final List<ResourceLocation> REQUIRED_CARVERS =
            Collections.unmodifiableList(Arrays.asList(CAVE, NETHER_CAVE, SKY_CAVE));

    private static volatile Map<ResourceLocation, ConfiguredCarverDefinition> definitions =
            Collections.emptyMap();
    private static boolean initialized;

    private ConfiguredCarverDataBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        CarverRegistryBootstrap.initialize();
        Map<ResourceLocation, ConfiguredCarverDefinition> loaded = load(null);
        if (!Registries.CONFIGURED_CARVER.replaceAllAtomic(loaded)) {
            throw new IllegalStateException(
                    "Could not atomically publish configured carver definitions");
        }
        definitions = Collections.unmodifiableMap(
                new LinkedHashMap<ResourceLocation, ConfiguredCarverDefinition>(loaded));
        initialized = true;
    }

    public static ConfiguredCarverDefinition get(ResourceLocation key) {
        initialize();
        return definitions.get(key);
    }

    public static Map<ResourceLocation, ConfiguredCarverDefinition> rawDefinitions() {
        initialize();
        return definitions;
    }

    public static Set<ResourceLocation> keys() {
        initialize();
        return definitions.keySet();
    }

    public static Collection<ConfiguredCarverDefinition> values() {
        initialize();
        return definitions.values();
    }

    public static int size() {
        initialize();
        return definitions.size();
    }

    public static List<ResourceLocation> builtInKeys() {
        return REQUIRED_CARVERS;
    }

    static Map<ResourceLocation, ConfiguredCarverDefinition> loadForTests(
            RegistryDataLoader.ResourceProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Resource provider cannot be null");
        }
        CarverRegistryBootstrap.initialize();
        return load(provider);
    }

    private static Map<ResourceLocation, ConfiguredCarverDefinition> load(
            RegistryDataLoader.ResourceProvider provider) {
        RegistryDataLoader.Decoder<ConfiguredCarverDefinition> decoder =
                new RegistryDataLoader.Decoder<ConfiguredCarverDefinition>() {
                    public ConfiguredCarverDefinition decode(
                            ResourceLocation key, JsonObject json) {
                        return ConfiguredCarverCodec.decode(key, json);
                    }
                };
        Map<ResourceLocation, ConfiguredCarverDefinition> loaded = provider == null
                ? RegistryDataLoader.loadAll("worldgen/carver", decoder)
                : RegistryDataLoader.loadAll("worldgen/carver", provider, decoder);
        for (int i = 0; i < REQUIRED_CARVERS.size(); i++) {
            ResourceLocation required = REQUIRED_CARVERS.get(i);
            ConfiguredCarverDefinition definition = loaded.get(required);
            if (definition == null || !required.equals(definition.getId())) {
                throw new IllegalStateException(
                        "Missing required configured carver " + required);
            }
        }
        return loaded;
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
