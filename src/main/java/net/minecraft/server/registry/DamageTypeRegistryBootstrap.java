package net.minecraft.server.registry;

import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.DamageType;
import net.minecraft.server.DamageTypeCodec;
import net.minecraft.server.DamageTypes;
import net.minecraft.server.resource.RegistryDataLoader;
import net.minecraft.server.util.ResourceLocation;

/** Loads and atomically publishes the 26.3-style damage-type registry. */
public final class DamageTypeRegistryBootstrap {
    private static volatile Map<ResourceLocation, DamageType> definitions =
            Collections.emptyMap();
    private static boolean initialized;

    private DamageTypeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        Map<ResourceLocation, DamageType> loaded = load(null);
        if (!Registries.DAMAGE_TYPE.replaceAllAtomic(loaded)) {
            throw new IllegalStateException(
                    "Could not atomically publish damage-type definitions");
        }
        definitions = Collections.unmodifiableMap(
                new LinkedHashMap<ResourceLocation, DamageType>(loaded));
        initialized = true;
        System.out.println("[DamageTypeRegistryBootstrap] Loaded "
                + loaded.size() + " damage types");
    }

    public static DamageType get(ResourceLocation key) {
        initialize();
        return definitions.get(key);
    }

    public static Map<ResourceLocation, DamageType> rawDefinitions() {
        initialize();
        return definitions;
    }

    public static Set<ResourceLocation> keys() {
        initialize();
        return definitions.keySet();
    }

    public static Collection<DamageType> values() {
        initialize();
        return definitions.values();
    }

    public static int size() {
        initialize();
        return definitions.size();
    }

    public static List<ResourceLocation> builtInKeys() {
        return DamageTypes.builtInTypes();
    }

    static Map<ResourceLocation, DamageType> loadForTests(
            RegistryDataLoader.ResourceProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException(
                    "Resource provider cannot be null");
        }
        return load(provider);
    }

    private static Map<ResourceLocation, DamageType> load(
            RegistryDataLoader.ResourceProvider provider) {
        RegistryDataLoader.Decoder<DamageType> decoder =
                new RegistryDataLoader.Decoder<DamageType>() {
                    public DamageType decode(
                            ResourceLocation key, JsonObject json) {
                        return DamageTypeCodec.decode(key, json);
                    }
                };
        Map<ResourceLocation, DamageType> decoded = provider == null
                ? RegistryDataLoader.loadAll("damage_type", decoder)
                : RegistryDataLoader.loadAll(
                        "damage_type", provider, decoder);
        for (ResourceLocation required : DamageTypes.builtInTypes()) {
            if (decoded.get(required) == null) {
                throw new IllegalStateException(
                        "Missing required damage type definition " + required);
            }
        }
        return decoded;
    }
}
