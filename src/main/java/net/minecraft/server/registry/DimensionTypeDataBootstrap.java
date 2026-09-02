package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Loads and atomically publishes typed dimension metadata. */
public final class DimensionTypeDataBootstrap {
    public static final ResourceLocation OVERWORLD = key("overworld");
    public static final ResourceLocation THE_NETHER = key("the_nether");
    public static final ResourceLocation SKY = key("sky");
    public static final List<ResourceLocation> REQUIRED_TYPES =
            Collections.unmodifiableList(Arrays.asList(OVERWORLD, THE_NETHER, SKY));

    private static volatile Map<ResourceLocation, DimensionTypeDefinition> rawDefinitions =
            Collections.emptyMap();
    private static boolean initialized;

    private DimensionTypeDataBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        Map<ResourceLocation, DimensionTypeDefinition> decoded = decode(null);
        if (!Registries.DIMENSION_TYPE.replaceAllAtomic(decoded)) {
            throw new IllegalStateException(
                    "Could not atomically publish dimension-type definitions");
        }
        rawDefinitions = immutableCopy(decoded);
        initialized = true;
        System.out.println("[DimensionTypeDataBootstrap] Loaded "
                + decoded.size() + " dimension types");
    }

    public static Map<ResourceLocation, DimensionTypeDefinition> rawDefinitions() {
        initialize();
        return rawDefinitions;
    }

    public static DimensionTypeDefinition get(ResourceLocation key) {
        initialize();
        return rawDefinitions.get(key);
    }

    static Map<ResourceLocation, DimensionTypeDefinition> decodeForTests(
            RegistryDataLoader.ResourceProvider provider) {
        return decode(provider);
    }

    private static Map<ResourceLocation, DimensionTypeDefinition> decode(
            RegistryDataLoader.ResourceProvider provider) {
        RegistryDataLoader.Decoder<DimensionTypeDefinition> decoder =
                new RegistryDataLoader.Decoder<DimensionTypeDefinition>() {
                    public DimensionTypeDefinition decode(
                            ResourceLocation key, JsonObject json) {
                        return DimensionTypeCodec.decode(key, json);
                    }
                };
        Map<ResourceLocation, DimensionTypeDefinition> decoded = provider == null
                ? RegistryDataLoader.loadAll("dimension_type", decoder)
                : RegistryDataLoader.loadAll("dimension_type", provider, decoder);
        for (int i = 0; i < REQUIRED_TYPES.size(); i++) {
            ResourceLocation required = REQUIRED_TYPES.get(i);
            if (!decoded.containsKey(required)) {
                throw new IllegalStateException(
                        "Missing required dimension type: " + required);
            }
        }
        return decoded;
    }

    private static Map<ResourceLocation, DimensionTypeDefinition> immutableCopy(
            Map<ResourceLocation, DimensionTypeDefinition> values) {
        return Collections.unmodifiableMap(
                new LinkedHashMap<ResourceLocation, DimensionTypeDefinition>(values));
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
