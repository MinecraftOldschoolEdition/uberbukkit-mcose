package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Loads, validates, binds, and atomically publishes legacy world presets. */
public final class WorldPresetDataBootstrap {
    public static final ResourceLocation DEFAULT = key("default");
    public static final ResourceLocation DEFAULT_1_1 = key("default_1_1");
    public static final ResourceLocation LARGE_BIOMES = key("large_biomes");
    public static final ResourceLocation ALPHA = key("alpha");
    public static final ResourceLocation ALPHA_SNOW = key("alpha_snow");
    public static final ResourceLocation FLAT = key("flat");
    public static final ResourceLocation SKY = key("sky");
    public static final ResourceLocation CLASSIC = key("classic");
    public static final ResourceLocation INFDEV = key("infdev");

    public static final List<ResourceLocation> REQUIRED_PRESETS =
            Collections.unmodifiableList(Arrays.asList(
                    DEFAULT,
                    DEFAULT_1_1,
                    LARGE_BIOMES,
                    ALPHA,
                    ALPHA_SNOW,
                    FLAT,
                    SKY,
                    CLASSIC,
                    INFDEV));

    private static final Map<Integer, ResourceLocation> TERRAIN_BINDINGS =
            buildTerrainBindings();
    private static volatile Map<ResourceLocation, WorldPresetDefinition> rawPresets =
            Collections.emptyMap();
    private static boolean initialized;

    private WorldPresetDataBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        DimensionTypeRegistryBootstrap.initialize();
        ChunkGeneratorTypeRegistryBootstrap.initialize();
        Map<ResourceLocation, WorldPresetDefinition> decoded = decode(null);
        if (!Registries.WORLD_PRESET.replaceAllAtomic(decoded)) {
            throw new IllegalStateException(
                    "Could not atomically publish world-preset definitions");
        }
        rawPresets = Collections.unmodifiableMap(
                new LinkedHashMap<ResourceLocation, WorldPresetDefinition>(decoded));
        initialized = true;
        System.out.println("[WorldPresetDataBootstrap] Loaded "
                + decoded.size() + " world presets; bound "
                + TERRAIN_BINDINGS.size() + " legacy terrain types");
    }

    public static Map<ResourceLocation, WorldPresetDefinition> rawPresets() {
        initialize();
        return rawPresets;
    }

    public static Map<Integer, ResourceLocation> terrainBindings() {
        return TERRAIN_BINDINGS;
    }

    public static WorldPresetDefinition presetForTerrainType(int terrainType) {
        initialize();
        ResourceLocation presetKey = TERRAIN_BINDINGS.get(Integer.valueOf(terrainType));
        if (presetKey == null) presetKey = DEFAULT;
        WorldPresetDefinition preset = rawPresets.get(presetKey);
        if (preset == null) {
            throw new IllegalStateException(
                    "Missing bound world preset " + presetKey
                            + " for terrain type " + terrainType);
        }
        return preset;
    }

    public static ResourceLocation generatorKeyFor(
            int terrainType, ResourceLocation dimensionKey) {
        if (dimensionKey == null) {
            throw new IllegalArgumentException("Dimension key cannot be null");
        }
        WorldPresetDefinition preset = presetForTerrainType(terrainType);
        WorldPresetDefinition.DimensionStem stem = preset.getDimension(dimensionKey);
        if (stem == null) {
            throw new IllegalStateException(
                    "World preset " + preset.getId()
                            + " has no dimension " + dimensionKey);
        }
        return stem.getGeneratorType();
    }

    public static ResourceLocation dimensionTypeKeyFor(
            int terrainType, ResourceLocation dimensionKey) {
        if (dimensionKey == null) {
            throw new IllegalArgumentException("Dimension key cannot be null");
        }
        WorldPresetDefinition preset = presetForTerrainType(terrainType);
        WorldPresetDefinition.DimensionStem stem = preset.getDimension(dimensionKey);
        if (stem == null) {
            throw new IllegalStateException(
                    "World preset " + preset.getId()
                            + " has no dimension " + dimensionKey);
        }
        return stem.getDimensionType();
    }

    static Map<ResourceLocation, WorldPresetDefinition> decodeForTests(
            RegistryDataLoader.ResourceProvider provider) {
        DimensionTypeRegistryBootstrap.initialize();
        ChunkGeneratorTypeRegistryBootstrap.initialize();
        return decode(provider);
    }

    private static Map<ResourceLocation, WorldPresetDefinition> decode(
            RegistryDataLoader.ResourceProvider provider) {
        RegistryDataLoader.Decoder<WorldPresetDefinition> decoder =
                new RegistryDataLoader.Decoder<WorldPresetDefinition>() {
                    public WorldPresetDefinition decode(
                            ResourceLocation key, JsonObject json) {
                        return WorldPresetCodec.decode(key, json);
                    }
                };
        Map<ResourceLocation, WorldPresetDefinition> decoded = provider == null
                ? RegistryDataLoader.loadAll("worldgen/world_preset", decoder)
                : RegistryDataLoader.loadAll("worldgen/world_preset", provider, decoder);
        for (int i = 0; i < REQUIRED_PRESETS.size(); i++) {
            ResourceLocation required = REQUIRED_PRESETS.get(i);
            WorldPresetDefinition preset = decoded.get(required);
            if (preset == null) {
                throw new IllegalStateException("Missing required world preset: " + required);
            }
            requireDimension(preset, WorldPresetGeneratorRouting.OVERWORLD);
            requireDimension(preset, WorldPresetGeneratorRouting.THE_NETHER);
            requireDimension(preset, WorldPresetGeneratorRouting.SKY);
        }
        return decoded;
    }

    private static void requireDimension(
            WorldPresetDefinition preset, ResourceLocation dimensionKey) {
        if (preset.getDimension(dimensionKey) == null) {
            throw new IllegalStateException(
                    "Required world preset " + preset.getId()
                            + " is missing dimension " + dimensionKey);
        }
    }

    private static Map<Integer, ResourceLocation> buildTerrainBindings() {
        LinkedHashMap<Integer, ResourceLocation> bindings =
                new LinkedHashMap<Integer, ResourceLocation>();
        bindings.put(Integer.valueOf(0), DEFAULT);
        bindings.put(Integer.valueOf(1), ALPHA);
        bindings.put(Integer.valueOf(2), FLAT);
        bindings.put(Integer.valueOf(3), SKY);
        bindings.put(Integer.valueOf(5), ALPHA_SNOW);
        bindings.put(Integer.valueOf(6), CLASSIC);
        bindings.put(Integer.valueOf(7), INFDEV);
        return Collections.unmodifiableMap(bindings);
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
